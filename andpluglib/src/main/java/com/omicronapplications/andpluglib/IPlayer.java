package com.omicronapplications.andpluglib;

public interface IPlayer {
    int PLAYER_STATE = 1;
    String BUNDLE_REQUEST = "request";
    String BUNDLE_STATE = "state";
    String BUNDLE_INFO = "info";

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
        SERVICE(0),
        INITIALIZE(1),
        PLAY(1),
        LOAD(2),
        RUN(3),
        PAUSE(4),
        STOP(5),
        UNLOAD(6),
        UNINITIALIZE(7),
        DESTROY(8),
        WATCHDOG(9);
        PlayerRequest(int val) { this.val = val; }
        int toInt() { return val; }
        private int val;
    }

    enum PlayerState {
        DEFAULT(0),
        CREATED(1),
        LOADED(2),
        PLAYING(3),
        PAUSED(4),
        STOPPED(5),
        ENDED(6),
        ERROR(7),
        FATAL(8);
        PlayerState(int val) { this.val = val; }
        int toInt() { return val; }
        private int val;
    }
}
