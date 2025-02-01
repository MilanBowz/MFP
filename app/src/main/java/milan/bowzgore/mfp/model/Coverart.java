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

import androidx.activity.result.ActivityResultLauncher;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import milan.bowzgore.mfp.fragment.SongsFragment;
import milan.bowzgore.mfp.library.SongLibrary;

import android.media.MediaMetadataRetriever;



public class Coverart {
    public ActivityResultLauncher<Intent> pickImageLauncher;

    private AudioModel musicFile;

    public void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    public String saveBitmapToFile(Activity activity, Bitmap bitmap) throws IOException {
        String fileName = "art" + System.currentTimeMillis() + ".png";
        File file = new File(activity.getCacheDir(), fileName);
        FileOutputStream outputStream = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        outputStream.flush();
        outputStream.close();
        return file.getAbsolutePath();
    }

    public String updateCoverArt(String coverArtPath) throws Exception {
        String filePath = SongLibrary.get().currentSong.getPath();
        AudioFile audioFile;

        try {
            audioFile = AudioFileIO.read(new File(filePath));
        } catch (Exception e) {
            Log.e("CoverArtUpdate", "Failed to read audio file", e);
            throw e;
        }

        Tag tag = audioFile.getTagOrCreateAndSetDefault();

        byte[] imageData = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            imageData = Files.readAllBytes(Paths.get(coverArtPath));
        }

        Artwork artwork = new Artwork();
        artwork.setBinaryData(imageData);
        artwork.setMimeType("image/png");

        try {
            tag.deleteArtworkField();
            tag.setField(artwork);
            audioFile.commit();
        } catch (Exception e) {
            Log.e("CoverArtUpdate", "Failed to update artwork", e);
            throw e;
        }

        // Update cached album art in AudioModel
        Bitmap newAlbumArt = BitmapFactory.decodeFile(coverArtPath);
        if (newAlbumArt != null) {
            SongLibrary.get().currentSong.setCachedArt(newAlbumArt);
        }

        // Update UI if SongsFragment is active
        if (viewPagerAdapter.getItem(1) instanceof SongsFragment) {
            if(SongLibrary.get().selectedFolder.equals(SongLibrary.get().tempFolder)){
                SongsFragment songsFragment = (SongsFragment) viewPagerAdapter.getItem(1);
                songsFragment.updateCurrentSong(SongLibrary.get().currentSong);
            }
        }
        return musicFile.getPath();
    }


    public void saveCoverArt(Context context, AudioModel currentsong) {
        // Define the directory for music
        String directory = Environment.DIRECTORY_PICTURES;
        ContentResolver resolver = context.getContentResolver();

        // Convert the Bitmap to byte array to inspect the MIME type
        byte[] imageData = currentsong.getArtByte();
        String mimeType = getImageType(imageData);
        // Determine the file extension based on MIME type
        String extension;
        Bitmap.CompressFormat compressFormat;

        switch (mimeType) {
            case "image/png":
                extension = ".png";
                compressFormat = Bitmap.CompressFormat.PNG;
                break;
            case "image/jpeg":
                extension = ".jpg";
                compressFormat = Bitmap.CompressFormat.JPEG;
                break;
            case "image/gif":
                extension = ".gif";
                compressFormat = Bitmap.CompressFormat.PNG; // No direct GIF
                break;
            default:
                return;
        }

        // Create content values
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "art_" + currentsong.getTitle() + extension);
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
        values.put(MediaStore.Images.Media.RELATIVE_PATH, directory + "/MFP/");

        // Insert the image in MediaStore
        Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (imageUri == null) {
            Log.e("CoverArtSaver", "Failed to create new MediaStore entry.");
            return;
        }

        try {
            // Open output stream and write bitmap data
            OutputStream outputStream = resolver.openOutputStream(imageUri);
            if (outputStream != null) {
                BitmapFactory.decodeByteArray(imageData, 0, imageData.length).compress(compressFormat, 100, outputStream);
                outputStream.close();
                Log.d("CoverArtSaver", "Cover art saved successfully.");
            }
        } catch (IOException e) {
            Log.e("CoverArtSaver", "Error saving cover art", e);
        }
    }

    // Determine the MIME type from the image byte array
    private String getImageType(byte[] imageData) {
        if (imageData != null && imageData.length > 1) {
            if (imageData[0] == (byte) 0x89 && imageData[1] == (byte) 0x50) {
                return "image/png"; // PNG signature
            } else if (imageData[0] == (byte) 0xFF && imageData[1] == (byte) 0xD8) {
                return "image/jpeg"; // JPEG signature
            } else if (imageData[0] == (byte) 0x47 && imageData[1] == (byte) 0x49) {
                return "image/gif"; // GIF signature
            }
        }
        return "image/png"; // Default MIME type if unrecognized
    }


    public void setSong(AudioModel path) {
        musicFile = path;
    }
}
