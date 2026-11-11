package milan.bowzgore.mfp;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
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
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import milan.bowzgore.mfp.fragment.FolderFragment;
import milan.bowzgore.mfp.fragment.PlayingFragment;
import milan.bowzgore.mfp.fragment.SongsFragment;
import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.service.NotificationService;
import milan.bowzgore.mfp.service.PowerHandler;

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
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        viewPager = findViewById(R.id.fragmentContainerView);
        viewPager.setVisibility(View.GONE);
        viewPager.setOffscreenPageLimit(1);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        PowerHandler.loadPlayMode(this);
        viewPagerAdapter = new ViewPagerAdapter(this);
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
        checkAndRequestPermissions();
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
            initializeSong(SongLibrary.get().loadCurrentSong(this),false);
            return;
        }

        AudioModel model = getAudioModelFromUri(this, audioUri);
        if (model == null) {
            initializeSong(SongLibrary.get().loadCurrentSong(this),false);
            return;
        }
        initializeSong(model,true);
    }

    private void initializeSong(AudioModel model,boolean startPlaying) {
        if (model == null) {
            executorService.execute(() -> {
                SongLibrary.get().getAllAudioFromDevice(
                        this, "", false
                );
                runOnUiThread(() -> viewPagerAdapter.showFolder());
            });
            return;
        }

        SongLibrary library = SongLibrary.get();
        // Set the selected song before starting the service.
        library.setPlaying(model, this);

        String path = model.getPath();
        int folderSplit = path.lastIndexOf("/");
        if (folderSplit < 0) {
            return;
        }

        String folderPath = path.substring(0, folderSplit);
        executorService.execute(() -> {
            library.syncTempAndSelectedFolder(folderPath);
            library.getAllAudioFromDevice(
                    this,
                    folderPath,
                    true
            );
            if(!startPlaying){
                // Only initialize playback after the library is ready.
                ContextCompat.startForegroundService(
                        this,
                        new Intent(this, NotificationService.class)
                                .setAction("INIT")
                );
                runOnUiThread(() -> {
                    viewPagerAdapter.scrollToCurrentSong();
                });
            }
            else {
                ContextCompat.startForegroundService(
                        this,
                        new Intent(this, NotificationService.class)
                                .setAction("INIT_PLAY")
                );
                runOnUiThread(() -> {
                    viewPagerAdapter.scrollToCurrentSong();
                });
            }
        });
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
            String duration = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
            String title = path.substring(path.lastIndexOf("/") + 1);

            return new AudioModel(id, path, title, duration);

        } catch (Exception e) {
            return null;
        }
    }


    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        viewPager.setCurrentItem(0, false);

        if (intent.getData() != null) {
            handleAudioFile(intent.getData());
        }
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewPager.getCurrentItem() == 1 && viewPagerAdapter.containsItem(3L)) {
                    runOnUiThread(() -> viewPagerAdapter.showFolder());
                    viewPager.setCurrentItem(1, true);
                }
            }
        });
    }

    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
        } else {
            return new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
    }

    private void checkAndRequestPermissions() {
        String[] permissions = getRequiredPermissions();

        boolean allGranted = true;

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            loadStartupSong();
        } else {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);
        }
    }

    private void loadStartupSong() {
        Uri uri = getIntent().getData();
        viewPager.setVisibility(View.VISIBLE);
        if (uri != null) {
            handleAudioFile(uri);
        } else {
            initializeSong(SongLibrary.get().loadCurrentSong(this),false);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_CODE) return;

        boolean granted = true;

        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }

        if (granted) {
            loadStartupSong();
        } else {
            viewPagerAdapter.showFolder();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(finishReceiver);
        super.onDestroy();
        viewPager = null;
        viewPagerAdapter = null;
    }

}