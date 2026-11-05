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
