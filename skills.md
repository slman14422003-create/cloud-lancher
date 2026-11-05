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
