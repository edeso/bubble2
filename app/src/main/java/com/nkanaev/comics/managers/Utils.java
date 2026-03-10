package com.nkanaev.comics.managers;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Insets;
import android.opengl.*;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Toast;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import com.gemalto.jp2.JP2Decoder;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.R;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.nkanaev.comics.parsers.Parser;

import javax.microedition.khronos.egl.EGL10;
import java.io.*;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

import static android.content.Context.ACTIVITY_SERVICE;

public final class Utils {
    public static int getScreenDpWidth(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return Math.round(displayMetrics.widthPixels / displayMetrics.density);
    }

    public static boolean isIceCreamSandwitchOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    public static boolean isHoneycombOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean isHoneycombMR1orLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;
    }

    public static boolean isJellyBeanMR1orLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    public static boolean isKitKatOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    public static boolean isLollipopOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public static boolean isMarshmallowOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    // FileChannel
    public static boolean isNougatOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    // java.nio.path
    public static boolean isOreoOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    // full edge-to-edge support
    public static boolean isQOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public static boolean isROrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    // forced edge-to-edge
    public static boolean isVanillaIceCreamOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM;
    }

    public static int getHeapSize(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        boolean isLargeHeap = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
        int memoryClass = am.getMemoryClass();
        if (isLargeHeap && Utils.isHoneycombOrLater()) {
            memoryClass = am.getLargeMemoryClass();
        }
        return 1024 * memoryClass;
    }

    public static int calculateBitmapSize(Bitmap bitmap) {
        int sizeInBytes;
        if (Utils.isHoneycombMR1orLater()) {
            sizeInBytes = bitmap.getByteCount();
        } else {
            sizeInBytes = bitmap.getRowBytes() * bitmap.getHeight();
        }
        return sizeInBytes / 1024;
    }

    public static boolean isImage(String filename) {
        return filename.matches("(?i).*\\.(jpe?g|bmp|gif|png|webp|jp2|j2k)$");
    }

    public static boolean isJP2Stream(BufferedInputStream bis) throws IOException {
        bis.mark(24);
        // TODO: JP2Decoder may need only 12bytes looks like, read 24 anyway
        byte[] b = new byte[24];
        bis.read(b);
        bis.reset();
        return JP2Decoder.isJPEG2000(b);
    }

    public static boolean isRarStream(InputStream is) {
        final byte[] rarSignature = new byte[]{'R', 'a', 'r', '!', 0x1A, 0x07};
        return inputStreamStartsWith(is, rarSignature);
    }

    public static boolean isPdfStream(InputStream is) {
        final byte[] pdfSignature = {'%', 'P', 'D', 'F', '-'};
        return inputStreamStartsWith(is, pdfSignature);
    }

    public static boolean isSevenZStream(InputStream is) {
        final byte[] sevenZSignature = {'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
        return inputStreamStartsWith(is, sevenZSignature);
    }

    public static boolean isZipStream(InputStream is) {
        final byte[] zipSignature = {'P', 'K', (byte) 0x03, (byte) 0x04};
        return inputStreamStartsWith(is, zipSignature);
    }

    public static boolean isTarStream(InputStream is) {
        final byte[] tarSignature = {'u', 's', 't', 'a', 'r'};
        return inputStreamStartsWith(is, tarSignature, 257);
    }

    private static boolean inputStreamStartsWith(InputStream is, byte[] bytesIn) {
        return inputStreamStartsWith(is, bytesIn, 0);
    }

    private static boolean inputStreamStartsWith(InputStream is, byte[] bytesIn, int offset){
        try {
            if (bytesIn == null)
                throw new IllegalArgumentException("bytesIn must not be Null");
            if (!is.markSupported())
                throw new IllegalArgumentException("inputStream must support mark/reset");

            byte[] isHeader = new byte[bytesIn.length];
            is.mark(offset + bytesIn.length);
            is.skip(offset);
            is.read(isHeader);
            is.reset();
            return Arrays.equals(bytesIn,isHeader);
        } catch (Exception e) {
            Log.e("Utils.isRarStream","",e);
        }
        return false;
    }

    public static boolean isDir(String filename) {
        File dir = new File(filename);
        if (!dir.isDirectory())
            return false;

        // find an image, image folder
        File[] files = dir.listFiles();
        if (files != null)
            for (File f : dir.listFiles()) {
                if (f.isFile() && Utils.isImage(f.getName())) {
                    return true;
                }
            }

        return false;
    }

    public static boolean isPdf(String filename) {
        return filename.matches("(?i).*\\.(pdf)$" );
    }

    public static boolean isZip(String filename) {
        return filename.matches("(?i).*\\.(zip|cbz)$");
    }

    public static boolean isRar(String filename) {
        return filename.matches("(?i).*\\.(rar|cbr)$");
    }

    public static boolean isTarball(String filename) {
        return filename.matches("(?i).*\\.(cbt|tar)$") ||
                isCompressedTarball(filename);
    }

    public static boolean isCompressedTarball(String filename) {
        return isTBR(filename) || isTBZ(filename) ||
                isTGZ(filename) || isTLZ(filename) ||
                isTXZ(filename) || isTZST(filename);
    }

    public static boolean isTGZ(String filename) {
        return filename.matches("(?i).*\\.(tar\\.gz|tgz)$");
    }

    public static boolean isTBZ(String filename) {
        return filename.matches("(?i).*\\.(tar\\.bz2?|tbz2?)$");
    }

    public static boolean isTLZ(String filename) {
        return filename.matches("(?i).*\\.(tar\\.lzma|tlz)$");
    }

    public static boolean isTXZ(String filename) {
        return filename.matches("(?i).*\\.(tar\\.xz|txz)$");
    }

    public static boolean isTZST(String filename) {
        return filename.matches("(?i).*\\.(tar\\.zstd?|tzs(|t|td))$");
    }

    public static boolean isTBR(String filename) {
        return filename.toLowerCase().matches("(?i).*\\.(tar\\.br|tbr)$");
    }

    public static boolean isSevenZ(String filename) {
        return filename.matches("(?i).*\\.(cb7|7z)$");
    }

    public static boolean isArchive(String filename) {
        return isZip(filename) || isRar(filename) ||
                isTarball(filename) || isSevenZ(filename) ||
                isPdf(filename);
    }

    static List<String> mimeTypesZip = Arrays.asList("application/zip", "application/cbz", "application/x-cbz");
    static List<String> mimeTypesRar = Arrays.asList("application/rar", "application/cbr", "application/x-cbr");
    static List<String> mimeTypesSevenZ = Arrays.asList("application/x-cb7", "application/x-7z-compressed");
    static List<String> mimeTypesTar = Arrays.asList("application/x-cbt", "application/x-compressed-tar", "application/x-bzip-compressed-tar", "application/x-tar", "application/x-gtar");
    static List<String> mimeTypesPdf = Arrays.asList("application/pdf", "application/x-pdf");
    public static String dummyFileNameFromMimeType(String mimeType){
        mimeType = mimeType.toLowerCase();
        if (mimeTypesZip.contains(mimeType))
            return "dummy.zip";
        else if (mimeTypesRar.contains(mimeType))
            return "dummy.rar";
        else if (mimeTypesSevenZ.contains(mimeType))
            return "dummy.7z";
        else if (mimeTypesTar.contains(mimeType))
            return "dummy.tar";
        else if (mimeTypesPdf.contains(mimeType))
            return "dummy.pdf";
        return null;
    }

    public static int getDeviceWidth(Context context) {
        DisplayMetrics displayMetrics = MainApplication.getAppContext().getResources().getDisplayMetrics();
        WindowManager windowManager = (WindowManager) MainApplication.getAppContext().getSystemService(Context.WINDOW_SERVICE);
        // this gives wrong scaledDensity, also it is nonsensical,
        // why should the current screen be the default screen?
        //DisplayMetrics displayMetrics = new DisplayMetrics();
        //windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        // the old way
        int width = displayMetrics.widthPixels;
        // using scaledDensity, because on PocoX4Pro5G 'density=2.75'
        // but 'scaledDensity=2.25' which is the accurate value
        float density = displayMetrics.scaledDensity;
        // the new way
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
            Insets insets = windowMetrics.getWindowInsets()
                    .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            if (Build.VERSION.SDK_INT >= 34) {
                //density = windowMetrics.getDensity();
            }
            width = Math.abs(windowMetrics.getBounds().width()) - insets.left - insets.right;
        }

        int value = Math.round(width / density);
        return value;
    }

    public static int getDeviceHeightPixels() {
        DisplayMetrics displayMetrics = MainApplication.getAppContext().getResources().getDisplayMetrics();
        return displayMetrics.heightPixels;
    }

    // a virtual limit based on the display pixel dimensions
    public static int getMaxPageSize(){
        return Math.round(1.25f * getMaxDisplayWidth());
    }

    // either display pixel width or height, which ever is bigger
    public static int getMaxDisplayWidth(){
        DisplayMetrics displayMetrics = MainApplication.getAppContext().getResources().getDisplayMetrics();
        int w = displayMetrics.widthPixels;
        int h = displayMetrics.heightPixels;
        return Math.max(w, h);
    }

    public static String MD5(String string) {
        try {
            byte[] strBytes = string.getBytes();
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] digest = messageDigest.digest(strBytes);
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < digest.length; ++i) {
                sb.append(Integer.toHexString((digest[i] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return string.replace("/", ".");
        }
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static int calculateMemorySize(Context context, int percentage) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        int memoryClass = activityManager.getLargeMemoryClass();
        return 1024 * 1024 * memoryClass / percentage;
    }

    private static File getCacheFolder(){
        Context context = MainApplication.getAppContext();
        File dir = context.getExternalCacheDir();
        if (dir == null)
            dir = context.getCacheDir();
        if (dir==null)
            throw new RuntimeException("Couldn't find cache dir!");
        return dir;
    }

    public static File getCoverCacheFile(Comic c) {
        CRC32 crc = new CRC32();
        crc.update(c.getFile().toString().getBytes());
        return getCoverCacheFile(String.format("%08X", crc.getValue()),"jpg");
    }

    private static File getCoverCacheFile(String identifier, String extension) {
        File dir = getCacheFolder();
        return new File(dir, "cover-" + identifier + (extension != null && !extension.isEmpty() ? "." + extension : ""));
    }

    public static void deleteCoverCacheFile(Comic comic) {
        File coverCacheFile = getCoverCacheFile(comic.getFile().getAbsolutePath(), "jpg");
        coverCacheFile.delete();
    }

    public static ByteArrayInputStream toByteArrayInputStream(InputStream is) throws IOException {
        return new ByteArrayInputStream(toByteArray(is));
    }

    public static byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            byte[] b = new byte[4096];
            int n = 0;
            while ((n = is.read(b)) != -1) {
                output.write(b, 0, n);
            }
            return output.toByteArray();
        } catch (Exception e) {
            Log.e("Utils.toByteArray", e.getMessage(), e);
            throw new IOException(e);
        } finally {
            close(output);
            close(is);
        }
    }

    public static void copyToFile(InputStream inStream, File file) throws IOException {
        OutputStream outStream = new FileOutputStream(file);
        copyToOutputStream(inStream,outStream);
    }

    public static void copyToOutputStream(InputStream inStream, OutputStream outStream) throws IOException {
        byte[] buffer = new byte[4 * 1024];
        int bytesRead;
        while ((bytesRead = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, bytesRead);
        }
        close(inStream);
        close(outStream);
    }

    public static void close(ZipFile z) {
        if (z == null) return;
        try {
            //  java.util.zip.ZipFile does not implement Closeable on API 18--
            z.close();
        } catch (final Throwable ignored) {
            // do nothing;
        }
    }

    public static void close(final Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (final Throwable t) {
            Log.e("Bubble2","Utils.close(Closeable)",t);
        }
    }

    public static void close(final Object o) {
        if (o == null) return;
        try {
            Class c = o.getClass();
            Method m;
            if (Parser.class.isAssignableFrom(c)) {
                m = c.getMethod("destroy");
            } else if (Bitmap.class.isAssignableFrom(c)) {
                m = c.getMethod("recycle");
            } else if (Collection.class.isAssignableFrom(c)) {
                m = c.getMethod("clear");
            } else {
                m = c.getMethod("close");
            }
            m.invoke(o);
        } catch (final Throwable t) {
            Log.e("Bubble2","Utils.close(Object)",t);
        }
    }

    public static File initCacheDirectory(String prefix) {
        File appCacheDir = getCacheFolder();
        // create a unique id
        String uuid = UUID.randomUUID().toString();
        prefix = (prefix == null || "".equals(prefix)) ? "" : prefix + "-";
        File mCacheDir = new File(appCacheDir, prefix + uuid);
        if (!mCacheDir.exists()) {
            boolean success = mCacheDir.mkdirs();
        }
        return mCacheDir;
    }

    // remove probably stale folders, exempt files (covers)
    public static void cleanCacheDir(){
        final List<Comic> comics = Storage.getStorage(MainApplication.getAppContext()).listComics();
        new Thread(){
            @Override
            public void run() {
                Set covers = new HashSet<String>(comics.size());
                for (Comic c : comics ) {
                    covers.add(Utils.getCoverCacheFile(c).getName());
                }
                comics.clear();
                File cacheDir = getCacheFolder();
                File[] files = cacheDir.listFiles();
                if (files!=null)
                    for (File f: files) {
                        // leftover unpacked comics
                        if (f.isDirectory())
                            rmDir(f,true);
                        // covers without matching db book entry
                        else if (!covers.contains(f.getName())) {
                            f.delete();
                        }
                    }
                covers.clear();
            }
        }.run();
    }

    public static void rmDir(File dir) {
        rmDir(dir, true);
    }

    public static void rmDir(File dir, boolean recursive) {
        if (dir == null || !dir.isDirectory())
            return;

        File[] files = dir.listFiles();
        if (files != null)
            for (File f : files) {
                if (recursive && f.isDirectory())
                    rmDir(f, true);
                else if (f.isFile())
                    f.delete();
            }
        dir.delete();
    }

    public static File[] listExternalStorageDirs() {
        File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(MainApplication.getAppContext(), null);
        // strip null entries (at least API16)
        ArrayList<File> entries = new ArrayList();
        for (int i = 0; externalFilesDirs != null && i < externalFilesDirs.length; i++) {
            File entry = externalFilesDirs[i];
            if (entry!=null)
                entries.add(entry);
        }
        return entries.toArray(new File[0]);
    }

    public static void showOKDialog(Activity activity, String title, String message) {
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.AppCompatAlertDialogStyle)
                .setTitle(title)
                .setMessage(message)
                .setNeutralButton(R.string.alert_action_neutral, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // nothing to do
                    }
                })
                .create();
        dialog.show();
    }

    public static String appendSlashIfMissing(String path){
        // don't treat empty strings
        if (path==null || path.equals(""))
            return path;

        if (!path.endsWith("/"))
            path += "/";

        return path;
    }

    public static String removeExtensionIfAny(String fileName) {
        int i = fileName.lastIndexOf('.');
        if (i < 1)
            return fileName;

        String name = fileName.substring(0, i);
        return name;
    }

    public static @ColorInt int getThemeColor(@AttrRes int resid, @StyleRes int themeid){
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = new ContextThemeWrapper(MainApplication.getAppContext(), themeid).getTheme();
        if (theme.resolveAttribute(resid, typedValue, true)) {
            @ColorInt int color = typedValue.data;
            return color;
        }
        return 0;
    }

    /**
     *  a helper method to measure time frames in milliseconds
     * @return current time in milliseconds
     */
    public static long now(){
        return System.currentTimeMillis();
    }

    /**
     *  a helper method to measure time frames in milliseconds
     * @param i time in milliseconds
     * @return milliseconds since time
     */
    public static long milliSecondsSince( long i ){
        return System.currentTimeMillis() - i;
    }

    /**
     * a helper method to nicely format the above output e.g. 12046ms -> 12.05s
     * @param i milliseconds
     * @return eg. "1.05" for a difference of 1047ms
     */
    public static String secondsSinceString( long i ){
        return String.format("%.2f", milliSecondsSince(i)/1000f);
    }

    public static double getGLEsVersion() {
        double version = Double.parseDouble(((ActivityManager) MainApplication.getAppContext().getSystemService(Context.ACTIVITY_SERVICE)).getDeviceConfigurationInfo().getGlEsVersion());
        return version;
    }

    private static int glMaxTextureSize = -1;

    public static int glMaxTextureSize(){
        if (glMaxTextureSize < 0)
            glMaxTextureSize = isJellyBeanMR1orLater() ? gl20MaxTextureSize() : gl10MaxTextureSize();
        return glMaxTextureSize;
    }

    public static int gl20MaxTextureSize(){
        EGLDisplay dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] vers = new int[2];
        EGL14.eglInitialize(dpy, vers, 0, vers, 1);

        int[] configAttr = {
                EGL14.EGL_COLOR_BUFFER_TYPE, EGL14.EGL_RGB_BUFFER,
                EGL14.EGL_LEVEL, 0,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfig = new int[1];
        EGL14.eglChooseConfig(dpy, configAttr, 0,
                configs, 0, 1, numConfig, 0);
        if (numConfig[0] == 0) {
            // TROUBLE! No config found.
        }
        EGLConfig config = configs[0];

        int[] surfAttr = {
                EGL14.EGL_WIDTH, 64,
                EGL14.EGL_HEIGHT, 64,
                EGL14.EGL_NONE
        };
        EGLSurface surf = EGL14.eglCreatePbufferSurface(dpy, config, surfAttr, 0);

        int[] ctxAttrib = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        EGLContext ctx = EGL14.eglCreateContext(dpy, config, EGL14.EGL_NO_CONTEXT, ctxAttrib, 0);

        EGL14.eglMakeCurrent(dpy, surf, surf, ctx);

        int[] maxSize = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxSize, 0);
        //GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxSize, 0);
        //GLES32.glGetIntegerv(GLES32.GL_MAX_TEXTURE_SIZE, maxSize, 0);

        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(dpy, surf);
        EGL14.eglDestroyContext(dpy, ctx);
        EGL14.eglTerminate(dpy);

        return maxSize[0];
    }

    public static int gl10MaxTextureSize(){
        EGL10 egl = (EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();

        javax.microedition.khronos.egl.EGLDisplay dpy = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        int[] vers = new int[2];
        egl.eglInitialize(dpy, vers);

        int[] configAttr = {
                EGL10.EGL_COLOR_BUFFER_TYPE, EGL10.EGL_RGB_BUFFER,
                EGL10.EGL_LEVEL, 0,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                EGL10.EGL_NONE
        };
        javax.microedition.khronos.egl.EGLConfig[] configs = new javax.microedition.khronos.egl.EGLConfig[1];
        int[] numConfig = new int[1];
        egl.eglChooseConfig(dpy, configAttr, configs, 1, numConfig);
        if (numConfig[0] == 0) {
            // TROUBLE! No config found.
        }
        javax.microedition.khronos.egl.EGLConfig config = configs[0];

        int[] surfAttr = {
                EGL10.EGL_WIDTH, 64,
                EGL10.EGL_HEIGHT, 64,
                EGL10.EGL_NONE
        };
        javax.microedition.khronos.egl.EGLSurface surf = egl.eglCreatePbufferSurface(dpy, config, surfAttr);
        final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;  // missing in EGL10
        int[] ctxAttrib = {
                EGL_CONTEXT_CLIENT_VERSION, 1,
                EGL10.EGL_NONE
        };
        javax.microedition.khronos.egl.EGLContext ctx = egl.eglCreateContext(dpy, config, EGL10.EGL_NO_CONTEXT, ctxAttrib);
        egl.eglMakeCurrent(dpy, surf, surf, ctx);
        int[] maxSize = new int[1];
        GLES10.glGetIntegerv(GLES10.GL_MAX_TEXTURE_SIZE, maxSize, 0);
        egl.eglMakeCurrent(dpy, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_CONTEXT);
        egl.eglDestroySurface(dpy, surf);
        egl.eglDestroyContext(dpy, ctx);
        egl.eglTerminate(dpy);

        return maxSize[0];
    }

    public static int eglMaxPBuffer() {
        // Safe minimum default size
        final int IMAGE_MAX_BITMAP_DIMENSION = 2048;

        // Get EGL Display
        EGL10 egl = (EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();
        javax.microedition.khronos.egl.EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        // Initialise
        int[] version = new int[2];
        egl.eglInitialize(display, version);

        // Query total number of configurations
        int[] totalConfigurations = new int[1];
        egl.eglGetConfigs(display, null, 0, totalConfigurations);

        // Query actual list configurations
        javax.microedition.khronos.egl.EGLConfig[] configurationsList = new javax.microedition.khronos.egl.EGLConfig[totalConfigurations[0]];
        egl.eglGetConfigs(display, configurationsList, totalConfigurations[0], totalConfigurations);

        int[] textureSize = new int[1];
        int maximumTextureSize = 0;

        // Iterate through all the configurations to located the maximum texture size
        for (int i = 0; i < totalConfigurations[0]; i++) {
            // Only need to check for width since opengl textures are always squared
            egl.eglGetConfigAttrib(display, configurationsList[i], EGL10.EGL_MAX_PBUFFER_WIDTH, textureSize);

            // Keep track of the maximum texture size
            if (maximumTextureSize < textureSize[0])
                maximumTextureSize = textureSize[0];
        }

        // Release
        egl.eglTerminate(display);

        // Return largest texture size found, or default
        return Math.max(maximumTextureSize, IMAGE_MAX_BITMAP_DIMENSION);
    }

    private static int MAXMEMORYSIZE = 0;

    public static int bitmapMaxMemorySize() {
        if (MAXMEMORYSIZE>0)
            return MAXMEMORYSIZE;

        // default is 100MB in
        // android/view/DisplayListCanvas.java and later in
        // android/graphics/RecordingCanvas.java
        MAXMEMORYSIZE = 100 * 1024 * 1024; // 100 MB;
        try {
            Method method = Class.forName("android.graphics.RecordingCanvas").getDeclaredMethod("getPanelFrameSize", new Class[0]);
            method.setAccessible(true);
            MAXMEMORYSIZE = (int) method.invoke(null);
        } catch (Exception e) {
            // ignore
        }
        return MAXMEMORYSIZE;
    }

    public static long getFolderSize(File folder, boolean recursive) {
        long length = 0;
        File[] files = folder.listFiles();

        for (int i = 0; files !=null && i < files.length; i++) {
            if (files[i].isFile()) {
                length += files[i].length();
            }
            else if (recursive) {
                length += getFolderSize(files[i],recursive);
            }
        }
        return length;
    }

    public static void disablePendingTransition(Activity activity){
        if (activity!=null)
            activity.overridePendingTransition(0,0);
    }

    public static void toast(CharSequence message) {
        Context context = MainApplication.getAppContext();
        toast(message, context);
    }

    public static void toast(CharSequence message, Context context) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static class ByteArrayOutputToInputStream extends ByteArrayOutputStream {
        public ByteArrayInputStream getInputStream() {
            // reuse protected byte buffer, save memory
            ByteArrayInputStream in = new ByteArrayInputStream(this.buf, 0, this.count);
            // free reference, prevent further modification
            this.buf = new byte[0];

            return in;
        }
    }

    public static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null)
            return false;

        if (needle.length() < 1)
            return true;

        for (int i = haystack.length() - needle.length(); i >= 0; i--) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length()))
                return true;
        }
        return false;
    }
}
