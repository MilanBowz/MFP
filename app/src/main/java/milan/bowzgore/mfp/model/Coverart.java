package milan.bowzgore.mfp.model;


import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Coverart {
    public ActivityResultLauncher<Intent> pickImageLauncher;
    private AudioModel musicFile;
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    public void openImagePicker() {
        pickImageLauncher.launch(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
    }

    public void updateCoverArt(Activity activity,Uri imageUri) {
        if (musicFile == null) {
            Log.e("CoverArtUpdate", "Music file is null, cannot update artwork.");
            return;
        }

        try {
            // Convert Uri to Bitmap
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(activity.getContentResolver(), imageUri);
            if (bitmap == null) {
                Log.e("CoverArtUpdate", "Failed to decode image.");
                return;
            }

            // Resize or compress image if necessary
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width > 1000 || height > 1000) {
                float scaleFactor = 1000f / Math.max(width, height);
                width = (int) (width * scaleFactor);
                height = (int) (height * scaleFactor);
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            }

            // Compress Bitmap to byte array (JPEG with high quality)
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            byte[] imageData = byteArrayOutputStream.toByteArray();

            String filePath = musicFile.getPath();
            AudioFile audioFile = AudioFileIO.read(new File(filePath));
            Tag tag = audioFile.getTagOrCreateAndSetDefault();

            // Create an Artwork object directly from byte[]
            Artwork artwork = new Artwork();
            artwork.setBinaryData(imageData);
            artwork.setMimeType(getImageType(imageData)); // Auto-detect format (PNG/JPEG)
            tag.deleteArtworkField();
            // Replace existing artwork
            tag.setField(artwork);
            audioFile.commit();
            musicFile.resetCachedArt();
            Log.i("CoverArtUpdate", "Cover art updated successfully!");

        } catch (Exception e) {
            Log.e("CoverArtUpdate", "Failed to update artwork", e);
        }
    }

    public void saveCoverArt(Context context) {
        if(musicFile!=null){
            executorService.execute(() -> {
                byte[] imageData = musicFile.getArtByte();
                if (imageData == null) return;
                String mimeType = getImageType(imageData);
                Bitmap.CompressFormat format = getCompressFormat(mimeType);
                if (format == null) return;

                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "art_" + musicFile.getTitle() + getFileExtension(mimeType));
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

    public AudioModel getSong() {
        return musicFile;
    }
}
