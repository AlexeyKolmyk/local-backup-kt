# LocalBackup — Current development status

This is a **short handoff for the next AI session**, not a historical log or duplicate of Git. Update only **after the developer manually verifies** the work. Never report a test/build as passed based solely on code generation. No commit hashes.

## Current step

- Step: 04 — Typed DataStore and latest batch
- Status: Completed, developer-verified
- Next: Step 05 — `ReconcileBackupUseCase` on startup for unfinished `IN_PROGRESS` batches (step file to be added by the developer)
- Actual exercise time elapsed: Not recorded yet

## Implemented

- Step 01: serialization setup, `model/BackupModels.kt`, basic screen.
- Step 02: `copy/StreamCopy.kt` (`COPY_BUFFER_SIZE = 256 * 1024`), `copy/FileCopier.kt` (`.partial` -> fsync -> rename, cleanup on failure/cancellation), `copy/BackupStorage.kt` (`filesDir/backups/<batchId>/<fileId>_<safeName>`).
- Step 03: `backup/CreateBackupUseCase.kt` (sequential `PENDING -> COPYING -> SUCCESS/FAILED`, awaited saves, `CancellationException` rethrown, `COMPLETED` after all attempts), port interfaces in `backup/BackupPorts.kt`, `copy/AppPrivateBackupFiles.kt`, `ui/BackupViewModel.kt` (single job, duplicate-start guard), `LocalBackupApplication` + `AppContainer` manual wiring.
- Step 04: `androidx.datastore:datastore` 1.1.7; `data/LatestBatchRecord.kt` (`LatestBatchRecord(latestBatch: BackupBatch? = null)` + JSON `LatestBatchSerializer`, malformed data -> `CorruptionException`); `data/DataStoreBatchStore.kt` (`updateData` transforms current value); single DataStore instance in `AppContainer` (`files/datastore/latest_batch.json`).
- UI: `StoredBatchState` Loading / Empty / Loaded / Error; Select disabled while loading, running, or on read error.
- `InMemoryBatchStore` now exists only as a test fake.
- `backups/` and `datastore/latest_batch.json` excluded from Auto Backup / device transfer.
- Tests: `StreamCopyTest` (4), `CreateBackupUseCaseTest` (4), `LatestBatchSerializerTest` (3: round trip with SUCCESS/FAILED, empty round trip, malformed -> `CorruptionException`).

## Developer-verified checks

- Build: Succeeded (developer-reported)
- App launch: Launches on emulator (developer-reported)
- Manual behavior: Fresh install shows Empty; batch and statuses survive force-stop/relaunch; new batch replaces previous; corrupted metadata file shows read error instead of Empty — "everything works as expected" (developer-reported)
- Unit tests: `./gradlew testDebugUnitTest` BUILD SUCCESSFUL (developer-reported)

## Known issues / deviations

- Killing the app mid-copy leaves the persisted batch `IN_PROGRESS`/`COPYING` until Step 05 reconciliation.
- Store property named `latestBatch` (step text says `lastBatch`).
- Corrupted metadata requires clearing app data to recover (no reset action in UI).
- Old batch folders are not cleaned up (by design; future work).

## Before committing each step (developer only)

- [ ] Compile/build project successfully.
- [ ] Launch app in emulator.
- [ ] Manually verify the step's acceptance criteria.
- [ ] Run all existing unit tests (or record explicitly why none exist/could run).
- [ ] Review changed code and update this file with **actual** outcomes.
- [ ] Commit code, tests and progress together.

The AI may implement code/tests and provide verification instructions, but must **not** run Gradle, adb, the emulator/app, or tests. See root `CLAUDE.md` for the mandatory verification gate: ask for the developer’s results, then update this file, then remind the developer to commit. Keep only current facts and next action here.
