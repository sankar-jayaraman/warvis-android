# WARVIS Architecture

**Last Updated:** 2026-09-19

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

No network, backend, account, or cloud sync is used.

## Doom Shield

Doom Shield is an opt-in Android Accessibility Service. After the user enables it in Android Accessibility settings, it detects blocked native apps such as Instagram and YouTube, and scans supported browser windows for blocked domains such as `facebook.com`, `instagram.com`, and `youtube.com`. Matching happens locally and full URLs are not stored.

WARVIS immediately opens Amazon Kindle when a blocked target is detected. If Kindle is unavailable, WARVIS opens its Google Play listing. Successful Kindle handoffs are stored locally as timestamped events; target keys are retained for diagnostics, but full browser URLs are never stored.

Every Sunday at 19:00 local time, WARVIS reports the number of handoffs during the current week and compares it with the previous week. The explicit goal is zero handoffs.

## Current persistence choice

The design target is local persistence. The current implementation uses Android private `SharedPreferences` for the small MVP state footprint. This can be replaced by Room/DataStore later without changing the UI event contract.
