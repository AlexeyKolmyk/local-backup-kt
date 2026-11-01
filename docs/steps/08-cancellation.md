# Step 08 — Cooperative Backup Cancellation (Optional)

## Goal

Allow a user to cancel an active backup safely: preserve fully completed copies, remove the in-progress `.partial` copy, mark unfinished files `CANCELLED`, and persist a `CANCELLED` batch outcome.

## Context

Read `CLAUDE.md`, `docs/ARCHITECTURE.md`, `docs/PROGRESS.md`, and this step before editing. Reuse existing `BackupViewModel`, `CreateBackupUseCase`, `FileCopier`, `BatchStore`, `BackupFile` / `BackupBatch` statuses and app-private file layout. Do not redesign the architecture.

## Scope / implementation

1. Add a **Cancel** action shown/enabled only while a backup is active. Prevent starting a new batch while cancellation/cleanup is still in progress; guard against repeated Cancel taps and finish/cancel races.
2. Keep the active backup `Job` owned by the `BackupViewModel` (or the existing execution owner). On Cancel, request coroutine cancellation; do not block the UI thread.
3. Ensure the buffered `FileCopier` copy loop checks coroutine cancellation between reads/writes (e.g. `currentCoroutineContext().ensureActive()`), using the existing `COPY_BUFFER_SIZE = 256 * 1024`. Closing streams must be deterministic.
4. Write to a uniquely named `.partial` destination and finalize it **only** after successful completion and close. On cancellation or copy failure, best-effort remove the current partial file. A successfully finalized copy is never deleted by Cancel.
5. **Propagate `CancellationException`.** Do not catch it as an ordinary per-file failure: `catch (e: Exception)` must rethrow cancellation before converting exceptions into `FAILED`.
6. Persist a coherent terminal state after user cancellation: retain existing `SUCCESS` and `FAILED` outcomes; set the current not-yet-finalized file and all unstarted files to `CANCELLED`; set batch status to `CANCELLED`. Use a short, bounded `withContext(NonCancellable + Dispatchers.IO)` section if necessary to clean up and persist despite a cancelled job. Do not perform a whole backup in `NonCancellable`.
7. The source of truth for persisted outcomes remains `BatchStore`. Ensure this terminal update does not race with an older `COPYING` / `IN_PROGRESS` update or a concurrently finishing copy. If the operation fully completes before cancellation takes effect, keep the legitimate `COMPLETED` result instead of rewriting it to `CANCELLED`.
8. The UI should clearly distinguish `CANCELLED` from `FAILED` and `INTERRUPTED`; restore this outcome after app restart. Refresh Stored Backups from Step 07 to show only completed saved files.

## Constraints / out of scope

- Cancel is **non-destructive** to prior successful backups and to original user documents.
- No automatic retry/resume; no deleting entire batches.
- No WorkManager, foreground service, progress notification or guarantee of continued execution after process death.
- Process death is **not** a user-initiated cancellation: existing reconciliation should still classify unfinished work as `INTERRUPTED`.
- Avoid excessive abstractions; do not weaken existing individual-file error handling.

## JVM unit tests to add now (if practical)

- Simulate cancellation during the second file in a three-file batch: first remains `SUCCESS`, second and third become `CANCELLED`, batch becomes `CANCELLED`, and the third copy is never attempted.
- Verify ordinary `IOException` still marks only the affected file `FAILED` and allows subsequent files to run.
- Where testable, verify cancellation of the stream-copy function is propagated rather than swallowed.

Use fake collaborators to drive cancellation deterministically. Do not add fragile sleeps or emulator-dependent tests merely for this feature.

## Developer verification (AI must NOT run)

- Build and launch the app yourself; run available JVM tests yourself.
- Start a batch containing a sufficiently large file; tap Cancel while bytes are being copied. Confirm that UI remains responsive and reaches a terminal `CANCELLED` state.
- Verify the active `.partial` copy disappears and no truncated file is exposed as a completed backup.
- Verify files copied before cancellation still exist, with unchanged hashes; untouched files were not copied.
- Restart the app and confirm the latest batch still shows `CANCELLED` and the correct per-file statuses.
- Verify normal copying still succeeds, and a failed file does not stop later files.
- Try Cancel very near completion; verify there is no inconsistent combination of batch/file statuses or deletion of a successfully finalized file.

Optional verification via Android Debug Bridge:

```bash
adb shell "run-as com.example.localbackup ls -lR files/backups"
```

## Acceptance criteria

- Cancel stops further copying cooperatively and does not freeze the UI.
- The current partial output is cleaned up; previously completed files remain intact.
- Unfinished file outcomes and the batch are persisted as `CANCELLED` when cancellation takes effect.
- A normal failure remains `FAILED`; unexpected process termination remains `INTERRUPTED` after reconciliation.
- The operation does not persist contradictory terminal states under a finish/cancel race.

## Verification gate

Do **not** run Gradle, tests, emulator or `adb`, and do **not** create commits. After implementation, summarize changed files, provide exact checks/commands for the developer, and request verification results. Do not mark the step completed or update `PROGRESS.md` as verified until the developer confirms. Then update `PROGRESS.md` with actual outcomes; the developer commits.
