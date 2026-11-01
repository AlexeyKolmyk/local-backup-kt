# LocalBackup — Current development status

This is a **short handoff for the next AI session**, not a historical log or duplicate of Git. Update only **after the developer manually verifies** the work. Never report a test/build as passed based solely on code generation. No commit hashes.

## Current step

- Step: 02 — Native picker and real buffered copy
- Status: Completed, developer-verified
- Next: Step 03 — `CreateBackupUseCase` + `BackupViewModel`; move the temporary copy loop out of `BackupScreen` (step file to be added by the developer)
- Actual exercise time elapsed: Not recorded yet

## Implemented

- Step 01: serialization setup, `model/BackupModels.kt`, basic `BackupScreen`.
- `copy/StreamCopy.kt`: `COPY_BUFFER_SIZE = 256 * 1024`, framework-free `copyStream()` with `ensureActive()` before each read and optional progress callback.
- `copy/FileCopier.kt`: one-file copy on `Dispatchers.IO` via `<dest>.partial` -> fsync/close -> rename; partial deleted on any failure/cancellation, exception rethrown.
- `copy/SourceDocument.kt`: `DISPLAY_NAME` / nullable `SIZE` query with fallbacks.
- `copy/BackupStorage.kt`: `filesDir/backups/<batchId>/<fileId>_<safeName>`.
- `BackupScreen`: `OpenMultipleDocuments` (`*/*`) + TEMPORARY sequential loop in `rememberCoroutineScope` showing OK/FAILED per file (to be replaced in Step 03; rotation mid-copy cancels it).
- `kotlinx-coroutines-android` 1.10.2 added.
- `backups/` excluded from Auto Backup / device transfer (`backup_rules.xml`, `data_extraction_rules.xml`).
- JVM tests `StreamCopyTest`: byte equality (~1 MiB, multi-chunk), max read request <= `COPY_BUFFER_SIZE`, empty source, final progress value.

## Developer-verified checks

- Build: Succeeded (developer-reported)
- App launch: Launches on emulator (developer-reported)
- Manual behavior: Multi-select of 3 files (pdf 24 MB, mp4 227 MB, bin 472 MB) and single-file runs (jpg, pdf) copied; SHA-256 of all 8 backups matches the originals in `/sdcard/Download`; no `.partial` files left (developer-reported `adb` output)
- UI responsiveness during large copy: copy was fast; progress shown; not explicitly stress-checked
- Unit tests: `./gradlew testDebugUnitTest` passed (developer-reported)

## Known issues / deviations

- Temporary copy loop lives in the UI layer until Step 03.
- Old batch folders are not cleaned up (by design; future work).

## Before committing each step (developer only)

- [ ] Compile/build project successfully.
- [ ] Launch app in emulator.
- [ ] Manually verify the step's acceptance criteria.
- [ ] Run all existing unit tests (or record explicitly why none exist/could run).
- [ ] Review changed code and update this file with **actual** outcomes.
- [ ] Commit code, tests and progress together.

The AI may implement code/tests and provide verification instructions, but must **not** run Gradle, adb, the emulator/app, or tests. See root `CLAUDE.md` for the mandatory verification gate: ask for the developer’s results, then update this file, then remind the developer to commit. Keep only current facts and next action here.
