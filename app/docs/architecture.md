# Android App Architecture

**Last Updated:** 2026-10-16

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
- `NightCurfewSchedule` / `NightCurfewPolicy` - Android-free immutable broad-curfew interval and classification logic.
- `NightCurfewRepository` - atomic SharedPreferences storage with validated snapshot reads and safe defaults.
- `InstagramGamingPolicy` / `InstagramGamingRepository` - pure paid-session state machine and
  atomic, versioned storage for the immutable purchase snapshot, bounded epoch/boot clock
  anchors, quarantine, and cumulative foreground duration.
- `DoomShieldInterventionActivity` - intervention screen launched when a blocked domain is detected.
- `DoomShieldServiceStatusReader` / `DoomShieldSettingsGateway` - Android boundary APIs for service status and Accessibility settings.
- `DisableAuthorizationState` - pure, short-lived, purpose-specific one-shot authorization for the WARVIS-originated disable route.
- `TaskRatchetClient` - HTTPS-only `/api2/me` validation and idempotent `/api2/me/charges` calls on `Dispatchers.IO`.
- `TaskRatchetCredentialStore` - Android Keystore AES/GCM encryption for the personal API key.
- `TaskRatchetAttemptRepository` / `TaskRatchetPaymentCoordinator` - process-wide serialized,
  account-and-credential-bound payment-attempt state and local benefit recovery.

## Persistence

`WarvisRepository` stores user progress in Android private `SharedPreferences`. This is local-only and scoped to the app sandbox.

Persisted keys:

- completed task IDs
- active stage ID
- knowledge-ring scores
- knowledge-ring update labels
- Doom Shield per-target grace-period timestamps
- Doom Shield broad-curfew enabled state and start/end minute-of-day values
- Instagram gaming configuration, immutable paid-session snapshot, epoch and same-boot elapsed
  session/quarantine deadlines, bounded last-observed clock metadata, cumulative foreground
  milliseconds, same-boot active-segment checkpoint metadata, and the granting TaskRatchet
  request ID
- TaskRatchet connection metadata, dry-run/live mode, and unresolved payment attempts

Disable authorizations are process-memory-only and are not stored with target entitlement keys.
The accessibility service reads a fresh validated curfew snapshot for each relevant event, so
schedule changes do not require restarting the service. Existing canonical targets are classified
before broad-curfew exemptions and remain blocked when the broad curfew is disabled or inactive.
For Instagram, quarantine is evaluated before the paid gaming session and generic 15-minute
target grace. An active gaming session allows only `app:com.instagram.android` and
`domain:instagram.com`. The service uses `elapsedRealtime()` only for live foreground deltas and
its generation-guarded, 15-second checkpoint timer. A persisted same-boot active segment is
settled on lifecycle callbacks; after abrupt process loss, at most one checkpoint interval is
recovered once, excluding longer unknown downtime. Same-boot elapsed deadlines survive process
recreation and take precedence over wall time. On reboot, backward clock adjustment and
implausible forward jumps preserve only the last trusted remaining duration, while plausible
forward evidence reduces it. The values are capped by the immutable purchase and re-anchored to
the new boot's elapsed clock, so the session and quarantine still expire without clock changes
immediately destroying or extending the purchase. Screen-off settles only the verified foreground
segment, pauses metering during sleep, and resumes after unlock only when the active window again
resolves to canonical Instagram.
The TaskRatchet API key is stored separately as AES/GCM ciphertext plus IV. The validated
TaskRatchet user ID and a random per-replacement credential generation bind pending attempts to
the exact account/key generation that created them. The AES key is
non-exportable Android Keystore material. TaskRatchet credential, configuration, and attempt
preferences are explicitly excluded from cloud backup and device transfer.

## Doom Shield privacy boundary

Doom Shield does not persist URLs. It matches native Instagram and YouTube app package names directly. For supported browsers, it recursively scans accessible node text, checks for a local blocked-domain match, and launches an intervention screen if matched.

If TaskRatchet confirms a target attempt, the app stores only the blocked target key and 15-minute
grace expiry timestamp in private app storage. Notes sent to TaskRatchet contain a bounded
canonical domain or package target, never the captured browser URL or path.

## Doom Shield enable and disable boundary

The Today card reads the actual Accessibility Service status and refreshes it whenever
`MainActivity` resumes. Enabling opens Android Accessibility settings directly. Disabling from
WARVIS requires a TaskRatchet confirmation and a visible 10-second cooldown. The amount is exactly
US$4.00 / 400 cents, displayed as "US$4.00 (roughly 25-28 DKK; bank conversion and FX fees may
vary)." Dry-run is the default. Live mode requires a validated API key, a payment method reported
by TaskRatchet, and a separate explicit acknowledgement covering immediate charging for all three
paid action scopes, monthly-limit implications, and FX variability.

Before any API call, WARVIS commits a purpose-and-target-scoped attempt with a stable request ID.
Retries, rotation, process recreation, timeouts, and lost responses reuse that ID. A successful
remote response is committed before the entitlement is created or Accessibility settings are
opened. If local follow-through fails, the confirmed attempt remains recoverable without a new
charge. The attempt is removed only after the intended benefit succeeds.

Instagram gaming uses a separate `InstagramGamingSession` TaskRatchet purpose and fixed canonical
target binding. The attempt stores a versioned immutable session ID plus duration/allowance/
quarantine snapshot, so later settings edits cannot change a pending or confirmed purchase.
Granting is idempotent by persisted TaskRatchet request ID; a remote-confirmed recovery can
materialize the same local benefit once without submitting another charge.

Disabling is indefinite until the user manually re-enables the service. It is not a target
entitlement, never starts `DoomShieldGracePeriod`, and does not unlock any target app.

This cannot be tamper-proof: after Android disables the Accessibility Service, WARVIS cannot
enforce policy or re-enable itself. Android Keystore protects the API key at rest, but an unlocked
or compromised device, malicious accessibility tooling, root, app tampering, force-stop, uninstall,
ADB, safe mode, and OEM-specific Settings paths remain risks or bypasses. There is no backend in
this version, so WARVIS cannot independently reconcile charges outside the idempotent local retry.
