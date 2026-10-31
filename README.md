# Stark Shelf

A Stark-style home screen launcher for Android. Your real installed apps sit on a swipeable 3D shelf of glowing HUD cards. Pick which apps appear, launch with a tap, and optionally make it your default home screen.

| HUD dial | Settings |
|:--:|:--:|
| ![HUD dial](docs/screenshots/shelf.png) | ![Settings](docs/screenshots/settings.png) |

> **About these images:** they are design previews rendered from the app's layout and colors, with sample app names. They are not captures from a physical device. To replace them, install the app, take real screenshots, and overwrite the two files in `docs/screenshots/`.

---

## Features

- **Radial dial navigation.** The outer ring holds the letters A-Z, `#` and a star. Touch a letter and the inner ring fills with the apps starting with it. Slide inward to an app and release to launch. Pick a letter, pick an app, launch, in one stroke. Release in the centre to cancel.
- **Star = most used.** The top of the ring (the default view) lists your most-launched apps first, so your daily apps are one touch away. Usage is counted on your device.
- **Type or speak.** The command bar at the bottom filters live and launches the top match with **Go**. The **MIC** button opens Android's voice input: say "open maps" and it launches.
- **Lock-on targeting.** The app under your finger gets an amber target lock with its details (package, version, date, launch count) in the panel below.
- **Jarvis look.** Deep navy, arc-reactor cyan and amber, rotating tick rings, a radar sweep, a grid background, a boot sequence, a greeting, clock, date and battery readout.
- **Sound and haptics.** Synthesized HUD blips for ring ticks, target lock, launch, boot and cancel, played on the media volume, plus a haptic tick. Turn sound off in settings.
- **Real apps, real icons.** Built from what is installed on your phone, and it updates live when you install or remove apps.
- **Choose your apps.** The settings page has a toggle for every app, plus Select all and Clear.
- **Home screen ready.** Set it as your default launcher. Back never closes the HUD.
- **Private.** No internet permission, no accounts, no analytics. Everything stays on your device.

---

## Install it (no Android Studio needed)

GitHub builds the APK for you.

### 1. Put the code on GitHub
1. Create a new empty repository on GitHub (private or public).
2. Push this folder to its `main` branch:
   ```bash
   cd stark-shelf
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/YOUR-USER/stark-shelf.git
   git push -u origin main
   ```
   If you use the website's upload button instead, make sure the hidden `.github` folder is included. Without it, the build will not start.

### 2. Let GitHub build the APK
1. Open the repository's **Actions** tab.
2. The **Build APK** workflow starts on its own after the push. You can also start it with **Run workflow**.
3. The first build takes about 5 minutes. Later builds are faster.
4. When it shows a green tick, the APK is ready.

### 3. Download it on your phone
- Open the repository's **Releases** page in your phone's browser and download `stark-shelf.apk`. Each build creates a new release.
- Alternatively, open the finished workflow run and download the `stark-shelf-apk` artifact (a zip, requires a GitHub login).

### 4. Install
1. Open the downloaded file. Android will ask you to allow installs from your browser (**Install unknown apps**). Allow it for this install.
2. Google Play Protect may show a warning because the app is not from the Play Store and is signed with a debug key. Choose **Install anyway**. You can review the full source in this repository.

### 5. Make it your home screen (optional)
1. Open **Stark Shelf** and tap **SET**.
2. Tap **SET AS HOME APP** and choose **Stark Shelf**.
3. Press your phone's Home button. The shelf is now your home screen.

### Switching back
Open Android **Settings > Apps > Default apps > Home app** and select your previous launcher. Uninstalling Stark Shelf also restores your old launcher.

---

## Using the app

| Action | How |
|---|---|
| Launch an app (fastest) | Touch a letter on the outer ring, slide inward to the app, release |
| Launch a favourite | Leave the ring on the star and tap one of the top apps |
| See more apps for a letter | Slide to the `+N` node at the end of the inner ring and release (it pages through) |
| Cancel | Slide into the centre circle and release |
| Search | Type in the command bar. **Go** launches the top match |
| Voice | Tap **MIC** and say "open <app name>" (needs Google voice input) |
| App details / uninstall | Lock on an app, then **INFO** |
| Choose which apps show | **SET**, then use the toggles |
| Turn sound off | **SET**, then the **SOUND** switch |

---

## How it works

```
Android system
   |  PackageManager.queryIntentActivities (launcher apps)
   v
Data.kt  ShelfVM  ----- DataStore (enabled apps, sound, launch counts)
   |  StateFlow: all apps / enabled / shown / usage
   v
MainActivity (Compose)
   |-- HudScreen      dial, target panel, command bar, voice
   |-- Sfx            synthesized sounds
   '-- SettingsScreen toggles, home-app shortcut
```

- **App list:** `loadApps()` in `Data.kt` queries every app with a launcher entry, skips Stark Shelf itself, and loads label, icon, version and a date.
- **Dates:** preinstalled apps (many Google apps) report an install time of 1970. The app treats anything before 2000 as invalid and shows the last update date instead, labelled **UPDATED**, or **BUILT-IN / FACTORY** if neither is valid.
- **Dial touch:** one gesture handler (`handleTouch` in `HudScreen.kt`) converts the finger position to a distance and an angle. The outer band picks a letter, the middle band picks the app sector, and the centre cancels. Releasing over an app launches it.
- **Sound:** `Sfx.kt` synthesizes short tones in code and plays them with `AudioTrack` on the media stream, so no audio files are needed and they follow your media volume.
- **Voice:** uses Android's built-in speech recognition screen (`RecognizerIntent`), so the app needs no microphone permission.
- **Launcher role:** the second intent filter in `AndroidManifest.xml` (`HOME` + `DEFAULT`) makes Android list it as a home app.

---

## Project structure

```
stark-shelf/
|-- .github/workflows/build.yml     CI: builds the APK, publishes a release
|-- app/
|   |-- build.gradle.kts            Android + Compose configuration
|   '-- src/main/
|       |-- AndroidManifest.xml     Home intent, app-visibility query
|       |-- res/                    Theme and launcher icon
|       '-- java/com/stark/shelf/
|           |-- MainActivity.kt     Entry point, screen switching, Back handling
|           |-- HudScreen.kt        Radial dial, target panel, command bar, voice, boot
|           |-- Sfx.kt              Synthesized HUD sounds
|           |-- SettingsScreen.kt   App toggles, home-app shortcut, sound
|           |-- Data.kt             App loading, ViewModel, saved settings
|           |-- Ui.kt               Corner brackets, buttons
|           '-- Theme.kt            Color palette
|-- docs/screenshots/               README images
|-- build.gradle.kts, settings.gradle.kts, gradle.properties
'-- README.md
```

---

## Customizing

| Change | Where |
|---|---|
| Colors (cyan, amber, background) | `Theme.kt` |
| Apps per ring page | `PAGE` in `HudScreen.kt` |
| Dial zones (letters, apps, cancel) | the `0.70f` and `0.30f` thresholds in `handleTouch` |
| Ring speed | the `tween(...)` durations in `Dial` |
| Sounds (pitch, length, volume) | the `add(...)` lines at the top of `Sfx.kt` |
| Boot text | the `seq` list in `HudScreen` |
| App name and icon | `AndroidManifest.xml`, `res/drawable/ic_launcher.xml` |
| Package id | `applicationId` and `namespace` in `app/build.gradle.kts` |

Change files, commit, push. GitHub builds a new APK each time.

---

## Build locally (optional)

Requirements: Android Studio Koala or newer, JDK 17.

1. Open the folder in Android Studio and let Gradle sync.
2. Press **Run**, or build from a terminal with `gradle assembleDebug` (Gradle 8.9).
3. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

---

## Permissions and privacy

- No `INTERNET` permission. The app cannot send data anywhere.
- Reads the list of launchable apps through a narrow `<queries>` declaration.
- Stores only your app selection and sound setting, locally.

---

## Troubleshooting

**The build fails in GitHub Actions.** Open the failed run, click the **Build debug APK** step, and copy the error. Most failures are a typo or a version mismatch and are quick to fix.

**No Actions tab or the workflow does not run.** Confirm `.github/workflows/build.yml` exists in the repo and that you pushed to `main`. Check that Actions are enabled under *Settings > Actions*.

**There is no Release.** Under *Settings > Actions > General > Workflow permissions*, choose **Read and write permissions**, then re-run the workflow. You can still use the run's artifact in the meantime.

**The phone blocks the install.** Allow **Install unknown apps** for the browser or file manager you used. If Play Protect warns, choose **Install anyway**.

**The dial is empty.** Open **SET** and enable some apps (or press **SELECT ALL**). A fresh install shows every app until you change a toggle.

**No sound.** Sounds play on the **media** volume, so raise it (or check it is not muted). Also check the **SOUND** switch in settings.

**The MIC button says voice input is unavailable.** It needs a speech recognition app (normally the Google app). Typing in the command bar works without it.

**A letter shows no apps.** Only apps you enabled in settings appear. Letters with no apps show `0 APPS`.

**Stark Shelf is not offered as a home app.** Open it once first, then try **SET AS HOME APP** again, or use Android *Settings > Apps > Default apps > Home app*.

**I am stuck on the HUD.** Open *Settings > Apps > Default apps > Home app* and pick another launcher. Gesture navigation and recents are handled by Android and keep working.

---

## Changelog

**0.2**
- New Jarvis-style radial dial replaces the swipe shelf: letter ring, app ring, one-stroke launch.
- Most-used apps on the star ring, with launch counts.
- Command bar with live filter and voice launch.
- Sounds now work (synthesized, media volume).
- Fixed the 1970 install date on preinstalled and Google apps.
- New Jarvis palette, boot sequence, greeting, clock and battery readout.

**0.1**
- First version: swipeable card shelf, settings, home screen support.

## Roadmap ideas

- Custom pinning and ordering for the star ring
- App categories as a second ring
- Dock for favorite apps, notification badges
- Release signing and an in-app update check

---

## Credits

Visual direction inspired by the Augma Repo "Stark UI" shelf and the Jarvis interface from the Iron Man films. Built with Kotlin and Jetpack Compose.
