package com.omicronapplications.andpluglib;

public interface IAndPlugCallback {
    void onServiceConnected();
    void onServiceDisconnected();
    void onNewState(IPlayer.PlayerRequest request, IPlayer.PlayerState state);
}
