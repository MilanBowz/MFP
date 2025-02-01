package milan.bowzgore.mfp.model;

import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import milan.bowzgore.mfp.fragment.PlayingFragment;
import milan.bowzgore.mfp.fragment.SongsFragment;
import milan.bowzgore.mfp.library.SongLibrary;


public class Coverart {
    public ActivityResultLauncher<Intent> pickImageLauncher;

    private String songpath;

    public void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    public String saveBitmapToFile(Activity activity, Bitmap bitmap) throws IOException {
        String fileName = "cover_art_" + System.currentTimeMillis() + ".jpg";
        File file = new File(activity.getCacheDir(), fileName);
        FileOutputStream outputStream = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        outputStream.flush();
        outputStream.close();
        return file.getAbsolutePath();
    }

//    private Bitmap resizeBitmap(Bitmap originalBitmap, int maxWidth, int maxHeight) {
//        int width = originalBitmap.getWidth();
//        int height = originalBitmap.getHeight();
//
//        float aspectRatio = (float) width / height;
//        if (width > height) {
//            width = maxWidth;
//            height = (int) (maxWidth / aspectRatio);
//        } else {
//            height = maxHeight;
//            width = (int) (maxHeight * aspectRatio);
//        }
//
//        return Bitmap.createScaledBitmap(originalBitmap, width, height, true);
//    }

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
        artwork.setMimeType("image/jpeg");

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
        return songpath;
    }

    public void setSongPath(String path) {
        songpath = path;
    }
}
