package milan.bowzgore.mfp;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;


import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import android.Manifest;
import android.provider.MediaStore;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import milan.bowzgore.mfp.fragment.FolderFragment;
import milan.bowzgore.mfp.fragment.PlayingFragment;
import milan.bowzgore.mfp.fragment.SongsFragment;
import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.service.NotificationService;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {

    public static ViewPager2 viewPager;
    public static ViewPagerAdapter viewPagerAdapter;
    private BottomNavigationView bottomNavigationView;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final int REQUEST_CODE = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(this::checkAndRequestPermissions).start();
        setContentView(R.layout.activity_main);
        viewPager = findViewById(R.id.fragmentContainerView);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        viewPagerAdapter = new ViewPagerAdapter(this);
        viewPagerAdapter.addFragment(new PlayingFragment());
        viewPagerAdapter.addFragment(new FolderFragment());
        viewPager.setAdapter(viewPagerAdapter);
        viewPager.setCurrentItem(0, false);

        this.findViewById(R.id.playing_button).setOnClickListener(v -> viewPager.setCurrentItem(0));
        this.findViewById(R.id.playlist_button).setOnClickListener(v -> viewPager.setCurrentItem(1));

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                bottomNavigationView.setSelectedItemId(position == 0 ? R.id.playing_button : R.id.playlist_button);
            }
        });
        setupBackNavigation();
        createNotificationChannel();
    }

    private void checkAndRequestPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }

        // Check the first permission only
        if (ContextCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);
        } else {
            handleAudioFile(SongLibrary.get().loadCurrentSong(this));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NotificationService.CHANNEL_ID, "Music Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Channel for music playback notifications");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void handleAudioFile(Uri audioUri) {
        if (audioUri == null) return;

        String filePath = getRealPathFromURI(this, audioUri);
        if (filePath == null) return;

        String folderPath = filePath.substring(0, filePath.lastIndexOf("/"));
        SongLibrary.get().setPlaying(new AudioModel(filePath, filePath.substring(filePath.lastIndexOf("/") + 1)));


        executorService.execute(() -> {
            SongLibrary.get().syncTempAndSelectedFolder(folderPath);
            SongLibrary.get().getAllAudioFromDevice(this, folderPath, true);
        });

        NotificationService.init_device_get();
        ContextCompat.startForegroundService(this, new Intent(this, NotificationService.class).setAction("PLAY"));
    }

    private void handleAudioFile(AudioModel audioUri) {
        if(SongLibrary.get().currentSong == null){
            NotificationService.isPlaying = false;
            SongLibrary.get().setPlaying(audioUri);
            if (audioUri != null) {
                int folderSplit = audioUri.getPath().lastIndexOf("/");
                executorService.execute(() -> {
                    SongLibrary.get().syncTempAndSelectedFolder(audioUri.getPath().substring(0, folderSplit));
                    SongLibrary.get().getAllAudioFromDevice(this, SongLibrary.get().selectedFolder, true);
                });
                NotificationService.init_device_get();
            }
            else{
                executorService.execute(() -> {
                    SongLibrary.get().getAllAudioFromDevice(this, null,false);
                });
            }
        }
    }

    private String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = context.getContentResolver().query(contentUri, new String[]{MediaStore.Audio.Media.DATA}, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            String filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
            cursor.close();
            return filePath;
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getData() != null) {
            handleAudioFile(intent.getData());
            viewPagerAdapter.updateFragment(1, new SongsFragment());
            viewPager.setCurrentItem(0, false);
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewPager.getCurrentItem() == 1 && viewPagerAdapter.getItem(1) instanceof SongsFragment) {
                    viewPagerAdapter.updateFragment(1, new FolderFragment());
                    viewPager.setCurrentItem(1, true);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE && ContextCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED) {
            handleAudioFile(SongLibrary.get().loadCurrentSong(this));
        }
    }
}
