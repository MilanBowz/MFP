package milan.bowzgore.mfp.service;

import android.Manifest;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import milan.bowzgore.mfp.library.SongLibrary;

public class PowerHandler {
    public static final int MODE_LOOP_ALL = 0;
    public static final int MODE_LOOP_OFF = 1;
    public static final int MODE_RANDOM = 2;
    public static int currentMode = MODE_LOOP_ALL;

    private final Context context;
    private PowerManager.WakeLock wakeLock;
    private boolean isInitialized = false;

    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    BroadcastReceiver headsetReceiver;

    private boolean hasAudioFocus = false;

    public PowerHandler(Context context) {
        this.context = context;
    }

    private void startMusicService(String action){
        Intent playIntent = new Intent(context, NotificationService.class);
        playIntent.setAction(action);  // Action that the service will handle
        context.startService(playIntent);
    }

    protected void releaseWakeLockAndAudioFocus() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        if (audioManager != null && audioFocusRequest != null) {
            int result = audioManager.abandonAudioFocusRequest(audioFocusRequest);
            hasAudioFocus = false;
            Log.d(
                    "AUDIO_FOCUS",
                    "abandonAudioFocus result = " + result
            );
        }
    }

    public boolean requestAudioFocus() {
        if (audioManager == null) return false;
        if (hasAudioFocus) {
            return true;
        }

        Log.d("AUDIO_FOCUS", "REQUESTING AUDIO FOCUS");

        int result = audioManager.requestAudioFocus(audioFocusRequest);

        Log.d("AUDIO_FOCUS", "REQUEST RESULT: " + result);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            hasAudioFocus = true;
            acquireWakeLock();
            return true;
        }

        return false;
    }

    protected void setup(){
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioManager.OnAudioFocusChangeListener afChangeListener = focusChange -> {
            Log.d(
                    "AUDIO_FOCUS",
                    "Focus changed: " + focusChange
            );
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS:
                    hasAudioFocus = false;
                    startMusicService("PAUSE");
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.d("AUDIO_FOCUS", "Audio focus regained");
                    hasAudioFocus = true;
                    break;
            }
        };
        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setWillPauseWhenDucked(false)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(afChangeListener)
                .build();
        setupBroadcast();
    }
    protected void stop(){
        releaseWakeLockAndAudioFocus();
        if (headsetReceiver != null) {
            try {
                context.unregisterReceiver(headsetReceiver);
                headsetReceiver = null;
            } catch (IllegalArgumentException e) {
                Log.println(Log.ERROR,"PowerHandler","PowerHandler release error"); // Handle case where receiver is not registered
            }
        }
    }
    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MusicWakeLock");
        }
        if (!wakeLock.isHeld()) {
            wakeLock.acquire(360*60*1000L /* 300 minutes / 5 hours */);
        }
    }

    private void setupBroadcast() {
        headsetReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                    Log.d("HEADSET", "Audio becoming noisy - pausing music");
                    startMusicService("PAUSE");
                }
                // Wired headphones disconnected
                else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                    int state = intent.getIntExtra("state", -1);
                    if (state == 0) {
                        Log.d("HEADSET", "Wired headphones disconnected");
                        startMusicService("PAUSE");
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        ContextCompat.registerReceiver(
                context,
                headsetReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }
    
    public static void setPlayMode() {
        if (currentMode == MODE_RANDOM) {
            SongLibrary.get().makeRandomList();
        }
        else {
            SongLibrary.get().returnToNormalList();
        }
    }
    public static void setPlayMode(Context context, int mode) {
        currentMode = mode;

        context.getSharedPreferences("media_prefs", Context.MODE_PRIVATE)
                .edit()
                .putInt("play_mode", currentMode)
                .apply();

        if (currentMode == MODE_RANDOM) {
            SongLibrary.get().makeRandomList();
        } else {
            SongLibrary.get().returnToNormalList();
        }
    }

    public static void toggleShuffle(Context context) {
        if (currentMode == MODE_RANDOM) {
            setPlayMode(context, MODE_LOOP_ALL);
        } else {

            setPlayMode(context, MODE_RANDOM);
        }
    }
    public static void toggleMode(Context context) {
        switch (currentMode){
            case MODE_LOOP_OFF -> {setPlayMode(context, MODE_LOOP_ALL);}
            case MODE_LOOP_ALL -> {setPlayMode(context, MODE_RANDOM);}
            case MODE_RANDOM -> {setPlayMode(context, MODE_LOOP_OFF);}
        }
    }
    public static void loadPlayMode(Context context) {
        currentMode = context.getSharedPreferences(
                "media_prefs",
                Context.MODE_PRIVATE
        ).getInt("play_mode", MODE_LOOP_ALL);
    }
}
