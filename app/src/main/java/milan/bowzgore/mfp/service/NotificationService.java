package milan.bowzgore.mfp.service;

import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;
import static milan.bowzgore.mfp.service.PowerHandler.currentMode;

import android.app.Notification;
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;

import milan.bowzgore.mfp.MainActivity;
import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.library.SongLibrary;

public class NotificationService extends Service {
    private final int NOTIFICATION_ID = 1;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    public static final String CHANNEL_ID = "media_playback_channel";
    private PowerHandler powerHandler;
    public static volatile MediaPlayer mediaPlayer;
    private MediaSessionHandler mediaSession;
    long lastPosition;
    public static volatile boolean isPlaying = false;
    public static volatile boolean playerPrepared = false;
    private final android.os.Handler progressHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private boolean playWhenPrepared = false;
    private int loadGeneration = 0;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying || !playerPrepared || mediaPlayer == null || mediaSession == null) {
                return;
            }

            if (mediaPlayer.isPlaying()) {
                mediaSession.updateMediaSessionPlaybackState(
                        PlaybackStateCompat.STATE_PLAYING
                );
                updateNotificationProgress();
            }

            progressHandler.postDelayed(this, 1000);
        }
    };
    // NotificationService

    @Override
    public void onCreate() {
        super.onCreate();
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
        PowerHandler.loadPlayMode(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        powerHandler = new PowerHandler(this);
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
        if (action == null) return START_NOT_STICKY;
        return startMusicService(action);
    }

    public int startMusicService(String action) {
        if (SongLibrary.get().currentSong != null) {
            switch (action) {
                case "PLAYPAUSE":
                    playPauseMusic();
                    break;
                case "PLAY":
                    playMusic();
                    break;
                case "REPLAY":
                    boolean resume = isPlaying;
                    loadSong(resume, 0);
                    break;
                case "IM_UPDATE":
                    SongLibrary.get().currentSong.clearArtworkCache();   // <-- one call, no versions
                    int position = playerPrepared && mediaPlayer != null
                            ? mediaPlayer.getCurrentPosition()
                            : 0;
                    SongLibrary.get().currentSong.clearArtworkCache();
                    loadSong(isPlaying, position);
                    mediaSession.updateMediaSessionPlaybackState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                    mediaSession.updateMetadata();
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("UPDATE"));
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
                case "LIST_PLAY":
                    if(PowerHandler.currentMode == 2){
                        if(SongLibrary.get().shuffledList.isEmpty()){
                            SongLibrary.get().makeRandomList();
                        }
                        else {
                            SongLibrary.get().songNumber = SongLibrary.get().shuffledList.indexOf(SongLibrary.get().currentSong);
                        }
                    }
                    loadSong(true, 0);
                    break;
                case "LOAD":
                case "UPDATE":
                    mediaSession.updateMediaSessionPlaybackState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                    //showNotification();
                    break;
                case "INIT":
                    if(!isPlaying){
                        if(PowerHandler.currentMode == 2){
                            if(SongLibrary.get().shuffledList.isEmpty()){
                                SongLibrary.get().makeRandomList();
                            }
                        }
                        showNotification();
                        loadSong(false,0);
                    }
                    break;
                case "INIT_PLAY":
                    if(PowerHandler.currentMode == 2){
                        if(SongLibrary.get().shuffledList.isEmpty()){
                            SongLibrary.get().makeRandomList();
                        }
                    }
                    showNotification();
                    loadSong(true,0);
                    break;
                case "STOP":
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
                    onStopFromNotification();
                    break;
                case "TOGGLE_PLAY_MODE":
                    PowerHandler.toggleShuffle(this);

                    getSharedPreferences("media_prefs", MODE_PRIVATE)
                            .edit()
                            .putInt("play_mode", PowerHandler.currentMode)
                            .apply();

                    mediaSession.updateMediaSessionPlaybackState(
                            isPlaying
                                    ? PlaybackStateCompat.STATE_PLAYING
                                    : PlaybackStateCompat.STATE_PAUSED
                    );
                    LocalBroadcastManager.getInstance(this)
                            .sendBroadcast(new Intent("TOGGLE_PLAY_MODE"));
                    if (notificationBuilder == null) break;
                    notificationBuilder.setContentText((SongLibrary.get().songNumber + 1) + "/"
                            + SongLibrary.get().songsList.size());

                    notificationManager.notify(
                            NOTIFICATION_ID,
                            notificationBuilder.build()
                    );
                    break;
            }
        }
        return START_STICKY;
    }


    private void showNotification() {
        if (SongLibrary.get().currentSong == null) {
            Log.d("NotificationService", "Current song is null, skipping notification");
            return;
        }
        SongLibrary library = SongLibrary.get();
        String indexText = (library.songNumber + 1) + "/" + library.songsList.size();

        Intent playIntent = new Intent(this, NotificationService.class).setAction("PLAY");
        Intent pauseIntent = new Intent(this, NotificationService.class).setAction("PAUSE");
        Intent nextIntent = new Intent(this, NotificationService.class).setAction("NEXT");
        Intent prevIntent = new Intent(this, NotificationService.class).setAction("PREV");
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
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Action actionToShow = isPlaying ?
                new NotificationCompat.Action(R.drawable.ic_baseline_pause_circle_outline_24, "Pause", pausePendingIntent) :
                new NotificationCompat.Action(R.drawable.ic_baseline_play_circle_outline_24, "Play", playPendingIntent);
        NotificationCompat.Action nextAction = new NotificationCompat.Action(R.drawable.ic_baseline_skip_next_24, "Next", nextPendingIntent);
        NotificationCompat.Action prevAction = new NotificationCompat.Action(R.drawable.ic_baseline_skip_previous_24, "Prev", prevPendingIntent);

        int duration = 0;
        int position = 0;
        if(playerPrepared){
            duration = mediaPlayer.getDuration();
            position = mediaPlayer.getCurrentPosition();
        }
        else{
            Log.w("NotificationService", "showNotification: MediaPlayer not ready");
        }
        if(mediaPlayer != null && duration != -1){
            notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.icon)
                    .setContentTitle(SongLibrary.get().currentSong.getTitle())
                    .setContentText(indexText)
                    .setContentIntent(contentIntent)
                    .setLargeIcon(SongLibrary.get().currentSong.getNotificationArtWithGlide(this))
                    .addAction(prevAction)
                    .addAction(actionToShow)
                    .addAction(nextAction)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setProgress(duration, position, false)
                    .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                            .setShowActionsInCompactView(0, 1, 2)// Show actions in compact view
                            .setMediaSession(mediaSession.getSessionToken()));
            Notification notification = notificationBuilder.build();

            notificationManager.notify(NOTIFICATION_ID, notification);
            startForeground(NOTIFICATION_ID, notification);
            Log.d("NotificationService", "Notification shown successfully");
        }

    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void loadSong(boolean autoplay, int seekPosition) {
        SongLibrary library = SongLibrary.get();

        if (library.currentSong == null) return;

        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }

        int generation = ++loadGeneration;

        playerPrepared = false;
        isPlaying = false;
        playWhenPrepared = autoplay;

        stopProgressUpdates();

        mediaPlayer.setOnPreparedListener(null);
        mediaPlayer.setOnCompletionListener(null);
        mediaPlayer.reset();

        mediaPlayer.setOnCompletionListener(mp -> {
            if (generation != loadGeneration) return;
            progressHandler.post(() -> {
                if (currentMode == PowerHandler.MODE_LOOP_ALL ||
                        currentMode == PowerHandler.MODE_RANDOM) {
                    startMusicService("NEXT");
                } else {
                    startMusicService("REPLAY");
                }
            });
        });

        mediaPlayer.setOnPreparedListener(mp -> {
            if (generation != loadGeneration) return;

            playerPrepared = true;

            int duration = mediaPlayer.getDuration();

            if (seekPosition > 0 && duration > 0) {
                mediaPlayer.seekTo(Math.min(seekPosition, duration));
            }

            mediaSession.updateMetadata();

            if (viewPagerAdapter != null) {
                viewPagerAdapter.updatePlayingFragment();
            }

            library.saveCurrentSong(getApplicationContext());

            if (playWhenPrepared) {
                playWhenPrepared = false;
                playMusic();
            } else {
                mediaSession.updateMediaSessionPlaybackState(
                        PlaybackStateCompat.STATE_PAUSED
                );
                showNotification();
            }
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            if (generation == loadGeneration) {
                playerPrepared = false;
                isPlaying = false;
                stopProgressUpdates();

                Log.e("NotificationService",
                        "MediaPlayer error: " + what + ", " + extra);
            }

            return true;
        });

        try {
            mediaPlayer.setDataSource(library.currentSong.getPath());
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            playerPrepared = false;
            isPlaying = false;
            Log.e("NotificationService", "Could not load song", e);
        }
    }


    private void playMusic() {
        if (mediaPlayer == null || !playerPrepared) return;
        if (!powerHandler.requestAudioFocus()) {
            isPlaying = false;
            return;
        }
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
        isPlaying = true;
        mediaSession.updateMediaSessionPlaybackState(
                PlaybackStateCompat.STATE_PLAYING
        );
        mediaSession.updateMetadata();
        showNotification();
        startProgressUpdates();
    }

    private void pauseMusic() {
        if (mediaPlayer == null || !playerPrepared) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        lastPosition = mediaPlayer.getCurrentPosition();
        isPlaying = false;
        stopProgressUpdates();
        mediaSession.updateMediaSessionPlaybackState(
                PlaybackStateCompat.STATE_PAUSED
        );

        mediaSession.updateMetadata();
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
        int next = SongLibrary.get().songNumber + 1;

        if (next >= SongLibrary.get().songsList.size()) {
            next = 0;
        }

        SongLibrary.get().setSongNumber(next);
        loadSong(true, 0);

        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent("UPDATE"));
    }
    private void playPreviousSong() {
        int previous = SongLibrary.get().songNumber - 1;
        if (previous < 0) {
            previous = SongLibrary.get().songsList.size() - 1;
        }
        SongLibrary.get().setSongNumber(previous);
        loadSong(true, 0);
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent("UPDATE"));
    }
    public void onStopFromNotification() {
        if (!isPlaying) {
            stopSelf();
        }
    }
    private void stopMusic() {
        isPlaying = false;
        stopProgressUpdates();

        if (powerHandler != null) {
            powerHandler.stop();
        }
    }

    @Override
    public void onDestroy() {
        stopMusic();
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        sendBroadcast(new Intent("FINISH_ACTIVITY"));

        if (mediaSession != null) {
            mediaSession.release();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        playerPrepared = false;
        stopForeground(true);
        super.onDestroy();
    }

    void updateNotificationProgress() {
        if (notificationBuilder == null ||
                !playerPrepared ||
                mediaPlayer == null) {
            return;
        }
        notificationBuilder.setProgress(
                mediaPlayer.getDuration(),
                mediaPlayer.getCurrentPosition(),
                false
        );
        notificationManager.notify(
                NOTIFICATION_ID,
                notificationBuilder.build()
        );
    }

    private void startProgressUpdates() {
        progressHandler.removeCallbacks(progressUpdater);
        progressHandler.post(progressUpdater);
    }

    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressUpdater);
    }

}