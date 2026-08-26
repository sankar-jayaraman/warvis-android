# WARVIS Implementation Notes

**Last Updated:** 2026-06-10

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

When a blocked target is detected, it starts `DoomShieldInterventionActivity`, which lets the user open WARVIS, go Home, or continue for 5 minutes.

The 5-minute grace period is stored in app-private `SharedPreferences` keyed by blocked app or canonical domain. The feature does not store full URLs and does not send browser text to any server.

## Design tradeoff

Room/DataStore were part of the research recommendation, but the first build uses `SharedPreferences` behind `WarvisRepository` because the MVP state is tiny. The repository boundary keeps the storage implementation replaceable.
