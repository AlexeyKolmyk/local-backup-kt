# Step 05 — Recovery / reconciliation on startup

**Goal:** Display truthful latest outcomes after interruption without trying to resume copying.

## Tasks

1. Implement `ReconcileBackupUseCase` to run **once per process initialization**, not on every Flow emission or Compose recomposition. Startup initialization finishes before enabling new backup selection.
2. Read saved latest batch. If absent or already terminal, show it unchanged (aside from optional lightweight file validation). If `IN_PROGRESS`, inspect unfinished items and known `.partial`/final destination paths.
3. Delete known partial destinations where possible. Mark `PENDING` or unfinalized `COPYING` as `INTERRUPTED`; if final file exists and passes conservative checks (e.g., known expected size), consider recovering `SUCCESS`. Size equality is not a cryptographic integrity guarantee.
4. Preserve previously recorded `SUCCESS`/`FAILED` results; persist reconciled batch with `INTERRUPTED` overall if it was interrupted. Do not mark entire batch `COMPLETED` merely because no pending items remain after converting them to `INTERRUPTED`.
5. Handle filesystem failures and DataStore failure without a crash/false `No backups yet` state.

## JVM unit test to add now

Given a persisted `IN_PROGRESS` batch with A=`SUCCESS`, B=`COPYING`, C=`PENDING` and no finalized destination for B, simulate destination existence/partial-file checks using a small fake filesystem probe (or isolated file-state logic). Assert A remains `SUCCESS`; B/C become `INTERRUPTED`; overall batch becomes `INTERRUPTED`. No real Android file picker needed. Test cleanup behavior separately only if easy to fake.

## Acceptance criteria

- Interrupted transfer never auto-resumes.
- `.partial` is never reported as completed; clean-up is attempted.
- Recorded successes remain visible; unfinished items shown as interrupted after next launch.
- App startup/return to foreground does not reconcile over a **still-running** live job.

## Not now

No SHA-256 integrity catalog, restart/resume service, orphan-file garbage collector, or full atomicity across file system/DataStore.

## Verify and handoff

The **developer** runs build, app and unit tests, and performs controlled interruption/relaunch where feasible. AI does not run commands. Record only what the developer verified. Suggested commit: `feat: reconcile interrupted backups`.
