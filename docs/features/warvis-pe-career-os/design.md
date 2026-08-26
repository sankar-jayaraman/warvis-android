# WARVIS PE Career OS - Design

**Last Updated:** 2026-06-07  
**Status:** Approved for implementation  
**Feature:** WARVIS Feature #1 - PE Career-Switch Operating System  

## 1. Introduction / Overview

WARVIS is a personal Android app that turns the user's private equity career-switch plan into a daily execution system. The first feature, **PE Career OS**, converts the latest v4 career documents into a local-first app with four MVP screens:

1. **Today** - Shows the current stage, compliance status, one focus task, and weekly momentum.
2. **Sprint Board** - Tracks the compliance gate and 4-week sprint checklist.
3. **Knowledge Rings** - Tracks self-assessed progress across five knowledge areas.
4. **Journey Map** - Shows the six-stage PE career path and highlights the active stage.

The feature solves the problem of static planning documents becoming hard to execute. Instead of rereading five Markdown files to decide what to do next, the user opens WARVIS and immediately sees the next action.

This is a greenfield project at `C:\Users\SNKR\WARVIS`. No existing WARVIS codebase was found. No `.github/copilot/project-config.md` file was found, so there are no ADO or GitHub Project integration requirements.

## 2. Goals

1. Provide a usable Android MVP for the PE career-switch plan with no backend and no cloud sync.
2. Seed the app with the core content from the v4 planning documents:
   - 6 career stages.
   - Week 0 compliance checklist.
   - 4-week sprint tasks.
   - 5 knowledge rings.
   - Key north-star and compliance messaging.
3. Let the user mark sprint/compliance tasks complete.
4. Let the user rate each knowledge ring from 1 to 5 and store the latest score.
5. Always make the compliance gate visible before expert-network or external-career tasks.
6. Keep all data local to the device.
7. Build with a simple Android architecture suitable for a solo developer and easy for a junior developer to extend.

## 3. User Stories

### US-01: See today's focus

As the user, I want to open WARVIS and see one highest-priority task so that I can act immediately without rereading the source documents.

**Acceptance criteria:**
- The Today screen displays exactly one Focus Card.
- The Focus Card chooses the first incomplete task by priority and sprint order.
- Compliance tasks appear before expert-network and external-career tasks.
- The card includes title, category, estimated time, and a completion action.
- Completing the task refreshes the Focus Card.

### US-02: Track the compliance gate

As the user, I want the app to show the SimCorp/employer compliance checklist before expert-network registration so that I avoid accidental conflicts of interest.

**Acceptance criteria:**
- The Sprint Board includes a **Week 0: Compliance Gate** section.
- Compliance items include contract review, Deutsche Borse policy review, forbidden topics, manager/P&C approval, expert-profile scoping, per-call conduct rules, anti-solicitation review, and PI insurance.
- Today screen shows compliance progress until all compliance tasks are complete.
- External-career tasks visually show when they depend on compliance clearance.

### US-03: Work through the 4-week sprint

As the user, I want a checklist organized by week so that I can execute the positioning sprint from the v4 plan.

**Acceptance criteria:**
- The Sprint Board displays Week 0 plus Weeks 1-4.
- Each week displays tasks grouped by category.
- Each task has a checkbox/status, estimated time, and source label.
- Each week shows completion count and percentage.
- Completion state persists after app restart.

### US-04: Track knowledge ring progress

As the user, I want to rate the five knowledge gaps weekly so that I can see where I need to focus.

**Acceptance criteria:**
- The Knowledge Rings screen displays five rings:
  1. Investment Management Operations.
  2. Regulatory Environment.
  3. Financial Modelling.
  4. PE Process and Mechanics.
  5. AI in Financial Services.
- Each ring shows current score from 1 to 5.
- The user can update a score.
- The app stores the last-updated timestamp.
- The screen highlights the lowest-score ring as the suggested focus.

### US-05: Understand the PE journey stage

As the user, I want to see the six-stage career path so that I know where I am and what comes next.

**Acceptance criteria:**
- The Journey Map displays all six stages:
  1. Positioning + Expert Networks.
  2. First TDD Projects.
  3. Portco CTO / VP Eng / AI Lead.
  4. Regular TDD + Fractional OP.
  5. Full Operating Partner.
  6. Scout / Angel Investor.
- Exactly one stage is active.
- Stage 3 displays a "Non-skippable operator credential" warning.
- The active stage can be advanced manually with confirmation.

## 4. Functional Requirements

1. The app must run as a native Android app.
2. The app must use a single-activity Compose UI.
3. The app must store seeded and user-progress data locally.
4. The app must seed default data on first launch.
5. The app must not require user login.
6. The app must not require internet access for MVP functionality.
7. The app must show bottom navigation with Today, Sprint, Knowledge, and Journey destinations.
8. The Today screen must calculate a Focus Card from incomplete tasks.
9. The Today screen must show compliance progress and weekly completion progress.
10. The Sprint Board must show Week 0 compliance and Weeks 1-4 sprint tasks.
11. The Sprint Board must allow marking tasks complete/incomplete.
12. The Knowledge Rings screen must allow editing scores from 1 to 5.
13. The Knowledge Rings screen must compute the suggested focus ring.
14. The Journey Map must display career stages and support manual advancement.
15. The app must preserve user progress after process death and app restart.
16. The app must include source labels for seeded items so the user knows they came from the v4 plan.

## 5. Non-Goals / Out of Scope

The MVP will not include:

1. LLM chat or AI/RAG assistant.
2. Cloud sync.
3. User accounts or authentication.
4. Financial scenario calculator.
5. Full reading-list tracker.
6. Expert-call CRM.
7. Calendar integration.
8. Push notifications.
9. Home-screen widgets.
10. LinkedIn content generation.
11. Multi-user support.
12. Automated import from OneDrive or SharePoint.
13. App Store / Play Store release packaging.

These may be added in later versions after the core workflow is useful.

## 6. Design Considerations

### Navigation

Use a Material 3 bottom navigation bar with four tabs:

- Today
- Sprint
- Knowledge
- Journey

### Tone

The app should feel like a calm chief of staff, not a generic productivity app. Compliance copy should be clear and serious, but not alarming.

### Compliance visibility

Compliance must stay visible until completed. The user should not be able to forget that external activities depend on employer approval and anti-solicitation review.

### Visual hierarchy

The Today screen should prioritize:

1. Compliance status.
2. One Focus Card.
3. Current stage.
4. Weekly progress.
5. North-star reminder.

## 7. Technical Considerations

### Greenfield findings

- No existing WARVIS project was found.
- No `.github/copilot/project-config.md` file was found.
- Java 17, Android SDK command-line tools, platform-tools, SDK Platform 35, Build Tools, and local Gradle 8.10.2 are installed for the user.
- The full Android Studio IDE installer was attempted through `winget`, but it required administrator elevation and did not complete in the CLI session.
- The project builds from the command line with `.\gradlew.bat :app:assembleDebug`.

### Architecture

Use a simple single-module Android project:

```text
com.warvis.android
├── MainActivity.kt
├── data
│   ├── SeedData.kt
│   ├── WarvisRepository.kt
│   └── model
├── ui
│   ├── WarvisApp.kt
│   ├── today
│   ├── sprint
│   ├── knowledge
│   └── journey
└── theme
```

For the first implementation pass, use a `StateFlow` repository with deterministic seed data and Android private `SharedPreferences` for the small user-progress state. This keeps the MVP simple while preserving completed tasks, knowledge scores, and active stage across app restarts. Room/DataStore can replace the repository internals in the next iteration without changing the UI contract.

### Recommended stack

- Kotlin
- Jetpack Compose
- Material 3
- Android Gradle Plugin
- Gradle wrapper
- Minimum SDK: 26
- Target SDK: latest available

### Persistence

The design target is local persistence. The first implementation uses Android private `SharedPreferences` behind a repository abstraction because the MVP state is tiny. Replace this with Room/DataStore when persistence requirements grow beyond simple user-progress fields.

## 8. Success Metrics

1. App opens to Today screen and shows a Focus Card.
2. User can complete a compliance task and see progress update.
3. User can complete sprint tasks and see weekly progress update.
4. User can update all five knowledge-ring scores.
5. User can view the six-stage journey and see Stage 3 marked as non-skippable.
6. All MVP UI screens are reachable from bottom navigation.
7. The project has a clear structure that a junior developer can extend.

## 9. Open Questions

1. Which exact Android SDK version will be installed locally for building and running the app?
2. Should the next implementation pass prioritize real Room/DataStore persistence or a polished UI prototype first?
3. Should the app later import live Markdown files from OneDrive, or should seed data remain hard-coded and versioned inside the app?

For this build, assume:

- Project path: `C:\Users\SNKR\WARVIS`.
- Package name: `com.warvis.android`.
- MVP first, no AI, no backend.
- Seed data is hard-coded from the v4 plan.
