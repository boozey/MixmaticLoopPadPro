package com.nakedape.mixmaticlooppad;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by Nathan on 9/2/2015.
 */
public class Utils {
    private static final String LOG_TAG = "Utils";

    // General Utilities

    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    public static void CopyFile(File src, File dst) throws IOException {
        FileChannel inChannel = new FileInputStream(src).getChannel();
        FileChannel outChannel = new FileOutputStream(dst).getChannel();
        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            if (inChannel != null)
                inChannel.close();
            if (outChannel != null)
                outChannel.close();
        }
    }

    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
                } finally {
                    fout.close();
                }
            /* if time should be restored as well
            long time = ze.getTime();
            if (time > 0)
                file.setLastModified(time);
            */
            }
        } finally {
            zis.close();
        }
    }



    // Wav Utilities

    public static float getWavLengthInSeconds(File wavFile, int sampleRate){
        int length = 0;
        InputStream wavStream = null;
        try {
            wavStream = new BufferedInputStream(new FileInputStream(wavFile));
            byte[] lenInt = new byte[4];
            wavStream.skip(40);
            wavStream.read(lenInt, 0, 4);
            ByteBuffer bb = ByteBuffer.wrap(lenInt).order(ByteOrder.LITTLE_ENDIAN);
            length = bb.getInt();
            wavStream.close();
        } catch (IOException e){e.printStackTrace();}
        finally {
            try {
                if (wavStream != null)
                    wavStream.close();
            } catch (IOException e){}
        }
        return (float)length  / sampleRate / 4;
    }

    public static int getWavSampleRate(File wavFile){
        int sampleRate = 44100;
        InputStream wavStream = null;
        try {
            wavStream = new BufferedInputStream(new FileInputStream(wavFile));
            byte[] lenInt = new byte[4];
            wavStream.skip(24);
            wavStream.read(lenInt, 0, 4);
            ByteBuffer bb = ByteBuffer.wrap(lenInt).order(ByteOrder.LITTLE_ENDIAN);
            sampleRate = bb.getInt();
            wavStream.close();
        } catch (IOException e){e.printStackTrace();}
        finally {
            try {
                if (wavStream != null)
                    wavStream.close();
            } catch (IOException e){}
        }
        return sampleRate;
    }



    // Bitmap utilities

    public static boolean WriteImage(Bitmap image, String filePath) {
        try {
            File file = new File(filePath);
            FileOutputStream fos = new FileOutputStream(filePath);
            image.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
            return true;
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "File not found: " + e.getMessage());
            return false;
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error accessing file: " + e.getMessage());
            return false;
        }
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
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

    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId, int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        Point dimensions = getScaledDimension(options.outWidth, options.outHeight, reqWidth, reqHeight);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, dimensions.x, dimensions.y);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public static Bitmap loadScaledBitmap(Resources res, int resId, int maxWidth, int maxHeight){
        Bitmap b = decodeSampledBitmapFromResource(res, resId, maxWidth, maxHeight);
        if (b.getHeight() > maxHeight || b.getWidth() > maxWidth) {
            Point p = getScaledDimension(b.getWidth(), b.getHeight(), maxWidth, maxHeight);
            float scale = (float)p.x / b.getWidth();
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, false);
        }
        return b;
    }

    public static Bitmap getScaledBitmapMaintainRatio(Bitmap b, int maxWidth, int maxHeight){
        if (b.getHeight() > maxHeight || b.getWidth() > maxWidth) {
            Point p = getScaledDimension(b.getWidth(), b.getHeight(), maxWidth, maxHeight);
            float scale = (float)p.x / b.getWidth();
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, false);
        }
        return b;
    }

    public static Bitmap getScaledBitmap(Bitmap b, int width, int height) {
        float scaleX = (float) width / b.getWidth();
        float scaleY = (float) height / b.getHeight();
        Matrix m = new Matrix();
        m.setScale(scaleX, scaleY);
        b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, false);
        return b;
    }

    public static Bitmap decodeSampledBitmapFromContentResolver(ContentResolver r, Uri uri, int reqWidth, int reqHeight) throws IOException{
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream inputStream = r.openInputStream(uri);
        BitmapFactory.decodeStream(inputStream, null, options);

        Point dimensions = getScaledDimension(options.outWidth, options.outHeight, reqWidth, reqHeight);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, dimensions.x, dimensions.y);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        inputStream.close();
        inputStream = r.openInputStream(uri);
        return BitmapFactory.decodeStream(inputStream, null, options);
    }

    public static Bitmap decodedSampledBitmapFromFile(File f, int reqWidth, int reqHeight){
        if (!f.exists()) return null;
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), options);

        Point dimensions = getScaledDimension(options.outWidth, options.outHeight, reqWidth, reqHeight);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, dimensions.x, dimensions.y);

        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(f.getPath(), options);
    }

    public static Point getScaledDimension(int origWidth, int origHeight, int maxWidth, int maxHeight){
        return getScaledDimension(new Point(origWidth, origHeight), new Point(maxWidth, maxHeight));
    }

    public static Point getScaledDimension(Point imgSize, Point boundary) {

        float original_width = imgSize.x;
        float original_height = imgSize.y;
        float bound_width = boundary.x;
        float bound_height = boundary.y;
        float new_width = original_width;
        float new_height = original_height;

        // first check if we need to scale width
        if (original_width > bound_width) {
            //scale width to fit
            new_width = bound_width;
            //scale height to maintain aspect ratio
            new_height = (new_width * original_height) / original_width;
        }

        // then check if we need to scale even with the new height
        if (new_height > bound_height) {
            //scale height to fit instead
            new_height = bound_height;
            //scale width to maintain aspect ratio
            new_width = (new_height * original_width) / original_height;
        }

        return new Point(Math.round(new_width), Math.round(new_height));
    }

    // Time & Dates
    /**
     * Return date in specified format.
     * @param milliSeconds Date in milliseconds
     * @param dateFormat Date format
     * @return String representing date in specified format
     */
    public static String getDate(long milliSeconds, String dateFormat) {
        // Create a DateFormatter object for displaying date in specified format.
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat, Locale.getDefault());

        // Create a calendar object that will convert the date and time value in milliseconds to date.
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(milliSeconds);
        return formatter.format(calendar.getTime());
    }

    public static final long SEC_IN_MILLIS = 1000;
    public static final long MIN_IN_MILLIS = SEC_IN_MILLIS * 60;
    public static final long HOUR_IN_MILLIS = MIN_IN_MILLIS * 60;
    public static final long DAY_IN_MILLIS = HOUR_IN_MILLIS * 24;
    public static final long WEEK_IN_MILLIS = DAY_IN_MILLIS * 7;
    public static final long MONTH_IN_MILLIS = DAY_IN_MILLIS * 30;

    public static String getTimeAgo(Resources res, long millis){
        long elapsedMillis = System.currentTimeMillis() - millis;

        if (elapsedMillis < MIN_IN_MILLIS)
            return res.getString(R.string.just_now);
        else if (elapsedMillis < 2 * MIN_IN_MILLIS)
            return res.getQuantityString(R.plurals.minute_ago_plurals, 1, 1);
        else if (elapsedMillis < 45 * MIN_IN_MILLIS) {
            int minutes = (int)(elapsedMillis / MIN_IN_MILLIS);
            return res.getQuantityString(R.plurals.minute_ago_plurals, minutes, minutes);
        } else if (elapsedMillis < 1.5 * HOUR_IN_MILLIS){
            return res.getQuantityString(R.plurals.hour_ago_plurals, 1, 1);
        }
        else if (elapsedMillis < 20 * HOUR_IN_MILLIS){
            int hours = (int)(elapsedMillis / HOUR_IN_MILLIS);
            return res.getQuantityString(R.plurals.hour_ago_plurals, hours, hours);
        }
        else if (elapsedMillis < WEEK_IN_MILLIS) {
            int days = Math.max(1, (int)(elapsedMillis / DAY_IN_MILLIS));
            return res.getQuantityString(R.plurals.day_ago_plurals, days, days);
        }
        else if (elapsedMillis < MONTH_IN_MILLIS) {
            int weeks = (int)(elapsedMillis / WEEK_IN_MILLIS);
            return res.getQuantityString(R.plurals.week_ago_plurals, weeks, weeks);
        }
        else if (elapsedMillis < MONTH_IN_MILLIS * 6){
            int months = (int)(elapsedMillis / MONTH_IN_MILLIS);
            return res.getQuantityString(R.plurals.month_ago_plurals, months, months);
        }
        else {
            return res.getString(R.string.long_time_ago);
        }
    }
}
