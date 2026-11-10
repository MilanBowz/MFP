
package milan.bowzgore.mfp.service;

import static milan.bowzgore.mfp.service.NotificationService.isPlaying;
import static milan.bowzgore.mfp.service.NotificationService.mediaPlayer;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.core.content.ContextCompat;

import milan.bowzgore.mfp.R;
import milan.bowzgore.mfp.library.SongLibrary;

class MediaSessionHandler {

    private final Context context;
    private MediaSessionCompat mediaSession;

    MediaSessionHandler(Context context) {
        this.context = context;
        setupMediaSession();
    }

    private void startMusicService(String action) {
        Intent intent = new Intent(context, NotificationService.class);
        intent.setAction(action);

        mediaSession.setActive(true);
        ContextCompat.startForegroundService(context, intent);
    }

    void updateMediaSessionPlaybackState(int state) {
        if (!NotificationService.playerPrepared || mediaPlayer == null || mediaSession == null) {
            return;
        }
        PlaybackStateCompat.CustomAction modeAction =
                switch (PowerHandler.currentMode) {
                    case PowerHandler.MODE_LOOP_ALL ->
                            new PlaybackStateCompat.CustomAction.Builder(
                                    "TOGGLE_PLAY_MODE",
                                    "Loop playlist",
                                    R.drawable.ic_baseline_loop_24
                            ).build();

                    case PowerHandler.MODE_LOOP_OFF ->
                            new PlaybackStateCompat.CustomAction.Builder(
                                    "TOGGLE_PLAY_MODE",
                                    "Loop playlist",
                                    R.drawable.ic_baseline_loop_off_24
                            ).build();

                    default ->
                            new PlaybackStateCompat.CustomAction.Builder(
                                    "TOGGLE_PLAY_MODE",
                                    "Shuffle",
                                    R.drawable.ic_baseline_loop_off_random
                            ).build();
                };
        PlaybackStateCompat playbackState =
                new PlaybackStateCompat.Builder()
                        .setActions(
                                PlaybackStateCompat.ACTION_PLAY |
                                        PlaybackStateCompat.ACTION_PAUSE |
                                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                        PlaybackStateCompat.ACTION_STOP |
                                        PlaybackStateCompat.ACTION_SEEK_TO |
                                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        )
                        .addCustomAction(modeAction)
                        .setState(
                                state,
                                mediaPlayer.getCurrentPosition(),
                                state == PlaybackStateCompat.STATE_PLAYING ? 1.0f : 0.0f
                        )
                        .build();
        mediaSession.setActive(true);
        mediaSession.setPlaybackState(playbackState);
    }

    void updateMetadata() {
        if (SongLibrary.get().currentSong == null ||
                !NotificationService.playerPrepared ||
                mediaPlayer == null ||
                mediaSession == null) {
            return;
        }

        MediaMetadataCompat metadata =
                new MediaMetadataCompat.Builder()
                        .putString(
                                MediaMetadataCompat.METADATA_KEY_TITLE,
                                SongLibrary.get().currentSong.getTitle()
                        )
                        .putBitmap(
                                MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                                SongLibrary.get().currentSong.getNotificationArtWithGlide(context)
                        )
                        .putLong(
                                MediaMetadataCompat.METADATA_KEY_DURATION,
                                mediaPlayer.getDuration()
                        )
                        .build();

        mediaSession.setMetadata(metadata);
    }

    MediaSessionCompat.Token getSessionToken() {
        return mediaSession.getSessionToken();
    }

    private void setupMediaSession() {
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                mediaButtonIntent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        mediaSession = new MediaSessionCompat(
                context,
                "NotificationService",
                null,
                pendingIntent
        );

        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        mediaSession.setCallback(new MediaSessionCompat.Callback() {

            @Override
            public void onPlay() {
                startMusicService("PLAY");
            }

            @Override
            public void onPause() {
                startMusicService("PAUSE");
            }

            @Override
            public void onStop() {
                startMusicService("PAUSE");
            }

            @Override
            public void onSkipToNext() {
                startMusicService("NEXT");
            }

            @Override
            public void onSkipToPrevious() {
                startMusicService("PREV");
            }

            @Override
            public void onSeekTo(long position) {
                if (mediaPlayer == null || !NotificationService.playerPrepared) {
                    return;
                }

                int duration = mediaPlayer.getDuration();
                if (duration <= 0) return;

                mediaPlayer.seekTo((int) Math.min(position, duration));

                updateMediaSessionPlaybackState(
                        isPlaying
                                ? PlaybackStateCompat.STATE_PLAYING
                                : PlaybackStateCompat.STATE_PAUSED
                );
            }

            @Override
            public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent event = intent.getParcelableExtra(
                        Intent.EXTRA_KEY_EVENT
                );

                if (event != null &&
                        event.getAction() == KeyEvent.ACTION_DOWN) {

                    switch (event.getKeyCode()) {
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            startMusicService("PLAYPAUSE");
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            startMusicService("NEXT");
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            startMusicService("PREV");
                            return true;
                    }
                }

                return super.onMediaButtonEvent(intent);
            }

            @Override
            public void onCustomAction(String action, Bundle extras) {
                if ("TOGGLE_PLAY_MODE".equals(action)) {
                    startMusicService("TOGGLE_PLAY_MODE");
                }
            }
        });
        mediaSession.setActive(true);
    }

    void release() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
    }
}
