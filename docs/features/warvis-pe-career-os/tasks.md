## Tasks

**Feature:** WARVIS PE Career OS  
**Design:** `docs/features/warvis-pe-career-os/design.md`  
**Project config:** Not found; ADO, Memory Bank, and GitHub Projects are treated as OFF.  
**Workflow docs:** `.github/copilot/task-workflow.md` and `.github/copilot/documentation-organization.md` were not found in this greenfield project; use the PRD skill defaults.

### Phase 1: Greenfield Android Project Scaffold

**Status:** Completed  
**Progress:** 7/7 tasks complete (100%)  
**Phase Started:** 2026-06-07 17:43:00 UTC+2  
**Phase Completed:** 2026-06-07 17:58:00 UTC+2  

- [x] 1.0 Create project structure and Android build configuration
  - **Relevant Documentation:**
    - `docs/features/warvis-pe-career-os/design.md` - Feature goals, scope, architecture assumptions, and MVP requirements
  - [x] 1.1 Create root Gradle project files
    - Create `settings.gradle.kts`
    - Create root `build.gradle.kts`
    - Create `gradle.properties`
    - Create `.gitignore`
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 1.2 Create Android app module build files
    - Create `app/build.gradle.kts`
    - Create `app/src/main/AndroidManifest.xml`
    - Use package namespace `com.warvis.android`
    - Set minimum SDK to 26
    - Enable Jetpack Compose
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - **Parallel Group A** (after 1.1 and 1.2 complete):
    - [x] 1.3 Create application entry point
      - Create `MainActivity.kt`
      - Configure a single-activity Compose app
      - **Started:** 2026-06-07 17:43:00 UTC+2
      - **Completed:** 2026-06-07 17:58:00 UTC+2
      - **Duration:** Single implementation pass
    - [x] 1.4 Create Material 3 theme files
      - Create `ui/theme/Color.kt`
      - Create `ui/theme/Theme.kt`
      - Create `ui/theme/Type.kt`
      - **Started:** 2026-06-07 17:43:00 UTC+2
      - **Completed:** 2026-06-07 17:58:00 UTC+2
      - **Duration:** Single implementation pass
  - [x] 1.5 Wire initial `WarvisApp` shell
    - Create `ui/WarvisApp.kt`
    - Add bottom navigation destinations for Today, Sprint, Knowledge, and Journey
    - Add placeholder screen content so the app can render before feature screens are implemented
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 1.6 Create/update project documentation
    - Create/update `docs/architecture.md`
    - Create/update `docs/setup.md`
    - Create/update `docs/implementation.md`
    - Create/update `docs/CLAUDE.md`
    - Use flat files under `docs/`
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 1.7 Create phase completion summary
    - Create `docs/tasks/TASK-1.0-GREENFIELD-ANDROID-PROJECT-SCAFFOLD-COMPLETION-SUMMARY.md`
    - Include files created, build assumptions, and validation limitations
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass

### Phase 2: Domain Model, Seed Data, and State Repository

**Status:** Completed  
**Progress:** 8/8 tasks complete (100%)  
**Phase Started:** 2026-06-07 17:43:00 UTC+2  
**Phase Completed:** 2026-06-07 17:58:00 UTC+2  

- [x] 2.0 Implement deterministic app state and seed data
  - **Relevant Documentation:**
    - `docs/features/warvis-pe-career-os/design.md` - Data requirements, user stories, functional requirements, and technical considerations
    - `docs/architecture.md` - Project architecture after Phase 1 creates it
    - `docs/implementation.md` - Implementation conventions after Phase 1 creates it
  - [x] 2.1 Define domain models
    - Create models for `Task`, `SprintWeek`, `KnowledgeRing`, `CareerStage`, and `WarvisState`
    - Include enums for task status/category/priority and career stage status
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - **Parallel Group A** (after 2.1 completes):
    - [x] 2.2 Create seeded compliance and sprint task data
      - Add Week 0 compliance tasks
      - Add Weeks 1-4 sprint tasks
      - Include category, priority, source label, and estimated time
      - **Started:** 2026-06-07 17:43:00 UTC+2
      - **Completed:** 2026-06-07 17:58:00 UTC+2
      - **Duration:** Single implementation pass
    - [x] 2.3 Create seeded knowledge ring and journey data
      - Add five knowledge rings
      - Add six career stages
      - Mark Stage 3 as non-skippable operator credential
      - **Started:** 2026-06-07 17:43:00 UTC+2
      - **Completed:** 2026-06-07 17:58:00 UTC+2
      - **Duration:** Single implementation pass
  - [x] 2.4 Implement `WarvisRepository`
    - Expose app state as `StateFlow`
    - Implement task completion toggles
    - Implement knowledge score updates
    - Implement active-stage advancement
    - Compute compliance progress and weekly progress from task state
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 2.5 Implement focus-card selection logic
    - Select the first incomplete task by priority and sprint order
    - Ensure compliance tasks sort before external-career tasks
    - Return a completion state if all tasks are complete
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 2.6 Add unit-testable pure functions where possible
    - Keep progress and focus-selection logic independent from Android UI
    - Add lightweight JVM unit tests if the Gradle/Java toolchain is available
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 2.7 Create/update component documentation
    - Create/update `app/docs/architecture.md`
    - Create/update `app/docs/implementation.md`
    - Create/update `app/docs/testing.md`
    - Use flat files under `app/docs/`
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 2.8 Create phase completion summary
    - Create `docs/tasks/TASK-2.0-DOMAIN-MODEL-SEED-DATA-STATE-REPOSITORY-COMPLETION-SUMMARY.md`
    - Include model decisions, seeded data coverage, and test status
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass

### Phase 3: MVP Compose Screens

**Status:** Completed  
**Progress:** 9/9 tasks complete (100%)  
**Phase Started:** 2026-06-07 17:43:00 UTC+2  
**Phase Completed:** 2026-06-07 17:58:00 UTC+2  

- [x] 3.0 Build the four MVP screens and navigation
  - **Relevant Documentation:**
    - `docs/features/warvis-pe-career-os/design.md` - UI requirements, screen list, acceptance criteria, and non-goals
    - `docs/architecture.md` - Project architecture
    - `docs/implementation.md` - Implementation conventions
    - `app/docs/architecture.md` - App component architecture after Phase 2 creates it
    - `app/docs/implementation.md` - App implementation notes after Phase 2 creates it
  - [x] 3.1 Create shared UI components
    - Create reusable card, progress, section header, and status chip composables
    - Keep components stateless where possible
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - **Parallel Group A** (after 3.1 completes):
    - [x] 3.2 Build Today screen
      - Show compliance status
      - Show current career stage
      - Show one Focus Card
      - Show weekly momentum and north-star reminder
      - **Started:** 2026-06-07 17:43:00 UTC+2
      - **Completed:** 2026-06-07 17:58:00 UTC+2
      - **Duration:** Single implementation pass
    - [x] 3.3 Build Sprint Board screen
      - Show Week 0 plus Weeks 1-4
      - Show task checkboxes and week progress
      - Show compliance-dependent task cues
      - **Started:** 2026-06-07 17:43:00 UTC+2
      - **Completed:** 2026-06-07 17:58:00 UTC+2
      - **Duration:** Single implementation pass
    - [x] 3.4 Build Knowledge Rings screen
      - Show five rings with 1-5 score controls
      - Show suggested focus ring
      - Persist score changes through repository state
      - **Started:** 2026-06-07 17:43:00 UTC+2
      - **Completed:** 2026-06-07 17:58:00 UTC+2
      - **Duration:** Single implementation pass
    - [x] 3.5 Build Journey Map screen
      - Show six career stages
      - Highlight active stage
      - Show non-skippable warning on Stage 3
      - Support manual stage advancement with confirmation
      - **Started:** 2026-06-07 17:43:00 UTC+2
      - **Completed:** 2026-06-07 17:58:00 UTC+2
      - **Duration:** Single implementation pass
  - [x] 3.6 Integrate screens into `WarvisApp`
    - Wire navigation to real screen composables
    - Pass repository state and event handlers down to screens
    - Remove placeholder content
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 3.7 Run UI smoke validation
    - Confirm all four tabs render
    - Confirm task toggles update Today and Sprint Board
    - Confirm knowledge score updates render immediately
    - Confirm active-stage advancement works
    - If Android toolchain is unavailable, document validation limitation
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 3.8 Create/update component documentation
    - Update `app/docs/implementation.md` with screen structure and state flow
    - Update `app/docs/testing.md` with available validation steps
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 3.9 Create phase completion summary
    - Create `docs/tasks/TASK-3.0-MVP-COMPOSE-SCREENS-COMPLETION-SUMMARY.md`
    - Include screen behavior, UI limitations, and validation status
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass

### Phase 4: Validation, Polish, and Handoff

**Status:** Completed  
**Progress:** 6/6 tasks complete (100%)  
**Phase Started:** 2026-06-07 17:43:00 UTC+2  
**Phase Completed:** 2026-06-07 17:58:00 UTC+2  

- [x] 4.0 Validate MVP completeness and prepare handoff
  - **Relevant Documentation:**
    - `docs/features/warvis-pe-career-os/design.md` - Acceptance criteria and success metrics
    - `docs/features/warvis-pe-career-os/tasks.md` - Implementation checklist and task status
    - `docs/architecture.md` - Project architecture
    - `docs/setup.md` - Setup and run instructions
    - `docs/implementation.md` - Implementation overview
    - `app/docs/testing.md` - Validation notes
  - [x] 4.1 Review design acceptance criteria
    - Check each user story against implemented behavior
    - Update `design.md` if implementation decisions changed
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 4.2 Run available build/test commands
    - Run Gradle build if Java and Android SDK are available
    - Run unit tests if available
    - If unavailable, document exact missing prerequisites
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 4.3 Polish user-facing copy
    - Verify compliance copy is clear and serious without being alarming
    - Verify Today screen explains the next action clearly
    - Verify Stage 3 warning is visible and understandable
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 4.4 Update all relevant documentation
    - Ensure `docs/setup.md` explains prerequisites
    - Ensure `docs/implementation.md` points to the main code paths
    - Ensure `app/docs/testing.md` explains validation status
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 4.5 Create final completion summary
    - Create `docs/tasks/TASK-4.0-VALIDATION-POLISH-HANDOFF-COMPLETION-SUMMARY.md`
    - Include final status, known limitations, and next recommended steps
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass
  - [x] 4.6 Final task status update
    - Mark completed tasks in `tasks.md`
    - Update phase progress fields
    - Ensure incomplete validation work is marked honestly
    - **Started:** 2026-06-07 17:43:00 UTC+2
    - **Completed:** 2026-06-07 17:58:00 UTC+2
    - **Duration:** Single implementation pass

