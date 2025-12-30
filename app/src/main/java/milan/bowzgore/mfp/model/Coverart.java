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
import android.app.RecoverableSecurityException;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
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


public class Coverart {
    public ActivityResultLauncher<Intent> pickImageLauncher;
    public ActivityResultLauncher<IntentSenderRequest> writePermissionLauncher;

    private AudioModel musicFile;
    private Uri pendingImageUri;
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    public static final int REQ_WRITE_PERMISSION = 1001;

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
            Bitmap bitmap = getResizedBitmap(activity, imageUri, 1000);
            if (bitmap == null) return;

            byte[] imageData = compressBitmap(bitmap);
            Uri audioUri = musicFile.getContentUri();
            if (audioUri == null) {
                Log.e("CoverArtUpdate", "Audio Uri is null");
                return;
            }

            File tempFile = copyToCache(activity, audioUri);
            AudioFile audioFile = AudioFileIO.read(tempFile);
            Tag tag = audioFile.getTagOrCreateAndSetDefault();

            if (musicFile.getPath().endsWith(".ogg") || musicFile.getPath().endsWith(".opus")) {
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
                Log.i("CoverArtUpdate", "Cover art updated successfully.");
                musicFile.resetCachedArt();
                tempFile.delete();
            } catch (RecoverableSecurityException rse) {
                pendingImageUri = imageUri;
                IntentSenderRequest request = new IntentSenderRequest.Builder(
                        rse.getUserAction().getActionIntent().getIntentSender()).build();
                writePermissionLauncher.launch(request);
            }
        } catch (Exception e) {
            Log.e("CoverArtUpdate", "Failed to update artwork", e);
        }
    }

    public void retryAfterPermission(Activity activity) {
        if (pendingImageUri == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) updateCoverArt(activity, pendingImageUri);
        pendingImageUri = null;
    }

    private Bitmap getResizedBitmap(Context context, Uri uri, int maxDim) throws IOException {
        Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
        if (bitmap == null) return null;
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w > maxDim || h > maxDim) {
            float scale = maxDim / (float) Math.max(w, h);
            bitmap = Bitmap.createScaledBitmap(bitmap, (int) (w * scale), (int) (h * scale), false);
        }
        return bitmap;
    }

    private byte[] compressBitmap(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        return baos.toByteArray();
    }

    private File copyToCache(Context context, Uri audioUri) throws IOException {
        String extension = getExtensionFromUri(context, audioUri);
        File temp = new File(context.getCacheDir(), "edit_audio." + extension);
        try (InputStream in = context.getContentResolver().openInputStream(audioUri);
             OutputStream out = new FileOutputStream(temp)) {
            copyStream(in, out);
        }
        return temp;
    }

    private void writeBack(Context context, Uri audioUri, File edited) throws IOException {
        try (OutputStream out = context.getContentResolver().openOutputStream(audioUri, "rwt");
             InputStream in = new FileInputStream(edited)) {
            copyStream(in, out);
        }
    }

    private void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
    }

    private String getExtensionFromUri(Context context, Uri uri) {
        String type = context.getContentResolver().getType(uri);
        return switch (type) {
            case "audio/mpeg" -> "mp3";
            case "audio/flac" -> "flac";
            case "audio/ogg" -> "ogg";
            case "audio/opus" -> "opus";
            case "audio/mp4" -> "m4a";
            default -> "mp3";
        };
    }

    private void updateOggCoverArt(Tag tag, byte[] imageData) throws Exception {
        tag.deleteField(FieldKey.COVER_ART);
        tag.deleteField("METADATA_BLOCK_PICTURE");
        byte[] pictureBlock = createFlacPictureBlock(imageData);
        tag.setField(FieldKey.valueOf(String.valueOf(FieldKey.COVER_ART)),
                Base64.encodeToString(pictureBlock, Base64.NO_WRAP));
    }

    private byte[] createFlacPictureBlock(byte[] imageData) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Bitmap bmp = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            out.write(intToBytes(3)); // front cover
            out.write(intToBytes("image/png".length()));
            out.write("image/png".getBytes(StandardCharsets.UTF_8));
            out.write(intToBytes(0)); // desc length
            out.write(intToBytes(bmp.getWidth()));
            out.write(intToBytes(bmp.getHeight()));
            out.write(intToBytes(32)); // color depth
            out.write(intToBytes(0)); // indexed colors
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

    //--------------------- Save Cover Art ---------------------
    public void saveCoverArt(Context context) {
        if (musicFile == null) return;

        executorService.execute(() -> {
            byte[] imageData = musicFile.getArtByte();
            if (imageData == null) return;

            String mime = getImageType(imageData);
            Bitmap.CompressFormat fmt = getCompressFormat(mime);
            if (fmt == null) return;

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "art_" + musicFile.getTitle() + getFileExtension(mime));
            values.put(MediaStore.Images.Media.MIME_TYPE, mime);
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MFP/");

            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return;

            try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out != null) BitmapFactory.decodeByteArray(imageData, 0, imageData.length).compress(fmt, 100, out);
            } catch (IOException e) {
                Log.e("CoverArtSaver", "Error saving cover art", e);
            }
        });
    }

    private String getImageType(byte[] data) {
        if (data.length > 1) {
            if ((data[0] & 0xFF) == 0x89 && (data[1] & 0xFF) == 0x50) return "image/png";
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) return "image/jpeg";
        }
        return "image/png";
    }

    private Bitmap.CompressFormat getCompressFormat(String mime) {
        return switch (mime) {
            case "image/png" -> Bitmap.CompressFormat.PNG;
            case "image/jpeg" -> Bitmap.CompressFormat.JPEG;
            default -> null;
        };
    }

    private String getFileExtension(String mime) {
        return switch (mime) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> "";
        };
    }

    public void setSong(AudioModel song) { musicFile = song; }
    public AudioModel getSong() { return musicFile; }
}