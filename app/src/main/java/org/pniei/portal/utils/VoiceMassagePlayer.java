package org.pniei.portal.utils;

import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;

public class VoiceMassagePlayer {
    public interface VoiceMassagePlayerListener {
        void onCompletionPlay();

        void onProgressPlay(int progress);

        void onDurationPlay(int duration);
    }

    private final static String TAG = "VoiceMassagePlayer";
    private static MediaPlayer player;
    private static VoiceMassagePlayerListener mListener = null;
    private static PlayingThread thread = null;

    public static void startPlaying(String filePath, VoiceMassagePlayerListener listener) {

        try {
            if (thread != null) thread.stopPlaying();
            if (mListener != null) mListener.onCompletionPlay();

            mListener = listener;

            player = new MediaPlayer();
            player.setDataSource(filePath);
            player.setOnCompletionListener(mediaPlayer -> {
                thread.stopPlaying();
                thread = null;
                mListener.onCompletionPlay();
                mListener = null;
            });
            player.prepare();

            mListener.onProgressPlay(player.getCurrentPosition());
            mListener.onDurationPlay(player.getDuration());

            thread = new PlayingThread(player);
            thread.start();
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed");
        }
    }

    public static void stopPlaying() {
        if (thread != null) {
            thread.stopPlaying();
            thread = null;
        }

        if (mListener != null) {
            mListener.onCompletionPlay();
            mListener = null;
        }
    }

    private static class PlayingThread extends Thread {
        private boolean isPlaying = false;
        private MediaPlayer mPlayer;

        public PlayingThread(MediaPlayer player) {
            mPlayer = player;
        }

        @Override
        public void run() {
            isPlaying = true;
            mPlayer.start();
            while (isPlaying) {
                synchronized (player) {
                    if (mPlayer != null && mPlayer.isPlaying()) {
                        mListener.onProgressPlay(mPlayer.getCurrentPosition());
                    }
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    isPlaying = false;
                    e.printStackTrace();
                }
            }
            if (mPlayer.isPlaying())
                mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }

        public void stopPlaying() {
            isPlaying = false;
        }
    }

}
