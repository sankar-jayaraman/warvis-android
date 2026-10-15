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
- `DoomShieldPolicy` - local target definitions, nighttime safety exemptions, and 15-minute target-entitlement policy.
- `DoomShieldInterventionActivity` - intervention screen launched when a blocked domain is detected.
- `DoomShieldServiceStatusReader` / `DoomShieldSettingsGateway` - Android boundary APIs for service status and Accessibility settings.
- `DisableAuthorizationState` - pure, short-lived, purpose-specific one-shot authorization for the WARVIS-originated disable route.

## Persistence

`WarvisRepository` stores user progress in Android private `SharedPreferences`. This is local-only and scoped to the app sandbox.

Persisted keys:

- completed task IDs
- active stage ID
- knowledge-ring scores
- knowledge-ring update labels
- Doom Shield per-target grace-period timestamps

Disable authorizations are process-memory-only and are not stored with target entitlement keys.

## Doom Shield privacy boundary

Doom Shield does not persist URLs. It matches native Instagram and YouTube app package names directly. For supported browsers, it recursively scans accessible node text, checks for a local blocked-domain match, and launches an intervention screen if matched.

If the user confirms a target dry run, the app stores only the blocked target key and 15-minute
grace expiry timestamp in private app storage.

## Doom Shield enable and disable boundary

The Today card reads the actual Accessibility Service status and refreshes it whenever
`MainActivity` resumes. Enabling opens Android Accessibility settings directly. Disabling from
WARVIS requires a simulated 25 kr commitment, an explicit no-charge disclosure, and a visible
10-second cooldown. Confirmation issues a 30-second, purpose-specific authorization that the
settings boundary consumes once before attempting component-specific Accessibility details, with
a generic Accessibility-settings fallback.

Disabling is indefinite until the user manually re-enables the service. It is not a target
entitlement, never starts `DoomShieldGracePeriod`, and does not unlock any target app.

This cannot be tamper-proof: after Android disables the Accessibility Service, WARVIS cannot
enforce policy or re-enable itself. The protected route covers only disable actions initiated in
WARVIS. Force-stop, uninstall, ADB, safe mode, and OEM-specific Settings paths remain bypasses.
Future robust enforcement requires a backend heartbeat plus real payment/authorization.
