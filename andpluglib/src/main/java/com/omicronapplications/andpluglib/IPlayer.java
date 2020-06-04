package com.omicronapplications.andpluglib;

public interface IPlayer {
    void initialize(int rate, boolean bit16, boolean usestereo, boolean left, boolean right, int bufferCount, int samples);
    void uninitialize();
    void load(String song);
    void unload();
    void play();
    void pause();
    void stop();
    void rewind(int subsong);
    void setRepeat(boolean repeat);
    boolean getRepeat();
    String getSong();
    String getTitle();
    String getAuthor();
    String getDesc();
    int getSubsongs();
    int getSubsong();
    void debugPath(String path);
    PlayerState getState();

    enum PlayerRequest {
        SERVICE,
        INITIALIZE,
        PLAY,
        LOAD,
        RUN,
        PAUSE,
        STOP,
        UNLOAD,
        UNINITIALIZE,
        DESTROY,
        WATCHDOG
    }

    enum PlayerState {
        DEFAULT,
        CREATED,
        LOADED,
        PLAYING,
        PAUSED,
        STOPPED,
        ENDED,
        ERROR,
        FATAL
    }
}
