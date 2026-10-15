# Android App Implementation

**Last Updated:** 2026-10-15

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

The career-plan UI is intentionally deterministic:

- No LLM calls.
- No accounts.
- No external storage.

TaskRatchet is the only network boundary. Its client uses `HttpURLConnection` on `Dispatchers.IO`,
HTTPS-only URLs, bounded timeouts, typed result mapping, and never logs or returns the API key.

Each screen receives `WarvisState` and callbacks. Screens do not mutate state directly.

## Doom Shield implementation

`DoomShieldAccessibilityService` detects:

- Instagram app package: `com.instagram.android`
- YouTube app package: `com.google.android.youtube`
- X app package: `com.twitter.android`
- Quora app package: `com.quora.android`
- Browser domains: `facebook.com`, `instagram.com`, `youtube.com`, `x.com`, `twitter.com`, `quora.com`, and common mobile/short aliases

`DoomShieldPolicy` centralizes blocked targets and the 15-minute target entitlement. TaskRatchet
configuration defaults to dry-run. The coordinator persists an idempotent attempt before calling
TaskRatchet, persists remote success before granting an entitlement or disable authorization, and
keeps confirmed attempts for recovery when local follow-through fails.
