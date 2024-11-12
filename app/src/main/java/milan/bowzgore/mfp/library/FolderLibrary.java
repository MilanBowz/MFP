package milan.bowzgore.mfp.library;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class FolderLibrary {
    public static List<String> folders = new ArrayList<>();
    public static String selectedFolder;
    public static String tempFolder;
    private static final String PREFS_NAME = "FolderLibraryPrefs";
    private static final String FOLDERS_KEY = "folders";

    private static class Holder {
        private static final FolderLibrary INSTANCE = new FolderLibrary();
    }

    // Public method to provide access to the Singleton instance
    public static FolderLibrary getFolderLibrary(){
        return FolderLibrary.Holder.INSTANCE;
    }

    public static void getMusicFolders(Context context, boolean reloadFolders) {
        if (!reloadFolders) {
            loadFolders(context);
            if (!folders.isEmpty()) {
                return;
            }
        }
        Set<String> musicFolders = new HashSet<>();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Audio.Media.DATA};
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";

        Cursor cursor = context.getContentResolver().query(uri, projection, selection, null, null);
        if (cursor != null) {
            int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            while (cursor.moveToNext()) {
                String filePath = cursor.getString(dataColumn);
                String folderPath = filePath.substring(0, filePath.lastIndexOf("/"));
                musicFolders.add(folderPath);
            }
            cursor.close();
        }
        folders.clear();
        folders.addAll(musicFolders);
        saveFolders(context);
    }

    public static void loadFolders(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> folderSet = preferences.getStringSet(FOLDERS_KEY, new HashSet<>());
        folders = new ArrayList<>(folderSet);
    }

    public static void saveFolders(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putStringSet(FOLDERS_KEY, new HashSet<>(folders));
        editor.apply();
    }

    public static String getFolderDisplay() {
        int lastSlashIndex = (tempFolder != null) ? tempFolder.lastIndexOf("/") : -1;
        return (lastSlashIndex != -1) ? tempFolder.substring(lastSlashIndex) : "SONGS";
    }

}