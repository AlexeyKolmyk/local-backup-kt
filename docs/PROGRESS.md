# LocalBackup — Current development status

This is a **short handoff for the next AI session**, not a historical log or duplicate of Git. Update only **after the developer manually verifies** the work. Never report a test/build as passed based solely on code generation. No commit hashes.

## Current step

- Step: 06 — Clean Material 3 screen and progress (Cancel not implemented yet)
- Status: Completed, developer-verified
- Next: Optional Cancel (also fixes Back-button gap) or Step 07 review/README, developer to decide
- Actual exercise time elapsed: Not recorded yet

## Implemented

- Step 01: serialization setup, `model/BackupModels.kt`, basic screen.
- Step 02: `copy/StreamCopy.kt` (`COPY_BUFFER_SIZE = 256 * 1024`), `copy/FileCopier.kt` (`.partial` -> fsync -> rename, cleanup on failure/cancellation), `copy/BackupStorage.kt` (`filesDir/backups/<batchId>/<fileId>_<safeName>`).
- Step 03: `backup/CreateBackupUseCase.kt` (sequential `PENDING -> COPYING -> SUCCESS/FAILED`, awaited saves, `CancellationException` rethrown, `COMPLETED` after all attempts), ports in `backup/BackupPorts.kt`, `copy/AppPrivateBackupFiles.kt`, `ui/BackupViewModel.kt` (single job, duplicate-start guard), `LocalBackupApplication` + `AppContainer` manual wiring.
- Step 04: `androidx.datastore:datastore` 1.1.7; `data/LatestBatchRecord.kt` + JSON `LatestBatchSerializer` (malformed -> `CorruptionException`); `data/DataStoreBatchStore.kt`; single DataStore instance; UI `StoredBatchState` Loading / Empty / Loaded / Error.
- Step 05: `backup/ReconcileBackupUseCase.kt` (only `IN_PROGRESS` batches; SUCCESS/FAILED preserved; PENDING/COPYING -> `.partial` deleted best effort, then INTERRUPTED unless finalized file size == known `expectedSize` -> SUCCESS; batch COMPLETED only if all SUCCESS/FAILED, else INTERRUPTED; probe errors -> INTERRUPTED). `DestinationProbe` port implemented by `AppPrivateBackupFiles`. `AppContainer.startupReconciliation` lazy `Deferred` in app scope = once per process; ViewModel shows Loading and keeps Select disabled until it finishes.
- Step 06: transient `FileProgress` from `CreateBackupUseCase` via `onProgress`, throttled to 150 ms in `BackupViewModel`; restrained blue theme (dynamic color off); 6 Material vector drawables in `res/drawable`; `BackupScreen` rebuilt as one `LazyColumn` (header, full-width Select, Loading / Empty / Error card / outlined summary card with processed count, current file, determinate-or-indeterminate bar capped at 99%, counts, start time; file rows with status icon, label, size, error, dividers); all text in `strings.xml`; previews for Empty / Copying / Results / Error.
- `backups/` and `datastore/latest_batch.json` excluded from Auto Backup / device transfer.
- Tests: `StreamCopyTest` (4), `CreateBackupUseCaseTest` (4), `LatestBatchSerializerTest` (3), `ReconcileBackupUseCaseTest` (7).

## Developer-verified checks

- Build: Succeeded (developer-reported)
- App launch: Launches on emulator (developer-reported)
- Manual behavior (Step 06): Results screens verified by screenshots — interrupted batch (2 Success, 1 Interrupted, summary card, icons, sizes) and completed batch with a long file name ellipsized; developer judged UI "looks good". Copying/progress-bar, Empty and Error screens not captured in screenshots.
- Manual behavior (Step 05): force-stop mid-batch -> unfinished files INTERRUPTED, batch INTERRUPTED, earlier SUCCESS preserved, no `.partial` left (developer screenshots + `adb` output).
- Unit tests: `./gradlew testDebugUnitTest` BUILD SUCCESSFUL (developer-reported)

## Known issues / deviations

- Back button mid-copy finishes the activity -> ViewModel job cancelled while the process lives; reconciliation already ran in this process, so the batch stays `IN_PROGRESS`/`COPYING` until the next process start. Planned fix: record unfinished items in a short `NonCancellable` block on cancellation (together with optional Cancel).
- Summary counts INTERRUPTED/FAILED as "processed" (e.g. "3 of 3 files processed" on an interrupted batch); "1 of 1 files" wording not pluralized.
- Store property named `latestBatch` (step text says `lastBatch`).
- Corrupted metadata requires clearing app data to recover (no reset action in UI).
- Old batch folders / orphaned copies are not cleaned up (by design; future work).

## Before committing each step (developer only)

- [ ] Compile/build project successfully.
- [ ] Launch app in emulator.
- [ ] Manually verify the step's acceptance criteria.
- [ ] Run all existing unit tests (or record explicitly why none exist/could run).
- [ ] Review changed code and update this file with **actual** outcomes.
- [ ] Commit code, tests and progress together.

The AI may implement code/tests and provide verification instructions, but must **not** run Gradle, adb, the emulator/app, or tests. See root `CLAUDE.md` for the mandatory verification gate: ask for the developer’s results, then update this file, then remind the developer to commit. Keep only current facts and next action here.
