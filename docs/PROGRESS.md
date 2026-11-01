# LocalBackup — Current development status

This is a **short handoff for the next AI session**, not a historical log or duplicate of Git. Update only **after the developer manually verifies** the work. Never report a test/build as passed based solely on code generation. No commit hashes.

## Current step

- Step: 03 — CreateBackupUseCase and file outcomes
- Status: Completed, developer-verified (see checks below)
- Next: Step 04 — persist latest batch with typed DataStore (`DataStoreBatchStore`) replacing `InMemoryBatchStore` (step file to be added by the developer)
- Actual exercise time elapsed: Not recorded yet

## Implemented

- Step 01: serialization setup, `model/BackupModels.kt`, basic screen.
- Step 02: `copy/StreamCopy.kt` (`COPY_BUFFER_SIZE = 256 * 1024`), `copy/FileCopier.kt` (`.partial` -> fsync -> rename, cleanup on failure/cancellation), `copy/BackupStorage.kt` (`filesDir/backups/<batchId>/<fileId>_<safeName>`), `backups/` excluded from Auto Backup / device transfer.
- `backup/BatchStore.kt`: `BatchStore` API (`latestBatch: Flow`, atomic `update {}`) + TEMPORARY `InMemoryBatchStore` (to be replaced in Step 04, not kept alongside).
- `backup/BackupPorts.kt`: `BackupSources`, `BackupDestinations`, `BackupCopier`, `SourceInfo` so the use case works on URI strings and is JVM-testable.
- `backup/CreateBackupUseCase.kt`: prepare batch dir, plan files, await initial `IN_PROGRESS` save, sequential `PENDING -> COPYING -> SUCCESS/FAILED`, path-free error messages, `CancellationException` rethrown, batch `COMPLETED` after all attempts.
- `copy/AppPrivateBackupFiles.kt` (SAF metadata + `Uri.fromFile` destinations), `copy/SourceInfoQuery.kt`; `FileCopier` implements `BackupCopier`.
- `ui/BackupViewModel.kt`: single active `Job`, duplicate-start guard, `BackupUiState` from store flow + running/error state. `BackupScreen` renders state only (stateless `BackupContent` + preview).
- `LocalBackupApplication` + `AppContainer` manual wiring (registered in manifest).
- Dependencies: `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` (lifecycle 2.11.0).
- Tests: `StreamCopyTest` (4), `CreateBackupUseCaseTest` (4: failure on B does not stop C, cancellation propagates and is not FAILED, unique destinations for equal names, empty selection).

## Developer-verified checks

- Build: Succeeded after Gradle sync (developer-reported)
- App launch: Launches on emulator (developer-reported)
- Manual behavior: Files copied sequentially; Select button disabled while running; rotation mid-copy keeps copying (developer-reported)
- Unit tests: `./gradlew testDebugUnitTest` BUILD SUCCESSFUL; `CreateBackupUseCaseTest` also passed in Android Studio (developer-reported)

## Known issues / deviations

- Batch results are in memory only until Step 04 (lost on process restart).
- Added small port interfaces + `AppContainer` (manual DI) to keep the use case Android-free; no DI framework.
- Old batch folders are not cleaned up (by design; future work).

## Before committing each step (developer only)

- [ ] Compile/build project successfully.
- [ ] Launch app in emulator.
- [ ] Manually verify the step's acceptance criteria.
- [ ] Run all existing unit tests (or record explicitly why none exist/could run).
- [ ] Review changed code and update this file with **actual** outcomes.
- [ ] Commit code, tests and progress together.

The AI may implement code/tests and provide verification instructions, but must **not** run Gradle, adb, the emulator/app, or tests. See root `CLAUDE.md` for the mandatory verification gate: ask for the developer’s results, then update this file, then remind the developer to commit. Keep only current facts and next action here.
