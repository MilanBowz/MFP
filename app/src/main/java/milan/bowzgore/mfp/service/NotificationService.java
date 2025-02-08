package milan.bowzgore.mfp.service;

import static milan.bowzgore.mfp.service.PowerHandler.isListPlaying;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
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
            if (isListPlaying) {
                startMusicService("NEXT");
            } else {
                startMusicService("START");
            }
            Log.d("MediaPlayer", "Playback completed");
        });
        powerHandler.setup();
        mediaSession = new MediaSessionHandler(this);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopForeground(true);
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        return startMusicService(action);
    }

    public int startMusicService(String action) {
        if (SongLibrary.get().currentSong != null) {
            switch (action) {
                case "PLAYPAUSE":
                    playPauseMusic();
                    break;
                case "PLAY":
                case "START":
                    playMusic();
                    break;
                case "PAUSE":
                    pauseMusic();
                    break;
                case "NEXT":
                    playNextSong();
                    break;
                case "PREV":
                    playPreviousSong();
                    break;
                case "NEW":
                    changePlaying();
                    break;
                case "LOAD":
                case "UPDATE":
                    mediaSession.updateMediaSessionPlaybackState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                    showNotification();
                    break;
                case "INIT":
                    init_device_get();
                    playMusic();
                    break;
                case "STOP":
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
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

        PendingIntent playPendingIntent = PendingIntent.getService(this, 0, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent nextPendingIntent = PendingIntent.getService(this, 2, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent prevPendingIntent = PendingIntent.getService(this, 3, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 4, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Action actionToShow = isPlaying ?
                new NotificationCompat.Action(R.drawable.ic_baseline_pause_circle_outline_24, "Pause", pausePendingIntent) :
                new NotificationCompat.Action(R.drawable.ic_baseline_play_circle_outline_24, "Play", playPendingIntent);
        NotificationCompat.Action nextAction = new NotificationCompat.Action(R.drawable.ic_baseline_skip_next_24, "Next", nextPendingIntent);
        NotificationCompat.Action prevAction = new NotificationCompat.Action(R.drawable.ic_baseline_skip_previous_24, "Prev", prevPendingIntent);
        NotificationCompat.Action stopAction = new NotificationCompat.Action(R.drawable.ic_baseline_close_24, "Stop", stopPendingIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(SongLibrary.get().currentSong.getTitle())
                .setContentIntent(contentIntent)
                .setLargeIcon(SongLibrary.get().currentSong.getArt(getBaseContext(),2))
                .addAction(prevAction)
                .addAction(actionToShow)
                .addAction(nextAction)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setProgress(mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition(), false)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2, 3)// Show actions in compact view
                        .setMediaSession(mediaSession.getSessionToken()));
        if (!isPlaying) {
            builder.addAction(stopAction);
            builder.setProgress(0, 0, false); // This hides the progress bar when the song isn't playing
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void playMusic() {
        isPlaying = true;
        mediaPlayer.start();
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        showNotification();
        powerHandler.requestAudioFocus();
        mediaSession.updateMetadata();
    }

    private void pauseMusic() {
        isPlaying = false;
        mediaPlayer.pause();
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED);
        showNotification();
        powerHandler.releaseWakeLockAndAudioFocus();
        mediaSession.updateMetadata();
    }

    private void playPauseMusic() {
        if (isPlaying) {
            pauseMusic();
        } else {
            playMusic();
        }
    }

    private void playNextSong() {
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT);
        if (SongLibrary.get().songNumber == SongLibrary.get().songsList.size() - 1) {
            changePlaying(0);
            playMusic();
        }
        else {
            changePlaying(SongLibrary.get().songNumber + 1);
            playMusic();
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("NEXT"));
        mediaSession.updateMetadata();
        showNotification();
    }

    private void playPreviousSong() {
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS);
        if (SongLibrary.get().songNumber == 0) {
            changePlaying(SongLibrary.get().songsList.size() - 1);
            playMusic();
        }
        else {
            changePlaying(SongLibrary.get().songNumber - 1);
            playMusic();
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("PREV"));
        mediaSession.updateMetadata();
        showNotification();
    }

    public void changePlaying(int index) {
        SongLibrary songLibrary = SongLibrary.get(); // Access the Singleton instance
        songLibrary.songNumber = index;
        songLibrary.currentSong = songLibrary.songsList.get(songLibrary.songNumber);
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(songLibrary.currentSong.getPath());
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        songLibrary.saveCurrentSong(getApplicationContext());
    }

    private void changePlaying() {
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(SongLibrary.get().currentSong.getPath());
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void init_device_get() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.reset();
            } catch (IllegalStateException e) {
                Log.e("MiniPlayer", "Error stopping or resetting MediaPlayer: " + e.getMessage());
            }
        }
        try {
            mediaPlayer.setDataSource(SongLibrary.get().currentSong.getPath());
            mediaPlayer.prepareAsync();
            isPlaying = false;
        } catch (IOException e) {
            Log.println(Log.ERROR, "mediaplayer", "mediaplayer error init Songlibrary");
        }
    }

    private void stopMusic() {
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED);
        mediaPlayer.reset();
        isPlaying = false;
        SongLibrary.get().currentSong = null;
        powerHandler.stop();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID); // Removes the notification
        stopForeground(true);
        stopSelf();
    }

    public void onStopFromNotification() {
        if (!isPlaying) {
            stopMusic();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMusic();
    }
}