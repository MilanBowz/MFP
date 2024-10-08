package milan.bowzgore.mfp;

import static milan.bowzgore.mfp.library.SongLibrary.currentSong;
import static milan.bowzgore.mfp.library.SongLibrary.songsList;
import static milan.bowzgore.mfp.notification.NotificationService.isPlaying;
import static milan.bowzgore.mfp.notification.NotificationService.mediaPlayer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.provider.MediaStore;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import milan.bowzgore.mfp.databinding.FragmentMiniPlayerBinding;
import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.notification.NotificationService;


public class MiniPlayer extends AppCompatActivity {
    //used if selected music file in file explorer

    private FragmentMiniPlayerBinding binding;
    private TextView titleTv ;
    private BroadcastReceiver receiver;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private AtomicBoolean isRunning = new AtomicBoolean(true);

    public MiniPlayer() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize view binding
        binding = FragmentMiniPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        titleTv = binding.songTitle;

        createNotificationChannel();

        Intent mainIntent = getIntent();
        Uri audioUri = mainIntent.getData();

        if (audioUri != null) {
            String filePath = getRealPathFromURI(this, audioUri);
            if (filePath != null) {
                String songTitle = filePath.substring(filePath.lastIndexOf("/")+1); // You might want to parse this better
                filePath = filePath.substring(0,filePath.lastIndexOf("/"));
                // Load the audio models from the library
                SongLibrary.getAllAudioFromDevice(this, filePath, songTitle);
                // Start the notification service
                Intent mainIntent2 = new Intent(this, NotificationService.class);
                mainIntent2.setAction("START");
                startService(mainIntent2);
            } else {
                // Handle the case where filePath is null
                titleTv.setText("Unable to retrieve file path.");
            }
        } else {
            // Handle the case where audioUri is null
            titleTv.setText("No audio file selected");
        }

        Intent mainIntent2 = new Intent(this, NotificationService.class);
        mainIntent2.setAction("START");

        setMusicResources();
        executorService.execute(() -> {
            for (AudioModel song : songsList) {
                if (!isRunning.get()) break;
                song.getEmbeddedArtwork(song.getPath());
            }
            FolderAdapter.addSongsFragment();
        });

        binding.previousSongButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationService.class);
            intent.setAction("PREV");
            startService(intent);
        });

        binding.playSongButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationService.class);
            intent.setAction("PLAY");
            startService(intent);
        });

        binding.nextSongButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationService.class);
            intent.setAction("NEXT");
            startService(intent);
        });
        binding.closeActivityButton.setOnClickListener(v -> {
            finish();
        });

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setMusicResources();  // Update UI based on notification changes
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter("NEXT"));
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter("PREV"));

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
    void setMusicResources(){
        if(currentSong != null && mediaPlayer != null){
            titleTv.setText(currentSong.getTitle());
            if (isPlaying) {
                binding.playSongButton.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
            } else {
                binding.playSongButton.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
            }
        }
        else{
            titleTv.setText(R.string.no_music_loaded);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver); // Avoid memory leaks
        // Signal to stop background work
        isRunning.set(false);

        // Properly shut down executor service
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                // Handle the case where the executor service did not terminate properly
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}