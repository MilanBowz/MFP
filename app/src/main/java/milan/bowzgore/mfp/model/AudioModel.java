package milan.bowzgore.mfp.model;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

import java.io.Serializable;
import java.lang.ref.SoftReference;

import milan.bowzgore.mfp.R;

public class AudioModel implements Serializable,Comparable<AudioModel> {
    String path;
    String title;
    String duration;

    // Bitmap image;
    // Cached album art (SoftReference prevents memory leaks)
    private transient SoftReference<Bitmap> cachedArt = null;

    public AudioModel(String path, String title, String duration) {
        this.path = path;
        this.title = title;
        this.duration = duration;
    }
    public AudioModel(String path, String title) {
        this.path = path;
        this.title = title;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTitle() {
        return title;
    }

    public String getDuration() {
        return duration;
    }

    protected void resetCachedArt() {
        this.cachedArt = null;
    }

    public byte[] getArtByte() {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(this.path);
            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();
            return art;
        } catch (Exception ignored) { }
        return null;
    }

    private void getBitmap() {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(this.path);
            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();

            if (art != null) {
                cachedArt = new SoftReference<>(decodeSampledBitmap(art, 500));
            }
        } catch (Exception ignored) {}
    }

    public Bitmap getArt(Context context,int type){
        if (cachedArt == null || cachedArt.get() == null) {
            getBitmap();
        }
        if(cachedArt != null){
            return switch (type) {
                case 0 -> scaleDownBitmap(cachedArt.get(), 500);
                case 1 -> scaleDownAndCropBitmap(cachedArt.get(), 100);
                case 2 -> scaleDownAndCropBitmap(cachedArt.get(), 350);
                default -> scaleDownAndCropBitmap(cachedArt.get(), 200);
            };
        }
        else {
            return BitmapFactory.decodeResource(context.getResources(), R.drawable.music_icon_big);
        }
    }
    public Bitmap getArt(int type){
        if (cachedArt == null || cachedArt.get() == null) {
            getBitmap();
        }
        if(cachedArt != null){
            return switch (type) {
                case 0 -> scaleDownBitmap(cachedArt.get(), 500);
                case 1 -> scaleDownAndCropBitmap(cachedArt.get(), 100);
                case 2 -> scaleDownAndCropBitmap(cachedArt.get(), 350);
                default -> scaleDownAndCropBitmap(cachedArt.get(), 200);
            };
        }
        return null;
    }

    private Bitmap scaleDownAndCropBitmap(Bitmap bitmap, int size) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // If the image is wider than tall, crop the sides
        if (width > height) {
            int xOffset = (width - height) / 2;
            bitmap = Bitmap.createBitmap(bitmap, xOffset, 0, height, height);
        }
        // If the image is taller, crop the top & bottom
        else if (height > width) {
            int yOffset = (height - width) / 2;
            bitmap = Bitmap.createBitmap(bitmap, 0, yOffset, width, width);
        }

        // Scale down to requested size
        return Bitmap.createScaledBitmap(bitmap, size, size, true);
    }
    private Bitmap scaleDownBitmap(Bitmap bitmap, int size) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float scale = (float) size / Math.max(width, height);

        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    private Bitmap decodeSampledBitmap(byte[] imageData, int size) {
        BitmapFactory.Options options = new BitmapFactory.Options();

        // Step 1: Decode only bounds to get original dimensions
        options.inJustDecodeBounds = true;
        options.inPreferredConfig = Bitmap.Config.RGB_565; // Uses less memory than ARGB_8888
        BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options);

        // Step 2: Calculate the appropriate sample size
        options.inSampleSize = calculateInSampleSize(options, size);

        // Step 3: Decode the image with the determined sample size
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options);
    }


    private int calculateInSampleSize(BitmapFactory.Options options, int reqSize) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        // Determine the smaller dimension
        // Calculate the ratio between the smallest dimension and the requested size
        double ratio = (double) Math.min(height, width) / reqSize;
        // If the image is smaller than or equal to the target size, no downsampling is needed.
        if (ratio <= 1) {
            return 1;
        }
        // Calculate the smallest power of 2 that is greater than or equal to the ratio.
        // This mimics the loop that doubles the sample size until the condition is violated.
        return (int) Math.pow(2, Math.ceil(Math.log(ratio) / Math.log(2)));
    }


    @Override
    public int compareTo(AudioModel other) {
        return this.title.compareToIgnoreCase(other.title); // Compare titles alphabetically
    }
    public void clearBitmap() {
        if (cachedArt != null) {
            Bitmap bitmap = cachedArt.get();
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();  // Recycle to free memory
            }
            cachedArt.clear();  // Clear the WeakReference
            cachedArt = null;   // Help GC collect it
        }
    }


}