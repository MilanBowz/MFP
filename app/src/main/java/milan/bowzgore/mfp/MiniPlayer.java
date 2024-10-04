package milan.bowzgore.mfp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.provider.MediaStore;
import android.widget.ImageButton;
import android.widget.TextView;

import milan.bowzgore.mfp.databinding.ActivityMainBinding;
import milan.bowzgore.mfp.databinding.FragmentMiniPlayerBinding;
import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.notification.NotificationService;


public class MiniPlayer extends AppCompatActivity {
    //used if selected music file in file explorer

    private FragmentMiniPlayerBinding binding;
    private TextView titleTv ;

    public MiniPlayer() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_mini_player);
        createNotificationChannel();

        // Find the buttons
        ImageButton prevButton = findViewById(R.id.prev_button);
        ImageButton playPauseButton = findViewById(R.id.play_pause_button);
        ImageButton nextButton = findViewById(R.id.next_button);

        Intent mainIntent = getIntent();
        Uri audioUri = mainIntent.getData(); // This retrieves the URI of the selected audio file
        // use public static List<AudioModel> getAllAudioFromDevice(final Context context, final String folderPath,final String song)
        if (audioUri != null) {
            String filePath = getRealPathFromURI(this, audioUri);
            if (filePath != null) {
                String songTitle = filePath.substring(filePath.lastIndexOf("/")+1); // You might want to parse this better
                filePath = filePath.substring(0,filePath.lastIndexOf("/")+1);
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
        startService(mainIntent2);

        binding = FragmentMiniPlayerBinding.inflate(getLayoutInflater()); // Inflate the correct layout
        setContentView(binding.getRoot()); // Set the root view of the binding
        //titleTv = getLayoutInflater().R.id.song_title;
        //titleTv = binding.songTitle;


            // Set click listeners
        prevButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationService.class);
            intent.setAction("PREVIOUS");
            startService(intent);
        });

        playPauseButton.setOnClickListener(v -> {
            // Call your method to play/pause the music
            Intent intent = new Intent(this,NotificationService.class);
            intent.setAction("PLAY");
            startService(intent);
        });

        nextButton.setOnClickListener(v -> {
            // Call your method to play the next song
            Intent intent = new Intent(this, NotificationService.class);
            intent.setAction("NEXT");
            startService(intent);
        });

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
}