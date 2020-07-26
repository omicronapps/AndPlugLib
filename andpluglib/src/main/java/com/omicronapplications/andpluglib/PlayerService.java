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
    private static final int PLAYER_SEEK = 9;
    private static final int PLAYER_REWIND = 10;
    private static final int DEBUG_SETPATH = 11;
    private static final String BUNDLE_EMU = "emu";
    private static final String BUNDLE_RATE = "rate";
    private static final String BUNDLE_USESTEREO = "usestereo";
    private static final String BUNDLE_LEFT = "left";
    private static final String BUNDLE_RIGHT = "right";
    private static final String BUNDLE_BUFFERS = "buffers";
    private static final String BUNDLE_SONG = "song";
    private static final String BUNDLE_SEEK = "seek";
    private static final String BUNDLE_SUBSONG = "subsong";
    private static final String BUNDLE_DEBUGPATH = "path";
    private final IBinder mBinder = new PlayerBinder();
    private HandlerThread mMessageThread;
    private MessageCallback mMessageCallback;
    private Handler mMessageHandler;
    private HandlerThread mPlayerThread;
    private PlayerRunner mPlayerRunner;
    private Handler mPlayerHandler;
    private Handler mReplyHandler;
    private volatile boolean mRepeat;
    private volatile PlayerState mState;
    private PlayerState mLostState;
    private String mSong;
    private long mLength;
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
    private short[] mBuf;
    private int mChannels;
    private int mBuffers;

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

        setState(PlayerRequest.SERVICE, PlayerState.DEFAULT, null);
    }

    @Override
    public void onDestroy() {
        mMessageHandler.removeCallbacksAndMessages(null);
        if (mMessageThread != null) {
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

        private String adPlugError(int error) {
            String errorString = "";
            switch (error) {
                case -1:
                    errorString = "illegal arguments";
                    break;
                case -2:
                    errorString = "illegal refresh rate";
                    break;
                default:
                    break;
            }
            return errorString;
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            boolean hasSamples = true;
            PlayerState state = PlayerState.DEFAULT;
            String info = null;
            if (mTrack == null || mBuf == null|| !mAdPlayer.isLoaded()) {
                Log.w(TAG, "run: not initialized: mTrack:" + mTrack + ", mBuf:" + mBuf + ", isLoaded:" + mAdPlayer.isLoaded());
                hasSamples = false;
                state = PlayerState.ERROR;
                info = "Player not initialized: " + mTrack + "/" + mBuf + mAdPlayer.isLoaded();
            }
            while (mIsRunning && hasSamples) {
//                boolean dataWritten = false;
                int samples = 0;
                int writtenSamples = 0;
                try {
                    playerLock();
                    if (mBuf != null) {
                        samples = mAdPlayer.oplUpdate(mBuf, mBuffers, mRepeat);
                        hasSamples = (samples > 0);
                        if (hasSamples) {
                            writtenSamples = mTrack.write(mBuf, 0, mChannels * samples);
                        }
                    }
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
//                    if (!dataWritten) {
//                        Log.w(TAG, "run: empty song");
//                        dataWritten = hasSamples;
//                        state = PlayerState.ERROR;
//                        info = "Nothing played back";
//                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "run: exiting player thread: " + e.getMessage());
                    state = PlayerState.ERROR;
                    info = "Player interrupted: " + e.getMessage();
                    break;
                } finally {
                    playerUnlock();
                }
                if (samples < 0) {
                    Log.e(TAG, "run: samples: " + samples);
                    state = PlayerState.ERROR;
                    info = "AdPlug error: " + adPlugError(samples) + " (" + samples + ")";
                    break;
                } else if (writtenSamples <= AudioManager.ERROR) {
                    Log.e(TAG, "run: writtenSamples: " + writtenSamples);
                    state = PlayerState.ERROR;
                    info = "AudioTrack error: " + writtenSamples;
                    break;
                }
            }
            if (state == PlayerState.DEFAULT) {
                state = mIsRunning ? PlayerState.ENDED : PlayerState.STOPPED;
            }
            setState(PlayerRequest.RUN, state, info);
        }
    }

    private final class MessageCallback implements Handler.Callback {
        private boolean startPlayer() {
            if (!canRequestFocus()) {
                Log.w(TAG, "startPlayer: can not request focus");
                return false;
            }
            if (!mAdPlayer.isLoaded()) {
                Log.w(TAG, "startPlayer: song not loaded");
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
            mBuf = null;
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
                    int emu = data.getInt(BUNDLE_EMU);
                    int rate = data.getInt(BUNDLE_RATE);
                    boolean usestereo = data.getBoolean(BUNDLE_USESTEREO);
                    if (!usestereo && emu == Opl.OPL_CNEMU.toInt()) {
                        usestereo = true;
                    }
                    boolean left = data.getBoolean(BUNDLE_LEFT);
                    boolean right = data.getBoolean(BUNDLE_RIGHT);
                    int buffers = data.getInt(BUNDLE_BUFFERS);
                    if (emu < 0 || emu > 4 || rate <= 0 || buffers <= 0) {
                        Log.e(TAG, "initialize: illegal player configuration, emu: " + emu + ", rate:" + rate + ", buffers:" + buffers);
                        setState(PlayerRequest.INITIALIZE, PlayerState.ERROR, "Failed to initialize: " + emu + "/" + rate + "/" + buffers);
                        break;
                    }
                    try {
                        messageLock();
                        shutdownPlayer();
                        // Create native player instance
                        mAdPlayer.initialize(emu, rate, usestereo, left, right);
                        // Create audio player
                        int channelConfig = usestereo ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
                        int audioFormat = ENCODING_PCM_16BIT;
                        int bufferSizeInBytes = AudioTrack.getMinBufferSize(rate, channelConfig, audioFormat);
                        bufferSizeInBytes = Math.max(bufferSizeInBytes, buffers);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mTrack = new AudioTrack.Builder().
                                    setAudioAttributes(new AudioAttributes.
                                            Builder().
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
                        mChannels = usestereo ? 2 : 1;
                        mBuffers = buffers;
                        // Stereo playback requires double amount of samples
                        mBuf = new short[mChannels * mBuffers];
                    } finally {
                        messageUnlock();
                    }
                    setState(PlayerRequest.INITIALIZE, PlayerState.CREATED, null);
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
                    setState(request, PlayerState.DEFAULT, null);
                    break;

                case PLAYER_LOAD:
                    boolean isLoaded = false;
                    data = msg.getData();
                    String song = data.getString(BUNDLE_SONG);
                    if (song == null || song.isEmpty()) {
                        Log.w(TAG, "load: not initialized: " + song);
                        setState(PlayerRequest.LOAD, PlayerState.ERROR, "No song selected");
                        break;
                    }
                    try {
                        messageLock();
                        stopPlayer();
                        isLoaded = mAdPlayer.load(song);
                        if (isLoaded) {
                            File file = new File(song);
                            mSong = file.getName();
                            mLength = mAdPlayer.plugSonglength(-1);
                            mTitle = mAdPlayer.plugGettitle();
                            mAuthor = mAdPlayer.plugGetauthor();
                            mDesc = mAdPlayer.plugGetdesc();
                            mNumSubsongs = mAdPlayer.plugGetsubsongs();
                            mCurSubsong = mAdPlayer.plugGetsubsong();
                        } else {
                            Log.w(TAG, "load: failed to load song: " + song);
                            mSong = "";
                            mLength = 0;
                            mTitle = "";
                            mAuthor = "";
                            mDesc = "";
                            mNumSubsongs = -1;
                            mCurSubsong = -1;
                        }
                    } finally {
                        messageUnlock();
                    }
                    PlayerState state = isLoaded ? PlayerState.LOADED : PlayerState.ERROR;
                    File file = new File(song);
                    setState(PlayerRequest.LOAD, state, isLoaded ? null : "Failed to load song: " + file.getName());
                    break;

                case PLAYER_UNLOAD:
                    try {
                        messageLock();
                        if (mAdPlayer.isLoaded()) {
                            mAdPlayer.unload();
                        }
                        mSong = "";
                        mLength = 0;
                        mTitle = "";
                        mAuthor = "";
                        mDesc = "";
                        mNumSubsongs = -1;
                        mCurSubsong = -1;
                    } finally {
                        messageUnlock();
                    }
                    setState(PlayerRequest.UNLOAD, PlayerState.CREATED, null);
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
                    setState(PlayerRequest.PLAY, state, playing ? null : "Failed to play song");
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
                    setState(PlayerRequest.PAUSE, state, paused ? null : "Failed to pause song");
                    break;

                case PLAYER_STOP:
                    try {
                        messageLock();
                        stopPlayer();
                    } finally {
                        messageUnlock();
                    }
                    setState(PlayerRequest.STOP, PlayerState.STOPPED, null);
                    break;

                case PLAYER_SEEK:
                    data = msg.getData();
                    long ms = data.getLong(BUNDLE_SEEK);
                    try {
                        messageLock();
                        mAdPlayer.plugSeek(ms);
                    } finally {
                        messageUnlock();
                    }
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

    private void setState(PlayerRequest request, PlayerState state, String info) {
        if (request == PlayerRequest.DESTROY) {
            postDestroy();
        }
        mState = state;
        if (mReplyHandler != null) {
            Message msg = mReplyHandler.obtainMessage(PLAYER_STATE);
            Bundle data = new Bundle();
            data.putInt(IPlayer.BUNDLE_REQUEST, request.toInt());
            data.putInt(IPlayer.BUNDLE_STATE, state.toInt());
            if (info != null) {
                data.putString(IPlayer.BUNDLE_INFO, info);
            }
            msg.setData(data);
            mReplyHandler.sendMessage(msg);
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

        if (mReplyHandler != null) {
            mReplyHandler.removeCallbacksAndMessages(null);
        }
        mReplyHandler = null;
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

    public void setHandler(Handler handler) {
        mReplyHandler = handler;
    }

    @Override
    public void initialize(Opl emu, int rate, boolean usestereo, boolean left, boolean right, int buffers) {
        Bundle data = new Bundle();
        data.putInt(BUNDLE_EMU, emu.toInt());
        data.putInt(BUNDLE_RATE, rate);
        data.putBoolean(BUNDLE_USESTEREO, usestereo);
        data.putBoolean(BUNDLE_LEFT, left);
        data.putBoolean(BUNDLE_RIGHT, right);
        data.putInt(BUNDLE_BUFFERS, buffers);
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
    public void seek(long ms) {
        Bundle data = new Bundle();
        data.putLong(BUNDLE_SEEK, ms);
        sendMessageToHandler(PLAYER_SEEK, data);
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
    public long getSonglength(int subsong) {
        return mLength;
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
