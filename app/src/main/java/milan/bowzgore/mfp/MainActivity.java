package milan.bowzgore.mfp;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.viewpager2.widget.ViewPager2;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    private final BroadcastReceiver finishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finishAffinity();
            // Release system resources
            System.runFinalization();
            System.gc();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(this::checkAndRequestPermissions).start();
        setContentView(R.layout.activity_main);
        viewPager = findViewById(R.id.fragmentContainerView);
        viewPager.setOffscreenPageLimit(1);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        viewPagerAdapter = new ViewPagerAdapter(this);
        viewPagerAdapter.initFragment(new PlayingFragment(),new FolderFragment());
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
        // Register receiver to listen for finish signal
        IntentFilter filter = new IntentFilter("FINISH_ACTIVITY");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                    finishReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );
        }
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
            if (getIntent().getData() != null) {
                handleAudioFile(getIntent().getData());
                if(viewPagerAdapter!=null){
                    runOnUiThread(() -> viewPagerAdapter.updateFragment(new SongsFragment()));
                }
            } else {
                handleAudioFile(SongLibrary.get().loadCurrentSong(this));
            }
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                NotificationService.CHANNEL_ID, "Music Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Channel for music playback notifications");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void handleAudioFile(Uri audioUri) {
        if (audioUri == null) {
            runOnUiThread(()-> viewPagerAdapter.updateFragment(new FolderFragment()));
            return;
        }
        AudioModel model = getAudioModelFromUri(this, audioUri);
        if (model == null) {
            runOnUiThread(() ->
                    viewPagerAdapter.updateFragment(new FolderFragment()));
            return;
        }
        SongLibrary.get().setPlaying(model,this);
        NotificationService.isPlaying = true;

        executorService.execute(() -> {
            SongLibrary.get().syncTempAndSelectedFolder(model.getPath());
            SongLibrary.get().getAllAudioFromDevice(this, model.getPath(), true);
        });
        runOnUiThread(() -> viewPagerAdapter.updateFragment(new SongsFragment()));
        ContextCompat.startForegroundService(this, new Intent(this, NotificationService.class).setAction("INIT"));
    }

    private void handleAudioFile(AudioModel audioUri) {
        if(SongLibrary.get().currentSong == null){
            NotificationService.isPlaying = false;
            SongLibrary.get().setPlaying(audioUri,this);
            if (audioUri != null) {
                int folderSplit = audioUri.getPath().lastIndexOf("/");
                executorService.execute(() -> {
                    SongLibrary.get().syncTempAndSelectedFolder(audioUri.getPath().substring(0, folderSplit));
                    SongLibrary.get().getAllAudioFromDevice(this, SongLibrary.get().selectedFolder, true);
                });
                ContextCompat.startForegroundService(this, new Intent(this, NotificationService.class).setAction("INIT"));
            }
            else{
                executorService.execute(() -> {
                    SongLibrary.get().getAllAudioFromDevice(this, null,false);
                    runOnUiThread(()-> viewPagerAdapter.updateFragment(new FolderFragment()));
                });
            }
        }
    }

    private AudioModel getAudioModelFromUri(Context context, Uri audioUri) {
        if (audioUri == null) return null;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
        };

        try (Cursor cursor = context.getContentResolver()
                .query(audioUri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return null;

            long id = cursor.getLong(
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));

            String path = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
            String folder = path.substring(0, path.lastIndexOf("/"));

            String duration = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));

            String title = path.substring(path.lastIndexOf("/") + 1);

            return new AudioModel(id, folder, title, duration);

        } catch (Exception e) {
            return null;
        }
    }


    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getData() != null) {
            viewPagerAdapter.updateFolderFragment();
            handleAudioFile(intent.getData());
            viewPager.setCurrentItem(0, false);
            viewPagerAdapter.updatePlayingFragment();
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewPager.getCurrentItem() == 1 && viewPagerAdapter.getItem(1) instanceof SongsFragment) {
                    runOnUiThread(() -> viewPagerAdapter.updateFragment(new FolderFragment()));
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

    @Override
    protected void onDestroy() {
        unregisterReceiver(finishReceiver);
        super.onDestroy();
        viewPager = null;
        viewPagerAdapter.clear();
        viewPagerAdapter = null;
    }
}
