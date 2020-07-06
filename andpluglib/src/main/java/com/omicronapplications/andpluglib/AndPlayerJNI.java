package com.omicronapplications.andpluglib;

class AndPlayerJNI {
    public native void initialize(int rate, boolean bit16, boolean usestereo, boolean left, boolean right);
    public native void uninitialize();
    public native boolean load(String str);
    public native void unload();
    public native boolean isLoaded();

    public native void oplWrite(int reg, int val);
    public native void oplSetchip(int n);
    public native int oplGetchip();
    public native void oplInit();
    public native int oplGettype();
    public native int oplUpdate16(short[] buf, int size, boolean repeat);
    public native int oplUpdate8(byte[] buf, int size, boolean repeat);
    public native void oplDebugPath(String str);

    public native String plugGetversion();
    public native void plugSeek(long ms);
    public native void plugRewind(int subsong);
    public native long plugSonglength(int subsong);
    public native String plugGettype();
    public native String plugGettitle();
    public native String plugGetauthor();
    public native String plugGetdesc();
    public native int plugGetsubsongs();
    public native int plugGetsubsong();
}
