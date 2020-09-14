package com.omicronapplications.andpluglib;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class PlayerController {
    private static final String TAG = "PlayerController";
    private final IAndPlugCallback mCallback;
    private final Context mContext;
    private PlayerService.PlayerBinder mBinder;
    private PlayerService mService;
    private PlayerConnection mConnection;
    private ReplyCallback mReplyCallback;
    private Handler mReplyHandler;

    public PlayerController(IAndPlugCallback callback, Context context) {
        mCallback = callback;
        mContext = context;
    }

    public boolean create() {
        if (mContext == null) {
            Log.e(TAG, "create: failed to set up ServiceConnection");
            return false;
        }
        // Bind to PlayerService
        if (mConnection == null) {
            mConnection = new PlayerConnection();
        } else {
            Log.w(TAG, "create: ServiceConnection instance already created");
        }
        Intent intent = new Intent(mContext, PlayerService.class);
        if (!mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "create: failed to bind to service");
        }
        return true;
    }

    public boolean destroy() {
        if ((mContext == null) || (mConnection == null)) {
            Log.w(TAG, "destroy: failed to unbind from service");
            return false;
        }
        // Unbind from PlayerService
        mContext.unbindService(mConnection);
        mConnection = null;
        return true;
    }

    public void restart() {
        destroy();
        create();
    }

    public IPlayer getService() {
        return mService;
    }

    private class ReplyCallback implements Handler.Callback {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == IPlayer.PLAYER_STATE) {
                Bundle data = msg.getData();
                int val = data.getInt(IPlayer.BUNDLE_REQUEST, 0);
                IPlayer.PlayerRequest request = IPlayer.PlayerRequest.values()[val];
                val = data.getInt(IPlayer.BUNDLE_STATE, 0);
                IPlayer.PlayerState state = IPlayer.PlayerState.values()[val];
                String info = data.getString(IPlayer.BUNDLE_INFO, "");
                if (mCallback != null) {
                    mCallback.onNewState(request, state, info);
                }
            } else if (msg.what == IPlayer.SONG_INFO) {
                Bundle data = msg.getData();
                String song = data.getString(IPlayer.BUNDLE_SONG);
                String type = data.getString(IPlayer.BUNDLE_TYPE);
                String title = data.getString(IPlayer.BUNDLE_TITLE);
                String author = data.getString(IPlayer.BUNDLE_AUTHOR);
                String desc = data.getString(IPlayer.BUNDLE_DESC);
                long length = data.getLong(IPlayer.BUNDLE_LENGTH, 0);
                long songlength = data.getLong(IPlayer.BUNDLE_SONGLENGTH, -1);
                int subsongs = data.getInt(IPlayer.BUNDLE_SUBSONGS, -1);
                boolean valid = data.getBoolean(IPlayer.BUNDLE_VALID, false);
                boolean playlist = data.getBoolean(IPlayer.BUNDLE_PLAYLIST, false);
                if (mCallback != null) {
                    mCallback.onSongInfo(song, type, title, author, desc, length, songlength, subsongs, valid, playlist);
                }
            }
            return true;
        }
    }

    private class PlayerConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBinder = (PlayerService.PlayerBinder) service;
            mService = mBinder.getService();
            if (mService != null) {
                Looper looper = mContext.getMainLooper();
                mReplyCallback = new ReplyCallback();
                mReplyHandler = new Handler(looper, mReplyCallback);
                mService.setHandler(mReplyHandler);
            }
            if (mCallback != null) {
                mCallback.onServiceConnected();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mCallback != null) {
                mCallback.onServiceDisconnected();
            }
            mBinder = null;
            mService = null;
            mReplyCallback = null;
            mReplyHandler = null;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            if (mConnection != null) {
                mContext.unbindService(mConnection);
                mConnection = null;
            }
        }

        @Override
        public void onNullBinding(ComponentName name) {
            if (mConnection != null) {
                mContext.unbindService(mConnection);
                mConnection = null;
            }
        }
    }
}
