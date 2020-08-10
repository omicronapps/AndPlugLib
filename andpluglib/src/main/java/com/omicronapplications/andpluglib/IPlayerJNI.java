package com.omicronapplications.andpluglib;

public interface IPlayerJNI {
    void setState(IPlayer.PlayerRequest request, IPlayer.PlayerState state, String info);
}
