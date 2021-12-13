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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
        IPlayer, IPlayerJNI, AudioManager.OnAudioFocusChangeListener {
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
    private static final int PLAYER_REPEAT = 11;
    private static final int INFO_SONG = 12;
    private static final int DEBUG_SETPATH = 13;
    private static final String BUNDLE_EMU = "emu";
    private static final String BUNDLE_RATE = "rate";
    private static final String BUNDLE_OBOE = "oboe";
    private static final String BUNDLE_USESTEREO = "usestereo";
    private static final String BUNDLE_BUFFERS = "buffers";
    private static final String BUNDLE_SONG = "song";
    private static final String BUNDLE_SEEK = "seek";
    private static final String BUNDLE_SUBSONG = "subsong";
    private static final String BUNDLE_REPEAT = "repeat";
    private static final String BUNDLE_TIME = "time";
    private static final String BUNDLE_DEBUGOPL = "debugopl";
    private static final String BUNDLE_DEBUGAUDIO = "debugaudio";
    private static final String BUNDLE_DEBUGPATH = "debugpath";
    private static final int OBOE_POLLING = 100;
    private final IBinder mBinder = new PlayerBinder();
    private HandlerThread mMessageThread;
    private MessageCallback mMessageCallback;
    private Handler mMessageHandler;
    private HandlerThread mPlayerThread;
    private PlayerRunner mPlayerRunner;
    private OboeRunner mOboeRunner;
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
    private final ReentrantLock mLock = new ReentrantLock(); // Guard: mAdPlayer, mTrack, mBuf, mChannels, mBuffers, mRate
    private final Condition mPlayerAccess = mLock.newCondition(); // PlayerRunner:wait, MessageCallback:signal
    private final ReentrantLock mInfoLock = new ReentrantLock(); // Guard: mAdPlayer
    private final AndPlayerJNI mAdPlayer = new AndPlayerJNI(this);
    private AudioTrack mTrack;
    private volatile boolean mIsRunning;
    private volatile boolean mMessageRequest;
    private boolean mOboe;
    private short[] mBuf;
    private int mChannels;
    private int mBuffers;
    private int mRate;
    private long mTotalSamples;
    // Debug use
    private boolean mDebugAudio;
    private boolean mDebugOpl;
    private String mDebugPath;
    private byte[] mDebugBuf;
    private int mFileIndex;
    private FileOutputStream mDebugStream;

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
        mOboeRunner = new OboeRunner();
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

    // Workaround for failing JNI-Java callbacks
    private final class OboeRunner implements Runnable {
        @Override
        public void run() {
            PlayerState state = PlayerState.values()[mAdPlayer.oboeGetState()];
            long ms = mAdPlayer.oboeGetTime();
            sendTime(ms);
            if (state != mState && (state == PlayerState.ENDED || state == PlayerState.STOPPED)) {
                setState(PlayerRequest.RUN, state, null);
            } else if (mState == PlayerState.LOADED || mState == PlayerState.PLAYING || mState == PlayerState.PAUSED) {
                mPlayerHandler.postDelayed(this, OBOE_POLLING);
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

        private void writeDebugFile(byte[] audioData, int offsetInBytes, int sizeInBytes) {
            if (mDebugStream != null && audioData != null && audioData.length > 0 && offsetInBytes >= 0 && sizeInBytes > 0) {
                try {
                    mDebugStream.write(audioData, offsetInBytes, sizeInBytes);
                } catch (IOException e) {
                    Log.e(TAG, "writeDebugFile: IOException: " + e.getMessage());
                }
            }
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            boolean hasSamples = true;
            PlayerState state = PlayerState.DEFAULT;
            String info = null;
            if (!mAdPlayer.plugIsLoaded() || mOboe) {
                Log.w(TAG, "run: AdPlug not loaded: isLoaded:" + mAdPlayer.plugIsLoaded() + ", mOboe:" + mOboe);
                hasSamples = false;
                state = PlayerState.ERROR;
                info = "AdPlug not loaded: " + mAdPlayer.plugIsLoaded() + "/" + mOboe;
            }
            while (mIsRunning && hasSamples) {
                int samples = 0;
                int writtenSamples = 0;
                try {
                    playerLock();
                    if (mBuf != null && mTrack != null) {
                        samples = mAdPlayer.oplUpdate(mBuf, mDebugBuf, mBuffers);
                        hasSamples = (samples > 0);
                        if (hasSamples) {
                            writtenSamples = mTrack.write(mBuf, 0, mChannels * samples);
                            writeDebugFile(mDebugBuf, 0, 2 * writtenSamples);
                        }
                    } else {
                        Log.w(TAG, "run: not initialized: mTrack:" + mTrack + ", mBuf:" + mBuf);
                        state = PlayerState.ERROR;
                        info = null;//"Player not initialized: " + mTrack + "/" + mBuf;
                        break;
                    }
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "run: InterruptedException: " + e.getMessage());
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
                mTotalSamples += writtenSamples;
                long ms = 1000 * mTotalSamples / mRate / mChannels;
                sendTime(ms);
            }
            if (state == PlayerState.DEFAULT) {
                state = mIsRunning ? PlayerState.ENDED : PlayerState.STOPPED;
            }
            setState(PlayerRequest.RUN, state, info);
        }
    }

    private final class MessageCallback implements Handler.Callback {
        private boolean startPlayer() {
            boolean started = false;
            if (!canRequestFocus()) {
                Log.w(TAG, "startPlayer: can not request focus");
                return false;
            }
            if (!mAdPlayer.plugIsLoaded()) {
                Log.w(TAG, "startPlayer: song not loaded");
                return false;
            }
            if (mOboe) {
                started = mAdPlayer.oboePlay();
                if (mPlayerThread != null) {
                    mPlayerHandler.postDelayed(mOboeRunner, OBOE_POLLING);
                    mIsRunning = true;
                }
            } else {
                try {
                    if (mTrack == null) {
                        Log.w(TAG, "startPlayer: not initialized");
                    } else if (mTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                        // Start playing audio data
                        mTrack.play();
                        started = true;
                    }
                } catch (IllegalStateException e) {
                    Log.e(TAG, "startPlayer: IllegalStateException: " + e.getMessage());
                }
                if (mPlayerThread != null) {
                    mIsRunning = true;
                    mPlayerHandler.post(mPlayerRunner);
                }
            }
            return started;
        }

        private boolean pausePlayer() {
            boolean paused = false;
            // Pause playing audio data
            if (mOboe) {
                paused = mAdPlayer.oboePause();
            } else {
                try {
                    if (mTrack == null) {
                        Log.w(TAG, "pausePlayer: not initialized");
                    } else {
                        mTrack.pause();
                        paused = true;
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "pausePlayer: IllegalStateException: " + e.getMessage());
                }
            }
            return paused;
        }

        private void stopPlayer() {
            mIsRunning = false;
            mTotalSamples = 0;
            sendTime(0);
            if (mOboe) {
                mAdPlayer.oboeStop();
            } else {
                try {
                    if (mTrack != null) {
                        mTrack.stop();
                        mTrack.flush();
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "stopPlayer: IllegalStateException: " + e.getMessage());
                }
            }
            if (mAdPlayer.plugIsLoaded()) {
                mAdPlayer.plugRewind(-1);
            }
        }

        private void shutdownPlayer() {
            stopPlayer();
            // Destroy native player instance
            mAdPlayer.plugUnload();
            mAdPlayer.oplCloseFile();
            mAdPlayer.oplUninitialize();
            // Destroy audio player
            if (mOboe) {
                mAdPlayer.oboeUninitialize();
            } else {
                if (mTrack != null) {
                    mTrack.release();
                    mTrack = null;
                }
                mBuf = null;
            }
            sendTime(0);
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

        private void openDebugFile() {
            if (mDebugAudio) {
                try {
                    mFileIndex++;
                    String path = mDebugPath + File.separator + "AudioTrack_" + String.format("%03d", mFileIndex) + ".raw";
                    mDebugStream = new FileOutputStream(path);
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "openDebugFile: FileNotFoundException: " + e.getMessage());
                }
                mDebugBuf = new byte[2 * mChannels * mBuffers];
            } else {
                mDebugStream = null;
                mDebugBuf = null;
            }
            if (mDebugOpl) {
                mAdPlayer.oplOpenFile();
            }
        }

        private void closeDebugFile() {
            if (mDebugAudio) {
                if (mDebugStream != null) {
                    try {
                        mDebugStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "closeDebugFile: IOException: " + e.getMessage());
                    }
                    mDebugStream = null;
                }
                mDebugBuf = null;
            } else if (mDebugOpl) {
                mAdPlayer.oplCloseFile();
            }
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case PLAYER_INITIALIZE:
                    Bundle data = msg.getData();
                    int emu = data.getInt(BUNDLE_EMU);
                    int rate = data.getInt(BUNDLE_RATE);
                    mOboe = data.getBoolean(BUNDLE_OBOE);
                    boolean usestereo = data.getBoolean(BUNDLE_USESTEREO);
                    if (!usestereo && emu == Opl.OPL_CNEMU.toInt()) {
                        usestereo = true;
                    }
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
                        mAdPlayer.oplInitialize(emu, rate, usestereo);
                        // Create audio player
                        if (mOboe) {
                            if (!mAdPlayer.oboeInitialize(rate, usestereo)) {
                                Log.e(TAG, "initialize: failed to initialize native playback");
                                setState(PlayerRequest.INITIALIZE, PlayerState.ERROR, "Failed to initialize native playback");
                                break;
                            }
                        } else {
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
                            mRate = rate;
                            // Stereo playback requires double amount of samples
                            mBuf = new short[mChannels * mBuffers];
                        }
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
                        Log.w(TAG, "load: not a valid file: " + song);
                        setState(PlayerRequest.LOAD, PlayerState.ERROR, "No song selected");
                        break;
                    }
                    try {
                        messageLock();
                        if (mOboe) {
                            if (!mAdPlayer.oboeRestart()) {
                                Log.w(TAG, "load: failed to restart native playback: " + song);
                                setState(PlayerRequest.LOAD, PlayerState.ERROR, "Failed to restart native playback");
                            }
                        } else {
                            stopPlayer();
                        }
                        isLoaded = mAdPlayer.plugLoad(song);
                        openDebugFile();
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
                        if (mAdPlayer.plugIsLoaded()) {
                            mAdPlayer.plugUnload();
                            closeDebugFile();
                        }
                    } finally {
                        messageUnlock();
                    }
                    mSong = "";
                    mLength = 0;
                    mTitle = "";
                    mAuthor = "";
                    mDesc = "";
                    mNumSubsongs = -1;
                    mCurSubsong = -1;
                    setState(PlayerRequest.UNLOAD, PlayerState.CREATED, null);
                    break;

                case PLAYER_PLAY:
                    boolean playing = false;
                    try {
                        messageLock();
                    } finally {
                        playing = startPlayer();
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
                        messageUnlock();
                    }
                    state = paused ? PlayerState.PAUSED : PlayerState.ERROR;
                    if (paused) {
                        messageLock();
                    }
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
                        mTotalSamples = mRate * mChannels * ms / 1000;
                        sendTime(ms);
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

                case PLAYER_REPEAT:
                    data = msg.getData();
                    mRepeat = data.getBoolean(BUNDLE_REPEAT);
                    mAdPlayer.oplSetRepeat(mRepeat);
                    break;

                case INFO_SONG:
                    isLoaded = false;
                    String type = "";
                    String title = "";
                    String author = "";
                    String desc = "";
                    long songlength = -1;
                    int subsongs = -1;
                    boolean valid = false;
                    data = msg.getData();
                    song = data.getString(BUNDLE_SONG);
                    long length = data.getLong(BUNDLE_LENGTH);
                    boolean playlist = false;
                    try {
                        mInfoLock.lock();
                        if (song == null || song.isEmpty()) {
                            Log.w(TAG, "songInfo: not a valid file: " + song);
                        } else {
                            file = new File(song);
                            playlist = isPlaylist(file);
                            isLoaded = mAdPlayer.infoLoad(song);
                        }
                        if (isLoaded) {
                            type = mAdPlayer.infoGettype();
                            title = mAdPlayer.infoGettitle();
                            author = mAdPlayer.infoGetauthor();
                            desc = mAdPlayer.infoGetdesc();
                            subsongs = mAdPlayer.infoGetsubsongs();
                            if (!type.startsWith("Reality ADlib Tracker (version ")) {
                                songlength = mAdPlayer.infoSonglength(-1);
                            }
                            valid = true;
                        }
                    } finally {
                        mInfoLock.unlock();
                    }
                    sendSongInfo(song, type, title, author, desc, length, songlength, subsongs, valid, playlist);
                    break;

                case DEBUG_SETPATH:
                    data = msg.getData();
                    mDebugAudio = data.getBoolean(BUNDLE_DEBUGAUDIO);
                    mDebugOpl = data.getBoolean(BUNDLE_DEBUGOPL);
                    mDebugPath = data.getString(BUNDLE_DEBUGPATH);
                    mDebugStream = null;
                    try {
                        messageLock();
                        if (mDebugPath != null && !mDebugPath.isEmpty()) {
                            if (mDebugOpl) {
                                mAdPlayer.oplDebugPath(mDebugPath);
                            }
                        } else {
                            mDebugAudio = false;
                            mDebugOpl = false;
                        }
                    } finally {
                        messageUnlock();
                    }
                    mFileIndex = 0;
                    break;

                default:
                    Log.w(TAG, "handleMessage: ignored illegal PlayerState: " + msg.what);
                    break;
            }

            return true;
        }
    }

    @Override
    public void setState(PlayerRequest request, PlayerState state, String info) {
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

    private void sendSongInfo(String song, String type, String title, String author, String desc, long length, long songlength, int subsongs, boolean valid, boolean playlist) {
        if (mReplyHandler != null) {
            Message msg = mReplyHandler.obtainMessage(SONG_INFO);
            Bundle data = new Bundle();
            data.putString(BUNDLE_SONG, song);
            data.putString(BUNDLE_TYPE, type);
            data.putString(BUNDLE_TITLE, title);
            data.putString(BUNDLE_AUTHOR, author);
            data.putString(BUNDLE_DESC, desc);
            data.putLong(BUNDLE_LENGTH, length);
            data.putLong(BUNDLE_SONGLENGTH, songlength);
            data.putInt(BUNDLE_SUBSONGS, subsongs);
            data.putBoolean(BUNDLE_VALID, valid);
            data.putBoolean(BUNDLE_PLAYLIST, playlist);
            msg.setData(data);
            mReplyHandler.sendMessage(msg);
        }
    }

    @Override
    public void sendTime(long ms) {
        if (mReplyHandler != null) {
            Message msg = mReplyHandler.obtainMessage(PLAY_TIME);
            Bundle data = new Bundle();
            data.putLong(BUNDLE_TIME, ms);
            data.putLong(BUNDLE_LENGTH, mLength);
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

    private static boolean isPlaylist(File file) {
        return (file != null) && !file.isDirectory() && file.getName().endsWith(".m3u");
    }

    private void sendMessageToHandler(int what, Bundle data) {
        if (mMessageHandler != null) {
            Message msg = mMessageHandler.obtainMessage(what);
            if (data != null) {
                msg.setData(data);
            }
            if (what != INFO_SONG) {
                mMessageHandler.removeMessages(what);
            }
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
        mOboeRunner = null;
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
    public void initialize(Opl emu, int rate, boolean oboe, boolean usestereo, int buffers) {
        Bundle data = new Bundle();
        data.putInt(BUNDLE_EMU, emu.toInt());
        data.putInt(BUNDLE_RATE, rate);
        data.putBoolean(BUNDLE_OBOE, oboe);
        data.putBoolean(BUNDLE_USESTEREO, usestereo);
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
        Bundle data = new Bundle();
        data.putBoolean(BUNDLE_REPEAT, repeat);
        sendMessageToHandler(PLAYER_REPEAT, data);
    }

    @Override
    public void songInfo(String song, long length) {
        Bundle data = new Bundle();
        data.putString(BUNDLE_SONG, song);
        data.putLong(BUNDLE_LENGTH, length);
        sendMessageToHandler(INFO_SONG, data);
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
    public void debugPath(boolean audioTrack, boolean opl, String path) {
        Bundle data = new Bundle();
        data.putBoolean(BUNDLE_DEBUGAUDIO, audioTrack);
        data.putBoolean(BUNDLE_DEBUGOPL, opl);
        data.putString(BUNDLE_DEBUGPATH, path);
        sendMessageToHandler(DEBUG_SETPATH, data);
    }

    @Override
    public PlayerState getState() {
        return mState;
    }
}
