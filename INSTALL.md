# Installing Newz on your Android phone

Newz is not in the Play Store. You build the app on your Mac once, then copy it to
your phone and install it yourself. This is called "sideloading". These steps take
about half an hour the first time, most of it waiting for downloads.

## 1. What you need

- A Mac (Apple silicon) with this project folder on it, and an internet connection.
- About 15 GB of free disk space for the Android build tools.
- An Android phone running Android 10 or newer.
- One way to get the app file onto the phone: a USB cable, or any way of sending
  yourself a file (for example AirDrop to a computer, a USB stick, or a file-sync app).

You do not need a Google account, and Newz never talks to Google.

## 2. Build it

Open the Terminal app, go to the project folder, and run these three commands one
after the other. Wait for each one to finish before starting the next.

```bash
bash scripts/setup-android-toolchain.sh
bash scripts/make-release-key.sh
./gradlew :androidApp:assembleRelease
```

- The first command installs the Java and Android build tools. You only need it once.
- The second command creates your personal signing key in the `keystore/` folder.
  It only does this once; running it again leaves the key alone.
  **Back up the `keystore/` folder** somewhere safe (see "Updating" below).
- The third command builds the app. It ends with `BUILD SUCCESSFUL`.

If you want to double-check the result, `bash scripts/verify-release.sh` confirms the
app is signed, asks for only two permissions (internet access and network status),
and contains no Google or tracking libraries.

## 3. Where the APK is

The finished app is one file:

```
androidApp/build/outputs/apk/release/androidApp-release.apk
```

In Finder, open the project folder and follow that path, or run
`open androidApp/build/outputs/apk/release` in Terminal to show it.

## 4. Install on the phone (no cable)

1. Copy `androidApp-release.apk` to your phone in whatever way you like.
2. On the phone, open the file (usually from the Files app or your Downloads).
3. Android will block it the first time. You need to
   **enable install from unknown sources** for the app you use to open the file.
   Go to **Settings › Apps › Special app access › Install unknown apps**, pick that app
   (for example Files or your browser), and turn on **Allow from this source**.
   The exact menu names vary a little between phone makers.
4. Go back and open the file again, then tap **Install**.
5. Once it is installed you can turn that setting off again.

## 5. Install with a cable (adb)

1. On the phone, turn on developer options: **Settings › About phone**, then tap
   **Build number** seven times.
2. Turn on **USB debugging** in **Settings › System › Developer options**.
3. Connect the phone with a USB cable and accept the "Allow USB debugging?" prompt.
4. In Terminal, in the project folder, run:

```bash
adb install androidApp/build/outputs/apk/release/androidApp-release.apk
```

If Terminal says `adb: command not found`, use the full path instead:
`/opt/homebrew/share/android-commandlinetools/platform-tools/adb install androidApp/build/outputs/apk/release/androidApp-release.apk`

## 6. Updating

To install a newer version, build again (step 2, last command) and install the new
APK the same way. Your sources, topics and saved stories stay in place.

This only works if the new build is signed with **the same key**. Android refuses an
update signed with a different key. Keep the `keystore/` folder backed up: if you
lose it, you have to uninstall Newz (which deletes its data on the phone; use
**Settings › Backup & restore › Export to a file** in Newz first to keep your settings) and then install the
build made with a new key.

## 7. A note on Android developer verification

Google has announced that, on certified Android phones, apps installed from outside
the Play Store will need to come from a verified developer. Newz is built and signed
by you, not by a registered developer. Installing with a cable through `adb`
(step 5) keeps working under these rules, so if the phone ever refuses the file in
step 4, use step 5 instead.
