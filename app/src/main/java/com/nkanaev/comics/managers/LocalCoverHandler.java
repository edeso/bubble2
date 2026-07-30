package com.nkanaev.comics.managers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.nkanaev.comics.parsers.Parser;
import com.nkanaev.comics.parsers.ParserFactory;
import com.nkanaev.comics.view.CoverImageView;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import java.io.*;


public class LocalCoverHandler extends RequestHandler {

    private final static String HANDLER_URI = "localcover";
    private Context mContext;

    public LocalCoverHandler(Context context) {
        mContext = context;
    }

    @Override
    public boolean canHandleRequest(Request data) {
        return HANDLER_URI.equals(data.uri.getScheme());
    }

    @Override
    public Result load(Request data, int networkPolicy) throws IOException {
        Bitmap cover = getCover(data.uri, null);
        return new Result(cover, Picasso.LoadedFrom.DISK);
    }

    public static Bitmap createCover(Comic c, InputStream is) throws IOException {
        if (c == null)
            return null;

        return getCover(getComicCoverUri(c), is);
    }

    public static Bitmap getCover(Comic c) throws IOException {
        if (c == null)
            return null;

        return getCover(getComicCoverUri(c), null);
    }

    private static Bitmap getCover(Uri comicUri, InputStream coverStream) throws IOException {
        Context ctx = MainApplication.getAppContext();

        Integer id = Integer.valueOf(comicUri.getFragment());
        Comic c = Storage.getStorage(ctx).getComic(id);
        File coverFile = Utils.getCoverCacheFile(c);

        // reuse saved cover cache file
        if (coverFile.isFile()) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            // Bitmap.Config.HARDWARE uses less memory
            if (Utils.isOreoOrLater())
                options.inPreferredConfig = Bitmap.Config.HARDWARE;
            Bitmap bitmap = BitmapFactory.decodeFile(coverFile.getAbsolutePath(), options);
            if (bitmap != null)
                return bitmap;
        }

        Parser parser = null;
        BufferedInputStream bis = null;
        FileOutputStream outputStream = null;
        try {
            InputStream stream;
            if ( coverStream != null) {
                stream = coverStream;
            } else {
                parser = ParserFactory.create(comicUri.getPath());

                if (parser == null)
                    throw new IOException("no parser for '" + comicUri + "'.");
                if (parser.numPages() < 1)
                    throw new IOException("comic '" + comicUri + "' has no pages.");

                stream = parser.getPage(0);

                // update db entry
                Storage.getStorage(ctx).updateBook(c.getId(), parser.getType(), parser.numPages());
            }

            bis = new BufferedInputStream(stream);
            byte[] data = Utils.toByteArray(bis);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);
            options.inSampleSize = Utils.calculateInSampleSize(options,
                    Constants.COVER_THUMBNAIL_WIDTH, Constants.COVER_THUMBNAIL_HEIGHT);
            options.inJustDecodeBounds = false;
            //options.inPreferredConfig = Bitmap.Config.RGB_565;

            Bitmap result = BitmapFactory.decodeByteArray(data, 0, data.length, options);

            // crop result to coverlike dimensions if needed
            int height = result.getHeight();
            int width = result.getWidth();
            int hLimit = (int) (width * (1 / CoverImageView.FACTOR));
            int wLimit = (int) (height * (2.5 * CoverImageView.FACTOR));
            Bitmap oldResult = result;
            if (height > width && height > hLimit) {
                result = Bitmap.createBitmap(result, 0, 0, width, hLimit);
                oldResult.recycle();
            } else if (width > height && width > wLimit) {
                result = Bitmap.createBitmap(result, width - wLimit - 1, 0, wLimit, height);
                oldResult.recycle();
            }

            // corner case, cache folder might be missing
            File folder = coverFile.getParentFile();
            if (!folder.exists())
                folder.mkdirs();

            // cache result
            synchronized (LocalCoverHandler.class) {
                if (coverFile != null) {
                    outputStream = new FileOutputStream(coverFile);
                    boolean success = result.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
                    if (!success)
                        coverFile.delete();
                }
            }

            return result;
        } catch (Exception e) {
            Log.e("LocalCoverHandler.getCover", c.getFile().toString(), e);
            if (!(e instanceof IOException))
                e = new IOException(e);
            throw (IOException) e;
        } finally {
            Utils.close(parser);
            Utils.close(bis);
            Utils.close(outputStream);
        }

    }

    public static Uri getComicCoverUri(Comic comic) {
        Uri.Builder b = new Uri.Builder().scheme(HANDLER_URI);
        if (comic != null) {
            b.path(comic.getFile().getAbsolutePath());
            // fragment containing the db id is actually used
            b.fragment(String.valueOf(comic.getId()));
        }
        return b.build();
    }
}
