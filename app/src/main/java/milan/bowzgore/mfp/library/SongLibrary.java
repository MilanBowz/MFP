package milan.bowzgore.mfp.library;

import static milan.bowzgore.mfp.library.FolderLibrary.tempFolder;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.service.NotificationService;

import org.json.JSONException;
import org.json.JSONObject;

public class SongLibrary {

    public List<AudioModel> songsList = new ArrayList<>();
    public int songNumber = 0 ;
    public AudioModel currentSong;

    private static final String SHARED_PREFS_NAME = "SongLibraryPrefs";
    private static final String KEY_CURRENT_SONG = "currentSong";

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
        Log.d("Debug", song);

        songNumber = songsList.indexOf(currentSong);
        tempFolder = folderPath;

        NotificationService.init_device_get();
        return songsList;
    }


    // Save the current song to SharedPreferences
    public void saveCurrentSong(Context context) {
        if (currentSong == null) return;

        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        try {
            JSONObject songJson = new JSONObject();
            songJson.put("path", currentSong.getPath());
            songJson.put("title", currentSong.getTitle());
            songJson.put("duration", currentSong.getDuration());

            editor.putString(KEY_CURRENT_SONG, songJson.toString());
            editor.apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Load the current song from SharedPreferences
    public String loadCurrentSong(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        String currentSongJson = preferences.getString(KEY_CURRENT_SONG, null);

        if (currentSongJson != null) {
            try {
                JSONObject songJson = new JSONObject(currentSongJson);
                String path = songJson.getString("path");
                return path;
            } catch (JSONException e) {
                e.printStackTrace();

            }
        }
        return null;
    }

}
