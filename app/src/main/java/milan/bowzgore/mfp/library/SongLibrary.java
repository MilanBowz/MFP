package milan.bowzgore.mfp.library;

import static milan.bowzgore.mfp.library.FolderLibrary.tempFolder;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.service.NotificationService;

public class SongLibrary {

    public List<AudioModel> songsList = new ArrayList<>();
    public int songNumber = 0 ;
    public AudioModel currentSong;

    private SongLibrary() {

    }

    // Inner static class responsible for holding the Singleton instance
    private static class Holder {
        private static final SongLibrary INSTANCE = new SongLibrary();
    }

    public static SongLibrary get(){
        return Holder.INSTANCE;
    }

    public List<AudioModel> getAllAudioFromDevice(final Context context, final String folderPath)  {
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.AudioColumns.DATA,
                MediaStore.Audio.AudioColumns.DURATION
        };

        String selection = MediaStore.Audio.Media.DATA + " LIKE ?";
        String[] selectionArgs = new String[]{folderPath + "/%"};

        List<AudioModel> audioModels = new ArrayList<>();

        Cursor c = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
        if (c != null) {
            audioModels.clear();
            while (c.moveToNext()) {
                String title = c.getString(0).substring(c.getString(0).lastIndexOf("/")+1);
                AudioModel audioModel = new AudioModel(c.getString(0), title, c.getString(1));
                audioModels.add(audioModel);
            }
            Collections.sort(audioModels);
            c.close();
        }
        Log.d("SongLibrary", "Number of songs fetched: " + audioModels.size());
        return songsList = audioModels;
    }
    public List<AudioModel> getAllAudioFromDevice(final Context context, final String folderPath,final String song) {
        getAllAudioFromDevice(context, folderPath);

        currentSong = songsList.stream()
                .filter(c -> c.getTitle().equals(song))
                .findFirst()
                .orElse(null);

        songNumber = songsList.indexOf(currentSong);
        tempFolder = folderPath;

        NotificationService.init_device_get();
        Log.d("SongLibrary", "Number of songs fetched: " + songsList.size());
        return songsList;
    }



}
