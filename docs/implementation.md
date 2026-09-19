# WARVIS Implementation Notes

**Last Updated:** 2026-09-19

## Project layout

```text
app/src/main/java/com/warvis/android
├── MainActivity.kt
├── data
│   ├── SeedData.kt
│   ├── WarvisRepository.kt
│   └── model/Models.kt
├── doom
│   ├── DoomShieldPolicy.kt
│   ├── DoomShieldAccessibilityService.kt
│   ├── DoomShieldSwitchLog.kt
│   ├── DoomShieldWeeklyReportManager.kt
│   ├── DoomShieldWeeklyReportReceiver.kt
│   └── DoomShieldInterventionActivity.kt
└── ui
    ├── WarvisApp.kt
    ├── components/WarvisComponents.kt
    ├── journey/JourneyMapScreen.kt
    ├── knowledge/KnowledgeRingsScreen.kt
    ├── sprint/SprintBoardScreen.kt
    ├── theme/
    └── today/TodayScreen.kt
```

## Core flow

1. `SeedData.initialState()` creates the default PE plan state.
2. `WarvisRepository` overlays persisted user progress from private preferences.
3. `WarvisApp` collects `StateFlow<WarvisState>`.
4. Screens render immutable state and call repository event handlers.

## MVP screens

- `TodayScreen` displays compliance progress, focus task, active stage, and sprint momentum.
- `SprintBoardScreen` displays Week 0 compliance and Weeks 1-4 tasks.
- `KnowledgeRingsScreen` displays five scoreable knowledge rings.
- `JourneyMapScreen` displays six career stages and supports manual advancement.

## Doom Shield

`DoomShieldAccessibilityService` listens for supported app and browser accessibility events. Native apps are matched by package name; supported browsers are scanned for local blocked domains.

Blocked native apps:

- Instagram (`com.instagram.android`)
- YouTube (`com.google.android.youtube`)

Blocked browser domains:

- `facebook.com`
- `m.facebook.com`
- `fb.com`
- `instagram.com`
- `www.instagram.com`
- `youtube.com`
- `www.youtube.com`
- `m.youtube.com`
- `youtu.be`

When a blocked target is detected, the service immediately opens Amazon Kindle. Android's package-visibility declaration allows WARVIS to locate Kindle's launch activity. If Kindle is unavailable, the service opens its Google Play listing instead.

Each successful Kindle handoff is recorded in app-private `SharedPreferences` with its timestamp and target key. `DoomShieldWeeklyReportManager` schedules an inexact repeating alarm for Sunday at 19:00 local time. The receiver compares Monday-to-Sunday handoffs with the preceding week, sends a progress notification, and removes events older than the comparison window.

The existing 30-second per-target cooldown prevents repeated accessibility events from creating duplicate redirects. The feature does not store full URLs and does not send browser text or event history to any server.

## Design tradeoff

Room/DataStore were part of the research recommendation, but the first build uses `SharedPreferences` behind `WarvisRepository` because the MVP state is tiny. The repository boundary keeps the storage implementation replaceable.
