# Android App Architecture

**Last Updated:** 2026-06-10

The Android app uses a small unidirectional state flow:

```text
User action
  -> Screen callback
  -> WarvisRepository mutation
  -> MutableStateFlow update
  -> Compose recomposition
```

## Components

- `MainActivity` - single Android entry point.
- `WarvisApp` - bottom navigation shell and state collection.
- `WarvisRepository` - MVP state owner and local persistence gateway.
- `SeedData` - hard-coded v4 career-plan seed data.
- `Models.kt` - domain models and computed state helpers.
- Screen packages - Today, Sprint, Knowledge, and Journey UI.
- `DoomShieldAccessibilityService` - opt-in service that detects blocked native apps and domains exposed by supported browsers.
- `DoomShieldPolicy` - local target definitions and 5-minute grace-period persistence.
- `DoomShieldInterventionActivity` - intervention screen launched when a blocked domain is detected.

## Persistence

`WarvisRepository` stores user progress in Android private `SharedPreferences`. This is local-only and scoped to the app sandbox.

Persisted keys:

- completed task IDs
- active stage ID
- knowledge-ring scores
- knowledge-ring update labels
- Doom Shield per-target grace-period timestamps

## Doom Shield privacy boundary

Doom Shield does not persist URLs. It matches native Instagram and YouTube app package names directly. For supported browsers, it recursively scans accessible node text, checks for a local blocked-domain match, and launches an intervention screen if matched.

If the user chooses **Continue for 5 minutes**, the app stores only the blocked target key and grace expiry timestamp in private app storage.
