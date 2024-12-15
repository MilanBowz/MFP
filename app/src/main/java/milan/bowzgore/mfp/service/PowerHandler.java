package milan.bowzgore.mfp.service;

import android.Manifest;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.PowerManager;

import androidx.core.app.ActivityCompat;

public class PowerHandler {

    private final Context context;
    private PowerManager.WakeLock wakeLock;

    public static boolean isListPlaying = false;

    public PowerHandler(Context context) {
        this.context = context;
    }

    private void startMusicService(String action){
        Intent playIntent = new Intent(context, NotificationService.class);
        playIntent.setAction(action);  // Action that the service will handle
        context.startService(playIntent);
    }

    protected void setWakelock(){
        if (isListPlaying) {
            acquireWakeLock();
            startMusicService("NEXT");
        }
        else {
            startMusicService("START");
            releaseWakeLock();
        }
    }

    protected void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MusicWakeLock");
        }

        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }

    // Method to release the wake lock
    protected void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
    private boolean isBluetoothAudioDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (device.getBluetoothClass() != null) {
                int deviceClass = device.getBluetoothClass().getDeviceClass();
                return deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                        deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                        deviceClass == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO ||
                        deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                        deviceClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER;
            }
        }
        return false;
    }
}
