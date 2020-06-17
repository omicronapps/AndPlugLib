# AndPlugLib

> AdLib music player library for Android

## Description

[AndPlugLib](https://github.com/omicronapps/AndPlugLib.git) is an AdLib music player library for Android, using the [AdPlug](http://adplug.github.io/) sound player library. Android audio playback and AdPlug native library control is implemented as an application service, with interfaces provided for player control and status callbacks. A separate helper class is also provided, for managing service lifecycle and monitoring the connection state.

AndPlugLib is used in [AndPlug](https://play.google.com/store/apps/details?id=com.omicronapplications.andplug) music player application for Android devices.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Testing](#testing)
- [Usage](#usage)
- [Example](#example)
- [Credits](#credits)
- [Release History](#release-history)
- [License](#license)

## Prerequisites

- [Android 4.0.3](https://developer.android.com/about/versions/android-4.0.3) (API Level: 15) or later (`ICE_CREAM_SANDWICH_MR1`)
- [CMake](https://cmake.org/) Release 3.4.1 or later
- [Android Gradle Plugin](https://developer.android.com/studio/releases/gradle-plugin) 4.0.0) or later (`gradle:4.0.0`)
- [AdPlug](https://github.com/adplug/adplug) Version 2.3.3 (_branch/tag:_ `release-2.3.3`)
- [libbinio](https://github.com/adplug/libbinio) Version 1.5 (_tag:_ `libbionio-1.5`)

## Installation

Setup steps:

1. Check out a local copy of AndPlugLib repository
2. Check out local copies of AdPlug and libbinio libraries under `andpluglib/src/main/cpp`
3. Apply patches to AdPlug and libbinio
4. Build library with Gradle, using Android Studio or directly from the command line 

```
$ git clone https://github.com/omicronapps/AndPlugLib.git
$ cd AndPlugLib/andpluglib/src/main/cpp/
$ git clone --branch release-2.3.3 https://github.com/adplug/adplug.git
$ patch adplug/src/version.h -i adplug.patch
$ git clone https://github.com/adplug/libbinio.git
$ patch libbinio/src/binio.h -i libbinio.patch
```

## Testing

AndPlugLib includes both instrumented unit tests and a simple test application.

### Instrumented tests

Located under `andpluglib/src/androidTest`.

These tests are run on a hardware device or emulator, and verifies correct operation of the `PlayerService` implementation and its usage of the native AdPlug library. A set of songs need to be downloaded and installed in order  to run the instrumented tests.

Setup steps:
```
cd AndPlugLib/andpluglib/src/androidTest/res/raw/
wget http://modland.com/pub/modules/Ad%20Lib/EdLib%20D00/MSK/en%20lille%20test.d00 en_lille_test_d00 
wget http://modland.com/pub/modules/Ad%20Lib/EdLib%20D00/MSK/fresh.d00 fresh_d00
wget http://modland.com/pub/modules/Ad%20Lib/EdLib%20D00/JCH/gone.d00 gone_d00
wget http://modland.com/pub/modules/Ad%20Lib/EdLib%20D00/Drax/coop-Metal/super%20nova.d00 super_nova_d00
wget http://modland.com/pub/modules/Ad%20Lib/EdLib%20D00/JCH/the%20alibi.d00 the_alibi_d00
```

### Test application

Located under `app/src/main`.

Uses hardcoded path to EdLib songs for playback testing (can be replaced by any other AdPlug compatible songs).

Setup steps:
```
cd AndPlugLib/app/src/main/assets/
wget http://modland.com/pub/modules/Ad%20Lib/EdLib%20D00/MSK/en%20lille%20test.d00 
wget http://modland.com/pub/modules/Ad%20Lib/EdLib%20D00/MSK/fresh.d00
wget http://modland.com/pub/modules/Ad%20Lib/EdLib%20D00/JCH/gone.d00
wget http://modland.com/pub/modules/Ad%20Lib/EdLib%20D00/Drax/coop-Metal/super%20nova.d00
wget http://modland.com/pub/modules/Ad%20Lib/EdLib%20D00/JCH/the%20alibi.d00
```

## Usage

AndPlugLib is controlled through the following class and interfaces:
- `PlayerController` - service management class 
- `IPlayer` - player service interface
- `IAndPlugCallback` - callback interface

### `PlayerController`

Manages an instance of the `PlayerService` application service.

#### Constructor

```PlayerController(IAndPlugCallback callback, Context context)```

Constructor for the `PlayerController` class.

Arguments:
- `callback` - allows registering for callbacks through the  `IAndPlugCallback` interface
- `context` - required in order for `PlayerController` to manage the application service

#### `create`

```void create()```

Create and connect (bind) to `PlayerService` application service. While the service is running, `PlayerController` monitors its state, and provides callbacks to a registered listener through `IAndPlugCallback` interface.

#### `destroy`

```boolean destroy()```

Disconnect (unbind) from the application service.

#### `restart`

```void restart()```

Convenience method combining `create()` and `destroy()` methods.

#### `getService`

```IPlayer getService()```

Returns a reference to a `PlayerService` instance. Only valid between `onServiceConnected()` and `onServiceDisconnected()` callbacks of `IAndPlugCallback`!

### `IPlayer`

#### initialize

```void initialize(int rate, boolean bit16, boolean usestereo, boolean left, boolean right, int bufferCount, int samples)```

Create native AdPlug instance and initialize `AudioTrack`. Confirmed initialized if `onNewState()` callback returns `PlayerState.CREATED`.

Arguments:
- `rate` - sample rate (Hz)
- `bit16` - true: 16 bit resolution, false: 8 bit resolution
- `usestereo` - true: stereo, false: mono
- `left` - copy left channel data to right channel if left == true and right == false
- `right` - copy right channel data to left channel if left == false and right == true
- `bufferCount` - buffer count
- `samples` - samples pre buffer (bytes)

#### uninitialize

```void uninitialize()```

Destroy native AdPlug instance and release `AudioTrack`. Confirmed destroyed if `onNewState()` callback returns `PlayerState.DEFAULT`.

#### load

```void load(String song)```

Loads AdPlug compatible song for playback. Ready for playback if `onNewState()` callback returns `PlayerState.LOADED`.

Argument:
- `song` - full path to song

#### unload

Unload song. Song unloaded once `onNewState()` callback returns `PlayerState.CREATED`.

```void unload()```

#### play

```void play()```

Start playback. Song playback started once `onNewState()` callback returns `PlayerState.PLAYING`.

#### pause

```void pause()```

Pause playback. Song playback paused once `onNewState()` callback returns `PlayerState.PAUSED`.

#### stop

```void stop()```

Stop playback. Song playback stopped once `onNewState()` callback returns `PlayerState.STOPPED`.

#### rewind

```void rewind(int subsong)```

Select sub-song.

Argument:
- `subsong` - index of sub-song

#### setRepeat

```void setRepeat(boolean repeat)```

Control repeat behavior of current song.

Argument:
- `repeat` - true: repeat song indefinitely, false: skip to next song at end of current song

#### getRepeat

```boolean getRepeat()```

Return whether current song is on repeat.

#### getSong

```String getSong()```

Return song file name.

#### getTitle

```String getTitle()```

Return song title.

#### getAuthor

```String getAuthor()```

Return song author.

#### getDesc

```String getDesc()```

Return song description.

#### getSubsongs

```int getSubsongs()```

Get number of sub-songs.

#### getSubsong

```int getSubsong()```

Get current sub-songs.

#### debugPath

```void debugPath(String path)```

Debug use only! Output PCM data to file.

Argument:
- `path` - true: path to store PCM data

#### getState

```PlayerState getState()```

Return playback state.

### `IAndPlugCallback`

Callback interface from `PlayerService` application service instance.

#### onServiceConnected

```void onServiceConnected()```

Application service connected. `PlayerController.getService()` may be used to retrieve a reference to a `PlayerService` instance following this callback.

#### onServiceDisconnected

```void onServiceDisconnected()```

Application service disconnected. Any reference to the `PlayerService` instance is now invalid.

#### onNewState

```void onNewState(IPlayer.PlayerRequest request, IPlayer.PlayerState state)```

Notification of new `PlayerService` state.

- `request` - requested state
- `state` - actual state

## Example

Implement `IAndPlugCallback` callback interface:

```
import com.omicronapplications.andpluglib.IAndPlugCallback;
import com.omicronapplications.andpluglib.IPlayer;

class AndPlugCallback implements IAndPlugCallback {
    @Override
    public void onServiceConnected() {
        // Bound to PlayerService, retrieve IPlayer instance
        IPlayer player = mController.getService();
    }

    @Override
    public void onServiceDisconnected() {
        // Unbound from PlayerService, IPlayer instance unusable
    }

    @Override
    public void onNewState(IPlayer.PlayerRequest request, IPlayer.PlayerState state) {
        // Requested and actual state reported
    }
}
```

Load native library, and create a `PlayerController` instance to bind to `PlayerService`:

```
import com.omicronapplications.andpluglib.PlayerController;

System.loadLibrary("andplug");
IAndPlugCallback callback = new AndPlugCallback();
PlayerController controller = new PlayerController(callback, getApplicationContext());
controller.create();
```

Retrieve `IPlayer` object on `IAndPlugCallback.onServiceConnected()` callback, and create player instance:

```
import com.omicronapplications.andpluglib.IPlayer;

IPlayer player = mController.getService();
player.initialize(44100, true, false, true, false, 32, 1024);
player.load("the alibi.d00");
player.play();
```

Stop playback and unload song:

```
player.stop();
player.unload();
player.uninitialize();
```

Destroy `PlayerController` instance to unbind from `PlayerService`:

```
controller.destroy();
```

## Credits

Copyright (C) 2019-2020 [Fredrik Claesson](https://github.com/omicronapps)

## Release History

- 1.0.0 Initial release
- 1.1.0 Player service refactored
- 1.2.0 Updated to AdPlug v2.3.2 and libbinio v1.5, migrated to AndroidX
- 1.3.0 Updated to AdPlug v2.3.3, improved error handling

## License

AndPlugLib is licensed under [GNU LESSER GENERAL PUBLIC LICENSE](LICENSE).
