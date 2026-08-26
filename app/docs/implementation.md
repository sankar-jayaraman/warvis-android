# Android App Implementation

**Last Updated:** 2026-06-10

## State model

`WarvisState` contains:

- `tasks`
- `knowledgeRings`
- `careerStages`

It also computes:

- `currentStage`
- `focusTask`
- `complianceProgress`
- `sprintProgress`
- `suggestedFocusRing`
- per-week progress

## Seed data

`SeedData` includes:

- 8 Week 0 compliance tasks.
- 17 sprint tasks across Weeks 1-4.
- 5 knowledge rings.
- 6 career stages.

## UI implementation

The UI is intentionally deterministic:

- No LLM calls.
- No network calls.
- No accounts.
- No external storage.

Each screen receives `WarvisState` and callbacks. Screens do not mutate state directly.

## Doom Shield implementation

`DoomShieldAccessibilityService` detects:

- Instagram app package: `com.instagram.android`
- YouTube app package: `com.google.android.youtube`
- Browser domains: `facebook.com`, `instagram.com`, `youtube.com`, and common mobile/short aliases

`DoomShieldPolicy` centralizes blocked targets and the 5-minute grace-period persistence used when the user chooses **Continue for 5 minutes**.
