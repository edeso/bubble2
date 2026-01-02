package com.nkanaev.comics.managers;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.nkanaev.comics.parsers.Parser;
import com.nkanaev.comics.parsers.ParserFactory;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.*;

public class Scanner {
    private Thread mUpdateThread;
    private List<Handler> mUpdateHandler;

    private boolean mIsStopped;
    private boolean mIsRestarted;
    private File mSubFolder = null;
    private boolean mRefreshAll = false;

    private Handler mRestartHandler = new RestartHandler(this);

    private static class RestartHandler extends Handler {
        private WeakReference<Scanner> mScannerRef;

        public RestartHandler(Scanner scanner) {
            mScannerRef = new WeakReference<>(scanner);
        }

        @Override
        public void handleMessage(Message msg) {
            Scanner scanner = mScannerRef.get();
            if (scanner != null) {
                scanner.scanLibrary();
            }
        }
    }

    private static Scanner mInstance;

    public synchronized static Scanner getInstance() {
        if (mInstance == null) {
            mInstance = new Scanner();
        }
        return mInstance;
    }

    private Scanner() {
        mInstance = this;
        mUpdateHandler = new ArrayList<>();
    }

    public boolean isRunning() {
        return mUpdateThread != null &&
                mUpdateThread.isAlive() &&
                mUpdateThread.getState() != Thread.State.TERMINATED &&
                mUpdateThread.getState() != Thread.State.NEW;
    }

    public void stop() {
        mIsStopped = true;
    }

    public void scanLibrary() {
        scanLibrary(null, false);
    }

    public void scanLibrary(File limitToSubFolder, boolean refreshAll) {
        // already running instances are informed to stop and restart with new parameters
        if (isRunning()) {
            mSubFolder = limitToSubFolder;
            mRefreshAll = refreshAll;
            mIsStopped = true;
            mIsRestarted = true;
            return;
        }

        if (mUpdateThread == null || mUpdateThread.getState() == Thread.State.TERMINATED) {
            LibraryUpdateRunnable runnable = new LibraryUpdateRunnable();
            mSubFolder = limitToSubFolder;
            mRefreshAll = refreshAll;
            mUpdateThread = new Thread(runnable);
            mUpdateThread.setPriority(Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE);
            mUpdateThread.start();
        }
    }

    public void addUpdateHandler(Handler handler) {
        mUpdateHandler.add(handler);
    }

    public void removeUpdateHandler(Handler handler) {
        mUpdateHandler.remove(handler);
    }

    private void notifyMediaUpdated() {
        for (Handler h : mUpdateHandler) {
            h.sendEmptyMessage(Constants.MESSAGE_MEDIA_UPDATED);
        }
    }

    private void notifyLibraryUpdateFinished() {
        for (Handler h : mUpdateHandler) {
            h.sendEmptyMessage(Constants.MESSAGE_MEDIA_UPDATE_FINISHED);
        }
    }

    private static Comic findComicInList(List<Comic> comics, File file) {
        if (file != null && comics != null)
            for (Comic comic : comics) {
                if (comic.getFile().equals(file))
                    return comic;
            }
        return null;
    }

    private class LibraryUpdateRunnable implements Runnable {

        public void refreshAll(boolean refreshAll) {
            mRefreshAll = refreshAll;
        }

        public void limitToSubFolder(File subFolder) {
            mSubFolder = subFolder;
        }

        @Override
        public void run() {
            try {
                Context ctx = MainApplication.getAppContext();
                String libDir = MainApplication.getPreferences()
                        .getString(Constants.SETTINGS_LIBRARY_DIR, "");

                if (libDir.equals(""))
                    return;

                File libDirFile = new File(libDir);
                if (mSubFolder != null) {
                    File folder = mSubFolder.getAbsoluteFile();
                    File absLibDir = libDirFile.getAbsoluteFile();
                    boolean isSubFolder = false;
                    do {
                        if (folder.equals(absLibDir)) {
                            isSubFolder = true;
                            break;
                        }
                    } while ((folder = folder.getParentFile()) != null);
                    if (!isSubFolder)
                        return;
                }

                Storage storage = Storage.getStorage(ctx);
                //Map<File, Comic> storageFiles = new HashMap<>();
                List<Comic> storedComics = storage.listComics(mSubFolder != null ? mSubFolder.toString() : null);

                // create list of "known" files available in storage database
                // for defined subfolder scan all (forced)
                // or null (libDir)
    /*            for (Comic c : storage.listComics(mSubFolder != null ? mSubFolder.toString() : null)) {
                    // memorize known
                    if (mSubFolder != null || c.getTotalPages() > 1) {
                        storageFiles.put(c.getFile(), c);
                    }
                    // rescan empties
                    else {
                        storage.removeComic(c.getId());
                    }
                }
*/
                File baseFolder = mSubFolder != null ? mSubFolder : libDirFile;
                // initial run, add to db for quick ui result, parse in second pass
                Deque<File> directories = new ArrayDeque<>();
                directories.add(baseFolder);
                long lastNotifyMediaUpdated = Utils.now();
                while (!directories.isEmpty()) {
                    if (mIsStopped)
                        break;

                    File dir = directories.pop();
                    File[] files = dir.listFiles();
                    // no files, no fun
                    if (files == null)
                        continue;

                    for (File file : files) {
                        if (mIsStopped)
                            break;

                        // add folder to search list, but continue (might be a dir-comic)
                        if (mSubFolder == null && file.isDirectory()) {
                            directories.add(file);
                        }

                        if (file.isFile() && !Utils.isArchive(file.getName())) {
                            // ignore unknown files
                            continue;
                        } else if (file.isDirectory() && !Utils.isDir(file.getAbsolutePath())) {
                            // ignore non-image folders
                            continue;
                        }

                        // add/update comic meta data for next run
                        // keep empty comic files, might point to bugs
                        Comic storedComic = findComicInList(storedComics, file);
                        // new book
                        if (storedComic != null){
                            // memorize "refound" comic
                            storedComics.remove(storedComic);
                        } else {
                            storage.addBook(file, null, 0);
                        }
                    }

                    // notify for each folder, maximum every 0.5s
                    if (Utils.milliSecondsSince(lastNotifyMediaUpdated) > 500) {
                        notifyMediaUpdated();
                        lastNotifyMediaUpdated = Utils.now();
                    }
                }

                // leftover storedComics weren't found, let's remove them
                // unless we were rudely interrupted
                if (!mIsStopped)
                    for (Comic missing : storedComics) {
                        Utils.deleteCoverCacheFile(missing);
                        storage.removeComic(missing.getId());
                    }

                notifyMediaUpdated();

                // second pass: search and parse zero-page comics
                storedComics = storage.listComics(mSubFolder != null ? mSubFolder.toString() : null);
                for (int i = 0; i < storedComics.size() && !mIsStopped; i++) {

                    Comic storedComic = storedComics.get(i);
                    // ignore known parsed comics, unless update is forced
                    if (!mRefreshAll && storedComic.getTotalPages() > 0)
                        continue;

                    Parser parser = null;
                    int count = 0;
                    try {
                        parser = ParserFactory.create(storedComic.getFile());
                        Log.d("Scanner#244", storedComic.getFile().toString());
                        // no parser? check log
                        if (parser == null)
                            continue;

                        try {
                            parser.parse();
                            count = parser.numPages();
                            // cache cover using already initialized parser
                            LocalCoverHandler.createCover(storedComic,parser.getPage(0));
                        } catch (Exception e) {
                            Log.e("Scanning#253", "parse", e);
                        } finally {
                            Utils.close(parser);
                        }

                        // update comic, keep empty comic files, might point to bugs
                        storage.updateBook(storedComic.getId(), parser.getType(), count);
                        Utils.deleteCoverCacheFile(storedComic);

                        // notify library views, maximum every 0.5s
                        if (Utils.milliSecondsSince(lastNotifyMediaUpdated) > 500) {
                            notifyMediaUpdated();
                            lastNotifyMediaUpdated = Utils.now();
                        }
                    } catch (Exception e) {
                        Log.e("Scanner", "update", e);
                    }
                }

            } finally {
                mIsStopped = false;

                if (mIsRestarted) {
                    mIsRestarted = false;
                    mRestartHandler.sendEmptyMessageDelayed(1, 200);
                } else {
                    notifyLibraryUpdateFinished();
                }
            }
        }
    }
}
