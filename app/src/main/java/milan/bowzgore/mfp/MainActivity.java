package milan.bowzgore.mfp;

import static milan.bowzgore.mfp.library.FolderLibrary.folders;
import static milan.bowzgore.mfp.library.FolderLibrary.selectedFolder;
import static milan.bowzgore.mfp.library.SongLibrary.currentSong;
import static milan.bowzgore.mfp.library.SongLibrary.songsList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import android.app.Activity;
import android.app.Application;
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

import milan.bowzgore.mfp.library.FolderLibrary;
import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.notification.NotificationService;
import milan.bowzgore.mfp.notification.ViewPagerAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


public class MainActivity extends AppCompatActivity implements Application.ActivityLifecycleCallbacks {

    public static ViewPager2 viewPager;
    public static ViewPagerAdapter viewPagerAdapter;
    private BottomNavigationView bottomNavigationView;
    private static final int REQUEST_CODE = 123;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private AtomicBoolean isRunning = new AtomicBoolean(true);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check and request permissions as before
        checkAndRequestPermissions();

        viewPager = findViewById(R.id.fragmentContainerView);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        viewPagerAdapter = new ViewPagerAdapter(this);
        // Add fragments to the adapter
        viewPagerAdapter.addFragment(new PlayingFragment());
        viewPagerAdapter.addFragment(new FolderFragment());
        if (FolderLibrary.selectedFolder != null)
            viewPagerAdapter.addFragment(new SongsFragment());

        viewPager.setAdapter(viewPagerAdapter);
        viewPager.setCurrentItem(0, false);

        this.findViewById(R.id.playing_button).setOnClickListener(v -> viewPager.setCurrentItem(0));
        this.findViewById(R.id.playlist_button).setOnClickListener(v -> viewPager.setCurrentItem(1));
        createNotificationChannel();

        // Set a listener for page changes in ViewPager2
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

        // Check if launched with VIEW intent (file selected)
        Uri audioUri = getIntent().getData();
        if (audioUri != null) {
            handleAudioFile(audioUri);
        }
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: Request READ_MEDIA_AUDIO and POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE);
            }
        } else {
            // Android 6 to 12: Request READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_CODE);
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

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        super.onResume();
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        super.onPause();
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        super.onStop();
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        //mediaPlayer.release();
        super.onDestroy();
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
                String songTitle = filePath.substring(filePath.lastIndexOf("/")+1); // You might want to parse this better
                filePath = filePath.substring(0,filePath.lastIndexOf("/"));
                SongLibrary.getAllAudioFromDevice(this, filePath, songTitle);
                executorService.execute(() -> {
                    for (AudioModel song : songsList) {
                        if (!isRunning.get()) break;
                        song.getEmbeddedArtwork(song.getPath());
                    }
                    FolderAdapter.addSongsFragment();
                });
            } else {
                // Handle the case where filePath is null
            }
        } else {
            // Handle the case where audioUri is null
        }

        Intent mainIntent2 = new Intent(this, NotificationService.class);
        mainIntent2.setAction("START");
        startService(mainIntent2);


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
        return null; // Return null if unable to retrieve path
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);  // Ensure that the new intent is available

        // Handle any data passed via the intent (if necessary)
        Uri audioUri = intent.getData();
        if (audioUri != null) {
            handleAudioFile(audioUri);  // Use your logic to handle the file
        }
    }

}
