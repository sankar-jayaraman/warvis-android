# WARVIS Testing

**Last Updated:** 2026-10-15

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
14. Navigate to `facebook.com`, `instagram.com`, `youtube.com`, `x.com`, `twitter.com`, or `quora.com`.
15. Confirm the Doom Shield commitment screen appears instead of immediately redirecting.
16. Tap **Open Kindle instead** and confirm Kindle opens, or its Play Store listing opens when Kindle is not installed.
17. Reopen a blocked target and tap **Test 25 kr unlock**.
18. Confirm the screen clearly states **25 kr**, **15 minutes**, and that no money will be charged in this test version.
19. Confirm **Confirm dry run — unlock 15 minutes** remains disabled during the visible 10-second cooling-off countdown.
20. Rotate the device or recreate the activity during the countdown; confirm access is not granted and the countdown does not restart.
21. After the countdown completes, confirm the unlock and verify the original app/browser returns or reopens when possible.
22. Confirm the same target remains accessible for 15 minutes, then becomes blocked again after expiry.
23. While one target is unlocked, open a different blocked app/site and confirm it remains blocked.
24. Unlock native X and confirm `x.com`/`twitter.com` aliases share the entitlement; repeat in the other direction.
25. Tap the confirmation rapidly and confirm it does not extend the original 15-minute entitlement.

## Future automated tests

Add JVM tests for:

- focus-card ordering
- progress calculations
- knowledge-ring suggested focus
- stage advancement
