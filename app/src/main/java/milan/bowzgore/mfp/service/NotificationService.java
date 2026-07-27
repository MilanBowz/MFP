package milan.bowzgore.mfp.service;

import static milan.bowzgore.mfp.MainActivity.viewPagerAdapter;
import static milan.bowzgore.mfp.service.PowerHandler.isListPlaying;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;

import android.net.Uri;
import android.os.IBinder;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;

import milan.bowzgore.mfp.MainActivity;
import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.library.SongLibrary;

public class NotificationService extends Service {
    private final int NOTIFICATION_ID = 1;
    public static final String CHANNEL_ID = "media_playback_channel";

    private PowerHandler powerHandler;
    public static ExoPlayer player;
    private MediaSessionHandler mediaSession;
    long lastPosition;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state){
            if(state == Player.STATE_READY){
                showNotification();
                mediaSession.updateMetadata();
                LocalBroadcastManager.getInstance(NotificationService.this)
                        .sendBroadcast(new Intent("PLAYER_READY"));
                if(viewPagerAdapter != null){
                    viewPagerAdapter.updatePlayingFragment();
                }
            }
            if(state == Player.STATE_ENDED){
                if(isListPlaying){
                    startMusicService("NEXT");
                }
            }
        }
    };

    public NotificationService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializePlayer();
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
                case "PAUSE":
                    pauseMusic();
                    lastPosition = player.getCurrentPosition();
                    break;
                case "IM_SAVE":
                    lastPosition = player.getCurrentPosition();
                    break;
                case "IM_UPDATE":
                    changePlaying(true);
                    break;
                case "NEXT":
                    playNextSong();
                    break;
                case "PREV":
                    playPreviousSong();
                    break;
                case "NEW":
                    changePlaying(false);
                    break;
                case "LOAD":
                case "UPDATE":
                    mediaSession.updateMediaSessionPlaybackState(player.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                    showNotification();
                    break;
                case "INIT":
                    init_device_get();
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

        NotificationCompat.Action actionToShow = player.isPlaying() ?
                new NotificationCompat.Action(R.drawable.ic_baseline_pause_circle_outline_24, "Pause", pausePendingIntent) :
                new NotificationCompat.Action(R.drawable.ic_baseline_play_circle_outline_24, "Play", playPendingIntent);
        NotificationCompat.Action nextAction = new NotificationCompat.Action(R.drawable.ic_baseline_skip_next_24, "Next", nextPendingIntent);
        NotificationCompat.Action prevAction = new NotificationCompat.Action(R.drawable.ic_baseline_skip_previous_24, "Prev", prevPendingIntent);
        NotificationCompat.Action stopAction = new NotificationCompat.Action(R.drawable.ic_baseline_close_24, "Stop", stopPendingIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(SongLibrary.get().currentSong.getTitle())
                .setContentIntent(contentIntent)
                .setLargeIcon(SongLibrary.get().currentSong.getNotificationArtWithGlide(this))
                .addAction(prevAction)
                .addAction(actionToShow)
                .addAction(nextAction)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setProgress((int) player.getDuration(), (int) player.getCurrentPosition(), false)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)// Show actions in compact view
                        .setMediaSession(mediaSession.getSessionToken()));
        if (!player.isPlaying()) {
            builder.addAction(stopAction);
            builder.setProgress(0, 0, false); // This hides the progress bar when the song isn't playing
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
        startForeground(NOTIFICATION_ID, builder.build());
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void playMusic() {
        player.play();
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        powerHandler.requestAudioFocus();
        mediaSession.updateMetadata();
    }

    private void pauseMusic() {
        player.pause();
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED);
        powerHandler.releaseWakeLockAndAudioFocus();
        mediaSession.updateMetadata();
    }

    private void playPauseMusic() {
        if (player.isPlaying()) {
            pauseMusic();
        } else {
            playMusic();
        }
    }

    private void playNextSong() {
        int next = SongLibrary.get().songNumber + 1;
        if(next >= SongLibrary.get().songsList.size()){
            next = 0;
        }
        changePlaying(next);
    }
    private void playPreviousSong() {
        int previous = SongLibrary.get().songNumber - 1;
        if(previous < 0){
            previous = SongLibrary.get().songsList.size() - 1;
        }
        changePlaying(previous);
    }

    private void changePlaying(int index) {
        SongLibrary songLibrary = SongLibrary.get();
        songLibrary.songNumber = index;
        songLibrary.currentSong =
                songLibrary.songsList.get(index);
        MediaItem item = MediaItem.fromUri(
                Uri.fromFile(
                        new File(songLibrary.currentSong.getPath())
                )
        );

        player.stop();
        player.clearMediaItems();
        player.setMediaItem(item);

        player.prepare();
        player.setPlayWhenReady(true);
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING);

        songLibrary.saveCurrentSong(getApplicationContext());
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("PLAYER_READY"));
        //showNotification();
        mediaSession.updateMetadata();
    }

    private void changePlaying(boolean isEdited) { // used in song list: SongsFragment  coverart update
        player.stop();
        player.clearMediaItems();

        MediaItem item = MediaItem.fromUri(Uri.fromFile(new File(SongLibrary.get().currentSong.getPath())));
        player.setMediaItem(item);
        player.prepare();
            // + mediaPlayer.seekTo(lastPosition); ?
        player.setPlayWhenReady(true);
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING);

        SongLibrary.get().saveCurrentSong(getApplicationContext());
        System.gc();
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("PLAYER_READY"));
        //showNotification();
        mediaSession.updateMetadata();
    }

    private void init_device_get() {
        initializePlayer();

        MediaItem item = MediaItem.fromUri(Uri.fromFile(new File(SongLibrary.get().currentSong.getPath())));
        player.setMediaItem(item);
        player.prepare();
        player.setPlayWhenReady(false);
        if(viewPagerAdapter != null){
            viewPagerAdapter.updatePlayingFragment();
            showNotification();
        }
    }

    private void stopMusic(){
        if(powerHandler != null){
            powerHandler.stop();
        }
        //SongLibrary lib = SongLibrary.get();
    }
    public void onStopFromNotification() {
        if (!player.isPlaying()) {
            onDestroy();
        }
    }
    @Override
    public void onDestroy() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID); // Removes the notification

        // Notify MainActivity to finish
        Intent intent = new Intent("FINISH_ACTIVITY");
        sendBroadcast(intent);
        stopMusic();
        stopForeground(true);
        stopSelf();
        super.onDestroy();
    }
    private void initializePlayer() {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            player.addListener(playerListener);
        }
        else{
            player.stop();
            player.clearMediaItems(); // Reset before setting a new data source
        }
    }

}