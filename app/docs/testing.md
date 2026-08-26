# WARVIS Testing

**Last Updated:** 2026-06-10

## Current validation status

The command-line Android toolchain is installed and the debug APK builds successfully.

Validated command:

```powershell
Set-Location C:\Users\SNKR\WARVIS
.\gradlew.bat :app:assembleDebug
```

Output APK:

```text
C:\Users\SNKR\WARVIS\app\build\outputs\apk\debug\app-debug.apk
```

## Manual smoke-test checklist

After connecting an emulator or Android device:

1. Build the app with `.\gradlew.bat :app:assembleDebug`.
2. Launch the app on an emulator or Android device.
3. Confirm the Today tab opens by default.
4. Mark the Focus Card task done and confirm it changes.
5. Open Sprint and confirm Week 0 plus Weeks 1-4 render.
6. Toggle a task and restart the app; confirm completion persists.
7. Open Knowledge and update all five scores.
8. Restart the app; confirm scores persist.
9. Open Journey and advance the active stage.
10. Restart the app; confirm active stage persists.
11. Open Today and tap **Enable in Accessibility Settings**.
12. Enable **WARVIS Doom Shield**.
13. Open Chrome or another supported browser.
14. Navigate to `facebook.com`, `instagram.com`, or `youtube.com`.
15. Confirm the Doom Shield intervention screen appears.
16. Tap **Continue for 5 minutes**.
17. Confirm the same site does not immediately re-trigger Doom Shield.
18. Open the Instagram app or YouTube app.
19. Confirm Doom Shield appears for each app when the 5-minute grace period is not active.

## Future automated tests

Add JVM tests for:

- focus-card ordering
- progress calculations
- knowledge-ring suggested focus
- stage advancement
