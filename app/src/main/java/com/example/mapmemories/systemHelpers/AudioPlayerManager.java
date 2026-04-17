package com.example.mapmemories.systemHelpers;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/* |-----------------------------------------------------------------------|
 * |                       МЕНЕДЖЕР АУДИОПЛЕЕРА                            |
 * |-----------------------------------------------------------------------| */
public class AudioPlayerManager {

    private static AudioPlayerManager instance;
    private MediaPlayer mediaPlayer;
    private String currentPlayingId;
    private boolean isPlaying = false;

    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private PlayerCallback callback;

    public interface PlayerCallback {
        void onStateChanged(String messageId, boolean isPlaying);
        void onProgressUpdate(String messageId, int currentPos, int maxDuration);
        void onError(String error);
    }

    private AudioPlayerManager() {}

    public static AudioPlayerManager getInstance() {
        if (instance == null) instance = new AudioPlayerManager();
        return instance;
    }

    public void setCallback(PlayerCallback callback) {
        this.callback = callback;
    }

    public void play(String messageId, String url) {
        // Если нажали Play на уже текущем треке
        if (messageId.equals(currentPlayingId) && mediaPlayer != null) {
            mediaPlayer.start();
            isPlaying = true;
            if (callback != null) callback.onStateChanged(currentPlayingId, true);
            startProgressTracker();
            return;
        }

        // Выключаем старое аудио, если играло
        stop();

        currentPlayingId = messageId;
        mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                mediaPlayer.start();
                isPlaying = true;
                if (callback != null) callback.onStateChanged(currentPlayingId, true);
                startProgressTracker();
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                mediaPlayer.seekTo(0);
                progressHandler.removeCallbacks(progressRunnable);
                if (callback != null) {
                    callback.onProgressUpdate(currentPlayingId, 0, mediaPlayer.getDuration());
                    callback.onStateChanged(currentPlayingId, false);
                }
            });
        } catch (Exception e) {
            if (callback != null) callback.onError("Ошибка загрузки аудио");
            stop();
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            progressHandler.removeCallbacks(progressRunnable);
            if (callback != null) callback.onStateChanged(currentPlayingId, false);
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            try { if (mediaPlayer.isPlaying()) mediaPlayer.stop(); } catch (Exception e) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
        isPlaying = false;
        String oldId = currentPlayingId;
        currentPlayingId = null;
        progressHandler.removeCallbacks(progressRunnable);
        if (callback != null && oldId != null) callback.onStateChanged(oldId, false);
    }

    public void seekTo(int position) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(position);
            if (callback != null) callback.onProgressUpdate(currentPlayingId, position, mediaPlayer.getDuration());
        }
    }

    public String getCurrentPlayingId() { return currentPlayingId; }
    public boolean isPlaying() { return isPlaying; }

    private void startProgressTracker() {
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPlaying) {
                if (callback != null) callback.onProgressUpdate(currentPlayingId, mediaPlayer.getCurrentPosition(), mediaPlayer.getDuration());
                progressHandler.postDelayed(this, 50);
            }
        }
    };
}