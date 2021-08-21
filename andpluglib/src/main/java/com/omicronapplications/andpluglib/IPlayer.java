package com.omicronapplications.andpluglib;

public interface IPlayer {
    int PLAYER_STATE = 1;
    int SONG_INFO = 2;
    int PLAY_TIME = 3;
    String BUNDLE_REQUEST = "request";
    String BUNDLE_STATE = "state";
    String BUNDLE_INFO = "info";
    String BUNDLE_SONG = "song";
    String BUNDLE_TYPE = "type";
    String BUNDLE_TITLE = "title";
    String BUNDLE_AUTHOR = "author";
    String BUNDLE_DESC = "desc";
    String BUNDLE_LENGTH = "length";
    String BUNDLE_SONGLENGTH = "songlength";
    String BUNDLE_SUBSONGS = "subsongs";
    String BUNDLE_VALID = "valid";
    String BUNDLE_PLAYLIST = "playlist";
    String BUNDLE_TIME = "time";

    void initialize(Opl emu, int rate, boolean oboe, boolean usestereo, int buffers);
    void uninitialize();
    void load(String song);
    void unload();
    void play();
    void pause();
    void stop();
    void seek(long ms);
    void rewind(int subsong);
    void setRepeat(boolean repeat);
    void songInfo(String song, long length);
    boolean getRepeat();
    String getSong();
    long getSonglength(int subsong);
    String getTitle();
    String getAuthor();
    String getDesc();
    int getSubsongs();
    int getSubsong();
    void debugPath(boolean audioTrack, boolean opl, String path);
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

    enum Opl {
        OPL_CEMU(0), // Default OPL emulator
        OPL_CKEMU(1), // Ken Silverman's OPL2 emulator
        OPL_CNEMU(2), // Nuked OPL3 emulator
        OPL_CTEMU(3), // Tatsuyuki Satoh's OPL2 emulator
        OPL_CWEMU(4); // DOSBox OPL3 emulator
        Opl(int val) { this.val = val; }
        int toInt() { return val; }
        private int val;
    }
}
