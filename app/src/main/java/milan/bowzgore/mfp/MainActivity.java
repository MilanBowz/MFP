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


public class MainActivity extends AppCompatActivity  {

    public static ViewPager2 viewPager;
    public static ViewPagerAdapter viewPagerAdapter;
    private BottomNavigationView bottomNavigationView;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.fragmentContainerView);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        viewPagerAdapter = new ViewPagerAdapter(this);

        viewPagerAdapter.addFragment(new PlayingFragment());

        viewPager.setAdapter(viewPagerAdapter);
        viewPager.setCurrentItem(0, false);

        if (SongLibrary.get().selectedFolder != null){
            viewPagerAdapter.addFragment(new SongsFragment());
        }
        else{
            viewPagerAdapter.addFragment(new FolderFragment());
        }

        this.findViewById(R.id.playing_button).setOnClickListener(v -> viewPager.setCurrentItem(0));
        this.findViewById(R.id.playlist_button).setOnClickListener(v -> viewPager.setCurrentItem(1));

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 0:
                        bottomNavigationView.setSelectedItemId(R.id.playing_button);
                        break;
                    case 1:
                        bottomNavigationView.setSelectedItemId(R.id.playlist_button);
                        break;
                }
            }
        });
        checkAndRequestPermissions();

        setupBackNavigation();
        createNotificationChannel();
    }

    private void checkAndRequestPermissions() {
        int REQUEST_CODE = 123;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            // Request READ_MEDIA_AUDIO, POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE);
            }
            else {
                onPermissionsGranted(); // Execute code immediately if permission is already granted
            }
            // Bluetooth connect
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] {
                        android.Manifest.permission.BLUETOOTH_CONNECT
                }, REQUEST_CODE); // Request code is arbitrary
            }
        }
        else {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S) { // Android 12+: Bluetooth connect
                if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[] {
                            android.Manifest.permission.BLUETOOTH_CONNECT
                    }, REQUEST_CODE); // Request code is arbitrary
                }
            }
            // Android 6 to 12: Request READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_CODE);
            }
            else {
                onPermissionsGranted(); // Execute code immediately if permission is already granted
            }
        }

        // Request WRITE_EXTERNAL_STORAGE for older APIs (below Android 10)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CODE);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Music Channel";
            String description = "Channel for music playback notifications";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(NotificationService.CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    private void handleAudioFile(Uri audioUri) {
        if (audioUri != null) {
            String filePath = getRealPathFromURI(this, audioUri);
            if (filePath != null) {
                int folderSplit = filePath.lastIndexOf("/");
                String songTitle = filePath.substring(folderSplit+1);
                SongLibrary.get().setPlaying(new AudioModel(filePath,songTitle));
                final String folderPath = filePath.substring(0,folderSplit);
                executorService.execute(() -> {
                    SongLibrary.get().syncTempAndSelectedFolder(folderPath);
                    SongLibrary.get().getAllAudioFromDevice(this, folderPath,true);
                });
                NotificationService.init_device_get();
                NotificationService.changePlaying(this,SongLibrary.get().songNumber);
                Intent mainIntent2 = new Intent(this, NotificationService.class);
                mainIntent2.setAction("PLAY");
                ContextCompat.startForegroundService(this,mainIntent2);
            }

        }
    }
    private void handleAudioFile(AudioModel audioUri) {
        NotificationService.isPlaying = false;
        SongLibrary.get().setPlaying(audioUri);
        if (audioUri != null) {
                int folderSplit = audioUri.getPath().lastIndexOf("/");
                executorService.execute(() -> {
                    SongLibrary.get().syncTempAndSelectedFolder(audioUri.getPath().substring(0,folderSplit));
                    SongLibrary.get().getAllAudioFromDevice(this, SongLibrary.get().selectedFolder,true);
                });
                NotificationService.init_device_get();
        }
        else{
            executorService.execute(() -> {
                SongLibrary.get().getAllAudioFromDevice(this, null,false);
            });
        }
    }

    private String getRealPathFromURI(Context context, Uri contentUri) {
        String[] projection = { MediaStore.Audio.Media.DATA };
        Cursor cursor = context.getContentResolver().query(contentUri, projection, null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            cursor.moveToFirst();
            String filePath = cursor.getString(column_index);
            cursor.close();
            return filePath;
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        Uri audioUri = intent.getData();
        if (audioUri != null) {
            handleAudioFile(audioUri);
            viewPagerAdapter.updateFragment(1, new FolderFragment());
            viewPager.setCurrentItem(0, false);
        }
    }

    private void setupBackNavigation() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewPager.getCurrentItem() == 1 && viewPagerAdapter.getItem(1) instanceof SongsFragment) {
                    viewPagerAdapter.updateFragment(1, new FolderFragment());
                    viewPager.setCurrentItem(1, true);  // Navigate to FolderFragment
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(
                this, // LifecycleOwner
                callback
        );
    }
    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 123) { // Match the request code
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                onPermissionsGranted(); // Execute your code immediately!
            }
        }
    }

    private void onPermissionsGranted() {
        // Example: Scan folders and load songs
        Uri audioUri = getIntent().getData();
        if (audioUri != null) {
            handleAudioFile(audioUri);
        }
        else{
            handleAudioFile(SongLibrary.get().loadCurrentSong(this));
        }
    }

}
