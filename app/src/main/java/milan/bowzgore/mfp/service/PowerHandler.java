package milan.bowzgore.mfp.service;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.PowerManager;

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
}
