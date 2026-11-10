package milan.bowzgore.mfp.fragment;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;
import static milan.bowzgore.mfp.MainActivity.viewPager;
import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;
import static milan.bowzgore.mfp.service.NotificationService.isPlaying;
import static milan.bowzgore.mfp.service.NotificationService.mediaPlayer;

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
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.databinding.FragmentPlayingBinding;
import milan.bowzgore.mfp.library.SongLibrary;
import milan.bowzgore.mfp.model.AudioModel;
import milan.bowzgore.mfp.model.Coverart;
import milan.bowzgore.mfp.service.NotificationService;
import milan.bowzgore.mfp.service.PowerHandler;

import static milan.bowzgore.mfp.service.PowerHandler.currentMode;

public class PlayingFragment extends Fragment {

    private TextView titleTv, currentTimeTv, totalTimeTv,indexNumberTv ;
    private ImageView pausePlay,nextBtn,previousBtn,musicIcon,togglePlayMode;
    private SeekBar seekBar;
    private BroadcastReceiver receiver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean userSeeking = false;
    private boolean fragmentActive = false;
    private int lastKnownDuration = -1;
    private final Coverart art = new Coverart();
    private transient boolean isArtworkChecked = false;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!fragmentActive || !isAdded() || getView() == null) {
                return;
            }
            try {
                if (SongLibrary.get().currentSong == null ||
                        mediaPlayer == null ||
                        !NotificationService.playerPrepared) {
                    resetSeekBar();
                } else {
                    updateSeekBarProgress();
                }

            } catch (IllegalStateException e) {
                Log.w("PlayingFragment", "Player temporarily unavailable", e);
            }

            if (fragmentActive) {
                handler.postDelayed(this, 250);
            }
        }
    };

    public PlayingFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FragmentPlayingBinding binding = FragmentPlayingBinding.inflate(inflater, container, false);

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

        titleTv.setSelected(true);
        setupSeekBarListener();
        //setupRunnable();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if(!Objects.equals(action, "TOGGLE_PLAY_MODE")){
                    setMusicResources();
                }
                else {
                    if (currentMode == 0) {
                        togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_24);
                    } else if(currentMode == 1){
                        togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_off_24);
                    }else {
                        togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_off_random);
                        // Update shuffled index if in random mode
                    }
                    updateIndexNumber();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("UPDATE");
        filter.addAction("PLAYER_READY");
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, filter);

        togglePlayMode.setOnClickListener(v -> {
            setPlayMode();
            if (currentMode == 0) {
                togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_24);
            } else if(currentMode == 1){
                togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_off_24);
            }else {
                togglePlayMode.setImageResource(R.drawable.ic_baseline_loop_off_random);
                // Update shuffled index if in random mode
            }
            updateIndexNumber();// Update index when mode changes
        });
        musicIcon.setOnLongClickListener(v -> {
            showChangeCoverArtDialog(); // save or change coverart
            return true;
        });
        indexNumberTv.setOnLongClickListener(v -> {
            SongLibrary library = SongLibrary.get();

            // Only scroll directly when the displayed list matches
            // the currently selected folder/list.
            if (library.isSyncTempSelectedFolder() && library.currentSong != null && viewPagerAdapter != null) {
                // Scroll after SongsFragment becomes visible.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isAdded()) return;
                    if(viewPagerAdapter.scrollToCurrentSong()){
                        Toast.makeText(
                                requireContext(),
                                "Showing current song in the list",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }, 250);
            }
            return false;
        });
    }

    private void setupSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar sb,
                            int progress,
                            boolean fromUser) {

                        if (fromUser) {
                            currentTimeTv.setText(
                                    convertToMMSS(String.valueOf(progress))
                            );
                        }
                    }
                    @Override
                    public void onStartTrackingTouch(SeekBar sb) {
                        userSeeking = true;
                    }
                    @Override
                    public void onStopTrackingTouch(SeekBar sb) {
                        userSeeking = false;
                        seekToPosition(sb.getProgress());
                    }
                });
    }

    private void setGeneralResources(){
        pausePlay.setOnClickListener(view -> pausePlay());
        nextBtn.setOnClickListener(v-> {
            isArtworkChecked = false;
            startMusicService("NEXT");
        });
        previousBtn.setOnClickListener(v-> {
            isArtworkChecked = false;
            startMusicService("PREV");
        });
    }
    public void updatePlayingFrament() {
        if (!isAdded() || getView() == null) return;
        isArtworkChecked = false;
        setMusicResources();
    }
    private void setMusicResources() {
        AudioModel song = SongLibrary.get().currentSong;

        if (song != null) {
            if(!isArtworkChecked){
                song.setGlideImage(this, musicIcon);
            }
            isArtworkChecked = true;
            titleTv.setText(song.getTitle());
            titleTv.setSelected(true);
            updateIndexNumber();

            if (mediaPlayer != null && NotificationService.playerPrepared) {
                try {
                    int duration = mediaPlayer.getDuration();
                    int position = mediaPlayer.getCurrentPosition();
                    if (duration > 0) {
                        lastKnownDuration = duration;
                        seekBar.setMax(duration);
                        seekBar.setProgress(Math.max(0, Math.min(position, duration)));
                        totalTimeTv.setText(convertToMMSS(String.valueOf(duration)));
                        currentTimeTv.setText(convertToMMSS(String.valueOf(position)));
                    } else {
                        resetSeekBar();
                    }

                } catch (IllegalStateException e) {
                    Log.w("PlayingFragment", "Player not ready yet", e);
                    resetSeekBar();
                }
            } else {
                resetSeekBar();
            }

        } else {
            titleTv.setText(R.string.no_music_loaded);
            titleTv.setSelected(false);
            resetSeekBar();
            musicIcon.setImageResource(R.drawable.music_icon_small);
        }
    }

    private void pausePlay(){
        if (mediaPlayer != null && isPlaying) {
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

    private void startMusicService(String action) {
        Intent intent = new Intent(getContext(), NotificationService.class);
        intent.setAction(action);
        ContextCompat.startForegroundService(requireActivity(),intent);
    }


    @Override
    public void onDestroyView() {
        fragmentActive = false;
        // Stop all pending progress updates.
        handler.removeCallbacks(progressRunnable);
        // Prevent callbacks from updating destroyed views.
        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(null);
        }
        if (receiver != null) {
            try {
                LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver);
            } catch (IllegalArgumentException e) {
                Log.w("PlayingFragment", "Receiver already unregistered");
            }
            receiver = null;
        }
        userSeeking = false;
        lastKnownDuration = -1;
        super.onDestroyView();
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
        PowerHandler.toggleMode(requireContext());

        if (viewPagerAdapter != null) {
            viewPagerAdapter.updatePlayingFragment();
        }
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(new Intent("TOGGLE_PLAY_MODE"));
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
                indexNumberTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent2));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    seekBar.setOutlineAmbientShadowColor(ContextCompat.getColor(requireContext(), R.color.accent2));
                }

            } else {
                // Normal or loop mode
                indexNumberTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    seekBar.setOutlineSpotShadowColor(ContextCompat.getColor(requireContext(), R.color.accent));
                }            }
            //viewPagerAdapter.updateSongsFragment();
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

    private void resetSeekBar() {
        if (userSeeking || seekBar == null) return;

        lastKnownDuration = -1;
        seekBar.setMax(1);
        seekBar.setProgress(0);

        currentTimeTv.setText("00:00");
        totalTimeTv.setText("00:00");
    }

    private void updateSeekBarProgress() {
        if (mediaPlayer == null || !NotificationService.playerPrepared) {
            return;
        }

        try {
            int duration = mediaPlayer.getDuration();
            int position = mediaPlayer.getCurrentPosition();

            if (duration <= 0) return;

            // Restore the SeekBar maximum if it was reset.
            if (lastKnownDuration != duration || seekBar.getMax() != duration) {
                lastKnownDuration = duration;
                seekBar.setMax(duration);
                totalTimeTv.setText(
                        convertToMMSS(String.valueOf(duration))
                );
            }

            if (!userSeeking) {
                position = Math.max(0, Math.min(position, duration));
                seekBar.setProgress(position);
                currentTimeTv.setText(
                        convertToMMSS(String.valueOf(position))
                );
            }
            pausePlay.setImageResource(
                    isPlaying
                            ? R.drawable.ic_baseline_pause_circle_outline_24
                            : R.drawable.ic_baseline_play_circle_outline_24
            );

        } catch (IllegalStateException e) {
            Log.w("PlayingFragment", "Could not update playback progress", e);
        }
    }
    private void seekToPosition(int requestedPosition) {

        if (mediaPlayer == null ||
                !NotificationService.playerPrepared) {
            return;
        }

        try {
            int duration = mediaPlayer.getDuration();

            if (duration <= 0) return;

            int position = Math.max(
                    0,
                    Math.min(requestedPosition, duration)
            );

            mediaPlayer.seekTo(position);

            // Immediately synchronize the displayed position.
            seekBar.setProgress(position);

            currentTimeTv.setText(
                    convertToMMSS(String.valueOf(position))
            );

        } catch (IllegalStateException e) {
            Log.e("PlayingFragment", "Seeking failed", e);
        }
    }
    private void setupRunnable() {
        fragmentActive = true;

        handler.removeCallbacks(progressRunnable);
        handler.post(progressRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop UI progress updates while the fragment is paused.
        fragmentActive = false;
        handler.removeCallbacks(progressRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded() || getView() == null) return;
        setMusicResources();
        // Restart progress updates.
        setupRunnable();
        if (pausePlay == null) return;
        pausePlay.setImageResource(
                isPlaying
                        ? R.drawable.ic_baseline_pause_circle_outline_24
                        : R.drawable.ic_baseline_play_circle_outline_24
        );
    }

}