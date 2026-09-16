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
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;
import java.io.IOException;

import milan.bowzgore.mfp.MainActivity;
import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.library.SongLibrary;

public class NotificationService extends Service {
    private final int NOTIFICATION_ID = 1;
    public static final String CHANNEL_ID = "media_playback_channel";
    private PowerHandler powerHandler;
    public static volatile MediaPlayer mediaPlayer;
    private MediaSessionHandler mediaSession;
    long lastPosition;
    public static Boolean isPlaying = false;

    public NotificationService() {
        if(mediaPlayer == null){
            mediaPlayer = new MediaPlayer();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
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
                case "REPLAY", "IM_UPDATE":
                    changePlaying(true);
                    if(isPlaying){
                        playMusic();
                    }
                    mediaSession.updateMediaSessionPlaybackState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                    showNotification();
                    break;
                case "PAUSE":
                    pauseMusic();
                    lastPosition = mediaPlayer.getCurrentPosition();
                    break;
                case "IM_SAVE":
                    lastPosition = mediaPlayer.getCurrentPosition();
                    break;
                case "NEXT":
                    playNextSong();
                    break;
                case "PREV":
                    playPreviousSong();
                    break;
                case "LIST_PLAY":
                    if(PowerHandler.currentMode == 2){
                        SongLibrary.get().makeRandomList();
                    }
                    loadCurrentSong();
                    playMusic();
                    break;
                case "LOAD":
                case "UPDATE":
                    mediaSession.updateMediaSessionPlaybackState(mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                    showNotification();
                    break;
                case "INIT":
                    init_device_get();
                    SongLibrary library = SongLibrary.get();
                    if(PowerHandler.currentMode == 2){
                        if(library.shuffledList.isEmpty()){
                            SongLibrary.get().makeRandomList();
                        }
                        else if(library.currentSong != null){
                            library.songNumber = library.shuffledList.indexOf(library.currentSong);
                        }
                    }
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
        if (SongLibrary.get().currentSong == null) {
            Log.d("NotificationService", "Current song is null, skipping notification");
            return;
        }
        SongLibrary library = SongLibrary.get();
        int currentIndex = library.songNumber + 1;
        String indexText = currentIndex + "/" + library.songsList.size();

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

        NotificationCompat.Action actionToShow = mediaPlayer.isPlaying() ?
                new NotificationCompat.Action(R.drawable.ic_baseline_pause_circle_outline_24, "Pause", pausePendingIntent) :
                new NotificationCompat.Action(R.drawable.ic_baseline_play_circle_outline_24, "Play", playPendingIntent);
        NotificationCompat.Action nextAction = new NotificationCompat.Action(R.drawable.ic_baseline_skip_next_24, "Next", nextPendingIntent);
        NotificationCompat.Action prevAction = new NotificationCompat.Action(R.drawable.ic_baseline_skip_previous_24, "Prev", prevPendingIntent);
        NotificationCompat.Action stopAction = new NotificationCompat.Action(R.drawable.ic_baseline_close_24, "Stop", stopPendingIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
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
                .setPriority(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setProgress((int) mediaPlayer.getDuration(), (int) mediaPlayer.getCurrentPosition(), false)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)// Show actions in compact view
                        .setMediaSession(mediaSession.getSessionToken()));
        if (!mediaPlayer.isPlaying()) {
            builder.addAction(stopAction);
            builder.setProgress(0, 0, false); // This hides the progress bar when the song isn't playing
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = builder.build();

        notificationManager.notify(NOTIFICATION_ID, notification);
        startForeground(NOTIFICATION_ID, notification);
        Log.d("NotificationService", "Notification shown successfully");
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
        if (mediaPlayer.isPlaying()) {
            pauseMusic();
        } else {
            playMusic();
        }
    }

    private void playNextSong() {
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT);

        int next = SongLibrary.get().songNumber + 1;
        if(next >= SongLibrary.get().songsList.size()){
            next = 0;
        }
        changePlaying(next);
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("NEXT"));
        mediaSession.updateMetadata();
        showNotification();
    }
    private void playPreviousSong() {
        mediaSession.updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS);
        int previous = SongLibrary.get().songNumber - 1;
        if(previous < 0){
            previous = SongLibrary.get().songsList.size() - 1;
        }
        changePlaying(previous);
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("NEXT"));
        mediaSession.updateMetadata();
        showNotification();
    }

    private void changePlaying(boolean isEdited) { // used in song list: SongsFragment  coverart update
        mediaPlayer.setOnPreparedListener(null);
        mediaPlayer.setOnCompletionListener(null);
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(SongLibrary.get().currentSong.getPath());
            mediaPlayer.prepare();
            mediaPlayer.setOnPreparedListener(mp->{
                if(isEdited){
                    mediaPlayer.seekTo((int) lastPosition);
                    if(isPlaying){
                        playMusic();
                    }
                }
                else {
                    playMusic();
                }
                mediaPlayer.setOnCompletionListener(mp1 -> {
                    if(currentMode == 0 || currentMode == 2){
                        startMusicService("NEXT");
                    } else{
                        startMusicService("REPLAY");
                    }
                });
                if(viewPagerAdapter != null){
                    viewPagerAdapter.updatePlayingFragment(); // update song in Playingfragment
                    if(isEdited) {
                        viewPagerAdapter.updateSongsFragment(); // update song in Songsfragment
                    }
                }
                SongLibrary.get().saveCurrentSong(getApplicationContext());
                System.gc();
            });
        } catch (IOException e) {
            Log.e("NotificationService.MediaPlayer", "changing song error with library");
        }
    }
    private void changePlaying(int index) {
        SongLibrary library = SongLibrary.get();
        mediaPlayer.setOnPreparedListener(null);
        mediaPlayer.setOnCompletionListener(null);
        library.setSongNumber(index);

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        mediaPlayer.reset();
        try {
            mediaPlayer.setDataSource(library.currentSong.getPath());
            mediaPlayer.prepare();
            mediaPlayer.setOnPreparedListener(mp->{
                playMusic();
                if(viewPagerAdapter != null){
                    viewPagerAdapter.updatePlayingFragment();
                }
                mediaPlayer.setOnCompletionListener(mp1 -> {
                    if(currentMode == 0 || currentMode == 2){
                        startMusicService("NEXT");
                    } else{
                        startMusicService("REPLAY");
                    }
                    Log.d("NotificationService.MediaPlayer", "Playback completed");
                });
                library.saveCurrentSong(getApplicationContext());
                System.gc();
            });
        } catch (IOException e) {
            Log.e("NotificationService.MediaPlayer", "changing song error with index");
        }
    }

    private void loadCurrentSong() { // used in song list: SongsFragment  coverart update
        if(mediaPlayer == null){
            mediaPlayer = new MediaPlayer();
        }
        mediaPlayer.setOnCompletionListener(null);
        mediaPlayer.reset(); // Reset before setting a new data source
        try {
            mediaPlayer.setDataSource(SongLibrary.get().currentSong.getPath());
            mediaPlayer.prepare();
            if(isPlaying){
                startMusicService("PLAY");
            }
            if(viewPagerAdapter != null){
                viewPagerAdapter.updatePlayingFragment();
            }
            mediaPlayer.setOnCompletionListener(mp1 -> {
                if(currentMode == 0 || currentMode == 2){
                    startMusicService("NEXT");
                } else{
                    startMusicService("REPLAY");
                }
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e("MediaPlayer", "Error occurred: " + what + ", " + extra);
                mediaPlayer.reset();
                return true;
            });
        } catch (IOException e) {
            Log.e("Notification.MediaPlayer", "Mediaplayer error init");
        }
    }


    private void init_device_get() {
        if(mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
        mediaPlayer.setOnCompletionListener(null);
        mediaPlayer.reset(); // Reset before setting a new data source
        try {
            mediaPlayer.setDataSource(SongLibrary.get().currentSong.getPath());
            mediaPlayer.prepare();
            if(isPlaying){
                startMusicService("PLAY");
            }
            if(viewPagerAdapter != null){
                viewPagerAdapter.updatePlayingFragment();
            }
            mediaPlayer.setOnCompletionListener(mp1 -> {
                if(currentMode == 0 || currentMode == 2){
                    startMusicService("NEXT");
                } else{
                    startMusicService("REPLAY");
                }
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e("MediaPlayer", "Error occurred: " + what + ", " + extra);
                mediaPlayer.reset();
                return true;
            });
        } catch (IOException e) {
            Log.e("Notification.MediaPlayer", "Mediaplayer error init");
        }
    }

    private void stopMusic(){
        isPlaying = false;
        if(powerHandler != null){
            powerHandler.stop();
        }
        //SongLibrary lib = SongLibrary.get();
    }
    public void onStopFromNotification() {
        if (!isPlaying) {
            onDestroy();
        }
    }
    @Override
    public void onDestroy() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID); // Removes the notification
        //player.removeListener(playerListener);
        // Notify MainActivity to finish
        Intent intent = new Intent("FINISH_ACTIVITY");
        sendBroadcast(intent);
        stopMusic();
        stopForeground(true);
        stopSelf();
        super.onDestroy();
    }


    /*@Override
    public void onTaskRemoved(Intent rootIntent) {
        // Keep service running when app is swiped away
        if (player.isPlaying()) {
            showNotification();
        }
    }*/

}