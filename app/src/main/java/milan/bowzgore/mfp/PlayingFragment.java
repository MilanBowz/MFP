package milan.bowzgore.mfp;

import static android.app.Activity.RESULT_OK;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;
import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;
import static milan.bowzgore.mfp.library.SongLibrary.*;
import static milan.bowzgore.mfp.notification.NotificationService.isListPlaying;
import static milan.bowzgore.mfp.notification.NotificationService.mediaPlayer;
import static milan.bowzgore.mfp.notification.NotificationService.setListPlaying;

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

import com.arthenica.mobileffmpeg.FFmpeg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import milan.bowzgore.mfp.databinding.FragmentPlayingBinding;
import milan.bowzgore.mfp.notification.NotificationService;

public class PlayingFragment extends Fragment {

    private FragmentPlayingBinding binding;
    private TextView titleTv, currentTimeTv, totalTimeTv ;
    private ImageView pausePlay,nextBtn,previousBtn,musicIcon,togglePlayMode;
    private SeekBar seekBar;
    private BroadcastReceiver receiver;
    Handler handler = new Handler();

    private ActivityResultLauncher<Intent> pickImageLauncher;

    public PlayingFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentPlayingBinding.inflate(inflater, container, false);
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
        Handler handler = new Handler();
        Runnable updateUIRunnable = () -> {
            try {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    seekBar.setProgress(mediaPlayer.getCurrentPosition());
                    currentTimeTv.setText(convertToMMSS(String.valueOf(mediaPlayer.getCurrentPosition())));
                    pausePlay.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
                } else if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                    pausePlay.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
                }
            } catch (IllegalStateException e) {
                // Handle the situation where mediaPlayer is in an invalid state
                e.printStackTrace();
                // You can update UI here to reset the state if needed
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        handler.post(updateUIRunnable);

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

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            // Convert the selected image to a Bitmap
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imageUri);
                            // Save the image to a temporary file and update the cover art
                            updateCoverArt(saveBitmapToFile(bitmap));
                        } catch (IOException e) {
                            e.printStackTrace();
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
        Handler handler = new Handler();
        Runnable updateUIRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        seekBar.setProgress(mediaPlayer.getCurrentPosition());
                        currentTimeTv.setText(convertToMMSS(String.valueOf(mediaPlayer.getCurrentPosition())));
                        pausePlay.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
                    } else if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                        pausePlay.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
                    }
                } catch (IllegalStateException e) {
                    // Handle the situation where mediaPlayer is in an invalid state
                    e.printStackTrace();
                    // You can update UI here to reset the state if needed
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    handler.postDelayed(this, 100); // Continue updating every 100ms
                }
            }
        };
        handler.post(updateUIRunnable);
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



    private void startMusicService(String action) {
        Intent intent = new Intent(getContext(), NotificationService.class);
        intent.setAction(action);
        requireContext().startService(intent);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister the receiver to avoid memory leaks
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver);
        if (handler != null) {
            handler = null;
        }
    }

    private void showChangeCoverArtDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.setContentView(R.layout.dialog_change_cover_art);

        Button selectCoverButton = dialog.findViewById(R.id.select_cover_button);
        selectCoverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open the file picker to choose a new cover image
                openImagePicker();
                dialog.dismiss();
            }
        });

        dialog.show();
    }
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private String saveBitmapToFile(Bitmap bitmap) throws IOException {
        // Generate a unique file name
        Bitmap resizedBitmap = resizeBitmap(bitmap, 800, 800);
        String fileName = "cover_art_" + System.currentTimeMillis() + ".jpg";
        File file = new File(getActivity().getCacheDir(), fileName);
        FileOutputStream outputStream = new FileOutputStream(file);
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
        outputStream.flush();
        outputStream.close();
        return file.getAbsolutePath();
    }
    private Bitmap resizeBitmap(Bitmap originalBitmap, int maxWidth, int maxHeight) {
        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();

        float aspectRatio = (float) width / height;
        if (width > height) {
            width = maxWidth;
            height = (int) (maxWidth / aspectRatio);
        } else {
            height = maxHeight;
            width = (int) (maxHeight * aspectRatio);
        }

        return Bitmap.createScaledBitmap(originalBitmap, width, height, true);
    }

    public void updateCoverArt(String coverArtPath) {
        long millis = mediaPlayer.getCurrentPosition();
        String filePath = currentSong.getPath();
        String tempFilePath = filePath.replace(".mp3", "_temp.mp3");

        // Define FFmpeg command to update cover art
        String[] command = {
                "-i", filePath,         // Input file is the current song
                "-i", coverArtPath,                  // New cover art file
                "-map", "0:a",                         // Map all streams from input file 0 without cover art
                "-map", "1",                         // Map the cover art from input file 1
                "-c", "copy",                        // Copy codecs (no re-encoding)
                "-id3v2_version", "3",               // Use ID3v2 version 3
                "-metadata:s:v:0", "title=Album cover",  // Set title metadata for the cover art
                "-metadata:s:v:0", "comment=Cover (front)", // Set comment metadata for the cover art
                tempFilePath                         // Output file is a temporary file
        };

        // Execute the FFmpeg command asynchronously
        FFmpeg.executeAsync(command, (executionId, returnCode) -> {
            if (returnCode == RETURN_CODE_SUCCESS) {
                Log.i("FFmpeg", "Cover art updated successfully.");

                // Attempt to replace the original file with the new file
                File originalFile = new File(filePath);
                File tempFile = new File(tempFilePath);
                boolean originalDeleted = originalFile.delete();
                if (originalDeleted) {
                    boolean tempRenamed = tempFile.renameTo(originalFile);
                    if (tempRenamed) {
                        Log.i("FFmpeg", "Original file replaced successfully.");
                        // Update MediaPlayer and related components
                        currentSong.getEmbeddedArtwork(currentSong.getPath());
                        setMusicResources();
                        viewPagerAdapter.updateFragment(2, new SongsFragment());
                    } else {
                        Log.e("FFmpeg", "Failed to rename temp file to original file.");
                        // Clean up the temp file if renaming fails
                        tempFile.delete();
                    }
                } else {
                    Log.e("FFmpeg", "Failed to delete original file.");
                    // Clean up the temp file if deletion fails
                    tempFile.delete();
                }
            } else {
                Log.e("FFmpeg", "Failed to update cover art. Return code: " + returnCode);
            }
        });
    }
}