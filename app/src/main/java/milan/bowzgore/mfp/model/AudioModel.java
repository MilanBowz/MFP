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

    public AudioModel(String songData) {
        String[] parts = songData.split(",");
        this.path = parts[0];
        this.title = parts[1];
        this.duration = parts[2];
    }
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

    public void setCachedArt(Bitmap cachedArt) {
        this.cachedArt = new SoftReference<>(cachedArt);
    }

    public byte[] getArtByte() {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(this.path);
            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();
            return art;
        } catch (Exception ignored) { }
        return new byte[0];
    }

    public Bitmap getArtBitmap(Context context) {
        if (cachedArt != null && cachedArt.get() != null) {
            return cachedArt.get();
        }
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(this.path);
            byte[] art = mmr.getEmbeddedPicture();
            mmr.release();

            if (art != null) {
                return decodeSampledBitmap(art, 400);
            }
        } catch (Exception ignored) {}
        return BitmapFactory.decodeResource(context.getResources(), R.drawable.music_icon_big);
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

        cachedArt = new SoftReference<>(BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options));
        return cachedArt.get();
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
}