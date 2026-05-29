# deepal-player

A homemade video player for the **Deepal S09**. Plug in a USB stick,
pick a file, pick a screen (your own, the passenger seat, or the rear),
hit PLAY. The video starts on the chosen screen and you keep a remote
on yours: pause, scrub, volume, next episode. Handy when the kids in
the back are watching cartoons and you don't want to mash buttons on
their display every time.

It is just Jetpack Compose, ExoPlayer (Media3) and libaums for USB.
Built around one specific car (Cockpit / HwSAPT, Android 12 underneath),
but it should also work on any Huawei head unit from the same family.

## How it works

The trick is simple: the app is installed **only on the driver's user**.
To cast video to a different screen we do not spin up a second copy on
some other Android user. We launch our own `PlayerActivity` inside our
process via `ActivityOptions.launchDisplayId`. Android happily lets an
Activity start on any display, and the per-user isolation does not get
in the way.

```
   driver (user 13, display 0)              passenger (display 6)
   ┌──────────────────────────┐             ┌──────────────────────┐
   │ MainActivity             │             │                      │
   │  Browser → Scenes → PLAY │             │  PlayerActivity      │
   │  │                       │ launchDisp=6│   ExoPlayer          │
   │  │  remoteTargets        ├────────────▶│   UsbContentProvider │
   │  ▼                       │             │   (reads via token)  │
   │ RemoteControlScreen      │◀── handle ──│                      │
   │  play / pause / seek /   │  registry   │  rear (display 4)    │
   │  next / prev / volume    │             │  PlayerActivity #2   │
   └──────────────────────────┘             └──────────────────────┘
```

Main pieces:

- **MainActivity** runs on the driver, owns the USB session and the
  `UsbContentProvider`.
- **PlayerActivity** is launched on the target display inside the same
  process.
- **PlayerRegistry** holds a `Map<displayId, Handle>`. Each
  `PlayerActivity` registers in `onCreate` and `MainActivity` drives it
  from its `RemoteControlScreen`.
- **UsbContentProvider** serves URIs shaped like
  `content://<authority>/<token>/<idx>/<name>`. One token owns the
  playlist of media files in the current folder. Prev/Next just bumps
  the index inside the URI, so ExoPlayer correctly reloads the media
  item.

## Stack

- Kotlin 2.0.21 + Compose (with the new K2 plugin).
- AGP 8.7.3 / Gradle 8.14, `compileSdk = 35`, `minSdk = 31`.
- Media3 1.7.1.
- [`io.github.anilbeesetti:nextlib-media3ext:1.7.1-0.9.0`](https://github.com/anilbeesetti/nextlib).
  FFmpeg decoders (EAC3, DTS, TrueHD, AC4, Opus, Vorbis, ALAC, FLAC).
  Without these the audio on a typical WEB-DL or BDRip often does not
  play because the stock Cockpit mixer does not handle EAC3.
- [`me.jahnen.libaums:core:0.10.0`](https://github.com/magnusja/libaums)
  plus `libusbcommunication:0.3.0` for USB-MSC. If the libusb backend
  trips on a control-transfer EIO (which happens when the Cockpit vold
  has already grabbed the device), the app silently falls back to the
  built-in Android backend (`DEVICE_CONNECTION_SYNC`).

## Build

```bash
cd <repo>/deepal-player
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew :app:assembleRelease
# output: app/build/outputs/apk/release/deepal-player-release.apk
```

The release build needs `app/release.keystore`. If you do not have one,
generate a self-signed keystore (it is in `.gitignore`, so it stays
local):

```bash
keytool -genkeypair -v \
  -keystore app/release.keystore \
  -alias deepal-player -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass <password> -keypass <password> \
  -dname "CN=Deepal Player,O=Private,C=XX"
```

The build script reads the keystore passwords from
`~/.gradle/gradle.properties`:

```properties
RELEASE_STORE_PASSWORD=<your password>
RELEASE_KEY_PASSWORD=<your password>
RELEASE_KEY_ALIAS=deepal-player
```

You can also pass them via environment variables with the same names.
With nothing set, the release build will fail when it tries to sign the
APK.

## Things to expect

- **USB is single-owner.** Only one process on the car can hold the
  stick at a time. If you also install deepal-player on the passenger
  user, whoever boots first claims the device. So we only install on
  the driver and cast to the other screens through `launchDisplayId`.
- **libusb sometimes fails.** When Cockpit's vold gets to the device
  first, libusb returns EIO. The app falls back to the Android backend
  and that usually works. If it does not, replugging the stick helps.
- **Tested on S09 only.** Avatr and Aito should work in theory (same
  HwSAPT base), but the display ids may differ. Not verified.

## Wishlist

- R8 / proguard for the release build (currently
  `isMinifyEnabled = false`).
- Hook up `CarAudioManager` from the Huawei Vehicle SDK so the system
  volume slider shows up.
- A proper multi-user mode: RPC over an abstract socket, so the
  passenger can browse the stick through a proxy instead of waiting on
  the driver.
