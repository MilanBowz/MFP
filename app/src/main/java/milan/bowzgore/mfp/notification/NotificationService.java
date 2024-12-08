package milan.bowzgore.mfp.notification;
import static milan.bowzgore.mfp.library.SongLibrary.currentSong;
import static milan.bowzgore.mfp.library.SongLibrary.songNumber;
import static milan.bowzgore.mfp.library.SongLibrary.songsList;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.util.List;

import milan.bowzgore.mfp.FolderFragment;
import milan.bowzgore.mfp.MainActivity;
import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.library.SongLibrary;

public class NotificationService extends Service {
    private final int NOTIFICATION_ID = 1;
    public static final String CHANNEL_ID = "media_playback_channel";

    public static boolean isPlaying = false;
    public static boolean isListPlaying = false;

    private PowerManager.WakeLock wakeLock;

    public static final MediaPlayer mediaPlayer = new MediaPlayer();
    private MediaSessionCompat mediaSession;

    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener afChangeListener;
    private boolean isInitialized = false;

    public NotificationService(){

    }

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MusicWakeLock");

        mediaPlayer.setOnCompletionListener(mp -> {
            if (isListPlaying) {
                if(!wakeLock.isHeld())
                    wakeLock.acquire();
                startMusicService("NEXT");
            }
            else {
                if(wakeLock.isHeld())
                    wakeLock.release();
                startMusicService("START");
            }
        });
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        afChangeListener = focusChange -> {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                case AudioManager.AUDIOFOCUS_LOSS:
                    pauseMusic();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (!isPlaying) {
                        playMusic();
                    }
                    break;
            }
        };

        setupMedaSession();

        final BroadcastReceiver headsetReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_HEADSET_PLUG.equals(intent.getAction()) && isInitialized) {
                    pauseMusic(); // Headset is unplugged, pause music
                }
                isInitialized = true;
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headsetReceiver, filter);
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
                    playPauseMusic();
                    updateMetadata();
                    break;
                case "NEXT":
                    playNextSong();
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
                    updateMetadata();
                    showNotification();
                    break;
                case "PREV":
                    playPreviousSong();
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
                    updateMetadata();
                    showNotification();
                    break;
                case "START":
                    if(isListPlaying && !wakeLock.isHeld())
                        wakeLock.acquire();
                    playMusic();
                    requestAudioFocus();
                    updateMetadata();
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

    private void updateMetadata() {
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSong.getTitle())
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                        currentSong.getImage())
                .build();

        mediaSession.setMetadata(metadata);
    }

    private void playMusic(){
        mediaPlayer.start();
        isPlaying = true;
        updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        showNotification();
    }
    private void pauseMusic(){
        mediaPlayer.pause();
        updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED);
        if (wakeLock.isHeld()) {
            wakeLock.release(); // Release WakeLock on pause
        }
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
    private void playNextSong(){
        if(songNumber == songsList.size()-1){
            changePlaying(0);
            playMusic();
            return;
        }
        changePlaying(songNumber +1);
        playMusic();
        showNotification();
    }
    private void playPreviousSong(){
        if(songNumber == 0){
            changePlaying(songsList.size()-1);
            playMusic();
            return;
        }
        changePlaying(songNumber -1);
        playMusic();
        showNotification();
    }
    public static void changePlaying(int index){
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
    public static void init_device_get(){
        if( mediaPlayer != null && currentSong != null){
            mediaPlayer.stop();
        }
        if( mediaPlayer != null && isPlaying ){
            mediaPlayer.reset();
        }
        try {
            Log.d("MiniPlayer", "Media path: " + currentSong.getPath());
            mediaPlayer.setDataSource(currentSong.getPath());
            System.out.println(currentSong.getPath());
            isPlaying = true;
            mediaPlayer.prepare();
            currentSong.getEmbeddedArtwork(currentSong.getPath());
        }
        catch (IOException e)
        {
            e.printStackTrace();
            Log.println(Log.ERROR,"mediaplayer","mediaplayer error init datasource Songlibrary");
        }
    }
    private void stopMusic(){
        updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED);
        mediaPlayer.reset();
        audioManager.abandonAudioFocus(afChangeListener);
        isPlaying = false;
        currentSong = null;
        if (wakeLock.isHeld()) {
            wakeLock.release(); // Release WakeLock when playback stops
        }
        stopForeground(true);
    }

    public void onStopFromNotification(){
        if(!isPlaying){
            stopMusic();
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            stopForeground(true);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMusic();

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        currentSong = null;
        isPlaying = false;
        stopForeground(true);
    }

    private void requestAudioFocus() {
        int result = audioManager.requestAudioFocus(afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            playMusic();
        }
    }

    private void setupMedaSession(){
        mediaSession = new MediaSessionCompat(this, "NotificationService");
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(
                this, 0, new Intent(Intent.ACTION_MEDIA_BUTTON), PendingIntent.FLAG_UPDATE_CURRENT));

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
                updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                super.onStop();
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
                            playPauseMusic();
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
    }
}