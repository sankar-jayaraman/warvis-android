# WARVIS Testing

**Last Updated:** 2026-10-16

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
11. On the Today Doom Shield card, open **Set up TaskRatchet payments**.
12. Follow the external TaskRatchet link to create/configure the account and obtain a personal API key.
13. Paste a non-production test key, confirm the field is password-masked, rotate the device, and confirm the token text is not restored from saved-instance state.
14. Validate the key and confirm WARVIS reports TaskRatchet payment-method presence and the monthly API charge limit.
15. Confirm the initial mode is **Dry-run (no money charged)** even after successful validation.
16. With no key or no payment method, attempt all three paid actions and confirm they fail closed with a direct route to setup.
17. Confirm the live-enable dialog states that every confirmed blocked-target override, Instagram gaming session, and disable request immediately charges **US$4.00**, that this is **roughly 25-28 DKK**, and that bank conversion/FX fees and monthly limits may vary/apply.
18. Dismiss the dialog and confirm dry-run remains active. Do not enable live charging during routine verification.
19. Disconnect and confirm configuration clears; reconnect with the test account and remain in dry-run.
20. Open Today and confirm Doom Shield shows its actual **Disabled** status, then tap **Enable Doom Shield**.
21. Enable **WARVIS Doom Shield**.
22. Return to WARVIS and confirm the status refreshes to **Enabled**.
23. Tap **Disable Doom Shield**, confirm the dedicated screen states TaskRatchet dry-run, **US$4.00 (roughly 25-28 DKK; bank conversion and FX fees may vary)**, and that disabling lasts until manual re-enable.
24. Press system Back and repeat with **Cancel**; confirm neither action calls TaskRatchet or opens Android Settings.
25. Start the visible 10-second cooldown and confirm the final disable button remains disabled until it completes.
26. Confirm the dry-run action, verify component-specific Accessibility details open when supported (otherwise generic Accessibility settings), and disable WARVIS Doom Shield.
27. Return to WARVIS and confirm the status refreshes to **Disabled** and no automatic re-enable occurs.
28. Repeat with Settings unavailable/blocked and confirm the remote-confirmed attempt remains recoverable; retry must reuse the request ID and must not create a second TaskRatchet charge record.
29. Re-enable Doom Shield manually before continuing.
30. Open Chrome or another supported browser.
31. Navigate to `facebook.com`, `instagram.com`, `youtube.com`, `x.com`, `twitter.com`, or `quora.com`.
32. Confirm the Doom Shield commitment screen appears instead of immediately redirecting.
33. Tap **Open Kindle instead** and confirm Kindle opens, or its Play Store listing opens when Kindle is not installed.
34. Reopen a blocked target, tap **Open Backlog in WARVIS**, and confirm the existing Backlog screen opens.
35. Press Back and confirm the blocked app/site is not revealed.
36. With MainActivity already open, repeat the Backlog action and confirm the existing activity navigates to Backlog.
37. Navigate away from Backlog, rotate the device, and confirm WARVIS does not replay the old Backlog request. Press Back and confirm WARVIS returns Home rather than revealing the blocked app.
38. Reopen a blocked target and start the TaskRatchet dry-run override.
39. Confirm the screen clearly states the USD amount/variable DKK estimate, **15 minutes**, and that dry-run does not charge money.
40. Confirm the final action remains disabled during the visible 10-second cooling-off countdown.
41. Rotate the device or recreate the activity during the countdown; confirm access is not granted and the countdown does not restart.
42. After the countdown completes, confirm the dry run and verify the original app/browser returns or reopens when possible.
43. Interrupt the network or recreate the activity after submission, then retry and verify TaskRatchet shows one stable request ID/result rather than a second attempt.
44. Simulate a confirmed remote result followed by entitlement persistence failure; confirm access remains closed, the confirmed attempt remains recoverable, and retry does not create a new charge.
45. Confirm the same target remains accessible for 15 minutes, then becomes blocked again after expiry.
46. While one target is unlocked, open a different blocked app/site and confirm it remains blocked.
47. Unlock native X and confirm `x.com`/`twitter.com` aliases share the entitlement; repeat in the other direction.
48. Tap the confirmation rapidly and confirm progress disables duplicate taps and does not extend the original entitlement.
49. At device-local 20:59, confirm existing Doom Shield blocking behavior remains unchanged.
50. On Today, tap **Configure night curfew and exemptions** and confirm the launchable installed-app list opens.
51. Confirm the default saved schedule is enabled, displays `21:00-10:00`, and says it crosses midnight.
52. Set equal start and end times; confirm clear validation appears, the invalid interval is not saved, and enabling is rejected.
53. While the curfew is disabled, change to `13:00-17:00`, leave the screen, reopen it, and confirm the disabled same-day schedule persisted.
54. Enable `13:00-17:00`; confirm an arbitrary non-exempt app is allowed at 12:59 and 17:00, and intercepted at 13:00 and 16:59.
55. Restore `21:00-10:00`; confirm an arbitrary non-exempt app is intercepted at 21:00 and 09:59, and allowed at 20:59 and 10:00.
56. Confirm only broad-curfew interceptions show the configured sleep interval; existing Instagram, YouTube, X, Quora, and blocked-domain interventions are not mislabeled as curfew-only.
57. Disable the broad curfew and confirm arbitrary apps are allowed while existing Doom Shield targets remain blocked.
58. Confirm automatic exemptions are switched on and disabled so they cannot be removed.
59. Search for a banking, authenticator, or password-manager app, enable it, leave the screen, reopen it, and confirm the package-ID selection persisted.
60. During the configured curfew, confirm Gmail/Outlook/Samsung Email, the default SMS app, WhatsApp, WhatsApp Business, and YouTube Music are allowed when installed.
61. During the configured curfew, confirm WARVIS, Home/launcher, phone/emergency, Clock, Settings, Kindle/Play Store, enabled keyboards, and enabled accessibility services remain accessible.
62. Confirm a manually exempted banking/authenticator/password-manager app is allowed during the configured curfew.
63. Confirm regular YouTube, Instagram, X, Quora, and blocked browser domains still trigger their canonical intervention even if their package was accidentally persisted as a user exemption.
64. Confirm Chrome and other browsers are not automatically exempt merely because they are preinstalled/system apps.
65. During the configured curfew, confirm an active target-specific 15-minute entitlement is honored for its app, but a different arbitrary app remains blocked.
66. Unlock two arbitrary apps separately and confirm their `app:<package>` entitlements do not affect each other.
67. Disable or change the default SMS/dialer, enabled keyboard, or accessibility service; after one minute, confirm the newly enabled package is automatically allowed.
68. During the configured curfew, confirm common Android/Google/Samsung permission controllers and separate in-call UI packages remain mandatory exemptions.
69. Open **Configure Instagram gaming session** and confirm defaults of 180/3/5 minutes and the
    documented 15-720/1-30/1-60 validation bounds.
70. Confirm invalid boundary values show explicit errors and are not persisted; save both minimum
    and maximum edge values, then restore 180/3/5.
71. In dry-run mode, complete the 10-second review and activate one session; rotate/recreate WARVIS
    and confirm the active status and countdowns survive.
72. In live mode, confirm the additional immediate-US$4 acknowledgement is mandatory and the
    pending purchase displays its immutable duration/allowance/quarantine snapshot.
73. During an active session, confirm native Instagram and `instagram.com` are allowed during both
    daytime blocking and the configured night curfew, while other targets remain unchanged.
74. Accumulate foreground use across native Instagram, browser Instagram, exits, and app switches;
    confirm only Instagram foreground time counts and quarantine starts at the exact allowance
    boundary without requiring another accessibility event.
    While Instagram remains active, trigger notifications, keyboard events, overlays, and
    background-package accessibility events; confirm none cancels or settles the meter. In one
    browser package, navigate away from and back to `instagram.com`; confirm metering stops and
    restarts from the observed URL/domain.
75. During quarantine, confirm native/browser Instagram are blocked even when their generic
    15-minute grace is active. Confirm no payment override is offered on the quarantine screen.
76. Recreate the accessibility service while Instagram is foreground and confirm metering resumes
    from persisted consumed allowance. Confirm periodic checkpoints preserve completed usage and
    that abrupt same-boot restart recovers no more than one 15-second tail exactly once, excluding
    longer unknown downtime; confirm an already-due quarantine/session boundary is enforced
    immediately. Repeat with the active root unavailable and confirm no false usage is scheduled.
77. Turn the screen off while Instagram is foreground, leave it asleep, then unlock. Confirm only
    usage before screen-off was settled, sleep time was not counted, and metering resumes only
    after canonical Instagram is verified as the active window.
78. During one boot, move wall time backward and forward and confirm elapsed deadlines still win.
    Reboot with simulated backward rollback and confirm only the last trusted remaining duration
    is preserved. Confirm plausible forward progress reduces the trusted remainder. Reboot with an
    implausible forward jump and confirm it neither immediately destroys the paid session nor
    shortens quarantine, while both remain capped by their last trusted/original durations and
    expire against the new boot's elapsed clock. Confirm a stale allowance callback after any
    stop/restart has no effect.
79. Reboot during active allowance and confirm offline foreground time was not counted. Reboot
    during quarantine and confirm the reboot-safe bounded clock policy; after expiry, confirm a
    fresh allowance resumes only while the fixed session deadline remains.
80. Simulate remote confirmation followed by local grant/attempt-completion failure; retry and
    confirm the exact purchase grants once without a second TaskRatchet charge.

Exercise typed failure handling in dry-run with test doubles or controlled failures: HTTP 400,
401/403, 402, 409, 422, 429, timeout/offline, malformed JSON, remote `failed`, credential
invalidation, attempt-persistence failure, and benefit-persistence failure. Every case must fail
closed, disclose no token, and preserve an unresolved/confirmed attempt when retry is safe.

The disable route is reliable only when initiated from WARVIS. Once the Accessibility Service is
disabled, Android prevents it from enforcing policy or re-enabling itself. Android Keystore
protects the API key at rest, but an unlocked/rooted/compromised device, force-stop, uninstall,
ADB, safe mode, app tampering, and OEM Settings surfaces can bypass this local-only design. There
is no backend reconciliation in this version.

## Focused JVM tests

Run the Doom Shield policy, nighttime classification, exemption-selection, and internal destination-contract tests:

```powershell
Set-Location C:\Users\SNKR\WARVIS
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.warvis.android.doom.DoomShieldPolicyTest" `
  --tests "com.warvis.android.doom.DoomShieldNightPolicyTest" `
  --tests "com.warvis.android.doom.NightCurfewScheduleTest" `
  --tests "com.warvis.android.doom.NightCurfewRepositoryTest" `
  --tests "com.warvis.android.doom.NightExemptionSelectionTest" `
  --tests "com.warvis.android.doom.InstagramGamingPolicyTest" `
  --tests "com.warvis.android.doom.InstagramGamingRepositoryTest" `
  --tests "com.warvis.android.doom.DoomShieldDisableAuthorizationTest" `
  --tests "com.warvis.android.ui.WarvisDestinationContractTest" `
  --tests "com.warvis.android.taskratchet.TaskRatchetPolicyTest" `
  --tests "com.warvis.android.taskratchet.TaskRatchetResultMapperTest" `
  --tests "com.warvis.android.taskratchet.TaskRatchetAttemptStateTest"
```

The nighttime tests cover default, same-day, and cross-midnight boundaries; disabled and invalid
schedules; corrupt persisted-value fallback; atomic save success/failure; unchanged canonical
targets; explicit and user exemptions; existing-target precedence; package validation; and
target-specific `app:<package>` keys. The selection tests verify that user choices remain
independent and cannot remove mandatory automatic exemptions. TaskRatchet tests cover the
400-cent invariant, dry-run/live labels, live-enable policy, documented HTTP mapping, atomic
request-ID reuse without unrelated-attempt loss, account/credential binding, pending-live
replacement/disconnect blocking, dry-run cleanup, and remote-confirmed recovery.

## Future automated tests

Add JVM tests for:

- focus-card ordering
- progress calculations
- knowledge-ring suggested focus
- stage advancement
