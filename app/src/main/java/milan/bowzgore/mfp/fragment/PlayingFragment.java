package milan.bowzgore.mfp.fragment;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;
import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;
import static milan.bowzgore.mfp.service.NotificationService.player;

import androidx.activity.result.contract.ActivityResultContracts;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
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

import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.databinding.FragmentPlayingBinding;
import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.model.Coverart;
import milan.bowzgore.mfp.service.NotificationService;

import static milan.bowzgore.mfp.service.PowerHandler.currentMode;

public class PlayingFragment extends Fragment {

    private TextView titleTv, currentTimeTv, totalTimeTv,indexNumberTv ;
    private ImageView pausePlay,nextBtn,previousBtn,musicIcon,togglePlayMode;
    private SeekBar seekBar;
    private BroadcastReceiver receiver;
    private Handler handler ;
    private final Coverart art = new Coverart();

    public PlayingFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FragmentPlayingBinding binding = FragmentPlayingBinding.inflate(inflater, container, false);

        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("media_prefs", MODE_PRIVATE);
        currentMode = sharedPreferences.getInt("play_mode", 0); // Default is false

        titleTv = binding.songTitle;
        currentTimeTv = binding.currentTime;
        totalTimeTv = binding.totalTime;
        indexNumberTv = binding.indexNumber;
        seekBar = binding.seekBar;
        pausePlay = binding.pausePlay;
        nextBtn =  binding.next;
        previousBtn = binding.previous;
        musicIcon = binding.musicIconBig;
        togglePlayMode = binding.togglePlayMode;

        setupFragment();
        art.writePermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartIntentSenderForResult(),
                        result -> {
                            if (result.getResultCode() == RESULT_OK) {
                                art.retryAfterPermission(requireActivity());
                            }
                        }
                );
        art.pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        try {
                                if(art.getSong() != null){
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        art.updateCoverArt(requireActivity(),result.getData().getData());
                                    }
                                }
                        } catch (Exception e) {
                            Log.e("AudioFile", "Failed to read audio file", e);
                        }
                    }
                }
        );

        return binding.getRoot();
    }

    private void setupFragment(){
        setGeneralResources();
        pausePlay.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
        if (currentMode == 0) {
            togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_24);
        } else if(currentMode == 1){
            togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_off_24);
        }
        else {
            togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_off_random);
        }
        setMusicResources();

        // Always setup seekbar listener and runnable
        setupSeekBarListener();
        titleTv.setSelected(true);
        setupRunnable(); // Always call this, even if player is null

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setMusicResources();  // Update UI based on notification changes
                // Restart the runnable if needed
                if (handler == null) {
                    setupRunnable();
                }
            }
        };
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, new IntentFilter("PLAYER_READY"));

        togglePlayMode.setOnClickListener(v -> {
            setPlayMode();
            if (currentMode == 0) {
                togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_24);
                SongLibrary.get().returnToNormalList();
            } else if(currentMode == 1){
                togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_off_24);
            }else {
                togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_off_random);
                SongLibrary.get().makeRandomList();
                // Update shuffled index if in random mode
            }
            updateIndexNumber();// Update index when mode changes
        });
        musicIcon.setOnLongClickListener(v -> {
            showChangeCoverArtDialog();
            return true;
        });
    }

    private void setupSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    player.seekTo(progress);
                    currentTimeTv.setText(convertToMMSS(String.valueOf(progress)));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                player.seekTo(seekBar.getProgress());
            }
        });
    }

    private void setGeneralResources(){
        pausePlay.setOnClickListener(view -> pausePlay());
        nextBtn.setOnClickListener(v-> startMusicService("NEXT"));
        previousBtn.setOnClickListener(v-> startMusicService("PREV"));
    }

    public void setMusicResources() {
        if (!isAdded()) { return; }
        AudioModel song = SongLibrary.get().currentSong;
        int index = SongLibrary.get().songNumber;
        if (song != null) {
            titleTv.setText(song.getTitle());
            updateIndexNumber();
            if(player != null){
                totalTimeTv.setText(convertToMMSS(song.getDuration()));
                seekBar.setMax((int) player.getDuration());
                currentTimeTv.setText(convertToMMSS(String.valueOf(player.getCurrentPosition())));
                seekBar.setProgress((int) player.getCurrentPosition());
            }
            song.setGlideImage(this, musicIcon);
            startMusicService("UPDATE");
        } else {
                titleTv.setText(R.string.no_music_loaded);
                seekBar.setMax(1);
                totalTimeTv.setText("00:00");
                currentTimeTv.setText("00:00");
                seekBar.setProgress(0);
                musicIcon.setImageResource(R.drawable.music_icon_big);
        }
    }

    private void pausePlay(){
        if (player != null && player.isPlaying()) {
            startMusicService("PAUSE");
            pausePlay.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
        } else {
            startMusicService("PLAY");
            pausePlay.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
        }
    }

    @SuppressLint("DefaultLocale")
    private String convertToMMSS(String duration) {
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
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        handler.removeCallbacksAndMessages(null);
        Runnable runner = new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) {return;}
                if (SongLibrary.get().currentSong == null || player == null) {
                    titleTv.setText(R.string.no_music_loaded);
                    musicIcon.setImageResource(R.drawable.music_icon_big);
                    seekBar.setMax(0);
                    seekBar.setProgress(0);
                    totalTimeTv.setText("00:00");
                    currentTimeTv.setText("00:00");
                    pausePlay.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
                }
                else {
                    // Update regardless of playing state to show current position
                    long duration = player.getDuration();
                    long currentPosition = player.getCurrentPosition();

                    if (duration > 0) {
                        seekBar.setMax((int) duration);
                        seekBar.setProgress((int) currentPosition);
                        currentTimeTv.setText(
                                convertToMMSS(
                                        String.valueOf(currentPosition)
                                )
                        );
                        totalTimeTv.setText(
                                convertToMMSS(
                                        String.valueOf(duration)
                                )
                        );
                    }

                    // Update play/pause button state
                    if (player.isPlaying()) {
                        pausePlay.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
                    } else {
                        pausePlay.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
                    }

                    // Update title if changed
                    AudioModel currentSong = SongLibrary.get().currentSong;
                    if (currentSong != null && !titleTv.getText().equals(currentSong.getTitle())) {
                        titleTv.setText(currentSong.getTitle());
                        currentSong.setGlideImage(PlayingFragment.this, musicIcon);
                    }
                }

                // Post the same runnable again after 300ms to keep updating
                handler.postDelayed(this, 300);
            }
        };
        handler.post(runner);
    }

    private void startMusicService(String action) {
        Intent intent = new Intent(getContext(), NotificationService.class);
        intent.setAction(action);
        ContextCompat.startForegroundService(requireActivity(),intent);
    }


    @Override
    public void onDestroy() {
        // Unregister the receiver to avoid memory leaks
        try {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            // Receiver was already unregistered
        }
        if (handler != null) {
            handler.removeCallbacks(null);
            handler = null;
        }
        super.onDestroy();
    }

    private void showChangeCoverArtDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_change_cover_art);
        art.setSong(SongLibrary.get().currentSong);
        Button selectCoverButton = dialog.findViewById(R.id.select_cover_button);
        selectCoverButton.setOnClickListener(v -> {
            art.openImagePicker();
            dialog.dismiss();
        });
        Button saveCoverButton = dialog.findViewById(R.id.save_cover_button);
        saveCoverButton.setOnClickListener(v -> {
            art.saveCoverArt(getContext());
            dialog.dismiss();
        });
        dialog.show();
    }

    public void setPlayMode() {
        if(currentMode <2 ){
            currentMode +=1;
        }
        else {
            currentMode = 0;
        }
        // Save to preferences
        SharedPreferences prefs = requireContext().getSharedPreferences("media_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("play_mode", currentMode);
        editor.apply();

        // Update UI if needed
        if (viewPagerAdapter != null) {
            viewPagerAdapter.updatePlayingFragment();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    private void updateIndexNumber() {
        if (!isAdded()) return;

        AudioModel currentSong = SongLibrary.get().currentSong;
        int totalSongs = SongLibrary.get().songsList.size();
        int currentIndex = SongLibrary.get().songNumber;

        if (currentSong != null && totalSongs > 0) {
            // Check if we're in random mode
            if (currentMode == 2) {
                // For random mode, show current position in shuffled list
                indexNumberTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            } else {
                // Normal or loop mode
                indexNumberTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue));
            }
            String indexText = (currentIndex >= 0 && currentIndex < totalSongs)
                    ? (currentIndex + 1) + "/" + totalSongs
                    : "1/" + totalSongs;

            SpannableString spannable = new SpannableString(indexText);
            int slashIndex = indexText.indexOf('/');
            if (slashIndex > 0) {
                // new font
                //indexNumberTv.setTypeface(null, Typeface.ITALIC);
                spannable.setSpan(new RelativeSizeSpan(2f), 0, slashIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new RelativeSizeSpan(1.5f), slashIndex, slashIndex+1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            indexNumberTv.setText(spannable);
        } else {
            indexNumberTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.color));
            indexNumberTv.setText("0/0");
        }
    }
}