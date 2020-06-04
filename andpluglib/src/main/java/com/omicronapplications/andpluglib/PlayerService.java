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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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
    private static final int PLAYER_INITIALIZE = 1;
    private static final int PLAYER_UNINITIALIZE = 2;
    private static final int PLAYER_DESTROY = 3;
    private static final int PLAYER_LOAD = 4;
    private static final int PLAYER_UNLOAD = 5;
    private static final int PLAYER_PLAY = 6;
    private static final int PLAYER_PAUSE = 7;
    private static final int PLAYER_STOP = 8;
    private static final int PLAYER_REWIND = 9;
    private static final int WATCHDOG_KICK = 10;
    private static final int DEBUG_SETPATH = 11;
    private static final String BUNDLE_RATE = "rate";
    private static final String BUNDLE_BIT16 = "bit16";
    private static final String BUNDLE_USESTEREO = "usestereo";
    private static final String BUNDLE_LEFT = "left";
    private static final String BUNDLE_RIGHT = "right";
    private static final String BUNDLE_BUFFERCOUNT = "bufferCount";
    private static final String BUNDLE_SAMPLES = "samples";
    private static final String BUNDLE_SONG = "song";
    private static final String BUNDLE_SUBSONG = "subsong";
    private static final String BUNDLE_DEBUGPATH = "path";
    private final IBinder mBinder = new PlayerBinder();
    private HandlerThread mMessageThread;
    private MessageCallback mMessageCallback;
    private Handler mMessageHandler;
    private HandlerThread mPlayerThread;
    private PlayerRunner mPlayerRunner;
    private Handler mPlayerHandler;
    private Handler mWatchdogHandler;
    private volatile boolean mRepeat;
    private volatile PlayerState mState;
    private PlayerState mLostState;
    private IAndPlugCallback mCallback;
    private WatchdogTimer mWatchdogRunnable;
    private String mSong;
    private String mTitle;
    private String mAuthor;
    private String mDesc;
    private int mNumSubsongs;
    private int mCurSubsong;
    // PlayerRunner/MessageCallback variables
    private final ReentrantLock mLock = new ReentrantLock();
    private final Condition mPlayerAccess = mLock.newCondition();
    private final AndPlayerJNI mAdPlayer = new AndPlayerJNI();
    private AudioTrack mTrack;
    private volatile boolean mIsRunning;
    private volatile boolean mMessageRequest;
    private short[] mBuf16;
    private byte[] mBuf8;
    private boolean mBit16;
    private int mChannels;
    private int mSamples;

    public final class PlayerBinder extends Binder {
        PlayerService getService() {
            return PlayerService.this;
        }
    }

    @Override
    public void onCreate() {
        mMessageThread = new HandlerThread("MessageHandler");
        try {
            mMessageThread.start();
        } catch (IllegalThreadStateException e) {
            Log.e(TAG, "onCreate: IllegalThreadStateException: " + e.getMessage());
        }
        mMessageCallback = new MessageCallback();
        Looper looper = mMessageThread.getLooper();
        mMessageHandler = new Handler(looper, mMessageCallback);

        mPlayerThread = new HandlerThread("AndPlug", Process.THREAD_PRIORITY_AUDIO);
        try {
            mPlayerThread.start();
        } catch (IllegalThreadStateException e) {
            Log.e(TAG, "onCreate: IllegalThreadStateException: " + e.getMessage());
        }
        mPlayerRunner = new PlayerRunner();
        looper = mPlayerThread.getLooper();
        mPlayerHandler = new Handler(looper);

        mWatchdogRunnable = new WatchdogTimer();
        mWatchdogHandler = new Handler(getMainLooper(), mWatchdogRunnable);
        setState(PlayerRequest.SERVICE, PlayerState.DEFAULT);
    }

    @Override
    public void onDestroy() {
        if (mMessageThread != null) {
            mMessageHandler.removeCallbacksAndMessages(null);
            sendMessageToHandler(PLAYER_DESTROY, null);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
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

    private final class PlayerRunner implements Runnable {
        private void playerLock() {
            if (!mLock.isHeldByCurrentThread()) {
                mLock.lock();
            }
            try {
                while (mMessageRequest) {
                    mPlayerAccess.await();
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "playerLock: InterruptedException: " + e.getMessage());
            }
        }

        private void playerUnlock() {
            mLock.unlock();
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            boolean hasSamples = true;
            if (mTrack == null || (mBuf16 == null && mBuf8 == null) || !mAdPlayer.isLoaded()) {
                Log.w(TAG, "run: not initialized: mTrack:" + mTrack + ", mBuf:" + mBuf16 + ", mBuf8:" + mBuf8 + ", isLoaded:" + mAdPlayer.isLoaded());
                setState(PlayerRequest.RUN, PlayerState.ERROR);
                hasSamples = false;
            }
            while (mIsRunning && hasSamples) {
                int writtenSample = -1;
                try {
                    playerLock();
                    if (mBit16 && (mBuf16 != null)) {
                        int newSamples = mAdPlayer.oplUpdate16(mBuf16, mSamples, mRepeat);
                        hasSamples = (newSamples > 0);
                        writtenSample = mTrack.write(mBuf16, 0, newSamples * mChannels);
                    } else if (mBuf8 != null) {
                        int newSamples = mAdPlayer.oplUpdate8(mBuf8, mSamples, mRepeat);
                        hasSamples = (newSamples > 0);
                        writtenSample = mTrack.write(mBuf8, 0, newSamples * mChannels);
                    }
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "run: exiting player thread");
                    break;
                } finally {
                    playerUnlock();
                }
                if (writtenSample <= AudioManager.ERROR) {
                    Log.e(TAG, "run: writtenSample: " + writtenSample);
                    setState(PlayerRequest.RUN, PlayerState.ERROR);
                    break;
                }
            }
            if (mState != PlayerState.ERROR) {
                setState(PlayerRequest.RUN, mIsRunning ? PlayerState.ENDED : PlayerState.STOPPED);
            }
        }
    }

    private final class MessageCallback implements Handler.Callback {
        private boolean startPlayer() {
            if (!canRequestFocus()) {
                Log.w(TAG, "startPlayer: can not request focus");
                return false;
            }
            if (mTrack == null) {
                Log.w(TAG, "startPlayer: not initialized");
                return false;
            }
            // Start playing audio data
            if (mTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    mTrack.play();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "startPlayer: IllegalStateException: " + e.getMessage());
                    return false;
                }
            }
            if (mPlayerThread != null) {
                mIsRunning = true;
                mPlayerHandler.post(mPlayerRunner);
            }
            return true;
        }

        private boolean pausePlayer() {
            if (mTrack == null) {
                Log.w(TAG, "pausePlayer: not initialized");
                return false;
            }
            // Pause playing audio data
            try {
                mTrack.pause();
            } catch (IllegalStateException e) {
                Log.w(TAG, "pausePlayer: IllegalStateException: " + e.getMessage());
                return false;
            }
            return true;
        }

        private void stopPlayer() {
            mIsRunning = false;
            if (mTrack != null) {
                try {
                    mTrack.stop();
                } catch (IllegalStateException e) {
                    Log.w(TAG, "stopPlayer: IllegalStateException:" + e.getMessage());
                }
                mTrack.flush();
            }
            if (mAdPlayer.isLoaded()) {
                mAdPlayer.plugRewind(0);
            }
        }

        private void shutdownPlayer() {
            stopPlayer();
            // Destroy native player instance
            mAdPlayer.unload();
            mAdPlayer.uninitialize();
            // Destroy audio player
            if (mTrack != null) {
                mTrack.release();
                mTrack = null;
            }
            mBuf16 = null;
            mBuf8 = null;
        }

        private void messageLock() {
            mMessageRequest = true;
            if (!mLock.isHeldByCurrentThread()) {
                mLock.lock();
            }
        }

        private void messageUnlock() {
            mMessageRequest = false;
            mPlayerAccess.signalAll();
            mLock.unlock();
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case PLAYER_INITIALIZE:
                    Bundle data = msg.getData();
                    int rate = data.getInt(BUNDLE_RATE);
                    boolean bit16 = data.getBoolean(BUNDLE_BIT16);
                    boolean usestereo = data.getBoolean(BUNDLE_USESTEREO);
                    boolean left = data.getBoolean(BUNDLE_LEFT);
                    boolean right = data.getBoolean(BUNDLE_RIGHT);
                    int bufferCount = data.getInt(BUNDLE_BUFFERCOUNT);
                    int samples = data.getInt(BUNDLE_SAMPLES);
                    if (rate <= 0 || bufferCount <= 0 || samples <= 0) {
                        Log.e(TAG, "initialize: illegal player configuration, rate:" + rate + ", bufferCount:" + bufferCount + ", samples:" + samples);
                        setState(PlayerRequest.INITIALIZE, PlayerState.FATAL);
                        break;
                    }
                    try {
                        messageLock();
                        shutdownPlayer();//stopPlayer();//shutdownPlayer();//
                        // Create native player instance
                        mAdPlayer.initialize(rate, bit16, usestereo, left, right);
                        // Create audio player
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
                        mBit16 = bit16;
                        mChannels = usestereo ? 2 : 1;
                        mSamples = samples;
                        // Stereo playback requires double amount of samples
                        if (mBit16) {
                            mBuf16 = new short[2 * mSamples];
                        } else {
                            mBuf8 = new byte[2 * mSamples];
                        }
                    } finally {
                        messageUnlock();
                    }
                    setState(PlayerRequest.INITIALIZE, PlayerState.CREATED);
                    break;

                case PLAYER_UNINITIALIZE:
                case PLAYER_DESTROY:
                    try {
                        messageLock();
                        shutdownPlayer();
                    } finally {
                        messageUnlock();
                    }
                    PlayerRequest request = (msg.what == PLAYER_UNINITIALIZE) ? PlayerRequest.UNINITIALIZE : PlayerRequest.DESTROY;
                    setState(request, PlayerState.DEFAULT);
                    break;

                case PLAYER_LOAD:
                    boolean loaded = false;
                    data = msg.getData();
                    String song = data.getString(BUNDLE_SONG);
                    if (song == null || song.isEmpty()) {
                        Log.w(TAG, "load: not initialized: " + song);
                        setState(PlayerRequest.LOAD, PlayerState.ERROR);
                        break;
                    }
                    try {
                        messageLock();
                        stopPlayer();
                        mAdPlayer.load(song);
                        if (mAdPlayer.isLoaded()) {
                            File file = new File(song);
                            mSong = file.getName();
                            mTitle = mAdPlayer.plugGettitle();
                            mAuthor = mAdPlayer.plugGetauthor();
                            mDesc = mAdPlayer.plugGetdesc();
                            mNumSubsongs = mAdPlayer.plugGetsubsongs();
                            mCurSubsong = mAdPlayer.plugGetsubsong();
                            loaded = true;
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
                            mNumSubsongs = -1;
                            mCurSubsong = -1;
                        }
                    } finally {
                        messageUnlock();
                    }
                    PlayerState state = loaded ? PlayerState.LOADED : PlayerState.ERROR;
                    setState(PlayerRequest.LOAD, state);
                    break;

                case PLAYER_UNLOAD:
                    try {
                        messageLock();
                        if (mAdPlayer.isLoaded()) {
                            mAdPlayer.unload();
                        }
                        mSong = "";
                        mTitle = "";
                        mAuthor = "";
                        mDesc = "";
                        mNumSubsongs = -1;
                        mCurSubsong = -1;
                    } finally {
                        messageUnlock();
                    }
                    setState(PlayerRequest.UNLOAD, PlayerState.CREATED);
                    break;

                case PLAYER_PLAY:
                    boolean playing = false;
                    try {
                        messageLock();
                        playing = startPlayer();
                    } finally {
                        messageUnlock();
                    }
                    state = playing ? PlayerState.PLAYING : PlayerState.ERROR;
                    setState(PlayerRequest.PLAY, state);
                    break;

                case PLAYER_PAUSE:
                    boolean paused = false;
                    try {
                        messageLock();
                        paused = pausePlayer();
                    } finally {
                        if (!paused) {
                            messageUnlock();
                        }
                    }
                    state = paused ? PlayerState.PAUSED : PlayerState.ERROR;
                    setState(PlayerRequest.PAUSE, state);
                    break;

                case PLAYER_STOP:
                    try {
                        messageLock();
                        stopPlayer();
                    } finally {
                        messageUnlock();
                    }
                    setState(PlayerRequest.STOP, PlayerState.STOPPED);
                    break;

                case PLAYER_REWIND:
                    data = msg.getData();
                    int subsong = data.getInt(BUNDLE_SUBSONG);
                    try {
                        messageLock();
                        mAdPlayer.plugRewind(subsong);
                    } finally {
                        messageUnlock();
                    }
                    break;

                case DEBUG_SETPATH:
                    data = msg.getData();
                    String path = data.getString(BUNDLE_DEBUGPATH);
                    try {
                        messageLock();
                        mAdPlayer.oplDebugPath(path);
                    } finally {
                        messageUnlock();
                    }
                    break;

                default:
                    Log.w(TAG, "handleMessage: ignored illegal PlayerState: " + msg.what);
                    break;
            }

            return true;
        }
    }

    private void setState(PlayerRequest request, PlayerState state) {
        if (request == PlayerRequest.DESTROY) {
            postDestroy();
        }
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

    private void sendMessageToHandler(int what, Bundle data) {
        if (mMessageHandler != null) {
            Message msg = mMessageHandler.obtainMessage(what);
            if (data != null) {
                msg.setData(data);
            }
            mMessageHandler.removeMessages(what);
            mMessageHandler.sendMessage(msg);
        } else {
            Log.e(TAG, "sendMessageToPlayer: no handler: " + what);
        }
    }

    private void postDestroy() {
        mMessageHandler.removeCallbacksAndMessages(null);
        if (mMessageThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mMessageThread.quitSafely();
            } else {
                mMessageThread.quit();
            }
        }
        mMessageThread = null;
        mMessageCallback = null;
        mMessageHandler = null;

        mPlayerHandler.removeCallbacksAndMessages(null);
        if (mPlayerThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mPlayerThread.quitSafely();
            } else {
                mPlayerThread.quit();
            }
        }
        mPlayerThread = null;
        mPlayerRunner = null;
        mPlayerHandler = null;

        mWatchdogHandler.removeCallbacksAndMessages(null);
        mWatchdogRunnable = null;
        mWatchdogHandler = null;
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
    public void initialize(int rate, boolean bit16, boolean usestereo, boolean left, boolean right, int bufferCount, int samples) {
        Bundle data = new Bundle();
        data.putInt(BUNDLE_RATE, rate);
        data.putBoolean(BUNDLE_BIT16, bit16);
        data.putBoolean(BUNDLE_USESTEREO, usestereo);
        data.putBoolean(BUNDLE_LEFT, left);
        data.putBoolean(BUNDLE_RIGHT, right);
        data.putInt(BUNDLE_BUFFERCOUNT, bufferCount);
        data.putInt(BUNDLE_SAMPLES, samples);
        sendMessageToHandler(PLAYER_INITIALIZE, data);
    }

    @Override
    public void uninitialize() {
        sendMessageToHandler(PLAYER_UNINITIALIZE, null);
    }

    @Override
    public void load(String song) {
        Bundle data = new Bundle();
        data.putString(BUNDLE_SONG, song);
        sendMessageToHandler(PLAYER_LOAD, data);
        if (mWatchdogHandler != null) {
            mWatchdogHandler.removeCallbacks(mWatchdogRunnable);
            mWatchdogHandler.postDelayed(mWatchdogRunnable, 2500);
        }
    }

    @Override
    public void unload() {
        sendMessageToHandler(PLAYER_UNLOAD, null);
    }

    @Override
    public void play() {
        sendMessageToHandler(PLAYER_PLAY, null);
    }

    @Override
    public void pause() {
        sendMessageToHandler(PLAYER_PAUSE, null);
    }

    @Override
    public void stop() {
        sendMessageToHandler(PLAYER_STOP, null);
    }

    @Override
    public void rewind(int subsong) {
        Bundle data = new Bundle();
        data.putInt(BUNDLE_SUBSONG, subsong);
        sendMessageToHandler(PLAYER_REWIND, data);
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
    public int getSubsongs() {
        return mNumSubsongs;
    }

    @Override
    public int getSubsong() {
        return mCurSubsong;
    }

    @Override
    public void debugPath(String path) {
        Bundle data = new Bundle();
        data.putString(BUNDLE_DEBUGPATH, path);
        sendMessageToHandler(DEBUG_SETPATH, data);
    }

    @Override
    public PlayerState getState() {
        return mState;
    }
}
