# Fire TV Launcher

A simple custom home screen for Fire TV (Fire OS, Android-based). It shows every installed app in a D-pad friendly grid with a clock. Back is ignored, Home returns to the top, and the Menu button opens App info for the highlighted app (uninstall / force stop).

Built for the Fire TV Stick HD (Fire OS 8, Android 11). It will not work on newer Vega OS devices.

## Build the APK

1. Install Android Studio and open this folder. Let Gradle sync (it downloads Gradle 8.7 itself).
2. Build > Build Bundle(s) / APK(s) > Build APK(s).
3. The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Install over ADB

1. On the TV: Settings > My Fire TV > Developer options > ADB debugging ON.
2. Find the TV's IP: Settings > My Fire TV > About > Network.
3. On your computer (same Wi-Fi) with `adb` installed:

   ```
   chmod +x install.sh restore.sh
   ./install.sh 192.168.1.50
   ```

The script connects, installs, and tries `cmd package set-home-activity` to make this the default home app.

## If it won't become the default

Amazon blocks `set-home-activity` on some Fire OS builds. Try, in order:

1. Press Home on the remote. If Android shows a chooser, pick **Home** and choose **Always**.
2. Disable the stock launcher (reversible, but can misbehave on some builds):

   ```
   adb -s <ip>:5555 shell pm list packages | grep -i launcher
   adb -s <ip>:5555 shell pm disable-user --user 0 <stock-launcher-package>
   ```

   On Fire OS 7/8 the stock package is usually `com.amazon.firelauncher`; older builds used `com.amazon.tv.launcher`. Use whatever the first command shows on your device. Press Home afterwards.

## Undo everything

```
./restore.sh <ip> <stock-launcher-package-you-disabled>
```

If the screen is ever stuck blank, `adb connect <ip>:5555` then `adb shell pm enable <stock-launcher-package>` and reboot the TV.

## Customising

- Grid, colours, clock: `app/src/main/res/layout/activity_main.xml` and `res/drawable/`
- App list and behaviour: `app/src/main/java/com/custom/firelauncher/MainActivity.kt`

## Privacy and debloat

First, the no-risk way (no ADB): on the TV go to Settings > Preferences > Privacy Settings and turn off
Device Usage Data, Collect App Usage Data and Interest-based Ads. Under Preferences > Featured Content,
turn off video and audio autoplay.

For more, `debloat.sh` disables packages from `packages.txt` using `pm disable-user` (reversible):

```
./debloat.sh <ip> list      # see what's installed
./debloat.sh <ip> dry       # preview, changes nothing
./debloat.sh <ip> apply
./debloat.sh <ip> restore   # undo
```

Package names differ between Fire OS versions and some system packages can't be disabled without extra privileges.
