package milan.bowzgore.mfp.notification;

import static milan.bowzgore.mfp.library.SongLibrary.currentSong;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.library.SongLibrary;

public class NotificationService extends Service {
    public static final int NOTIFICATION_ID = 1;
    public static final String CHANNEL_ID = "media_playback_channel";

    public static boolean isPlaying = false;
    public static boolean isListPlaying = false;

    private PowerManager.WakeLock wakeLock;

    public static MediaPlayer mediaPlayer = new MediaPlayer() ;
    private MediaSessionCompat mediaSession;


    @Override
    public void onCreate() {
        super.onCreate();

        mediaPlayer.setOnCompletionListener(mp -> {
            if (isListPlaying) {
                startMusicService("NEXT");
            }
            else {
                startMusicService("PLAY");
            }
        });
        mediaSession = new MediaSessionCompat(this, "NotificationService");
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(
                this, 0, new Intent(Intent.ACTION_MEDIA_BUTTON), PendingIntent.FLAG_UPDATE_CURRENT));
        // Set up MediaSession Callback to handle media actions
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                if (mediaPlayer != null) {
                    startMusicService("PLAY");
                    if(isPlaying){
                        updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING);
                    }
                    else {
                        updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED);
                    }
                }
            }
            @Override
            public void onPause() {
                super.onPause();
                if (mediaPlayer != null) {
                    startMusicService("PAUSE");
                    if(isPlaying){
                        updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING);
                    }
                    else {
                        updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED);
                    }
                }

            }
            @Override
            public void onStop() {
                super.onStop();
                if (mediaPlayer != null) {
                    updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                    stopMusic();
                    stopSelf();
                }
            }
            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                if (mediaPlayer != null) {
                    startMusicService("NEXT");
                    updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT);
                }
            }
            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                if (mediaPlayer != null) {
                    startMusicService("PREV");
                    updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS);
                }
            }

            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo((int) pos);
                }
            }
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                KeyEvent event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (event.getKeyCode()) {
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            if (mediaPlayer != null) {
                                pauseMusic();
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            startMusicService("NEXT");
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            startMusicService("PREV");
                            return true;
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent);
            }
        });
        mediaSession.setActive(true);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MusicWakeLock");
        wakeLock.acquire(60*60*1000L /*60 minutes*/);
    }

    private void updateMediaSessionPlaybackState(int state) {
        PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_STOP |
                                PlaybackStateCompat.ACTION_SEEK_TO |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, mediaPlayer.getCurrentPosition(), 1.0f);

        mediaSession.setPlaybackState(playbackStateBuilder.build());
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // Handle the case where the intent is null
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        return startMusicService(action);
    }
    public int startMusicService(String action) {
        if (currentSong != null) {
            switch (action) {
                case "PLAY":
                case "PAUSE":
                    pauseMusic();
                    break;
                case "NEXT":
                    playNextSong();
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
                    break;
                case "PREV":
                    playPreviousSong();
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
                    break;
                case "START":
                    playMusic();
                    break;
                case "STOP":
                    stopForeground(true);
                    stopSelf();
                    System.exit(2);
                    break;
            }
            updateMetadata();
            showNotification();
        }
        return START_STICKY;
    }


    private void showNotification() {
        Intent playIntent = new Intent(this, NotificationService.class).setAction("PLAY");
        Intent pauseIntent = new Intent(this, NotificationService.class).setAction("PAUSE");
        Intent nextIntent = new Intent(this, NotificationService.class).setAction("NEXT");
        Intent prevIntent = new Intent(this, NotificationService.class).setAction("PREV");
        Intent stopIntent = new Intent(this, NotificationService.class).setAction("STOP");

        PendingIntent playPendingIntent = PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent nextPendingIntent = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent prevPendingIntent = PendingIntent.getService(this, 3, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Action actionToShow = isPlaying ?
                new NotificationCompat.Action(R.drawable.ic_baseline_pause_circle_outline_24, "Pause", pausePendingIntent) :
                new NotificationCompat.Action(R.drawable.ic_baseline_play_circle_outline_24, "Play", playPendingIntent);
        NotificationCompat.Action nextAction = new NotificationCompat.Action(R.drawable.ic_baseline_skip_next_24, "Next", nextPendingIntent);
        NotificationCompat.Action prevAction = new NotificationCompat.Action(R.drawable.ic_baseline_skip_previous_24, "Prev", prevPendingIntent);
        NotificationCompat.Action stopAction = new NotificationCompat.Action(R.drawable.ic_baseline_close_24, "Stop", stopPendingIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(SongLibrary.currentSong.getTitle())
                .addAction(prevAction)
                .addAction(actionToShow) // Show the appropriate action
                .addAction(nextAction)
                .addAction(stopAction) // Add the stop action
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2, 3)// Show actions in compact view
                        .setMediaSession(mediaSession.getSessionToken()));
        startForeground(NOTIFICATION_ID, builder.build());
        //updateMetadata();
    }



    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateMetadata() {
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSong.getTitle())
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                        currentSong.getImage())
                .build();

        mediaSession.setMetadata(metadata);
    }

    // music player actions

    public void playMusic(){
        updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        mediaPlayer.start();
        isPlaying = true;
    }

    public void pauseMusic() {
        if (isPlaying) {
            updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED);
            mediaPlayer.pause();
            isPlaying = false;

        } else {
            updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING);
            mediaPlayer.start();
            isPlaying = true;
        }
    }
    public void playNextSong(){
        if(SongLibrary.songNumber == SongLibrary.songsList.size()-1){
            SongLibrary.changePlaying(0);
            playMusic();
            return;
        }
        SongLibrary.changePlaying(SongLibrary.songNumber +1);
        playMusic();
    }
    public void playPreviousSong(){
        if(SongLibrary.songNumber == 0){
            SongLibrary.changePlaying(SongLibrary.songsList.size()-1);
            playMusic();
            return;
        }
        SongLibrary.changePlaying(SongLibrary.songNumber -1);
        playMusic();
    }
    public void stopMusic(){
        updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED);
        mediaPlayer.pause();
        mediaPlayer.release();
        isPlaying = false;
    }
    public static void setListPlaying() {
        isListPlaying = ! isListPlaying;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopForeground(true);
    }

}