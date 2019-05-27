package com.omicronapplications.andpluglib;

public interface IPlayer {
    void create(int rate, boolean bit16, boolean usestereo, boolean left, boolean right, int bufferCount, int samples);
    void destroy();
    void load(String song);
    void unload();
    void play();
    void pause();
    void stop();
    void setRepeat(boolean repeat);
    boolean getRepeat();
    String getSong();
    String getTitle();
    String getAuthor();
    String getDesc();
    PlayerState getState();

    enum PlayerRequest {
        SERVICE,
        CREATE,
        PLAY,
        LOAD,
        RUN,
        PAUSE,
        STOP,
        UNLOAD,
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
