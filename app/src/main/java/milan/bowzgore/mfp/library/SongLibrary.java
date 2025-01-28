package milan.bowzgore.mfp.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import milan.bowzgore.mfp.model.AudioModel;

import org.json.JSONException;
import org.json.JSONObject;

public class SongLibrary {

    public List<AudioModel> songsList = new ArrayList<>();
    public int songNumber = 0 ;
    public AudioModel currentSong;

    public List<String> folders = new ArrayList<>();
    public String selectedFolder;
    public String tempFolder;

    private final String SHARED_PREFS_NAME = "SongLibraryPrefs";
    private final String KEY_CURRENT_SONG = "currentSong";

    private SongLibrary() {

    }

    // Inner static class responsible for holding the Singleton instance
    private static class Holder {
        private static final SongLibrary INSTANCE = new SongLibrary();
    }

    public static SongLibrary get(){
        return Holder.INSTANCE;
    }

    public List<AudioModel> getAllAudioFromDevice(final Context context, final String folderPath) {
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.AudioColumns.DATA,
                MediaStore.Audio.AudioColumns.DURATION
        };

        // Combine selection criteria
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        List<AudioModel> audioModels = new ArrayList<>();
        Set<String> musicFolders = new HashSet<>();

        try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, null, null)) {
            if (cursor != null) {
                int dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DATA);
                int durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION);

                while (cursor.moveToNext()) {
                    String filePath = cursor.getString(dataIndex);
                    String folder = filePath.substring(0, filePath.lastIndexOf("/"));
                    musicFolders.add(folder);

                    if(folder.equals(folderPath)){
                        String title = filePath.substring(filePath.lastIndexOf("/") + 1);
                        String duration = cursor.getString(durationIndex);
                        audioModels.add(new AudioModel(filePath, title, duration));
                    }
                }
                // Optional: Sort the audio files if needed
                if (!audioModels.isEmpty()) {
                    Collections.sort(audioModels);
                }
            }
        } catch (Exception e) {
            Log.e("SongLibrary", "Error fetching audio files", e);
        }

        // Update folders list if required
        folders.clear();
        folders.addAll(musicFolders);
        Collections.sort(folders);

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
    public AudioModel loadCurrentSong(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        String currentSongJson = preferences.getString(KEY_CURRENT_SONG, null);

        if (currentSongJson != null) {
            try {
                JSONObject songJson = new JSONObject(currentSongJson);
                String path = songJson.getString("path");
                String title = songJson.getString("title");
                String duration = songJson.getString("duration");
                return new AudioModel(path,title,duration);
            } catch (JSONException e) {
                e.printStackTrace();

            }
        }
        return null;
    }

    public String getFolderDisplay() {
        int lastSlashIndex = (tempFolder != null) ? tempFolder.lastIndexOf("/") : -1;
        return (lastSlashIndex != -1) ? tempFolder.substring(lastSlashIndex) : "SONGS";
    }

}
