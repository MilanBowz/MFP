package milan.bowzgore.mfp.model;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;

public class AudioModel implements Serializable,Comparable<AudioModel> {
    String path;
    String title;
    String duration;
    Bitmap image;

    public AudioModel(String songData) {
        String[] parts = songData.split(",");
        this.path = parts[0];
        this.title = parts[1];
        this.duration = parts[2];
        this.image = stringToBitmap(parts[3]);
    }
    public AudioModel(String path, String title, String duration) {
        this.path = path;
        this.title = title;
        this.duration = duration;
    }
    public AudioModel(String path, String title, String duration,Bitmap image) {
        this.path = path;
        this.title = title;
        this.duration = duration;
        this.image = image;
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

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public Bitmap getImage() {
        return image;
    }

    public void setImage(Bitmap imageUrl) {
        this.image = imageUrl;
    }

    public String bitmapToString(Bitmap bitmap) {
        if (bitmap == null) return "";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.DEFAULT);
    }

    public Bitmap getEmbeddedArtwork(String filePath) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(filePath);
            byte[] data = mmr.getEmbeddedPicture();
            if (data != null) {
                this.image = BitmapFactory.decodeByteArray(data, 0, data.length);
                return image;
            }
            mmr.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Bitmap stringToBitmap(String encodedString) {
        if (encodedString.isEmpty()) return null;
        byte[] decodedString = Base64.decode(encodedString, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
    }

    @Override
    public int compareTo(AudioModel other) {
        return this.title.compareToIgnoreCase(other.title); // Compare titles alphabetically
    }
}