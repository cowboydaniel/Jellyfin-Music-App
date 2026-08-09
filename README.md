# Jellyfin Music

An Android music client for Jellyfin, written in Kotlin with Jetpack Compose.
Audio plays through a foreground `MediaSessionService`, so it keeps going when
the screen locks or the app is backgrounded. A search tab queries Lidarr and
lets you request anything your library doesn't already have.

## Features

- **Sign-in** against `POST /Users/AuthenticateByName`. The server URL, access
  token and user ID are stored in `EncryptedSharedPreferences`.
- **Library browsing** — Artist → Album → Track via the Jellyfin `/Items` API,
  plus your existing Jellyfin playlists.
- **Background playback** — ExoPlayer inside an `androidx.media3`
  `MediaSessionService` with lock-screen and notification transport controls,
  audio focus handling, and pause-on-headphone-unplug.
- **Mini player** on every screen, and a full-screen now-playing view with a
  seek bar, shuffle, repeat (off / all / one), skip, and a reorderable queue.
- **Search & request** — searches Lidarr's artist and album lookup endpoints,
  cross-references the results against your Jellyfin library, and flags each one
  as *In library* or offers a *Request* button. Requesting POSTs to Lidarr's
  `/api/v1/artist` or `/api/v1/album` with `monitored: true` and search-on-add.
- **Settings** — Jellyfin URL, Lidarr URL, Lidarr API key, root folder, and
  quality/metadata profiles (fetched from Lidarr with "Test connection").

## Build and install

Requires JDK 17+ and the Android SDK (platform 35, build-tools 35.0.0).

```bash
# 1. Clone
git clone https://github.com/cowboydaniel/jellyfin-music-app.git
cd jellyfin-music-app

# 2. Point Gradle at your Android SDK (skip if ANDROID_HOME is already set)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# 3. Build the debug APK
./gradlew assembleDebug

# 4. Plug in your phone with USB debugging enabled, then confirm adb sees it
adb devices

# 5. Install straight to the phone
./gradlew installDebug
```

`installDebug` builds and installs in one step. If you'd rather install an APK
you already built:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

To launch it from the command line, and to watch the playback logs:

```bash
adb shell am start -n com.jellyfinmusic/.MainActivity
adb logcat -s MusicService ExoPlayerImpl AndroidRuntime
```

If `adb devices` shows the phone as `unauthorized`, accept the USB debugging
prompt on the device. If it shows nothing at all, try `adb kill-server && adb
start-server` and a different cable or port.

## First run

1. Sign in with your Jellyfin server URL (e.g. `http://192.168.1.10:8096`),
   username and password.
2. Open **Settings**, enter your Lidarr URL (e.g. `http://192.168.1.10:8686`)
   and API key (Lidarr → Settings → General → API Key), then tap **Test Lidarr
   connection** to pull your root folder and profile lists.
3. Browse under **Library**, or use **Search** to request music you don't have.

Cleartext HTTP is enabled so plain `http://` LAN addresses work without extra
configuration.
