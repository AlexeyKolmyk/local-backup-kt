# Step 07 — Stored Backups & Individual Deletion

## Goal

Show all **physically stored backup copies** from every batch and let the user delete an individual backup copy safely. Keep the latest-batch history and persistence design unchanged.

## Context

Read `CLAUDE.md`, `docs/ARCHITECTURE.md`, `docs/PROGRESS.md`, and this step before editing. The current `DataStore` contains the **latest batch only**, while copies from older batches remain under `context.filesDir/backups/<batchId>/`. This screen is an inventory of files on disk, **not** a historical database of backup attempts.

## Scope / implementation

1. Add a **Stored Backups** section below the latest-batch section on the existing Compose screen. Include an empty state. Display each physically present backup file's readable name and size; optionally group by batch directory or show a flat list. Keep the UI consistent with the existing Material 3 design.
2. Enumerate files under `context.filesDir/backups/` on `Dispatchers.IO`. Include successfully finalized regular files only; **exclude `.partial` files**, directories and non-backup artifacts. Do not infer file existence from `BackupBatch` records. Keep filenames and storage identifiers distinct if filenames are prefixed with generated IDs.
3. Represent each listed item with enough stable identity to delete **that exact file**, e.g. an internal relative path/identifier, display name and actual size. Never treat an untrusted display name as a filesystem path.
4. Provide a delete action for each item. Display a confirmation dialog mentioning the selected backup file. After confirmation, delete **only** the app-owned destination copy; never delete or modify its source `content://` URI. Refresh the list after success; show a visible error if deletion fails.
5. Constrain deletion strictly to an existing regular file within the designated backups directory. Resolve paths canonically or use safe file references obtained from your own inventory; reject path traversal, symbolic-link escapes and any target outside that root. If an empty batch directory remains, it may be removed after deletion.
6. Disable deletion while a batch is actively copying; guard this in the operation layer as well as the UI, to avoid races with the copier. Refresh inventory after batch completion and after a successful deletion.
7. Keep the latest batch's recorded statuses (`SUCCESS`, `FAILED`, etc.) historically accurate. Deleting its physical copy **does not** retroactively make a successful copy `FAILED`. If latest-batch UI displays availability, reflect that separately.

## Constraints / out of scope

- No Room, extra database, second persistence mechanism, or changes to the latest-batch-only `DataStore` contract.
- No full historical batch status reconstruction. The inventory shows what is currently on disk, not every previous attempted file.
- No operation on user-selected source files; no broad Android storage permissions.
- No multi-select deletion, sharing/export, rename or opening backups unless already supported.
- Avoid unnecessary layers and dependencies; reuse current ViewModel/architecture sensibly.

## JVM unit tests to add now (if the filesystem boundary is testable without Android)

- Inventory lists finalized copies from multiple batch directories, excludes `.partial`, and reports actual sizes.
- Deleting one copy preserves unrelated files, and an invalid/out-of-root target cannot be deleted.

Use temporary directories in JVM tests if useful; no emulator is required for those tests. Do not introduce an elaborate abstraction solely to support testing. If implementation is Android-bound, prioritize the manual verification below instead.

## Developer verification (AI must NOT run)

- Build and launch the app yourself.
- Confirm copies from **older and latest** batches appear under Stored Backups with correct size and friendly name.
- Confirm temporary `.partial` files (if present) do not appear.
- Cancel the confirmation dialog: file remains on disk and in the list.
- Confirm deletion of one chosen backup: it disappears from the list and from `filesDir/backups/`; other files are intact, source under Downloads remains intact.
- Check that latest-batch recorded `SUCCESS` stays `SUCCESS` after its copy is deleted.
- Confirm deletion is unavailable during an active backup and that failure to delete shows a message.
- Run existing JVM tests and any newly added tests yourself.

Optional verification via Android Debug Bridge:

```bash
adb shell "run-as com.example.localbackup ls -lR files/backups"
```

## Acceptance criteria

- The UI shows all existing finalized app-private backup files, including those from earlier batches.
- Individual deletion requires confirmation and removes exactly the selected destination copy.
- The inventory refreshes; source documents and other backup files remain untouched.
- Latest-batch persistence and historical result statuses remain unchanged.
- No files are modified by the AI outside the agreed step scope.

## Verification gate

Do **not** run Gradle, tests, emulator or `adb`, and do **not** create commits. After implementation, summarize changed files, list exact commands/manual checks for the developer, and ask for verification results. Do not mark this step completed or update `PROGRESS.md` as verified until the developer confirms. Then update `PROGRESS.md` with actual outcomes; the developer commits.
