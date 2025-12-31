package milan.bowzgore.mfp.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;

import java.io.File;
import java.io.Serializable;

import milan.bowzgore.mfp.R;

public class AudioModel implements Serializable,Comparable<AudioModel> {
    String path;
    String title;
    String duration;
    long mediaStoreId;
    transient Uri contentUri;


    public AudioModel(long id, String path, String title, String duration) {
        this.mediaStoreId = id;
        this.path = path;
        this.title = title;
        this.duration = duration;
        this.contentUri = Uri.withAppendedPath(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                String.valueOf(id)
        );
    }

    protected Uri getContentUri() {
        if (contentUri == null && mediaStoreId > 0) {
            contentUri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    String.valueOf(mediaStoreId)
            );
        }
        return contentUri;
    }

    public Bitmap getNotificationArtWithGlide(Context context) {
        try {
            return Glide.with(context)
                    .asBitmap()
                    .load(getAlbumArtUri()) // or albumArtUri
                    .override(256, 256) // notification size
                    .signature(new ObjectKey(new File(getPath()).lastModified()))
                    .submit()
                    .get();
        } catch (Exception e) {
            Log.e("AudioModel","image update as bitmap failed ");
            return null;
        }
    }

    private Uri getAlbumArtUri() {
        return Uri.parse("content://media/external/audio/media/"
                + mediaStoreId + "/albumart");
    }

    public void setGlideImage(Fragment view, int width, int height, ImageView destination){
        Glide.with(view)
                .load(getAlbumArtUri()) // best source
                .centerCrop()
                .error(R.drawable.music_icon_big)
                .override(width, height) // match your old type=0 size
                .dontAnimate()
                .signature(new ObjectKey(new File(getPath()).lastModified())) // only reload if file changed
                .into(destination);
    }
    public void setGlideImage(View view, int width, int height, ImageView destination){
        Glide.with(view)
                .load(getAlbumArtUri()) // best source
                .placeholder(R.drawable.music_icon_big)
                .error(R.drawable.music_icon_big)
                .centerCrop()
                .override(width, height) // match your old type=0 size
                .dontAnimate()
                .signature(new ObjectKey(new File(getPath()).lastModified())) // only reload if file changed
                .into(destination);
    }

    @Override
    public int compareTo(AudioModel other) {
        return this.title.compareToIgnoreCase(other.title); // Compare titles alphabetically
    }

    /* ------------------ Getters and Setters ------------------ */

    public long getMediaStoreId() {
        return mediaStoreId;
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
}