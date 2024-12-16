package milan.bowzgore.mfp.service;

import static milan.bowzgore.mfp.library.SongLibrary.currentSong;
import static milan.bowzgore.mfp.library.SongLibrary.songNumber;
import static milan.bowzgore.mfp.library.SongLibrary.songsList;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;

import milan.bowzgore.mfp.MainActivity;
import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.library.SongLibrary;

public class NotificationService extends Service {
    private final int NOTIFICATION_ID = 1;
    public static final String CHANNEL_ID = "media_playback_channel";

    public static boolean isPlaying = false;

    private PowerHandler powerHandler;

    public static final MediaPlayer mediaPlayer = new MediaPlayer();
    private MediaSessionHandler mediaSession;

    public NotificationService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        powerHandler = new PowerHandler(this);

        mediaPlayer.setOnCompletionListener(mp -> {
            powerHandler.setWakelock();
        });

        powerHandler.setup();
        mediaSession = new MediaSessionHandler(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        return startMusicService(action);
    }

    public int startMusicService(String action) {
        if (currentSong != null) {
            switch (action) {
                case "PLAYPAUSE":
                    playPauseMusic();
                    mediaSession.updateMetadata();
                    break;
                case "PLAY":
                    playMusic();
                    mediaSession.updateMetadata();
                    break;
                case "PAUSE":
                    pauseMusic();
                    mediaSession.updateMetadata();
                    break;
                case "NEXT":
                    playNextSong();
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
                    mediaSession.updateMetadata();
                    showNotification();
                    break;
                case "PREV":
                    playPreviousSong();
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
                    mediaSession.updateMetadata();
                    showNotification();
                    break;
                case "START":
                    powerHandler.acquireWakeLock();
                    playMusic();
                    powerHandler.requestAudioFocus();
                    mediaSession.updateMetadata();
                    showNotification();
                    break;
                case "STOP":
                    onStopFromNotification();
                    break;
            }
        }
        return START_STICKY;
    }


    private void showNotification() {
        Intent playIntent = new Intent(this, NotificationService.class).setAction("PLAY");
        Intent pauseIntent = new Intent(this, NotificationService.class).setAction("PAUSE");
        Intent nextIntent = new Intent(this, NotificationService.class).setAction("NEXT");
        Intent prevIntent = new Intent(this, NotificationService.class).setAction("PREV");
        Intent stopIntent = new Intent(this, NotificationService.class).setAction("STOP");
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent playPendingIntent = PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent nextPendingIntent = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent prevPendingIntent = PendingIntent.getService(this, 3, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Action actionToShow = isPlaying ?
                new NotificationCompat.Action(R.drawable.ic_baseline_pause_circle_outline_24, "Pause", pausePendingIntent) :
                new NotificationCompat.Action(R.drawable.ic_baseline_play_circle_outline_24, "Play", playPendingIntent);
        NotificationCompat.Action nextAction = new NotificationCompat.Action(R.drawable.ic_baseline_skip_next_24, "Next", nextPendingIntent);
        NotificationCompat.Action prevAction = new NotificationCompat.Action(R.drawable.ic_baseline_skip_previous_24, "Prev", prevPendingIntent);
        NotificationCompat.Action stopAction = new NotificationCompat.Action(R.drawable.ic_baseline_close_24, "Stop", stopPendingIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(SongLibrary.currentSong.getTitle())
                .setContentIntent(contentIntent)
                .setLargeIcon(currentSong.getImage())
                .setShowWhen(false)
                .addAction(prevAction)
                .addAction(actionToShow)
                .addAction(nextAction)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2, 3)// Show actions in compact view
                        .setMediaSession(mediaSession.getSessionToken()));
        if (!isPlaying) {
            builder.addAction(stopAction);
        }
        startForeground(NOTIFICATION_ID, builder.build());
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void playMusic() {
        mediaPlayer.start();
        isPlaying = true;
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        showNotification();
    }

    private void pauseMusic() {
        mediaPlayer.pause();
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED);
        powerHandler.releaseWakeLockAndAudioFocus();
        isPlaying = false;
        showNotification();
    }

    private void playPauseMusic() {
        if (isPlaying) {
            pauseMusic();
        } else {
            playMusic();
        }
    }

    private void playNextSong() {
        if (songNumber == songsList.size() - 1) {
            changePlaying(0);
            playMusic();
            return;
        }
        changePlaying(songNumber + 1);
        playMusic();
        showNotification();
    }

    private void playPreviousSong() {
        if (songNumber == 0) {
            changePlaying(songsList.size() - 1);
            playMusic();
            return;
        }
        changePlaying(songNumber - 1);
        playMusic();
        showNotification();
    }

    public static void changePlaying(int index) {
        SongLibrary.songNumber = index;
        currentSong = SongLibrary.songsList.get(SongLibrary.songNumber);
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(currentSong.getPath());
            System.out.println(currentSong.getPath());
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void init_device_get() {
        if (mediaPlayer != null && currentSong != null) {
            mediaPlayer.stop();
        }
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.reset();
        }
        try {
            Log.d("MiniPlayer", "Media path: " + currentSong.getPath());
            mediaPlayer.setDataSource(currentSong.getPath());
            System.out.println(currentSong.getPath());
            isPlaying = true;
            mediaPlayer.prepare();
            currentSong.getEmbeddedArtwork(currentSong.getPath());
        } catch (IOException e) {
            e.printStackTrace();
            Log.println(Log.ERROR, "mediaplayer", "mediaplayer error init datasource Songlibrary");
        }
    }

    private void stopMusic() {
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED);
        mediaPlayer.reset();
        isPlaying = false;
        currentSong = null;
        powerHandler.releaseWakeLockAndAudioFocus();
        stopForeground(true);
    }

    public void onStopFromNotification() {
        if (!isPlaying) {
            stopMusic();
            powerHandler.releaseWakeLockAndAudioFocus();
            stopForeground(true);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMusic();
        powerHandler.releaseWakeLockAndAudioFocus();
        currentSong = null;
        isPlaying = false;
        stopForeground(true);
    }
}