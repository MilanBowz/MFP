package milan.bowzgore.mfp.service;

import static milan.bowzgore.mfp.service.NotificationService.isPlaying;

import android.Manifest;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class PowerHandler {

    private final Context context;
    private PowerManager.WakeLock wakeLock;

    public static boolean isListPlaying = false;
    private boolean isInitialized = false;

    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;

    BroadcastReceiver headsetReceiver;

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
            if(!wakeLock.isHeld()){
                audioManager.abandonAudioFocus(afChangeListener);
            }
        }
    }
    protected void requestAudioFocus() {
        int result = audioManager.requestAudioFocus(afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            acquireWakeLock();
        }
    }

    protected void setup(){
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        requestAudioFocus();
        afChangeListener = focusChange -> {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    startMusicService("PAUSE");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    startMusicService("PAUSE");
                    releaseWakeLockAndAudioFocus();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (!isPlaying) {
                        startMusicService("PLAY");
                    }
                    break;
            }
        };
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
                    // Handle wired headset
                    if (Intent.ACTION_HEADSET_PLUG.equals(action) && isInitialized) {
                        //pauseMusic(); // Headset is unplugged, pause music
                        startMusicService("PAUSE");
                    }
                    // Handle Bluetooth device connection
                    if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (device != null && isSpeaker(device)) {
                            startMusicService("PAUSE"); // Pause music when a new Bluetooth audio speaker connects
                        }
                    }
                    // Handle Bluetooth device disconnection
                    if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (device != null && (isHeadset(device) || isSpeaker(device))) {
                            startMusicService("PAUSE");// Pause music when a new Bluetooth audio device connects
                        }
                    }
                    isInitialized = true;
                }
            };
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED); // Add Bluetooth connection events
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            context.registerReceiver(headsetReceiver, filter);
        } catch (IllegalArgumentException e) {
            Log.println(Log.ERROR, "PowerHandler", "PowerHandler register error"); // Handle case where receiver is not registered
        }

    }

    private boolean isHeadset(BluetoothDevice device) {
        // Check if the device is a headset
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (device.getBluetoothClass() != null) {
                int deviceClass = device.getBluetoothClass().getDeviceClass();
                return deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                        deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES;
            }
        }
        return false;
    }

    private boolean isSpeaker(BluetoothDevice device) {
        // Check if the device is a speaker
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (device.getBluetoothClass() != null) {
                int deviceClass = device.getBluetoothClass().getDeviceClass();
                return deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                        deviceClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER;
            }
        }
        return false;
    }
}
