package milan.bowzgore.mfp;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;
import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;
import static milan.bowzgore.mfp.library.SongLibrary.*;
import static milan.bowzgore.mfp.notification.NotificationService.isListPlaying;
import static milan.bowzgore.mfp.notification.NotificationService.isPlaying;
import static milan.bowzgore.mfp.notification.NotificationService.mediaPlayer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.concurrent.TimeUnit;

import milan.bowzgore.mfp.databinding.FragmentPlayingBinding;
import milan.bowzgore.mfp.model.Coverart;
import milan.bowzgore.mfp.notification.NotificationService;

public class PlayingFragment extends Fragment {

    private TextView titleTv, currentTimeTv, totalTimeTv ;
    private ImageView pausePlay,nextBtn,previousBtn,musicIcon,togglePlayMode;
    private SeekBar seekBar;
    private BroadcastReceiver receiver;
    Handler handler ;

    Coverart art = new Coverart();

    public PlayingFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FragmentPlayingBinding binding = FragmentPlayingBinding.inflate(inflater, container, false);

        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("media_prefs", MODE_PRIVATE);
        isListPlaying = sharedPreferences.getBoolean("isListPlaying", false); // Default is false

        titleTv = binding.songTitle;
        currentTimeTv = binding.currentTime;
        totalTimeTv = binding.totalTime;
        seekBar = binding.seekBar;
        pausePlay = binding.pausePlay;
        nextBtn =  binding.next;
        previousBtn = binding.previous;
        musicIcon = binding.musicIconBig;
        togglePlayMode = binding.togglePlayMode;
        if (isListPlaying) {
            togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_24);
        } else {
            togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_off_24);
        }
        setGeneralResources();
        setMusicResources();

        currentTimeTv.setText(convertToMMSS(String.valueOf(mediaPlayer.getCurrentPosition())));
        setupSeekBarListener();
        seekBar.setProgress(mediaPlayer.getCurrentPosition());
        titleTv.setSelected(true);
        setupRunnable();

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setMusicResources();  // Update UI based on notification changes
            }
        };
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, new IntentFilter("NEXT"));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, new IntentFilter("PREV"));

        togglePlayMode.setOnClickListener(v -> {
            setListPlaying();
            if (isListPlaying) {
                togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_24);
            } else {
                togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_off_24);
            }
        });

        Coverart.pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            // Convert the selected image to a Bitmap
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);
                            // Save the image to a temporary file and update the cover art
                            art.updateCoverArt(art.saveBitmapToFile(requireActivity(),bitmap));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
        );

        musicIcon.setOnLongClickListener(v -> {
            showChangeCoverArtDialog();
            return true;
        });
        return binding.getRoot();
    }
    @Override
    public void onResume() {
        super.onResume();
        setGeneralResources();
        setMusicResources();

        if (isListPlaying) {
            togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_24);
        } else {
            togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_off_24);
        }
        currentTimeTv.setText(convertToMMSS(String.valueOf(mediaPlayer.getCurrentPosition())));
        setupSeekBarListener();
        seekBar.setProgress(mediaPlayer.getCurrentPosition());
        titleTv.setSelected(true);
        setupRunnable();

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setMusicResources();  // Update UI based on notification changes
            }
        };
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, new IntentFilter("NEXT"));
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, new IntentFilter("PREV"));

        togglePlayMode.setOnClickListener(v -> {
            setListPlaying();
            if (isListPlaying) {
                togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_24);
            } else {
                togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_off_24);
            }
        });
    }
    private void setupSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mediaPlayer != null && fromUser) {
                    mediaPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Optional: Add logic if needed
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Optional: Add logic if needed
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(seekBar.getProgress());
                }
            }
        });
    }
    void setGeneralResources(){
        if(currentSong != null){
            pausePlay.setOnClickListener(view -> pausePlay());
            nextBtn.setOnClickListener(v-> playNextSong());
            previousBtn.setOnClickListener(v-> playPreviousSong());

        }
        else{
            titleTv.setText(R.string.no_music_loaded);
        }
    }

    void setMusicResources(){
        if(currentSong != null){
            titleTv.setText(currentSong.getTitle());
            totalTimeTv.setText(convertToMMSS(currentSong.getDuration()));
            seekBar.setMax(mediaPlayer.getDuration());

            if (currentSong.getImage() != null) {
                musicIcon.setImageBitmap(currentSong.getImage());
            } else {
                musicIcon.setImageResource(R.drawable.music_icon_big); // Fallback image
            }
        }
        else{
            titleTv.setText(R.string.no_music_loaded);
        }
    }

    private void pausePlay(){
        startMusicService("PAUSE");
    }

    private void playNextSong(){
        startMusicService("NEXT");
        setMusicResources();
    }
    private void playPreviousSong(){
        startMusicService("PREV");
        setMusicResources();
    }

    @SuppressLint("DefaultLocale")
    public static String convertToMMSS(String duration) {
        try {
            long millis = Long.parseLong(duration);
            return String.format("%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
                    TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1));
        } catch (NumberFormatException e) {
            // Handle the case where the duration is already in "MM:SS" format
            return duration;
        }
    }

    private void setupRunnable(){
        this.handler = new Handler();
        Handler handler = this.handler;
        Runnable runner = new Runnable() {
            @Override
            public void run() {
                try {
                    if(currentSong != null){
                        if (mediaPlayer != null && isPlaying) {
                            seekBar.setProgress(mediaPlayer.getCurrentPosition());
                            currentTimeTv.setText(convertToMMSS(String.valueOf(mediaPlayer.getCurrentPosition())));
                            pausePlay.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
                        } else if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                            pausePlay.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    handler.postDelayed(this, 100); // Continue updating every 100ms
                }
            }
        };
        handler.post(runner);
    }

    private void startMusicService(String action) {
        Intent intent = new Intent(getContext(), NotificationService.class);
        intent.setAction(action);
        requireContext().startService(intent);
    }


    @Override
    public void onDestroy() {
        resetFragment();
        super.onDestroy();
    }
    @Override
    public void onPause(){
        resetFragment();
        super.onPause();
    }

    public void resetFragment(){
        // Unregister the receiver to avoid memory leaks
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver);
        if (handler != null) {
            handler.removeCallbacks(null);
            handler = null;
        }
    }

    private void showChangeCoverArtDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_change_cover_art);

        Button selectCoverButton = dialog.findViewById(R.id.select_cover_button);
        selectCoverButton.setOnClickListener(v -> {
            // Open the file picker to choose a new cover image
            art.openImagePicker();
            dialog.dismiss();
        });

        dialog.show();
    }

    public void setListPlaying() {
        isListPlaying = ! isListPlaying;
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("media_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isListPlaying", isListPlaying);
        editor.apply();
    }

}