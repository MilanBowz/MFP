package milan.bowzgore.mfp.model;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.RequiresApi;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.app.RecoverableSecurityException;
import android.content.IntentSender;
import androidx.activity.result.IntentSenderRequest;



public class Coverart {
    public ActivityResultLauncher<Intent> pickImageLauncher;
    private AudioModel musicFile;
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    private Uri pendingAudioUri;
    private Uri pendingImageUri;
    public static final int REQ_WRITE_PERMISSION = 1001;

    public ActivityResultLauncher<IntentSenderRequest> writePermissionLauncher;

    public void openImagePicker() {
        pickImageLauncher.launch(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void updateCoverArt(Activity activity, Uri imageUri) {
        if (musicFile == null) {
            Log.e("CoverArtUpdate", "Music file is null, cannot update artwork.");
            return;
        }

        try {
            // Convert Uri to Bitmap
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                    activity.getContentResolver(),
                    imageUri);
            if (bitmap == null) return;

            // Resize or compress image if necessary
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width > 1000 || height > 1000) {
                float scaleFactor = 1000f / Math.max(width, height);
                width = (int) (width * scaleFactor);
                height = (int) (height * scaleFactor);
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
            }

            // Compress Bitmap to byte array (PNG preferred)
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            byte[] imageData = byteArrayOutputStream.toByteArray();

            Uri audioUri = musicFile.getContentUri();
            if (audioUri == null) {
                Log.e("CoverArtUpdate", "Audio Uri is null");
                return;
            }
            File tempFile = copyToCache(activity, audioUri);
            AudioFile audioFile = AudioFileIO.read(tempFile);
            Tag tag = audioFile.getTagOrCreateAndSetDefault();
            if (musicFile.getPath().endsWith(".ogg")
                    || musicFile.getPath().endsWith(".opus")) {
                updateOggCoverArt(tag, imageData);
            } else {
                Artwork artwork = new Artwork();
                artwork.setBinaryData(imageData);
                artwork.setMimeType(getImageType(imageData));
                tag.deleteArtworkField();
                tag.setField(artwork);
            }
            audioFile.commit();
            try {
                writeBack(activity, audioUri, tempFile);
                Log.i("CoverArtUpdate", "Cover art updated successfully");

                tempFile.delete();
                musicFile.resetCachedArt();

            } catch (RecoverableSecurityException rse) {

                pendingImageUri = imageUri;

                IntentSender intentSender = rse.getUserAction()
                        .getActionIntent()
                        .getIntentSender();

                IntentSenderRequest request =
                        new IntentSenderRequest.Builder(intentSender).build();

                writePermissionLauncher.launch(request);
            }

            Log.i("CoverArtUpdate", "Cover art updated successfully (Android 10+)");

            tempFile.delete();
            musicFile.resetCachedArt();
        } catch (Exception e) {
            Log.e("CoverArtUpdate", "Failed to update artwork", e);
        }
    }

    private File copyToCache(Context context, Uri audioUri) throws IOException {
        String extension = getExtensionFromUri(context, audioUri);
        File temp = new File(context.getCacheDir(), "edit_audio." + extension);

        try (InputStream in = context.getContentResolver().openInputStream(audioUri);
             OutputStream out = new FileOutputStream(temp)) {

            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        }
        return temp;
    }
    private String getExtensionFromUri(Context context, Uri uri) {
        String type = context.getContentResolver().getType(uri);
        if (type == null) return "mp3";
        if (type.equals("audio/mpeg")) return "mp3";
        if (type.equals("audio/flac")) return "flac";
        if (type.equals("audio/ogg")) return "ogg";
        if (type.equals("audio/opus")) return "opus";
        if (type.equals("audio/mp4")) return "m4a";
        return "mp3"; // fallback
    }

    private void writeBack(Context context, Uri audioUri, File edited) throws IOException {
        try (OutputStream out =
                     context.getContentResolver().openOutputStream(audioUri, "rwt");
             InputStream in = new FileInputStream(edited)) {

            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        }
    }

    private void updateOggCoverArt(Tag tag, byte[] imageData) throws Exception {
        // Remove old artwork
        tag.deleteField(FieldKey.COVER_ART);
        tag.deleteField("METADATA_BLOCK_PICTURE");

        // Create FLAC style PICTURE block
        byte[] pictureBlock = createFlacPictureBlock(imageData);

        // Base64 encode it
        String base64Picture = Base64.encodeToString(pictureBlock, Base64.NO_WRAP);

        // Insert as METADATA_BLOCK_PICTURE
        tag.setField(FieldKey.valueOf(String.valueOf(FieldKey.COVER_ART)), base64Picture);
    }

    private byte[] createFlacPictureBlock(byte[] imageData) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Bitmap bmp = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            out.write(intToBytes(3)); // Picture type: 3 = Front Cover
            out.write(intToBytes("image/png".length()));
            out.write("image/png".getBytes(StandardCharsets.UTF_8)); // MIME type
            out.write(intToBytes(0)); // Description length
            out.write(intToBytes(bmp.getWidth()));
            out.write(intToBytes(bmp.getHeight()));
            out.write(intToBytes(32)); // Color depth placeholder
            out.write(intToBytes(0)); // No indexed colors
            out.write(intToBytes(imageData.length));
            out.write(imageData);
        } catch (IOException e) {
            Log.e("FlacPictureBlock", "Error creating picture block", e);
        }
        return out.toByteArray();
    }

    private byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    public void saveCoverArt(Context context) {
        if (musicFile != null) {
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
        return switch (mimeType) {
            case "image/png" -> Bitmap.CompressFormat.PNG;
            case "image/jpeg" -> Bitmap.CompressFormat.JPEG;
            default -> null;
        };
    }

    private String getFileExtension(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> "";
        };
    }

    public void setSong(AudioModel song) {
        musicFile = song;
    }

    public AudioModel getSong() {
        return musicFile;
    }

    public void retryAfterPermission(Activity activity) {
        if (pendingImageUri == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            updateCoverArt(activity, pendingImageUri);
        }
        pendingImageUri = null;
        pendingAudioUri = null;
    }


}
