package com.omicronapplications.andpluglib;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.omicronapplications.andpluglib.test.R.raw;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class PlayerServiceTest {
    private static PlayerService service;
    private static Callback callback;
    private static CompletableFuture<IPlayer.PlayerState> completableFuture = new CompletableFuture<>();

    private static class Callback implements IAndPlugCallback {
        @Override
        public void onServiceConnected() {}

        @Override
        public void onServiceDisconnected() {}

        @Override
        public void onNewState(IPlayer.PlayerRequest request, IPlayer.PlayerState state, String info) {
            completableFuture.complete(state);
        }
    }

    private static void waitForCallback() {
        try {
            completableFuture.get();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        } catch (ExecutionException e) {
            fail(e.getMessage());
        }
        completableFuture = new CompletableFuture<>();
    }

    private static File fileFromResources(String prefix, String suffix, int id) {
        File f = null;
        try {
            f = File.createTempFile(prefix, suffix);
            InputStream is = InstrumentationRegistry.getInstrumentation().getContext().getResources().openRawResource(id);
            int size = is.available();
            byte[] buffer = new byte[size];
            int num = is.read(buffer);
            assertEquals(size, num);
            is.close();
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(buffer);
            fos.close();
        } catch (IOException e) {
            assertFalse("IOException", false);
        }
        return f;
    }

    private static void serviceWait() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e)
        {
            assertFalse("InterruptedException", false);
        }
    }

    private static class MessageCallback implements Handler.Callback {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == IPlayer.PLAYER_STATE) {
                Bundle data = msg.getData();
                int val = data.getInt(IPlayer.BUNDLE_REQUEST);
                IPlayer.PlayerRequest request = IPlayer.PlayerRequest.values()[val];
                val = data.getInt(IPlayer.BUNDLE_STATE);
                IPlayer.PlayerState state = IPlayer.PlayerState.values()[val];
                String info = data.getString(IPlayer.BUNDLE_INFO);
                callback.onNewState(request, state, info);
                return true;
            }
            return false;
        }
    }

    @ClassRule
    public static final ServiceTestRule mServiceRule = new ServiceTestRule();

    @BeforeClass
    public static void startService() throws TimeoutException {
        System.loadLibrary("andplug");
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), PlayerService.class);
        IBinder binder = mServiceRule.bindService(intent);
        service = ((PlayerService.PlayerBinder) binder).getService();
        Handler.Callback handlerCallback = new MessageCallback();
        Looper looper = Looper.getMainLooper();
        Handler handler = new Handler(looper, handlerCallback);
        callback = new Callback();
        service.setHandler(handler);
    }

    @Test
    public void testDefaultValues() throws TimeoutException {
        assertFalse("getRepeat", service.getRepeat());
        service.setRepeat(true);
        assertTrue("getRepeat", service.getRepeat());
        service.setRepeat(false);
    }

    @Test
    public void testDuplicateCalls() throws TimeoutException {
        // Multiple setups
        service.initialize(44100, true, false, true, false, 32, 1024);
        waitForCallback();
        assertEquals(IPlayer.PlayerState.CREATED, service.getState());
        service.initialize(44100, true, false, true, false, 32, 1024);

        // Multiple loads
        File f = fileFromResources("Super_Nova", ".d00", raw.super_nova_d00);
        service.load(f.getAbsolutePath());
        waitForCallback();
        assertEquals(IPlayer.PlayerState.LOADED, service.getState());
        service.load(f.getAbsolutePath());

        // Multiple playback requests
        service.play();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.PLAYING, service.getState());

        // Multiple stop requests
        service.stop();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.STOPPED, service.getState());
        service.stop();

        // Multiple unloads
        service.unload();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.CREATED, service.getState());
        service.unload();

        // Multiple teardowns
        service.uninitialize();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.DEFAULT, service.getState());
        service.uninitialize();
    }

    @Test
    public void testLoadAndPlay() throws TimeoutException {
        // Setup
        assertEquals(IPlayer.PlayerState.DEFAULT, service.getState());
        service.initialize(44100, true, false, true, false, 32, 1024);
        waitForCallback();
        assertEquals(IPlayer.PlayerState.CREATED, service.getState());
        File f = fileFromResources("gone", ".d00", raw.gone_d00);
        service.load(f.getAbsolutePath());
        waitForCallback();
        assertEquals(IPlayer.PlayerState.LOADED, service.getState());
        assertEquals("getTitle", "", service.getTitle());
        assertEquals("getAuthor", "", service.getAuthor());
        assertEquals("getDesc", " \"GONE...\" by DRAX - converted by JCH, 13/1-1992. Player & music (C) Vibrants, 1992.", service.getDesc());

        // Play - stop
        service.play();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.PLAYING, service.getState());
        service.stop();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.STOPPED, service.getState());

        // Teardown
        service.unload();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.CREATED, service.getState());
        service.uninitialize();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.DEFAULT, service.getState());
    }

    @Test
    public void testMultipleSongs() throws TimeoutException {
        // Setup
        service.initialize(44100, true, false, true, false, 32, 1024);
        waitForCallback();
        assertEquals(IPlayer.PlayerState.CREATED, service.getState());

        // Song 1
        File f = fileFromResources("en lille test", ".d00", raw.en_lille_test_d00);
        service.load(f.getAbsolutePath());
        waitForCallback();
        assertEquals(IPlayer.PlayerState.LOADED, service.getState());
        assertEquals("getTitle", "En lille test", service.getTitle());
        assertEquals("getAuthor", "Morten Sigaard", service.getAuthor());
        assertEquals("getDesc", "", service.getDesc());
        service.stop();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.STOPPED, service.getState());

        // Song 2
        f = fileFromResources("gone", ".d00", raw.gone_d00);
        service.load(f.getAbsolutePath());
        waitForCallback();
        assertEquals(IPlayer.PlayerState.LOADED, service.getState());
        assertEquals("getTitle", "", service.getTitle());
        assertEquals("getAuthor", "", service.getAuthor());
        assertEquals("getDesc", " \"GONE...\" by DRAX - converted by JCH, 13/1-1992. Player & music (C) Vibrants, 1992.", service.getDesc());
        service.stop();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.STOPPED, service.getState());

        // Song 3
        f = fileFromResources("fresh", ".d00", raw.fresh_d00);
        service.load(f.getAbsolutePath());
        waitForCallback();
        assertEquals(service.getState(), IPlayer.PlayerState.LOADED);
        assertEquals("getTitle", "Fresh", service.getTitle());
        assertEquals("getAuthor", "Morten Sigaard", service.getAuthor());
        assertEquals("getDesc", "", service.getDesc());
        service.stop();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.STOPPED, service.getState());

        // Song 4
        f = fileFromResources("Super_Nova", ".d00", raw.super_nova_d00);
        service.load(f.getAbsolutePath());
        waitForCallback();
        assertEquals(service.getState(), IPlayer.PlayerState.LOADED);
        assertEquals("getTitle", "Super Nova", service.getTitle());
        assertEquals("getAuthor", "Metal & Drax (V)", service.getAuthor());
        assertEquals("getDesc", "", service.getDesc());
        service.stop();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.STOPPED, service.getState());

        // Song 5
        f = fileFromResources("the alibi", ".d00", raw.the_alibi_d00);
        service.load(f.getAbsolutePath());
        waitForCallback();
        assertEquals(IPlayer.PlayerState.LOADED, service.getState());
        assertEquals("getTitle", "", service.getTitle());
        assertEquals("getAuthor", "", service.getAuthor());
        assertEquals("getDesc", " Music originally composed by LAXITY on the Commodore 64 (in his own routine), and then later converted to the PC by JCH.  AdLib Player (C) Copyright 1992 Jens-Christian Huus.", service.getDesc());
        service.stop();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.STOPPED, service.getState());

        // Play - stop
        service.play();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.PLAYING, service.getState());
        service.stop();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.STOPPED, service.getState());

        // Teardown
        service.unload();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.CREATED, service.getState());
        service.uninitialize();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.DEFAULT, service.getState());
    }

    @Test
    public void testPlaybackControls() throws TimeoutException {
        // Setup
        service.initialize(44100, true, false, true, false, 32, 1024);
        waitForCallback();
        assertEquals(IPlayer.PlayerState.CREATED, service.getState());
        File f = fileFromResources("gone", ".d00", raw.gone_d00);
        service.load(f.getAbsolutePath());
        waitForCallback();
        assertEquals(IPlayer.PlayerState.LOADED, service.getState());

        // Immediate stop
        service.play();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.PLAYING, service.getState());
        service.stop();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.STOPPED, service.getState());

        // Play - pause - play - stop
        service.play();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.PLAYING, service.getState());
        service.pause();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.PAUSED, service.getState());
        service.play();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.PLAYING, service.getState());
        service.stop();
        serviceWait();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.STOPPED, service.getState());

        // Teardown
        service.unload();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.CREATED, service.getState());
        service.uninitialize();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.DEFAULT, service.getState());
    }

    @Test
    public void testSongNotLoaded() throws TimeoutException {
        // Setup
        service.initialize(44100, true, false, true, false, 32, 1024);
        waitForCallback();
        assertEquals(IPlayer.PlayerState.CREATED, service.getState());

        // Unload with no song loaded (ignored)
        service.unload();

        // Unloaded stop - resume - pause - play
        service.stop();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.STOPPED, service.getState());
        service.play();
        service.pause();
        service.play();

        // Multiple unload (ignored)
        File f = fileFromResources("the alibi", ".d00", raw.the_alibi_d00);
        service.load(f.getAbsolutePath());
        waitForCallback();
        assertEquals(IPlayer.PlayerState.LOADED, service.getState());
        service.unload();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.CREATED, service.getState());
        service.unload();

        // Teardown
        service.uninitialize();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.DEFAULT, service.getState());
    }

    @Test
    public void testUninitializedCalls() throws TimeoutException {
        // Teardown without setup (ignored)
        service.uninitialize();

        // Uninitialized unload
        service.unload();
        waitForCallback();
        assertEquals(IPlayer.PlayerState.CREATED, service.getState());

        // Uninitialized load
        File f = fileFromResources("Super_Nova", ".d00", raw.super_nova_d00);
        service.load(f.getAbsolutePath());
        waitForCallback();
        assertEquals(IPlayer.PlayerState.ERROR, service.getState());

        // Uninitialized stop - resume - pause - play
        service.stop();
        service.play();
        service.pause();
        service.play();
    }
}
