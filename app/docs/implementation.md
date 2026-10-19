# Android App Implementation

**Last Updated:** 2026-10-16

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

The optional Instagram gaming section creates a dedicated US$4 TaskRatchet purchase rather than
reusing the generic 15-minute override. Defaults are a 180-minute overall session, 3 cumulative
foreground minutes between quarantines, and a 5-minute quarantine. Validated bounds are:
session `15..720`, allowance `1..30`, and quarantine `1..60` minutes. Session and quarantine
deadlines are start-inclusive/end-exclusive; reaching the allowance boundary blocks immediately.
All minute-to-millisecond conversions are checked and all persisted session data is committed as
one versioned SharedPreferences record.

While canonical native or browser Instagram is foreground, the accessibility service accumulates
`SystemClock.elapsedRealtime()` deltas and schedules one generation-guarded callback at the
earlier of the allowance/session boundary or a 15-second checkpoint. The atomic session record
stores the same-boot active-segment boot ID, start, latest checkpoint, and consumed-usage snapshot.
Lifecycle callbacks settle and clear the segment. After unexpected process loss, same-boot
recovery consumes that checkpoint exactly once and charges at most one 15-second tail; any longer
unknown downtime is excluded.
Transitions are derived from `rootInActiveWindow`, not the accessibility event's package, so
notification, keyboard, overlay, and background-package events cannot cancel a still-active
Instagram meter. Browser URL changes are reclassified from the active tree even within the same
browser package. Screen-off settles the current segment and clears the timer; unlock and service
recreation resume only after a fresh canonical active-window observation.

The atomic record persists the Android boot count plus elapsed session/quarantine deadlines.
Within one boot these monotonic deadlines override wall-clock changes. Across reboot, reconciliation
uses persisted trusted remaining durations. A backward wall adjustment preserves, but never
increases, those trusted values. Plausible forward progress subtracts elapsed epoch evidence.
Forward changes beyond six hours are treated as an untrusted clock jump and preserve the trusted
session/quarantine remainder rather than immediately destroying a purchase or shortening
quarantine. Re-anchoring to the new boot's elapsed clock then guarantees eventual expiry and is
always capped by the last trusted remainder and original configured duration. At quarantine, both
canonical forms remain blocked before paid-session or generic-grace checks.

`NightCurfewSchedule` and `NightCurfewPolicy` are Android-free immutable policy types. Minute-of-day
values are constrained to `0..1439`; enabled schedules support same-day and cross-midnight
start-inclusive/end-exclusive intervals. `NightCurfewRepository` reads one SharedPreferences
snapshot per accessibility event, falls back to the enabled `21:00-10:00` default for corrupt or
invalid persisted state, and writes the enabled/start/end tuple with one synchronous `commit()`.
The Night app exemptions screen edits the schedule with minute-precision time pickers and reports
validation or persistence failures without presenting unsaved state as successful.
