package com.omicronapplications.andpluglib;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import java.io.File;

import static android.media.AudioAttributes.CONTENT_TYPE_MUSIC;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioFormat.ENCODING_PCM_8BIT;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.AudioTrack.MODE_STREAM;

public class PlayerService extends Service implements
        IPlayer, AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = "PlayerService";
    private static final int PLAYER_CREATE = 1;
    private static final int PLAYER_DESTROY = 2;
    private static final int PLAYER_LOAD = 3;
    private static final int PLAYER_UNLOAD = 4;
    private static final int PLAYER_PLAY = 5;
    private static final int PLAYER_PAUSE = 6;
    private static final int PLAYER_STOP = 7;
    private static final int WATCHDOG_KICK = 8;
    private static final String BUNDLE_RATE = "rate";
    private static final String BUNDLE_BIT16 = "bit16";
    private static final String BUNDLE_USESTEREO = "usestereo";
    private static final String BUNDLE_LEFT = "left";
    private static final String BUNDLE_RIGHT = "right";
    private static final String BUNDLE_BUFFERCOUNT = "bufferCount";
    private static final String BUNDLE_SAMPLES = "samples";
    private static final String BUNDLE_SONG = "song";
    private final IBinder mBinder = new PlayerBinder();
    private HandlerThread mThread;
    private Handler mHandler;
    private Handler mWatchdogHandler;
    private volatile boolean mIsRunning;
    private volatile boolean mRepeat;
    private volatile PlayerState mState;
    private PlayerState mLostState;
    private IAndPlugCallback mCallback;
    private PlayerHandler mRunnable;
    private WatchdogTimer mWatchdogRunnable;
    private String mSong;
    private String mTitle;
    private String mAuthor;
    private String mDesc;

    public final class PlayerBinder extends Binder {
        PlayerService getService() {
            return PlayerService.this;
        }
    }

    private final class WatchdogTimer implements Handler.Callback, Runnable {
        private final Object mWatchdogLock = new Object();
        private volatile boolean mStateChanged;

        @Override
        public boolean handleMessage(Message msg) {
            synchronized (mWatchdogLock) {
                if (msg.what == WATCHDOG_KICK) {
                    mStateChanged = true;
                }
            }
            return true;
        }

        @Override
        public void run() {
            synchronized (mWatchdogLock) {
                if (!mStateChanged) {
                    Log.e(TAG, "Player thread stuck");
                    setState(PlayerRequest.WATCHDOG, PlayerState.FATAL);
                }
                mStateChanged = false;
            }
        }
    }

    private final class PlayerHandler implements Handler.Callback, Runnable {
        private final Object mRequestLock = new Object();
        private AndPlayerJNI mAdPlayer;
        private AudioTrack mTrack;
        private long mOplPtr;
        private short[] mBuf;
        private byte[] mBuf8;
        private boolean mBit16;
        private int mChannels;
        private int mSamples;

        private void waitForPlayer() {
            mIsRunning = false;
            synchronized (mRequestLock) {
                while (mState == PlayerState.PLAYING) {
                    try {
                        mRequestLock.wait();
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
        }

        @Override
        public boolean handleMessage(Message msg) {
            waitForPlayer();
            switch (msg.what) {
                case PLAYER_CREATE:
                    Bundle data = msg.getData();
                    int rate = data.getInt(BUNDLE_RATE);
                    boolean bit16 = data.getBoolean(BUNDLE_BIT16);
                    boolean usestereo = data.getBoolean(BUNDLE_USESTEREO);
                    boolean left = data.getBoolean(BUNDLE_LEFT);
                    boolean right = data.getBoolean(BUNDLE_RIGHT);
                    int bufferCount = data.getInt(BUNDLE_BUFFERCOUNT);
                    int samples = data.getInt(BUNDLE_SAMPLES);
                    if (rate <= 0 || bufferCount <= 0 || samples <= 0) {
                        Log.e(TAG, "create: illegal player configuration, rate:" + rate + ", bufferCount:" + bufferCount + ", samples:" + samples);
                        setState(PlayerRequest.CREATE, PlayerState.FATAL);
                        break;
                    }
                    // Create native player instance
                    if (mAdPlayer == null) {
                        mAdPlayer = new AndPlayerJNI();
                    }
                    if (mOplPtr == 0) {
                        mOplPtr = mAdPlayer.create(rate, bit16, usestereo, left, right);
                    }
                    // Create audio player
                    if (mTrack == null) {
                        int channelConfig = usestereo ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
                        int audioFormat = bit16 ? ENCODING_PCM_16BIT : ENCODING_PCM_8BIT;
                        int bufferSizeInBytes = bufferCount * samples;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mTrack = new AudioTrack.Builder().
                                    setAudioAttributes(new AudioAttributes.Builder().
                                            setContentType(CONTENT_TYPE_MUSIC).
                                            setLegacyStreamType(STREAM_MUSIC).
                                            setUsage(USAGE_MEDIA).
                                            build()).
                                    setAudioFormat(new AudioFormat.
                                            Builder().
                                            setEncoding(audioFormat).
                                            setChannelMask(channelConfig).
                                            setSampleRate(rate).
                                            build()).
                                    setBufferSizeInBytes(bufferSizeInBytes).
                                    setTransferMode(MODE_STREAM).
                                    build();
                        } else {
                            mTrack = new AudioTrack(STREAM_MUSIC, rate, channelConfig, audioFormat, bufferSizeInBytes, MODE_STREAM);
                        }
                    }
                    mBit16 = bit16;
                    mChannels = usestereo ? 2 : 1;
                    mSamples = samples;
                    setState(PlayerRequest.CREATE, PlayerState.CREATED);
                    break;

                case PLAYER_DESTROY:
                    // Destroy native player instance
                    if (mAdPlayer != null && mOplPtr != 0) {
                        mAdPlayer.destroy(mOplPtr);
                    }
                    mAdPlayer = null;
                    mOplPtr = 0;
                    // Destroy audio player
                    if (mTrack != null) {
                        mTrack.release();
                    }
                    mTrack = null;
                    setState(PlayerRequest.DESTROY, PlayerState.DEFAULT);
                    break;

                case PLAYER_LOAD:
                    data = msg.getData();
                    String song = data.getString(BUNDLE_SONG);
                    if (mAdPlayer == null || mOplPtr == 0 || song == null || song.isEmpty()) {
                        Log.w(TAG, "load: not initialized: mAdPlayer:" + mAdPlayer + ", mOplPtr:" + mOplPtr + ", BUNDLE_SONG:" + song);
                        setState(PlayerRequest.LOAD, PlayerState.ERROR);
                        break;
                    }
                    mAdPlayer.load(mOplPtr, song);
                    if (mAdPlayer.isLoaded(mOplPtr)) {
                        File file = new File(song);
                        mSong = file.getName();
                        mTitle = mAdPlayer.plugGettitle(mOplPtr);
                        mAuthor = mAdPlayer.plugGetauthor(mOplPtr);
                        mDesc = mAdPlayer.plugGetdesc(mOplPtr);
                        setState(PlayerRequest.LOAD, PlayerState.LOADED);
                        if (mWatchdogHandler != null) {
                            Message watchdog = mWatchdogHandler.obtainMessage(WATCHDOG_KICK);
                            mWatchdogHandler.sendMessage(watchdog);
                        }
                    } else {
                        Log.w(TAG, "load: not loaded");
                        mSong = "";
                        mTitle = "";
                        mAuthor = "";
                        mDesc = "";
                        setState(PlayerRequest.LOAD, PlayerState.ERROR);
                    }
                    break;

                case PLAYER_UNLOAD:
                    if (mAdPlayer != null && mOplPtr != 0 && mAdPlayer.isLoaded(mOplPtr)) {
                        mAdPlayer.unload(mOplPtr);
                    }
                    mSong = "";
                    mTitle = "";
                    mAuthor = "";
                    mDesc = "";
                    setState(PlayerRequest.UNLOAD, PlayerState.CREATED);
                    break;

                case PLAYER_PLAY:
                    if (!canRequestFocus()) {
                        Log.w(TAG, "play: can not request focus");
                        setState(PlayerRequest.PLAY, PlayerState.ERROR);
                        break;
                    }
                    if (mTrack == null || mHandler == null || mAdPlayer == null || mOplPtr == 0) {
                        Log.w(TAG, "play: not initialized: mTrack:" + mTrack + ", mHandler:" + mHandler + ", mAdPlayer:" + mAdPlayer + ", mOplPtr:" + mOplPtr);
                        setState(PlayerRequest.PLAY, PlayerState.ERROR);
                        break;
                    }
                    if (!mAdPlayer.isLoaded(mOplPtr)) {
                        Log.w(TAG, "play: not loaded");
                        setState(PlayerRequest.PLAY, PlayerState.ERROR);
                        break;
                    }
                    // Stereo playback requires double amount of samples
                    if (mBuf == null) {
                        mBuf = new short[2 * mSamples];
                    }
                    if (mBuf8 == null) {
                        mBuf8 = new byte[2 * mSamples];
                    }
                    // Start playing audio data
                    try {
                        mTrack.play();
                    } catch (IllegalStateException e) {
                        Log.w(TAG, "play: " + e.getMessage());
                        setState(PlayerRequest.PLAY, PlayerState.ERROR);
                        break;
                    }
                    // Start background thread
                    mIsRunning = true;
                    setState(PlayerRequest.PLAY, PlayerState.PAUSED);
                    mHandler.post(mRunnable);
                    break;

                case PLAYER_PAUSE:
                    if (mTrack == null) {
                        Log.w(TAG, "pause: not initialized");
                        setState(PlayerRequest.PAUSE, PlayerState.ERROR);
                        break;
                    }
                    // Pause playing audio data
                    try {
                        mTrack.pause();
                    } catch (IllegalStateException e) {
                        Log.w(TAG, "pause: " + e.getMessage());
                        setState(PlayerRequest.PAUSE, PlayerState.ERROR);
                        break;
                    }
                    if (!mAdPlayer.isLoaded(mOplPtr)) {
                        Log.w(TAG, "pause: not loaded");
                        setState(PlayerRequest.PAUSE, PlayerState.ERROR);
                        break;
                    }
                    setState(PlayerRequest.PAUSE, PlayerState.PAUSED);
                    break;

                case PLAYER_STOP:
                    if (mTrack == null || mAdPlayer == null || mOplPtr == 0) {
                        Log.w(TAG, "stop: not initialized: mTrack:" + mTrack + ", mAdPlayer:" + mAdPlayer + ", mOplPtr:" + mOplPtr);
                        setState(PlayerRequest.STOP, PlayerState.ERROR);
                        break;
                    }
                    // Stop playing audio data
                    try {
                        mTrack.pause();
                    } catch (IllegalStateException e) {
                        // Uninitialized AudioTrack
                        Log.w(TAG, "stop: " + e.getMessage());
                        setState(PlayerRequest.STOP, PlayerState.ERROR);
                        break;
                    }
                    mTrack.flush();
                    if (!mAdPlayer.isLoaded(mOplPtr)) {
                        Log.w(TAG, "stop: not loaded");
                        setState(PlayerRequest.STOP, PlayerState.ERROR);
                        break;
                    }
                    mAdPlayer.plugRewind(mOplPtr, 0);
                    mBuf = null;
                    mBuf8 = null;
                    setState(PlayerRequest.STOP, PlayerState.STOPPED);
                    break;

                default:
                    Log.w(TAG, "handleMessage: ignored illegal PlayerState: " + msg.what);
                    break;
            }

            return true;
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            boolean hasSamples = true;
            synchronized (mRequestLock) {
                setState(PlayerRequest.RUN, PlayerState.PLAYING);
                if (mTrack == null || mAdPlayer == null || mOplPtr == 0 || !mAdPlayer.isLoaded(mOplPtr)) {
                    Log.w(TAG, "run: not initialized: mTrack:" + mTrack + ", mAdPlayer:" + mAdPlayer + ", mOplPtr:" + mOplPtr);
                    setState(PlayerRequest.RUN, PlayerState.ERROR);
                    hasSamples = false;
                }
                while (mIsRunning && hasSamples) {
                    int writtenSample = -1;
                    if (mBit16 && (mBuf != null)) {
                        int newSamples = mAdPlayer.oplUpdate16(mOplPtr, mBuf, mSamples, mRepeat);
                        hasSamples = (newSamples > 0);
                        writtenSample = mTrack.write(mBuf, 0, newSamples * mChannels);
                    } else if (mBuf8 != null) {
                        int newSamples = mAdPlayer.oplUpdate8(mOplPtr, mBuf8, mSamples, mRepeat);
                        hasSamples = (newSamples > 0);
                        writtenSample = mTrack.write(mBuf8, 0, newSamples * mChannels);
                    }
                    if (writtenSample <= AudioManager.ERROR) {
                        Log.e(TAG, "run: writtenSample: " + writtenSample);
                        setState(PlayerRequest.RUN, PlayerState.ERROR);
                        break;
                    }
                }
                mRequestLock.notifyAll();
                if (mState != PlayerState.ERROR) {
                    setState(PlayerRequest.RUN, mIsRunning ? PlayerState.ENDED : PlayerState.STOPPED);
                }
            }
        }
    }

    private void setState(PlayerRequest request, PlayerState state) {
        if (mState != state) {
            mState = state;
            if (mCallback != null) {
                mCallback.onNewState(request, mState);
            }
        }
    }

    private boolean canRequestFocus() {
        boolean focus = false;
        AudioManager manager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        if (manager != null) {
            int status;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                status = manager.requestAudioFocus(new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).
                        setAcceptsDelayedFocusGain(false).
                        setAudioAttributes(new AudioAttributes.Builder().
                                setContentType(CONTENT_TYPE_MUSIC).
                                setLegacyStreamType(STREAM_MUSIC).
                                setUsage(USAGE_MEDIA).
                                build()).
                        setFocusGain(AudioManager.AUDIOFOCUS_GAIN).
                        setOnAudioFocusChangeListener(this).
                        setWillPauseWhenDucked(true).
                        build());
            } else {
                status = manager.requestAudioFocus(this, STREAM_MUSIC, AUDIOFOCUS_GAIN);
            }
            if (status == AUDIOFOCUS_REQUEST_GRANTED) {
                focus = true;
            } else if (status == AUDIOFOCUS_REQUEST_FAILED) {
                focus = false;
            }
        }
        return focus;
    }

    private void sendMessageToPlayer(int what, Bundle data) {
        if (mHandler != null) {
            Message msg = mHandler.obtainMessage(what);
            if (data != null) {
                msg.setData(data);
            }
            mIsRunning = false;
            mHandler.removeMessages(what);
            mHandler.sendMessage(msg);
        } else {
            Log.w(TAG, "sendMessageToPlayer: no handler: " + what);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AUDIOFOCUS_GAIN:
            case AUDIOFOCUS_GAIN_TRANSIENT:
            case AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
            case AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                if (mLostState == PlayerState.PLAYING) {
                    mLostState = getState();
                    play();
                }
                break;
            case AUDIOFOCUS_LOSS:
            case AUDIOFOCUS_LOSS_TRANSIENT:
            case AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    mLostState = getState();
                    pause();
                break;
            default:
                break;
        }
    }

    public void setCallback(IAndPlugCallback callback) {
        mCallback = callback;
    }

    @Override
    public void onCreate() {
        mThread = new HandlerThread("PlayerHandler", Process.THREAD_PRIORITY_AUDIO);
        try {
            mThread.start();
        } catch (IllegalThreadStateException e) {
            Log.e(TAG, "onCreate: " + e.getMessage());
        }
        Looper looper = mThread.getLooper();
        mRunnable = new PlayerHandler();
        mHandler = new Handler(looper, mRunnable);
        mWatchdogRunnable = new WatchdogTimer();
        mWatchdogHandler = new Handler(getMainLooper(), mWatchdogRunnable);
        setState(PlayerRequest.SERVICE, PlayerState.DEFAULT);
    }

    @Override
    public void onDestroy() {
        mIsRunning = false;
        // Stop background thread
        if (mThread != null) {
            try {
                mThread.join(100);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
            mThread.quit();
            mThread = null;
        }
        mHandler = null;
        mRunnable = null;
        mWatchdogHandler = null;
        mWatchdogRunnable = null;
        setState(PlayerRequest.SERVICE, PlayerState.DEFAULT);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    @Override
    public void create(int rate, boolean bit16, boolean usestereo, boolean left, boolean right, int bufferCount, int samples) {
        Bundle data = new Bundle();
        data.putInt(BUNDLE_RATE, rate);
        data.putBoolean(BUNDLE_BIT16, bit16);
        data.putBoolean(BUNDLE_USESTEREO, usestereo);
        data.putBoolean(BUNDLE_LEFT, left);
        data.putBoolean(BUNDLE_RIGHT, right);
        data.putInt(BUNDLE_BUFFERCOUNT, bufferCount);
        data.putInt(BUNDLE_SAMPLES, samples);
        sendMessageToPlayer(PLAYER_CREATE, data);
    }

    @Override
    public void destroy() {
        sendMessageToPlayer(PLAYER_DESTROY, null);
    }

    @Override
    public void load(String song) {
        Bundle data = new Bundle();
        data.putString(BUNDLE_SONG, song);
        sendMessageToPlayer(PLAYER_LOAD, data);
        if (mWatchdogHandler != null) {
            mWatchdogHandler.removeCallbacks(mWatchdogRunnable);
            mWatchdogHandler.postDelayed(mWatchdogRunnable, 2500);
        }
    }

    @Override
    public void unload() {
        sendMessageToPlayer(PLAYER_UNLOAD, null);
    }

    @Override
    public void play() {
        sendMessageToPlayer(PLAYER_PLAY, null);
    }

    @Override
    public void pause() {
        sendMessageToPlayer(PLAYER_PAUSE, null);
    }

    @Override
    public void stop() {
        sendMessageToPlayer(PLAYER_STOP, null);
    }

    @Override
    public void setRepeat(boolean repeat) {
        mRepeat = repeat;
    }

    @Override
    public boolean getRepeat() {
        return mRepeat;
    }

    @Override
    public String getSong() {
        return mSong;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getAuthor() {
        return mAuthor;
    }

    @Override
    public String getDesc() {
        return mDesc;
    }

    @Override
    public PlayerState getState() {
        return mState;
    }
}
