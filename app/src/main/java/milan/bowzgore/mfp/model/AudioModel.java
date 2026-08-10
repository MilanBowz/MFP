package milan.bowzgore.mfp.model;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
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
    private transient Bitmap cachedArtwork;

    public AudioModel(long id, String path, String title, String duration) {
        this.mediaStoreId = id;
        this.path = path;
        this.title = title;
        this.duration = duration;
        if (id > 0) {
            this.contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        }
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
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                // Android 10 - broken MediaStore album art, use embedded
                return getEmbeddedArtwork();
            } else {
                // All other versions - use Glide with album art URI
                return Glide.with(context)
                        .asBitmap()
                        .load(getAlbumArtUri(context))
                        .override(256, 256)
                        .signature(new ObjectKey(new File(getPath()).lastModified()))
                        .submit()
                        .get();
            }
        } catch (Exception e) {
            Log.e("AudioModel","image update as bitmap failed", e);
            return null;
        }
    }

    private Uri getAlbumArtUri(Context context) {
        if (mediaStoreId <= 0) return null;
        return ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/media"), mediaStoreId)
                .buildUpon().appendPath("albumart").build();
    }
    private Bitmap getEmbeddedArtwork() {
        if (cachedArtwork != null) {
            return cachedArtwork;
        }

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(this.path);
            byte[] artData = mmr.getEmbeddedPicture();
            if (artData != null) {
                cachedArtwork = BitmapFactory.decodeByteArray(artData, 0, artData.length);
                return cachedArtwork;
            }
        } catch (Exception e) {
            Log.w("AudioModel", "Failed to extract embedded art: " + e.getMessage());
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {}
        }
        return null;
    }

    public void setGlideImage(Fragment view, ImageView destination){ // Playing Fragment
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) {
            Glide.with(view)
                    .load(getAlbumArtUri(view.getContext())) // best source
                    .error(R.drawable.music_icon_big)
                    .dontAnimate()
                    .signature(new ObjectKey(new File(getPath()).lastModified())) // only reload if file changed
                    .into(destination);
        }
        else {
            Bitmap embeddedArt = getEmbeddedArtwork();
            if (embeddedArt != null) {
                destination.setImageBitmap(embeddedArt);
            } else {
                destination.setImageResource(R.drawable.music_icon_big);
            }
        }
    }
    public void setGlideImage(View view, int width, int height, ImageView destination){ // Song list
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) {
            Glide.with(view)
                    .load(getAlbumArtUri(view.getContext())) // best source
                    .placeholder(R.drawable.music_icon_big)
                    .error(R.drawable.music_icon_big)
                    .centerCrop()
                    .override(width, height) // match your old type=0 size
                    .dontAnimate()
                    .signature(new ObjectKey(new File(getPath()).lastModified())) // only reload if file changed
                    .into(destination);
        }
        else {
            Bitmap embeddedArt = getEmbeddedArtwork();
            if (embeddedArt != null) {
                destination.setImageBitmap(embeddedArt);
            } else {
                destination.setImageResource(R.drawable.music_icon_big);
            }
        }
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
            return mmr.getEmbeddedPicture();
        } catch (Exception e) {
            Log.w("AudioModel", "Failed to get art bytes: " + e.getMessage());
            return null;
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
    }
}