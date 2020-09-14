package com.omicronapplications.andpluglib;

public interface IAndPlugCallback {
    void onServiceConnected();
    void onServiceDisconnected();
    void onNewState(IPlayer.PlayerRequest request, IPlayer.PlayerState state, String info);
    void onSongInfo(String song, String type, String title, String author, String desc, long length, long songlength, int subsongs, boolean valid, boolean playlist);
}
