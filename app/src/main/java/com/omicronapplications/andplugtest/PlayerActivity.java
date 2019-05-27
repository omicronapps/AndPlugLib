package com.omicronapplications.andplugtest;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.omicronapplications.andpluglib.IAndPlugCallback;
import com.omicronapplications.andpluglib.IPlayer;
import com.omicronapplications.andpluglib.PlayerController;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.omicronapplications.andpluglib.IPlayer.PlayerState.LOADED;

public class PlayerActivity extends Activity implements IAndPlugCallback {
    private static final String TAG = "PlayerActivity";
    private PlayerController mController;
    private IPlayer mPlayer;

    static {
        System.loadLibrary("andplug");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mController = new PlayerController(this, getApplicationContext());
        mController.create();
    }

    @Override
    public void onDestroy() {
        mController.destroy();
        super.onDestroy();
    }

    private File fileFromAssets(String fileName) {
        File f = new File(getCacheDir()+"/"+fileName);
        try {
            InputStream is = getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            int i = is.read(buffer);
            if (i != size) {
                Log.e(TAG, "failed to read file: " + fileName);
            }
            is.close();
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(buffer);
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e);
        }
        return f;
    }

    @Override
    public void onServiceConnected() {
        mPlayer = mController.getService();
        String fileName = "the alibi.d00";
        File f = fileFromAssets(fileName);
        if (mPlayer != null) {
            mPlayer.create(44100, true, false, true, false, 32, 1024);
            mPlayer.load(f.getAbsolutePath());
            mPlayer.setRepeat(true);
            mPlayer.play();
        }
    }

    @Override
    public void onServiceDisconnected() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.unload();
            mPlayer.destroy();
            mPlayer = null;
        }
    }

    @Override
    public void onNewState(IPlayer.PlayerRequest request, IPlayer.PlayerState state) {
        String title = mPlayer.getTitle();
        String author = mPlayer.getAuthor();
        String desc = mPlayer.getDesc();
        if (state == LOADED) {
            Log.d(TAG, "title: " + title + ", author: " + author + ", desc: " + desc);
        }
    }
}
