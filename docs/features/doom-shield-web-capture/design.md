# WARVIS Doom Shield Web Capture - Design

**Last Updated:** 2026-06-10  
**Status:** Approved for implementation  
**Feature:** Doom Shield - capture navigation to distracting websites

## 1. Introduction / Overview

Doom Shield helps the user break doom-scrolling loops that block PE career progress. It detects when the user opens distracting native apps such as Instagram or YouTube, or distracting websites such as Facebook, Instagram, or YouTube in a supported mobile browser, and immediately shows a WARVIS intervention screen.

Android does not let normal apps monitor other apps or browser URLs. The supported approach is an **Accessibility Service** that the user explicitly enables in Android Settings. Once enabled, WARVIS can observe browser window content and look for blocked domains locally on the device.

## 2. Goals

1. Detect native app launches for Instagram and YouTube.
2. Detect browser navigation to blocked domains such as `facebook.com`, `instagram.com`, and `youtube.com`.
3. Intervene immediately by launching a WARVIS screen with a clear anti-doom-scroll prompt.
4. Let the user continue for 5 minutes, then intervene again if the same target is still being used.
5. Keep detection local-only; do not send browsing content anywhere.
6. Make the feature opt-in through Android Accessibility settings.
7. Avoid collecting or storing browsing history.

## 3. User Stories

### US-01: Intercept distracting apps and websites

As the user, I want WARVIS to notice when I open Instagram, YouTube, Facebook, Instagram web, or YouTube web so that I can stop before losing time.

**Acceptance criteria:**
- WARVIS includes an Accessibility Service visible in Android Accessibility settings.
- When enabled, the service listens for active app and browser content changes.
- If the foreground package is Instagram or YouTube, WARVIS opens an intervention Activity.
- If visible browser text contains a blocked domain, WARVIS opens an intervention Activity.

### US-02: Get a career-aligned intervention

As the user, I want the intervention to remind me that doom scrolling is blocking my PE career goals.

**Acceptance criteria:**
- The intervention screen names the detected blocked domain.
- The intervention screen has actions to open WARVIS, go Home, or continue for 5 minutes.
- The intervention uses calm but direct copy.

### US-04: Continue briefly, then re-intervene

As the user, I want an intentional short override when I choose to continue so that WARVIS gives me agency without letting the scroll loop become open-ended.

**Acceptance criteria:**
- Tapping **Continue for 5 minutes** starts a 5-minute grace period for that same app or canonical domain.
- During the grace period, WARVIS does not immediately re-open the intervention for that target.
- After the grace period expires, WARVIS can intervene again if the target is still active or reopened.

### US-03: Keep browsing private

As the user, I want WARVIS to protect my privacy while detecting these sites.

**Acceptance criteria:**
- WARVIS does not store full URLs.
- WARVIS does not send browser text or URL data to a server.
- WARVIS only matches local blocked-domain strings.

## 4. Functional Requirements

1. Add an Android `AccessibilityService` named `DoomShieldAccessibilityService`.
2. Add an accessibility service XML config under `res/xml`.
3. Register the service in `AndroidManifest.xml` with `android.permission.BIND_ACCESSIBILITY_SERVICE`.
4. Inspect active browser windows for blocked-domain text.
5. Detect native app packages:
   - Instagram: `com.instagram.android`
   - YouTube: `com.google.android.youtube`
6. Support common browser packages, including Chrome, Samsung Internet, Firefox, Edge, Brave, Opera, and DuckDuckGo.
7. Match at least these domains:
   - `facebook.com`
   - `m.facebook.com`
   - `fb.com`
   - `instagram.com`
   - `www.instagram.com`
   - `youtube.com`
   - `www.youtube.com`
   - `m.youtube.com`
   - `youtu.be`
8. Launch `DoomShieldInterventionActivity` when a blocked app or domain is detected.
9. Add a short cooldown to avoid repeated intervention spam while the same page remains visible.
10. When the user selects **Continue for 5 minutes**, store a local per-target grace period and suppress intervention until it expires.
11. Add a Today-screen card explaining how to enable Doom Shield in Accessibility settings.
12. Build successfully with `.\gradlew.bat :app:assembleDebug`.

## 5. Non-Goals / Out of Scope

1. This version will not block the native Facebook app by package name.
2. This version will not use VPN/DNS blocking.
3. This version will not use overlay permission.
4. This version will not store browsing history.
5. This version will not sync blocked domains to a server.
6. This version will not expose a configurable domain list UI.

## 6. Design Considerations

### Intervention tone

The intervention should be direct:

> "Doom Shield caught facebook.com. This is the loop that steals your PE sprint time."

But it should not shame the user. It should offer a way back to WARVIS and an explicit override.

### Privacy copy

The Today screen should state that the feature requires Accessibility permission and that URL/domain matching happens on-device.

## 7. Technical Considerations

Accessibility URL detection is best-effort. Browsers differ in how they expose URL bar text to accessibility. WARVIS should scan the accessibility node tree for blocked domains and avoid storing any raw text.

The service should ignore events from WARVIS itself to avoid loops after launching the intervention screen.

The 5-minute grace period should be app-private and keyed by blocked app package or canonical domain. It should not contain full URLs.

## 8. Success Metrics

1. WARVIS exposes a Doom Shield Accessibility Service in Android Settings.
2. WARVIS builds successfully.
3. When Instagram or YouTube is opened, WARVIS launches the intervention screen.
4. When a supported browser exposes a blocked domain in accessible text, WARVIS launches the intervention screen.
5. Continue for 5 minutes suppresses the same target until the timer expires.
6. The user can return to WARVIS from the intervention.

## 9. Open Questions

1. Should a future version add native app package blocking for Facebook?
2. Should a future version allow configurable blocked domains and focus schedules?
3. Should a future version use a stronger intervention such as automatic Home navigation?
