package milan.bowzgore.mfp.library;

import static milan.bowzgore.mfp.notification.NotificationService.mediaPlayer;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.notification.NotificationService;

public class SongLibrary {

    public static List<AudioModel> songsList = new ArrayList<>();
    public static int songNumber = 0 ;
    public static AudioModel currentSong;

    private SongLibrary() {
    }

    // Inner static class responsible for holding the Singleton instance
    private static class Holder {
        private static final SongLibrary INSTANCE = new SongLibrary();
    }

    // Public method to provide access to the Singleton instance
    public static SongLibrary getSongLibrary(){
        return Holder.INSTANCE;
    }

    public static List<AudioModel> getAllAudioFromDevice(final Context context, final String folderPath)  {
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
        return audioModels;

        //updateUI();
    }
    public static List<AudioModel> getAllAudioFromDevice(final Context context, final String folderPath,final String song) {
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.AudioColumns.DATA,
                MediaStore.Audio.AudioColumns.DURATION
        };
        if(mediaPlayer != null){
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();

        String selection = MediaStore.Audio.Media.DATA + " LIKE ?";
        String[] selectionArgs = new String[]{folderPath + "%"};

        songsList = new ArrayList<>();

        Cursor c = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
        if (c != null) {
            songsList.clear();
            while (c.moveToNext()) {
                String title = c.getString(0).substring(c.getString(0).lastIndexOf("/")+1);
                AudioModel audioModel = new AudioModel(c.getString(0), title, c.getString(1));
                songsList.add(audioModel);
                if (audioModel.getTitle().equals(song)) {
                    currentSong = audioModel;
                    songNumber = songsList.size()-1;
                    try {
                        mediaPlayer.setDataSource(currentSong.getPath());
                        System.out.println(currentSong.getPath());
                        mediaPlayer.prepare();
                    }
                    catch (IOException exc){

                    }
                    Intent intent = new Intent(context, NotificationService.class);
                    intent.setAction("START");
                    context.startService(intent);
                }
            }
            //Collections.sort(songsList);
            c.close();
        }
        return songsList;
    }

    public static void changePlaying(int index){
        songNumber = index;
        currentSong = getSongLibrary().songsList.get(songNumber);
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(currentSong.getPath());
            System.out.println(currentSong.getPath());
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
