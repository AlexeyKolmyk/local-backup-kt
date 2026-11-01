# Step 04 — Typed DataStore and latest batch

**Goal:** Persist the latest batch and each outcome so relaunch shows real recorded results.

## Required API

Use `androidx.datastore:datastore` Typed DataStore with a custom `Serializer` using `kotlinx.serialization` JSON. Use one scoped DataStore instance for the file. `BatchStore.lastBatch: Flow<BackupBatch?>` and atomic `updateData`.

## Tasks

1. Configure compatible DataStore and serialization plugin/dependencies. Prefer versions already in the project catalog; do not guess when sync fails.
2. Implement `Serializer<BackupBatch?>` (or equivalent explicit wrapper type) with a safe empty/no-batch representation. Handle genuine malformed-data/IO errors transparently; do not silently turn corruption into Empty. The `Serializer` must preserve previous data guarantees per DataStore contract.
3. Implement `DataStoreBatchStore` with `lastBatch` and `save`/per-file status and error/overall batch updates. Transform the **current** value passed to `updateData` instead of overwriting it with a stale snapshot.
4. Wire UseCase to **await** saving the initial batch before first copy; persist every terminal per-file outcome before proceeding; persist overall `COMPLETED` at end. UI observes Flow through ViewModel (`StateFlow`, lifecycle-aware collection).
5. Keep byte-level progress transient, not stored per chunk. Differentiate data loading, no backup, and persistent-store error states.

## Optional JVM unit test

If time permits, add a serializer JSON round-trip test for a `BackupBatch` with success and failed file outcomes. This is not a substitute for the developer's manual DataStore relaunch check. Avoid a new framework solely for this test.

## Acceptance criteria

- Fresh install shows Empty, not a fake batch.
- After copying 2+ files, force-stop/relaunch and display same latest batch/statuses.
- A new completed batch replaces metadata for prior batch; no unexpected stale writes.
- Persistence error surfaces clearly instead of reporting success/empty.

## Not now

No Room, migrations framework, queue, automatic resume, or full history.

## Verify and handoff

The **developer** runs build, app, existing unit tests, and real emulator relaunch check; AI does not execute them. Record developer-confirmed results in progress before committing. Suggested commit: `feat: persist latest backup batch`.
