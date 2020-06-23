package com.omicronapplications.andplugtest;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.omicronapplications.andpluglib.IAndPlugCallback;
import com.omicronapplications.andpluglib.IPlayer;
import com.omicronapplications.andpluglib.PlayerController;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.omicronapplications.andpluglib.IPlayer.PlayerState.LOADED;

public class PlayerActivity extends Activity implements IAndPlugCallback, View.OnClickListener {
    private static final String TAG = "PlayerActivity";
    private PlayerController mController;
    private IPlayer mPlayer;
    private String[] mFileNames = {
            "en_lille_test.d00",
            "fresh.d00",
            "gone.d00",
            "super_nova.d00",
            "the_alibi.d00"
    };
    private int mIndex = -1;
    private int mSong = 1;

    static {
        System.loadLibrary("andplug");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        Button clickButton = findViewById(R.id.create_button);
        clickButton.setOnClickListener(this);
        clickButton = findViewById(R.id.destroy_button);
        clickButton.setOnClickListener(this);
        clickButton = findViewById(R.id.initialize_button);
        clickButton.setOnClickListener(this);
        clickButton = findViewById(R.id.uninitialize_button);
        clickButton.setOnClickListener(this);
        clickButton = findViewById(R.id.previous_button);
        clickButton.setOnClickListener(this);
        clickButton = findViewById(R.id.info_button);
        clickButton.setOnClickListener(this);
        clickButton = findViewById(R.id.next_button);
        clickButton.setOnClickListener(this);
        clickButton = findViewById(R.id.play_button);
        clickButton.setOnClickListener(this);
        clickButton = findViewById(R.id.pause_button);
        clickButton.setOnClickListener(this);
        clickButton = findViewById(R.id.stop_button);
        clickButton.setOnClickListener(this);
        clickButton = findViewById(R.id.previous_song_button);
        clickButton.setOnClickListener(this);
        clickButton = findViewById(R.id.next_song_button);
        clickButton.setOnClickListener(this);
        mController = new PlayerController(this, getApplicationContext());
        mController.create();
    }

    @Override
    public void onDestroy() {
        if (mController != null) {
            mController.destroy();
            mController = null;
        }
        super.onDestroy();
    }

    private File fileFromAssets(String fileName) {
        File f = new File(getCacheDir() + File.separator + fileName);
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
            Log.w(TAG, e.getMessage());
        }
        return f;
    }

    private String getDebugDir(boolean external) {
        File dir = null;
        if (external) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                File[] externals = getExternalFilesDirs(null);
                if (externals != null) {
                    if (externals.length > 0 && externals[0] != null) {
                        dir = externals[0];
                        if (externals.length > 1 && externals[1] != null) {
                            dir = externals[1];
                        }
                    }
                }
            } else {
                dir = getExternalFilesDir(null);
            }
        } else {
            dir = getFilesDir();
        }
        String path = null;
        if (dir != null) {
            path = dir.getAbsolutePath();
        }
        Log.i(TAG, "getDebugDir: " + path);
        return path;
    }

    @Override
    public void onServiceConnected() {
        mPlayer = mController.getService();
        String fileName = "the alibi.d00";
        File f = fileFromAssets(fileName);
        if (mPlayer != null) {
            mPlayer.debugPath(getDebugDir(true));
            mPlayer.initialize(44100, true, false, true, false, 1, 131072);
            mPlayer.setRepeat(true);
        }
    }

    @Override
    public void onServiceDisconnected() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.unload();
            mPlayer.uninitialize();
            mPlayer = null;
        }
    }

    @Override
    public void onNewState(IPlayer.PlayerRequest request, IPlayer.PlayerState state, String info) {
        String title = mPlayer.getTitle();
        String author = mPlayer.getAuthor();
        String desc = mPlayer.getDesc();
        if (state == LOADED) {
            Log.i(TAG, "title: " + title + ", author: " + author + ", desc: " + desc);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.create_button:
                mController.create();
                break;
            case R.id.destroy_button:
                mController.destroy();
                break;
            case R.id.initialize_button:
                if (mPlayer != null) {
                    mPlayer.initialize(44100, true, false, true, false, 32, 1024);
                    mPlayer.setRepeat(true);
                }
                break;
            case R.id.uninitialize_button:
                if (mPlayer != null) {
                    mPlayer.uninitialize();
                    mPlayer.setRepeat(false);
                }
                break;
            case R.id.previous_button:
                mIndex = mIndex - 1;
                if (mIndex < 0) {
                    mIndex = mFileNames.length - 1;
                }
                String fileName = mFileNames[mIndex];
                File f = fileFromAssets(fileName);
                mPlayer = mController.getService();
                if (mPlayer != null) {
                    mPlayer.load(f.getAbsolutePath());
                    mPlayer.play();
                }
                break;
            case R.id.info_button:
                mPlayer = mController.getService();
                if (mPlayer != null) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("song:").append(mPlayer.getSong());
                    builder.append(System.getProperty("line.separator"));
                    builder.append("title:").append(mPlayer.getTitle());
                    builder.append(System.getProperty("line.separator"));
                    builder.append("author:").append(mPlayer.getAuthor());
                    builder.append(System.getProperty("line.separator"));
                    builder.append("desc:").append(mPlayer.getDesc());
                    Toast.makeText(getApplicationContext(), builder.toString(), Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.next_button:
                mIndex = mIndex + 1;
                if (mIndex >= mFileNames.length) {
                    mIndex = 0;
                }
                fileName = mFileNames[mIndex];
                f = fileFromAssets(fileName);
                mPlayer = mController.getService();
                if (mPlayer != null) {
                    mPlayer.load(f.getAbsolutePath());
                    mSong = 1;
                    mPlayer.play();
                }
                break;
            case R.id.play_button:
                mPlayer = mController.getService();
                if (mPlayer != null) {
                    mPlayer.play();
                }
                break;
            case R.id.pause_button:
                mPlayer = mController.getService();
                if (mPlayer != null) {
                    mPlayer.pause();
                }
                break;
            case R.id.stop_button:
                mPlayer = mController.getService();
                if (mPlayer != null) {
                    mPlayer.stop();
                }
                break;
            case R.id.previous_song_button:
                mPlayer = mController.getService();
                if (mPlayer != null) {
                    mSong--;
                    if (mSong < 1) {
                        mSong = 1;
                    }
                    mPlayer.rewind(mSong);
                    mPlayer.stop();
                    mPlayer.play();
                }
                break;
            case R.id.next_song_button:
                mPlayer = mController.getService();
                mSong++;
                if (mPlayer != null) {
                    mPlayer.rewind(mSong);
                    mPlayer.stop();
                    mPlayer.play();
                }
                break;
            default:
                break;
        }
    }
}
