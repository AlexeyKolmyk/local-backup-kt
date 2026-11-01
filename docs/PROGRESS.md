# LocalBackup — Current development status

This is a **short handoff for the next AI session**, not a historical log or duplicate of Git. Update only **after the developer manually verifies** the work. Never report a test/build as passed based solely on code generation. No commit hashes.

## Current step

- Step: 01 — Project setup and domain models
- Status: Completed, developer-verified
- Next: Step 02 — stream copier (`FileCopier`) with JVM tests (step file to be added by the developer)
- Actual exercise time elapsed: Not recorded yet

## Implemented

- Kotlin serialization plugin + `kotlinx-serialization-json` 1.9.0 via version catalog.
- `model/BackupModels.kt`: `BackupBatch`, `BackupFile`, `BackupStatus`, `BackupBatchStatus` as in `ARCHITECTURE.md`.
- `ui/BackupScreen.kt`: title + placeholder `Select files` button (no action); hosted by `MainActivity`.
- `.DS_Store` added to `.gitignore`.

## Developer-verified checks

- Build: Gradle sync and build succeeded (developer-reported)
- App launch: App launches on emulator, shows title and button (developer-reported)
- Manual behavior: Basic screen shown; no functional behavior in this step
- Unit tests: `./gradlew testDebugUnitTest` BUILD SUCCESSFUL (only template `ExampleUnitTest`)

## Known issues / deviations

- `android:allowBackup="true"` would include `filesDir/backups` in Android Auto Backup; plan to exclude it in backup/data-extraction rules when copying is added.

## Before committing each step (developer only)

- [ ] Compile/build project successfully.
- [ ] Launch app in emulator.
- [ ] Manually verify the step's acceptance criteria.
- [ ] Run all existing unit tests (or record explicitly why none exist/could run).
- [ ] Review changed code and update this file with **actual** outcomes.
- [ ] Commit code, tests and progress together.

The AI may implement code/tests and provide verification instructions, but must **not** run Gradle, adb, the emulator/app, or tests. See root `CLAUDE.md` for the mandatory verification gate: ask for the developer’s results, then update this file, then remind the developer to commit. Keep only current facts and next action here.
