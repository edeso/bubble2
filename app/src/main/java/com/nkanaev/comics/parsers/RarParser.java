package com.nkanaev.comics.parsers;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.managers.IgnoreCaseComparator;
import com.nkanaev.comics.managers.Utils;

import java.io.*;
import java.util.*;


public class RarParser extends AbstractParser {
    private ArrayList<FileHeader> mHeaders = new ArrayList<>();
    private Archive mArchive;
    private boolean mParsedAlready = false;
    private File mCacheDir;
    private boolean mSolidFileExtracted = false;

    public RarParser() {
        super(new Class[]{InputStream.class, File.class});
    }

    @Override
    public synchronized void parse() throws IOException {
        if (mParsedAlready)
            return;

        Object source = getSource();
        try {
            if (source instanceof InputStream)
                mArchive = new Archive((InputStream) source);
            else
                mArchive = new Archive((File) source);
            parseArchive();
            mParsedAlready = true;
        } catch (RarException e) {
           throw new IOException("unable to open archive", e);
        }
    }

    private void parseArchive() throws IOException {
        mHeaders = new ArrayList<>();
        FileHeader header = mArchive.nextFileHeader();
        while (header != null) {
            if (!header.isDirectory()) {
                String name = getName(header);
                if (Utils.isImage(name)) {
                    mHeaders.add(header);
                }
            }

            header = mArchive.nextFileHeader();
        }

        Collections.sort(mHeaders, new IgnoreCaseComparator() {
            @Override
            public String stringValue(Object o) {
                return getName((FileHeader) o);
            }
        });
    }

    private String getName(FileHeader header) {
        return header.getFileName();
    }

    @Override
    public int numPages() throws IOException {
        parse();
        return mHeaders.size();
    }

    @Override
    public InputStream getPage(int num) throws IOException {
        // make sure we're parsed
        parse();

        if (mArchive.getMainHeader().isSolid()) {
            // solid archives require special treatment
            synchronized (this) {
                if (!mSolidFileExtracted && num != 0) {
                    for (FileHeader h : mArchive.getFileHeaders()) {
                        if (!h.isDirectory() && Utils.isImage(getName(h))) {
                            getPageStream(h, true);
                        }
                    }
                    mSolidFileExtracted = true;
                }
            }
        }
        return getPageStream(mHeaders.get(num), mArchive.getMainHeader().isSolid());
    }

    private InputStream getPageStream(FileHeader header, boolean cache) throws IOException {
        try {
            synchronized (this) {
                if (cache) {
                    if (mCacheDir == null)
                        initCacheDirectory(MainApplication.getAppContext().getExternalCacheDir());

                    String name = getName(header);
                    File cacheFile = new File(mCacheDir, Utils.MD5(name));

                    if (cacheFile.exists()) {
                        return new FileInputStream(cacheFile);
                    }

                    if (!cacheFile.exists()) {
                        FileOutputStream os = new FileOutputStream(cacheFile);
                        try {
                            mArchive.extractFile(header, os);
                        } catch (Exception e) {
                            os.close();
                            cacheFile.delete();
                            throw e;
                        }
                        os.close();
                    }

                    return new FileInputStream(cacheFile);
                }

                return new ByteArrayInputStream(Utils.toByteArray(mArchive.getInputStream(header)));
            }
        } catch (RarException e) {
            throw new IOException("unable to parse rar");
        }
    }

    @Override
    public Map getPageMetaData(int num) throws IOException {
        parse();
        Map m = new HashMap();
        m.put(Parser.PAGEMETADATA_KEY_NAME,mHeaders.get(num).getFileName());
        return m;
    }

    @Override
    public void destroy() {
        mParsedAlready = false;
        mSolidFileExtracted = false;
        if (mCacheDir != null) {
            for (File f : mCacheDir.listFiles()) {
                f.delete();
            }
            mCacheDir.delete();
            mCacheDir = null;
        }
        Utils.close(mArchive);
    }

    @Override
    public String getType() {
        return "Rar";
    }

    private void initCacheDirectory(File cacheDirectory) {
        String uuid = UUID.randomUUID().toString();
        mCacheDir = new File(cacheDirectory, "rar-" + uuid);
        if (!mCacheDir.exists()) {
            boolean success = mCacheDir.mkdirs();
        }
    }

}
