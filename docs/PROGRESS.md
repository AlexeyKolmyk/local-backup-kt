# LocalBackup — Current development status

This is a **short handoff for the next AI session**, not a historical log or duplicate of Git. Update only **after the developer manually verifies** the work. Never report a test/build as passed based solely on code generation. No commit hashes.

## Current step

- Step: 09 — Verify, document, submit
- Status: Completed. All steps 01–08 implemented; README written; final clean build + tests passed (developer-reported)
- Next: Developer commits README, step 09 file and this progress update; submission done
- Actual exercise time elapsed: about 3 hours (initial commit 14:14 CET to final docs commit; developer-confirmed)

## Implemented

- Step 01: serialization setup, `model/BackupModels.kt`, basic screen.
- Step 02: `copy/StreamCopy.kt` (`COPY_BUFFER_SIZE = 256 * 1024`), `copy/FileCopier.kt` (`.partial` -> fsync -> rename, cleanup on failure/cancellation), `copy/BackupStorage.kt` (`filesDir/backups/<batchId>/<fileId>_<safeName>`).
- Step 03: `backup/CreateBackupUseCase.kt` (sequential `PENDING -> COPYING -> SUCCESS/FAILED`, awaited saves, `CancellationException` rethrown, `COMPLETED` after all attempts), ports in `backup/BackupPorts.kt`, `copy/AppPrivateBackupFiles.kt`, `ui/BackupViewModel.kt` (single job, duplicate-start guard), `LocalBackupApplication` + `AppContainer` manual wiring.
- Step 04: `androidx.datastore:datastore` 1.1.7; `data/LatestBatchRecord.kt` + JSON `LatestBatchSerializer` (malformed -> `CorruptionException`); `data/DataStoreBatchStore.kt`; single DataStore instance; UI `StoredBatchState` Loading / Empty / Loaded / Error.
- Step 05: `backup/ReconcileBackupUseCase.kt` (only `IN_PROGRESS` batches; SUCCESS/FAILED preserved; PENDING/COPYING -> `.partial` deleted best effort, then INTERRUPTED unless finalized file size == known `expectedSize` -> SUCCESS; batch COMPLETED only if all SUCCESS/FAILED, else INTERRUPTED; probe errors -> INTERRUPTED). `DestinationProbe` port implemented by `AppPrivateBackupFiles`. `AppContainer.startupReconciliation` lazy `Deferred` in app scope = once per process; ViewModel shows Loading and keeps Select disabled until it finishes.
- Step 06: transient `FileProgress` from `CreateBackupUseCase` via `onProgress`, throttled to 150 ms in `BackupViewModel`; restrained blue theme (dynamic color off); 6 Material vector drawables in `res/drawable`; `BackupScreen` rebuilt as one `LazyColumn` (header, full-width Select, Loading / Empty / Error card / outlined summary card with processed count, current file, determinate-or-indeterminate bar capped at 99%, counts, start time; file rows with status icon, label, size, error, dividers); all text in `strings.xml`; previews for Empty / Copying / Results / Error.
- Step 07: `copy/BackupInventory.kt` (disk inventory of finalized copies under `backups/<batch>/`, excludes `.partial`/folders/symlinks/stray files; `delete(id)` with canonical-path check: only an existing finalized regular file directly in a batch dir of the root; removes emptied batch dir); `StoredBackupsState` in `BackupViewModel` refreshed after reconciliation, each batch and each delete; delete refused while a batch runs (ViewModel guard + disabled UI); "Stored backups" section with confirmation dialog; latest-batch records untouched.
- Step 08: `backup/UserCancellationException.kt` (+ `isUserCancellation()`); `CreateBackupUseCase` on cancellation runs a short `NonCancellable` section: keeps SUCCESS/FAILED, deletes active `.partial` via `DestinationProbe`, marks unfinished CANCELLED (user cancel) or INTERRUPTED (any other cancellation, e.g. Back closing the screen), batch CANCELLED/INTERRUPTED, or COMPLETED if nothing was left; per-file SUCCESS/FAILED written in `NonCancellable`; finalized full-size active file kept as SUCCESS. `BackupViewModel.onCancelBackup()` (repeat-tap/finish race guards, `isCancelling` until cleanup persisted). "Cancel backup" button inside the summary card under the progress bar.
- `backups/` and `datastore/latest_batch.json` excluded from Auto Backup / device transfer.
- Tests: `StreamCopyTest` (4), `CreateBackupUseCaseTest` (4), `LatestBatchSerializerTest` (3), `ReconcileBackupUseCaseTest` (7), `BackupInventoryTest` (7); Step 08 added 4 cancellation tests to `CreateBackupUseCaseTest` (replacing the old cancel test) and 1 to `StreamCopyTest`.

## Developer-verified checks

- Build: Succeeded (developer-reported)
- App launch: Launches on emulator (developer-reported)
- Manual behavior (Step 08): Cancel during `large_test.bin` -> "Backup cancelled", earlier files Success, bin Cancelled; Cancel shown under progress, delete icons disabled while copying; Stored backups list only finished copies; `adb` shows 0 `.partial` files and all copies' SHA-256 match originals; after force-stop/relaunch the batch still shows Cancelled (developer screenshots + `adb` output). Cancel very near completion: not checked.
- Manual behavior (Step 07): Deleted all stored backups via UI; `files/backups` empty afterwards. Not reported: dialog Cancel, source intact after delete, latest batch still SUCCESS after deleting its copy.
- Manual behavior (Step 06): Results screens verified by screenshots.
- Manual behavior (Step 05): force-stop mid-batch -> unfinished INTERRUPTED, earlier SUCCESS preserved, no `.partial`.
- Unit tests: `./gradlew testDebugUnitTest` passed (developer-reported)
- Final (Step 09): `./gradlew clean assembleDebug testDebugUnitTest` BUILD SUCCESSFUL (developer-reported; harmless "Unable to strip libandroidx.graphics.path.so, libdatastore_shared_counter.so" info). README AI-assistance disclosure confirmed by developer.

## Known issues / deviations

- Back button mid-copy cancels the job: now recorded as INTERRUPTED (Step 08); not manually re-checked.
- Summary counts INTERRUPTED/FAILED as "processed" (e.g. "3 of 3 files processed" on an interrupted batch); "1 of 1 files" wording not pluralized.
- Stored backups show the sanitized on-disk name (e.g. spaces become `_`), not the original display name.
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
