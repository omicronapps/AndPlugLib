package com.omicronapplications.andpluglib;

class AndPlayerJNI {
    public native long create(int rate, boolean bit16, boolean usestereo, boolean left, boolean right);
    public native void destroy(long ptr);
    public native long load(long ptr, String str);
    public native void unload(long ptr);
    public native boolean isLoaded(long ptr);

    public native void oplWrite(long ptr, int reg, int val);
    public native void oplSetchip(long ptr, int n);
    public native int oplGetchip(long ptr);
    public native void oplInit(long ptr);
    public native int oplGettype(long ptr);
    public native int oplUpdate16(long ptr, short[] buf, int samples, boolean repeat);
    public native int oplUpdate8(long ptr, byte[] buf, int samples, boolean repeat);

    public native String plugGetversion();
    public native void plugSeek(long ptr, long ms);
    public native void plugRewind(long ptr, int subsong);
    public native long plugSonglength(long ptr, int subsong);
    public native String plugGettype(long ptr);
    public native String plugGettitle(long ptr);
    public native String plugGetauthor(long ptr);
    public native String plugGetdesc(long ptr);
}
