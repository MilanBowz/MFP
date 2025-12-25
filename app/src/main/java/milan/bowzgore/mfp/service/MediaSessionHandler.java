package milan.bowzgore.mfp.service;

import static milan.bowzgore.mfp.service.NotificationService.mediaPlayer;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.core.content.ContextCompat;

import milan.bowzgore.mfp.library.SongLibrary;


class MediaSessionHandler {
    private final Context context;
    private MediaSessionCompat mediaSession;

    public MediaSessionHandler(Context context) {
        this.context = context;
        setupMediaSession();
    }

    private void startMusicService(String action) {
        Intent playIntent = new Intent(context, NotificationService.class);
        playIntent.setAction(action);  // Action that the service will handle
        ContextCompat.startForegroundService(context,playIntent);
    }

    protected void updateMediaSessionPlaybackState(int state) {
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

    public MediaSessionCompat.Token getSessionToken() {
        return mediaSession.getSessionToken();
    }

    void updateMetadata() {
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, SongLibrary.get().currentSong.getTitle())
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, SongLibrary.get().currentSong.getArt(2))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration())
                .build();
        mediaSession.setMetadata(metadata);
    }

    public void setupMediaSession() {
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                mediaButtonIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        mediaSession = new MediaSessionCompat(context, "NotificationService", null, pendingIntent);


        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() { startMusicService("PLAY"); }

            @Override
            public void onPause() { startMusicService("PAUSE"); }

            @Override
            public void onStop() {
                updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED);
            }

            @Override
            public void onSkipToNext() { startMusicService("NEXT"); }

            @Override
            public void onSkipToPrevious() { startMusicService("PREV"); }

            @Override
            public void onSeekTo(long pos) {
                mediaPlayer.seekTo((int) pos);
                updateMediaSessionPlaybackState(
                        NotificationService.isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED
                );
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                KeyEvent event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (event.getKeyCode()) {
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            startMusicService("PLAYPAUSE"); return true;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            startMusicService("NEXT"); return true;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            startMusicService("PREV"); return true;
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent);
            }
        });

        mediaSession.setActive(true); // Only once
    }

}
