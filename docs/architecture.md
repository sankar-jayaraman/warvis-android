# WARVIS Architecture

**Last Updated:** 2026-10-16

WARVIS is a single-module native Android app.

## Architecture style

- Single `ComponentActivity`.
- Jetpack Compose UI.
- Local-first state.
- Repository exposes a single `StateFlow<WarvisState>`.
- UI reads immutable state and sends events back to the repository.

```text
MainActivity
  -> WarvisTheme
  -> WarvisApp
     -> WarvisRepository
        -> SeedData
        -> SharedPreferences persistence
     -> Today / Sprint / Knowledge / Journey screens
DoomShieldAccessibilityService
  -> detects blocked native apps and supported browser domains
  -> applies the current persisted broad-curfew schedule to other non-exempt apps
  -> immediately launches Amazon Kindle
  -> records each successful handoff locally
DoomShieldWeeklyReportReceiver
  -> compares the current week's handoffs with the previous week
  -> sends a Sunday 19:00 local-time progress notification
```

## Data ownership

The repository is the source of truth for the MVP. It starts from seeded v4 plan data, then overlays locally persisted user progress:

- completed task IDs
- knowledge-ring scores
- active career stage

The career-plan repository remains local-only. TaskRatchet is the sole network integration and is
used only for payment-gated Doom Shield actions; there is no WARVIS backend or cloud sync.

## Doom Shield

Doom Shield is an opt-in Android Accessibility Service. After the user enables it in Android Accessibility settings, it detects blocked native apps such as Instagram, YouTube, X, and Quora, and scans supported browser windows for their blocked domains. Matching happens locally and full URLs are not stored.

The Today card reflects the actual service status on app resume. Enabling opens Accessibility
settings directly. A WARVIS-originated disable action uses a TaskRatchet-gated screen with a
10-second cooldown, then consumes a short-lived one-shot disable authorization before opening
component-specific Accessibility details (falling back to generic settings). Blocked-target
overrides use the same payment provider but create only a target-specific 15-minute entitlement.
The exact amount is US$4.00 / 400 cents, described as roughly 25-28 DKK with bank conversion and FX
fees varying. Dry-run is default; live charging requires validated credentials, a confirmed payment
method, and explicit acknowledgement.

The broad curfew is a separate local policy with an enabled toggle and minute-precision start/end
times. The default is enabled `21:00-10:00` in device-local time. Pure schedule logic handles both
same-day and cross-midnight intervals, while a SharedPreferences repository atomically stores the
three configuration values and falls back on corrupt or equal-endpoint state. The Accessibility
Service reads a fresh schedule snapshot per event; canonical blocked targets retain precedence.

The personal TaskRatchet key is AES/GCM-encrypted using a non-exportable Android Keystore key.
Only ciphertext and IV are stored in private preferences, and TaskRatchet preferences are excluded
from backup/device transfer. Every attempt is persisted before the request with a stable idempotency
ID and is scoped by purpose plus target. Remote success is persisted before granting the benefit;
confirmed-but-incomplete attempts remain recoverable and are cleared only after benefit success.

This local route is not tamper-proof. Once disabled, the Accessibility Service cannot enforce or
re-enable itself. Force-stop, uninstall, ADB, safe mode, and OEM Settings paths can bypass it.
Android Keystore protects data at rest but not an unlocked, rooted, compromised, or tampered device.
Robust reconciliation and enforcement would require a backend heartbeat; this version intentionally
has no backend.

WARVIS immediately opens Amazon Kindle when a blocked target is detected. If Kindle is unavailable, WARVIS opens its Google Play listing. Successful Kindle handoffs are stored locally as timestamped events; target keys are retained for diagnostics, but full browser URLs are never stored.

Every Sunday at 19:00 local time, WARVIS reports the number of handoffs during the current week and compares it with the previous week. The explicit goal is zero handoffs.

## Current persistence choice

The design target is local persistence. The current implementation uses Android private `SharedPreferences` for the small MVP state footprint. This can be replaced by Room/DataStore later without changing the UI event contract.
