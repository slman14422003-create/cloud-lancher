# Cloud Launcher - work log

## Task: home menu button (iOS style)
- Removed the four shortcut buttons row from the bottom of HomeScreen (library, recents, protected, setup).
- Added HomeMenuButton (ui/components/HomeMenu.kt): small glass circle button at the top end of the screen, below the status bar.
- Tap opens an iOS-like popup menu: rounded dark card, thin dividers, label + accent icon per row, spring scale-in from the top-end corner, fade/scale-out on close.
- Menu entries call the same callbacks as before: onOpenDrawer, onOpenRecents, onOpenProtected, vm.openSetup().
- Tap outside or back dismisses the popup (PopupProperties focusable).
- No new strings or dependencies: reuses home_library, home_recents, home_protected, home_setup and material-icons-extended (MoreHoriz).

## Files touched
- app/src/main/java/com/cloud/launcher/ui/components/HomeMenu.kt (new)
- app/src/main/java/com/cloud/launcher/ui/screens/HomeScreen.kt

## Notes for next tasks
- Build was not run in the sandbox (no network for Gradle); verify with assembleDebug / GitHub Actions.
- Tuning points: gapPx (46.dp) for distance under the button, card width 220.dp, card color 0xF2262628.

## Task: custom recents + hardware keys (Samsung J5)
- AppMonitorService now filters key events (canRequestFilterKeyEvents + flagRequestFilterKeyEvents). KEYCODE_APP_SWITCH and KEYCODE_MENU are consumed; on key up it opens the launcher recents screen (utils/RecentsIntent.kt), so the system recents screen is never shown.
- MainActivity: new recentsSignal / recentsFromApp state. Pressing the key again while recents is open closes it.
- Back key: inside the launcher it pops one step (recents, drawer, protected -> home). If recents was opened from another app, back calls moveTaskToBack so the previous app returns.
- Root: RootManager.enableAccessibility writes enabled_accessibility_services via su, called from LauncherViewModel when root is granted, so no manual toggle is needed.
- CustomRecentsScreen redesigned: glass rows, header with app count, free RAM bar (ActivityManager.MemoryInfo), pill clear-all button.
- Home menu button is pinned to the physical top-right regardless of RTL (Ltr provider overlay).
- Performance: R8 + shrinkResources on release, profileinstaller, icon cache bitmaps 112px, shorter nav fades (150/110ms), lighter popup shadow.

## Notes
- Not built in the sandbox. Check assembleRelease (R8 rules in proguard-rules.pro) and test the J5 key: if the left key does not open the launcher recents, the accessibility service is not enabled or the firmware handles the key before filtering.

## Task: setup wizard, default launcher detection, system app, permissions
- SetupDialog removed. New full-screen SetupWizard (ui/components/SetupWizard.kt), 6 steps: welcome, root, permissions, default launcher, system app, done. Shown automatically only on first run; completion/skip is stored in DataStore (setup_shown). Reopen from the home menu (Setup entry).
- Default launcher row used to be hard-coded as not granted. Now PermissionUtils.isDefaultLauncher resolves the HOME intent and compares the package; state refreshes on ON_RESUME.
- Root helpers (RootManager): grantAllPermissions (pm grant, appops, deviceidle whitelist, accessibility), setDefaultLauncher (cmd role / set-home-activity), enableAccessibility now tries WRITE_SECURE_SETTINGS directly first.
- System app: root/SystemInstaller.kt copies the APK to /system/priv-app/CloudLauncher and installs res/raw/privapp_permissions.xml to /system/etc/permissions, then removeUserCopyAndReboot deletes the /data copy and reboots (user data is kept).
- Manifest: added REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, WAKE_LOCK, VIBRATE, REORDER_TASKS, SET_WALLPAPER(_HINTS), and privileged GET_TASKS, REAL_GET_TASKS, FORCE_STOP_PACKAGES, WRITE_SECURE_SETTINGS; android:persistent=true (effective only as system app).
- RULE: every privileged permission requested in the manifest must also be listed in privapp_permissions.xml, otherwise Android 9+ can refuse to boot with the app in priv-app.

## Notes
- Not built in the sandbox. Test the system install on the J5 (Android 7 ignores privapp xml, harmless). On Android 10+ /system is read-only and the installer will report failure.

## Task: home grid, drag reorder, stronger app protection
- New home grid (4 columns, up to 16 apps, DataStore key home_apps). VM: homeApps, toggleHome, setHomeOrder, setFavoritesOrder, MAX_HOME.
- New ui/components/ReorderableAppGrid.kt, used for both the home grid and the favorites dock: long press then drag reorders with animated slots (RTL aware), long press without moving opens the app context menu, tap launches. Tap is suppressed for 350ms after a drag.
- Context menu now has separate entries: home screen, favorites dock, protect (prevent system from closing).
- Protection: OomController scripts now walk the full ps list, so child processes (pkg:service) also get oom_adj -17 / oom_score_adj -1000 and renice -20; also appops RUN_IN_BACKGROUND/RUN_ANY_IN_BACKGROUND allow, set-inactive false, standby bucket active, deviceidle whitelist. Monitor loop every 2s, relaunch cooldown 15s, the launcher process itself is protected while the service runs, MAX_PROTECTED raised to 8.
- Removed all Arabic comments from Kotlin sources (user preference). Arabic stays only in res/values/strings.xml.

## Notes
- Limits: no userspace method can make a process absolutely unkillable. oom_score_adj -1000 survives lmkd, but ActivityManager can reset it on state changes (re-applied within 2s), and the user can still force-stop from system settings.
- Drag is not tested on device; check the drag feel on the J5 (spring stiffness in ReorderableAppGrid).

## Task: updates over the system app
- Cause: versionCode was fixed at 1. For a system app, PackageManager keeps a /data update only if its versionCode is greater than the /system copy; equal or lower means the update is dropped on reboot (or refused as a downgrade).
- app/build.gradle.kts: versionCode is now autoVersionCode (env VERSION_CODE if set, otherwise minutes since epoch), versionName 1.0.<code>. Every build is newer than the previous one, no manual bump needed.
- Debug builds are signed with the same release keystore (cloud-release.jks), so debug and release can update each other and the system copy. A different signature gives INSTALL_FAILED_UPDATE_INCOMPATIBLE.
- Wizard system step: if the app is an updated system app (FLAG_UPDATED_SYSTEM_APP) it offers "merge update into system", which copies the current APK to /system/priv-app and reboots; the old /data copy is then dropped automatically.

## Notes
- Normal update flow: install the new APK over the current one, no reboot needed. The system copy only needs refreshing through the wizard if you want the update to survive a factory reset.
- The system copy installed earlier has versionCode 1, so any build from now on is accepted as an update.

## Task: custom recents fixes and redesign, system settings menu entry
- Close (X) button: ProcessManager now runs am force-stop with --user 0 (fallback without), fixes PATH for the su shell, verifies via ps, and falls back to kill -9 on every process of the package (including :service processes). Kill all runs force-stops 4 at a time with a 60s timeout, then hard-kills leftovers. Without root it falls back to ActivityManager.killBackgroundProcesses.
- ViewModel: closeApp (suspend, returns success), closing / killingAll state, messages flow (snackbar in recents, toast elsewhere), unprotect-and-close action for protected apps, retry action on failure. Closed apps are removed from recents (kill all too), freed MB reported.
- CustomRecentsScreen redesigned: animated RAM ring card, running count and apps RAM, filter chips (all / running / protected), sort by recent or RAM, per-app RAM badge + bar, status dot, spinner while closing, animated row removal, haptics, glass snackbar, banner to request root or auto-fix tracking permissions.
- Home menu: the setup entry is replaced by "System settings" (Settings.ACTION_SETTINGS). The setup wizard still shows automatically on first run only.

## Notes
- Not built in the sandbox (no Gradle). Verify with assembleDebug / GitHub Actions. If X still fails on the J5, check the snackbar text: "تعذّر إغلاق" means the system restarted the app right after the kill.

## Task: home screen redesign
- Background: dark blue to black vertical gradient plus a soft accent radial glow behind the clock.
- ClockWidget: gradient clock text with accent glow, date in a glass pill with an accent dot, battery and free RAM chips (refresh every 15s).
- HomeScreen: glass search pill above the dock (opens the library), richer empty state card with an animated swipe-up hint.
- Not built in the sandbox; verify with assembleDebug.

## Task: home screen polish
- Empty-state hint card shows once only (DataStore key home_hint_shown, set when first shown) and hides itself after 15s with a fade.
- Softer background (gradient fades to black at 50%, weaker glow), search pill now uses liquidGlass like the dock so both match, battery/RAM chips forced LTR so the text reads "528 MB".

## Task: performance on Android 7 (Samsung J5)
- Navigation: exit transitions removed, enter is a 110ms fade only (no scale/spring on the drawer). Two full-screen alpha layers per transition were the main cost.
- ProtectionService: monitor interval 2s -> 4s; monitor script uses pidof only (no full ps scan, no per-process while loop) each cycle.
- Recents: running-app refresh every 7s (was 4s), RSS rounded to 4MB steps so rows do not recompose every refresh, RAM sampled every 5s in 4MB steps.
- Icons are preloaded into IconCache in the background after the app list loads (favorites and home apps first), so the drawer opens without per-tile decoding.
- Glass brushes are top-level constants; home background brush is cached with drawWithCache; clock text blur shadow removed.
- Release only: baseline-prof.txt added (profileinstaller already present). With root, the app runs "pm compile -m speed -f com.cloud.launcher" once per versionCode, 20s after launch, so ART precompiles the whole app. Debug builds are much slower than release on this device, test with the release APK.
