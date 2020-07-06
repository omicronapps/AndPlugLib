package com.omicronapplications.andpluglib;

public interface IPlayer {
    int PLAYER_STATE = 1;
    String BUNDLE_REQUEST = "request";
    String BUNDLE_STATE = "state";
    String BUNDLE_INFO = "info";

    void initialize(int rate, boolean bit16, boolean usestereo, boolean left, boolean right, int buffers, int overhead);
    void uninitialize();
    void load(String song);
    void unload();
    void play();
    void pause();
    void stop();
    void seek(long ms);
    void rewind(int subsong);
    void setRepeat(boolean repeat);
    boolean getRepeat();
    String getSong();
    long getSonglength(int subsong);
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
        PLAY(2),
        LOAD(3),
        RUN(4),
        PAUSE(5),
        STOP(6),
        UNLOAD(7),
        UNINITIALIZE(8),
        DESTROY(9);
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
