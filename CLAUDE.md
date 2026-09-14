# Jellyfin Music — working notes

A Jellyfin music client for Android: Kotlin, Jetpack Compose, media3/ExoPlayer
behind a foreground `MediaLibraryService`, with Lidarr-backed search-and-request.

## Debug signing — do not regenerate the keystore

`app/debug.keystore` is committed and `app/build.gradle.kts` pins the debug
`signingConfig` to it. Leave both alone.

The reason is that Android refuses to install an update whose signing key
differs from the installed package's:

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.jellyfinmusic
signatures do not match newer version; ignoring!
```

Gradle's default is a key it generates in `~/.android/debug.keystore`. That
path does not survive a fresh build container, so every rebuild elsewhere
produced a differently-signed APK, and installing it over the previous one
required `adb uninstall` — which wipes the Jellyfin session, the saved queue,
the Lidarr and YouTube keys, and every downloaded track.

The committed key is the standard debug one (`androiddebugkey` / `android`).
It is not a secret and cannot sign a release build; signing a release needs a
separate keystore that must NOT be committed.

Current debug signer, for checking an APK with
`apksigner verify --print-certs`:

```
SHA-256 ec2cbc3c01bb8aa3cca71d7c1a9d7e82d7d1b73887aee8f99d2a2da76408de07
```

If that digest ever changes, the pin has broken — fix the build rather than
telling the user to uninstall.

## Building in a fresh container

The Android SDK and `local.properties` are both ephemeral and gitignored, so a
recycled container needs them recreated before Gradle will run:

```
echo "sdk.dir=/root/android-sdk" > local.properties
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Send it to the
user with the file tool once it builds — don't just name the path.

## Git

Work lands on `main`. Pushes fail with a 403 at ref advertisement when the
session's GitHub connection is authenticated as the wrong account; check with
`get_me` before assuming the credentials were lost.

## Audio format

The library standard is `.ogg`. `JellyfinRepository.streamUrl()` claims
`ogg`/`oga`/`vorbis` among its direct-stream containers so the server does not
transcode them, and `downloadUrl()` always uses `static=true` — the streaming
tiers can return an HLS playlist, and a progressive download of one fetches
the playlist text instead of any audio.
