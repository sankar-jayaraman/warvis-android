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
11. Open Today and confirm Doom Shield shows its actual **Disabled** status, then tap **Enable Doom Shield**.
12. Enable **WARVIS Doom Shield**.
13. Return to WARVIS and confirm the status refreshes to **Enabled**.
14. Tap **Disable Doom Shield**, confirm the dedicated screen states **25 kr**, **no real charge**, and that disabling lasts until manual re-enable.
15. Press system Back and repeat with **Cancel**; confirm neither action opens Android Settings.
16. Start the visible 10-second cooldown and confirm the final disable button remains disabled until it completes.
17. Confirm the disable action, verify component-specific Accessibility details open when supported (otherwise generic Accessibility settings), and disable WARVIS Doom Shield.
18. Return to WARVIS and confirm the status refreshes to **Disabled** and no automatic re-enable occurs.
19. Repeat with Settings unavailable/blocked on a test device or stub and confirm WARVIS displays an explicit error and remains safely in the commitment screen.
20. Re-enable Doom Shield manually before continuing.
21. Open Chrome or another supported browser.
22. Navigate to `facebook.com`, `instagram.com`, `youtube.com`, `x.com`, `twitter.com`, or `quora.com`.
23. Confirm the Doom Shield commitment screen appears instead of immediately redirecting.
24. Tap **Open Kindle instead** and confirm Kindle opens, or its Play Store listing opens when Kindle is not installed.
25. Reopen a blocked target, tap **Open Backlog in WARVIS**, and confirm the existing Backlog screen opens.
26. Press Back and confirm the blocked app/site is not revealed.
27. With MainActivity already open, repeat the Backlog action and confirm the existing activity navigates to Backlog.
28. Navigate away from Backlog, rotate the device, and confirm WARVIS does not replay the old Backlog request. Press Back and confirm WARVIS returns Home rather than revealing the blocked app.
29. Reopen a blocked target and tap **Test 25 kr unlock**.
30. Confirm the screen clearly states **25 kr**, **15 minutes**, and that no money will be charged in this test version.
31. Confirm **Confirm dry run — unlock 15 minutes** remains disabled during the visible 10-second cooling-off countdown.
32. Rotate the device or recreate the activity during the countdown; confirm access is not granted and the countdown does not restart.
33. After the countdown completes, confirm the unlock and verify the original app/browser returns or reopens when possible.
34. Confirm a persistence failure shows an explicit error and does not reopen the blocked target.
35. Confirm the same target remains accessible for 15 minutes, then becomes blocked again after expiry.
36. While one target is unlocked, open a different blocked app/site and confirm it remains blocked.
37. Unlock native X and confirm `x.com`/`twitter.com` aliases share the entitlement; repeat in the other direction.
38. Tap the confirmation rapidly and confirm it does not extend the original 15-minute entitlement.
39. At device-local 20:59, confirm existing Doom Shield blocking behavior remains unchanged.
40. On Today, tap **Review night app exemptions** and confirm the launchable installed-app list opens.
41. Confirm automatic exemptions are switched on and disabled so they cannot be removed.
42. Search for a banking, authenticator, or password-manager app, enable it, leave the screen, reopen it, and confirm the package-ID selection persisted.
43. At 21:00 and 09:59, open an arbitrary non-exempt app and confirm the existing intervention and 25 kr dry-run flow appears with the sleep message.
44. At 20:59 and 10:00, open that same arbitrary app and confirm it is allowed.
45. During the night window, confirm Gmail/Outlook/Samsung Email, the default SMS app, WhatsApp, WhatsApp Business, and YouTube Music are allowed when installed.
46. During the night window, confirm WARVIS, Home/launcher, phone/emergency, Clock, Settings, Kindle/Play Store, enabled keyboards, and enabled accessibility services remain accessible.
47. Confirm a manually exempted banking/authenticator/password-manager app is allowed during the night window.
48. Confirm regular YouTube, Instagram, X, Quora, and blocked browser domains still trigger their canonical intervention even if their package was accidentally persisted as a user exemption.
49. Confirm Chrome and other browsers are not automatically exempt merely because they are preinstalled/system apps.
50. During the night window, confirm an active target-specific 15-minute entitlement is honored for its app, but a different arbitrary app remains blocked.
51. Unlock two arbitrary apps separately and confirm their `app:<package>` entitlements do not affect each other.
52. Disable or change the default SMS/dialer, enabled keyboard, or accessibility service; after one minute, confirm the newly enabled package is automatically allowed.
53. During the night window, confirm common Android/Google/Samsung permission controllers and separate in-call UI packages remain mandatory exemptions.

The disable route is reliable only when initiated from WARVIS. Once the Accessibility Service is
disabled, Android prevents it from enforcing policy or re-enabling itself. Force-stop, uninstall,
ADB, safe mode, and OEM-specific Settings surfaces can bypass this local-only design. Robust
enforcement would require a backend heartbeat and real payment/authorization service.

## Focused JVM tests

Run the Doom Shield policy, nighttime classification, exemption-selection, and internal destination-contract tests:

```powershell
Set-Location C:\Users\SNKR\WARVIS
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.warvis.android.doom.DoomShieldPolicyTest" `
  --tests "com.warvis.android.doom.DoomShieldNightPolicyTest" `
  --tests "com.warvis.android.doom.NightExemptionSelectionTest" `
  --tests "com.warvis.android.doom.DoomShieldDisableAuthorizationTest" `
  --tests "com.warvis.android.ui.WarvisDestinationContractTest"
```

The nighttime tests cover exact 21:00/10:00 boundaries, unchanged canonical targets,
explicit and user exemptions, existing-target precedence, package validation, and
target-specific `app:<package>` keys. The selection tests verify that user choices remain
independent and cannot remove mandatory automatic exemptions.

## Future automated tests

Add JVM tests for:

- focus-card ordering
- progress calculations
- knowledge-ring suggested focus
- stage advancement
