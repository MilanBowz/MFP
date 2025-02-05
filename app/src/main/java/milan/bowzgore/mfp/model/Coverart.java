package milan.bowzgore.mfp.model;

import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import milan.bowzgore.mfp.fragment.SongsFragment;
import milan.bowzgore.mfp.library.SongLibrary;

public class Coverart {
    public ActivityResultLauncher<Intent> pickImageLauncher;
    private AudioModel musicFile;
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    public void openImagePicker() {
        pickImageLauncher.launch(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
    }

    public void updateCoverArt(Activity activity, Bitmap bitmap) {
            try {
                File file = new File(activity.getCacheDir(), "art" + System.currentTimeMillis() + ".png");
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                }

                String filePath = SongLibrary.get().currentSong.getPath();
                byte[] imageData = Files.readAllBytes(Paths.get(file.getAbsolutePath()));

                AudioFile audioFile = AudioFileIO.read(new File(filePath));
                Tag tag = audioFile.getTagOrCreateAndSetDefault();
                Artwork artwork = new Artwork();
                artwork.setBinaryData(imageData);
                artwork.setMimeType("image/png");
                tag.deleteArtworkField();
                tag.setField(artwork);
                audioFile.commit();
                Bitmap newAlbumArt = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (newAlbumArt != null) {
                    SongLibrary.get().currentSong.setCachedArt(newAlbumArt);
                }

            } catch (Exception e) {
                Log.e("CoverArtUpdate", "Failed to update artwork", e);
            }
    }

    public void saveCoverArt(Context context, AudioModel currentSong) {
        executorService.execute(() -> {
            byte[] imageData = currentSong.getArtByte();
            String mimeType = getImageType(imageData);
            Bitmap.CompressFormat format = getCompressFormat(mimeType);
            if (format == null) return;

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "art_" + currentSong.getTitle() + getFileExtension(mimeType));
            values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MFP/");

            Uri imageUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (imageUri == null) {
                Log.e("CoverArtSaver", "Failed to create MediaStore entry.");
                return;
            }

            try (OutputStream outputStream = context.getContentResolver().openOutputStream(imageUri)) {
                if (outputStream != null) {
                    BitmapFactory.decodeByteArray(imageData, 0, imageData.length).compress(format, 100, outputStream);
                    Log.d("CoverArtSaver", "Cover art saved successfully.");
                }
            } catch (IOException e) {
                Log.e("CoverArtSaver", "Error saving cover art", e);
            }
        });
    }

    private String getImageType(byte[] imageData) {
        if (imageData != null && imageData.length > 1) {
            if (imageData[0] == (byte) 0x89 && imageData[1] == (byte) 0x50) return "image/png";
            if (imageData[0] == (byte) 0xFF && imageData[1] == (byte) 0xD8) return "image/jpeg";
        }
        return "image/png";
    }

    private Bitmap.CompressFormat getCompressFormat(String mimeType) {
        switch (mimeType) {
            case "image/png": return Bitmap.CompressFormat.PNG;
            case "image/jpeg": return Bitmap.CompressFormat.JPEG;
            default: return null;
        }
    }

    private String getFileExtension(String mimeType) {
        switch (mimeType) {
            case "image/png": return ".png";
            case "image/jpeg": return ".jpg";
            default: return "";
        }
    }

    public void setSong(AudioModel song) {
        musicFile = song;
    }
}
