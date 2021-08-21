package com.omicronapplications.andpluglib;

class AndPlayerJNI {
    private IPlayerJNI mPlayer;

    public AndPlayerJNI(IPlayerJNI player) {
        mPlayer = player;
    }
    public native void oplInitialize(int emu, int rate, boolean usestereo);
    public native void oplUninitialize();
    public native void oplSetRepeat(boolean repeat);
    public native void oplWrite(int reg, int val);
    public native void oplSetchip(int n);
    public native int oplGetchip();
    public native void oplInit();
    public native int oplGettype();
    public native int oplUpdate(short[] buf, byte[] debugBuf, int size);
    public native void oplDebugPath(String str);
    public native void oplOpenFile();
    public native void oplCloseFile();
    public void setState(int request, int state, String info) {
        if (mPlayer != null) {
            mPlayer.setState(IPlayer.PlayerRequest.values()[request], IPlayer.PlayerState.values()[state], info);
        }
    }
    public void setTime(long ms) {
        if (mPlayer != null) {
            mPlayer.sendTime(ms);
        }
    }

    public native String plugGetversion();
    public native boolean plugLoad(String str);
    public native void plugUnload();
    public native boolean plugIsLoaded();
    public native void plugSeek(long ms);
    public native void plugRewind(int subsong);
    public native long plugSonglength(int subsong);
    public native String plugGettype();
    public native String plugGettitle();
    public native String plugGetauthor();
    public native String plugGetdesc();
    public native int plugGetsubsongs();
    public native int plugGetsubsong();

    public native boolean oboeInitialize(int rate, boolean usestereo);
    public native boolean oboeUninitialize();
    public native boolean oboeRestart();
    public native boolean oboePlay();
    public native boolean oboePause();
    public native boolean oboeStop();
    public native int oboeGetState();
    public native long oboeGetTime();

    public native boolean infoLoad(String str);
    public native long infoSonglength(int subsong);
    public native String infoGettype();
    public native String infoGettitle();
    public native String infoGetauthor();
    public native String infoGetdesc();
    public native int infoGetsubsongs();
}
