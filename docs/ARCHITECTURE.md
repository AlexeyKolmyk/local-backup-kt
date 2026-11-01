# LocalBackup — Architecture and technical decisions

## Goal and scope

Build a small native Kotlin Android app within **2–4 hours total, including setup, tests and documentation**. Users select multiple real documents with Android's native picker, the app makes real local backup copies without blocking UI or reading whole files into memory, reports per-file success/failure, and shows the latest recorded batch when relaunched. An individual failure must not stop attempts for remaining files.

No backend, guaranteed background execution, automatic restart/resume after process death, or production-grade polish. Prefer a smaller reliable implementation over optional features. Record actual time spent and unfinished work in README.

## Fixed technical choices

- Kotlin, single `ComponentActivity`, Jetpack Compose with Material 3; one screen, no navigation library.
- `ActivityResultContracts.OpenMultipleDocuments()` through `rememberLauncherForActivityResult`; MIME types `arrayOf("*/*")`. Empty result does nothing.
- Use picker-granted access, `ContentResolver.openInputStream(uri)` and `ContentResolver.query()` (`OpenableColumns.DISPLAY_NAME`, nullable `SIZE`). No `READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE` permission.
- Copy to app-private `context.filesDir/backups/<batch-id>/` using `InputStream`/`OutputStream`, one reusable **256 KiB** buffer per active copy (`COPY_BUFFER_SIZE = 256 * 1024`), `Dispatchers.IO`, and coroutine cancellation checks. No whole-source-file `readBytes()`. This size is a pragmatic starting point, not a benchmarked optimum.
- Distinct generated destination filenames/IDs prevent collisions; preserve original display name in metadata.
- Copy into an adjacent `.partial` destination first, close streams, then finalize/rename to the destination file. Delete partial output on normal errors/cancellation when possible.
- Sequential batch processing; continue after per-file I/O failures. Propagate `CancellationException` and do not accidentally convert cancellation to ordinary `FAILED`.
- Typed Jetpack DataStore with `kotlinx.serialization` JSON for **only the latest batch metadata**. Expose `Flow<BackupBatch?>` through `BatchStore`, persist initial batch before copying and each terminal file outcome. `updateData` transforms the current stored value atomically. Avoid multiple DataStore instances for the same file.
- Do not persist byte-by-byte progress. Transient progress in ViewModel/UI, throttled if reported frequently. UI observes persisted batch through `Flow`/`StateFlow` and `collectAsStateWithLifecycle()`.
- Use `viewModelScope` to own a currently running `Job`. Going to background does not *by itself* cancel it, but completion in the background is **not guaranteed**; no Foreground Service or WorkManager.
- While a batch runs, disable `Select files` and reject a duplicate start in the ViewModel. This app supports one running batch and persists only the latest batch.

## Models

Use these shapes unless implementation reveals a concrete issue. Keep models immutable (`val`), serializable, and update with `copy()`.

```kotlin
@Serializable
data class BackupBatch(
    val id: String,
    val createdAt: Long,
    val status: BackupBatchStatus,
    val files: List<BackupFile>
)

@Serializable
data class BackupFile(
    val id: String,
    val name: String,
    val sourceUri: String,
    val destinationUri: String,
    val expectedSize: Long?,
    val status: BackupStatus,
    val errorMessage: String? = null
)

@Serializable
enum class BackupStatus { PENDING, COPYING, SUCCESS, FAILED, INTERRUPTED, CANCELLED }

@Serializable
enum class BackupBatchStatus { IN_PROGRESS, COMPLETED, INTERRUPTED, CANCELLED }
```

`sourceUri` and `destinationUri` are **serialized strings representing Android `Uri` values**; neither the model nor the byte-copy loop decides how to acquire permissions or open the streams. For the initial private destination, `Uri.fromFile(file).toString()` is sufficient; source `content://` URIs come from SAF. Creation and opening of destination resources is a storage-layer concern. A destination URI may identify the intended final file before that file is created; failed per-file creation is reported as `FAILED`, not a reason to pretend a file succeeded. Source URI strings are **not** persisted permission grants; resuming access to sources after process death is out of scope.

## Components and ownership

- `MainActivity`: Compose host, lightweight wiring.
- `BackupScreen`: render UI, launch native picker, forward selection and Cancel actions. No file I/O.
- `BackupViewModel`: expose `BackupUiState` combining persisted status and transient progress/loading, trigger `CreateBackupUseCase` in `viewModelScope`, own/cancel active job, block concurrent starts. No loop over files.
- `CreateBackupUseCase`: business workflow for **one batch**: validate/preflight necessary inputs, prepare metadata and unique destination names, persist initial batch, sequentially call copier, persist results/status; handle ordinary per-file failure separately from cancellation.
- `FileCopier`: actual copy of **one** file using streams, `.partial`, finalization, cleanup and cancellation awareness; no DataStore/Compose knowledge.
- `BatchStore` / `DataStoreBatchStore`: persist/read latest batch only, atomic updates based on current value. No copy logic.
- `ReconcileBackupUseCase`: invoked once on startup **before enabling new backups**; correct unfinished previous batch from saved metadata plus disk state, remove known partials, persist recovery result. Never resume the transfer.

Command path: `BackupScreen -> BackupViewModel -> CreateBackupUseCase -> FileCopier / BatchStore`. Status path: `DataStore -> BatchStore Flow -> BackupViewModel -> Compose`. Transient byte progress may use a callback from copier via use case; it is not a persisted status stream.

## Lifecycle and state transitions

1. First launch: load latest batch; no saved batch => Empty. Handle a read/corruption error explicitly instead of silently reporting Empty.
2. Once selected files are validated and planned, make `BackupBatch(IN_PROGRESS, PENDING...)` and **await** persistence before copying.
3. Before copying each item, set `COPYING` (persisting this state is useful but not essential if `PENDING` means not yet recorded as finished). After success/failure, persist `SUCCESS`/`FAILED` and any error message **before** next item.
4. Once every file was attempted, mark batch `COMPLETED` even if some entries `FAILED` (completed ≠ all successful).
5. If process dies, no callback or `finally` is guaranteed. At next launch, reconcile unfinished `IN_PROGRESS`; mark unfinished items `INTERRUPTED`, except a fully finalized destination can be recognized as `SUCCESS` after a cautious existence/size check. Remove `.partial` when possible. Mark overall batch `INTERRUPTED` unless it can genuinely be established that all files were processed.
6. `CANCELLED` is reserved for explicit user cancellation (time permitting). Cancel stops pending/future copying, cleans the active `.partial`, keeps already successful copies, and records remaining files and batch as `CANCELLED`. Deletion of successful backups is a different feature, not part of Cancel. Cancellation cleanup/persistence must handle already-cancelled coroutine context, e.g. a short `NonCancellable` finalization; this does not protect against process death.
7. Existing `SUCCESS` and `FAILED` records are not indiscriminately overwritten by reconciliation. Size matching is a useful sanity check **not** an integrity proof; checksum verification is not required.

## Safety, constraints and known limitations

- Do not delete source files; do not treat an incomplete `.partial` as a finished backup.
- A per-file exception must not prevent attempts for other files; except cancellation, which stops the batch.
- Avoid losing previously recorded results because of delayed/out-of-order saves: await sequential `BatchStore` writes; use DataStore's `updateData` against its current value.
- `DataStore` metadata and physical file writes are **not a single transaction**. Reconciliation repairs the narrow crash window to the extent practical; don't claim strict crash consistency or full data-integrity verification.
- App-private copies disappear upon uninstall. User-selected destination folders via `ACTION_OPEN_DOCUMENT_TREE` are a future improvement; stream-copying stays conceptually the same but destination opening/finalization needs an adapter.
- Storing only latest metadata does not automatically delete files from older batches. Don't silently delete old successful copies; document orphaned/older-copy cleanup as future work.
- File source providers may report unknown sizes or have read failures. Report unknown progress with an indeterminate UI.
- If permissions, destination preparation, or persistence are fundamentally unavailable, report a clear operation-level error rather than starting a broken batch; errors discovered during individual file copying are recorded per file.
- Prefer truthful failure display over hiding exceptions; avoid exposing raw stack traces or sensitive absolute paths in UI.

## Incremental verification and ownership

AI-specific instructions and the mandatory stop-before-verification workflow live in root `CLAUDE.md`. Read it before implementation. Git commit-message prefixes such as `feat:`/`chore:` are optional; clear ordinary descriptions are equally acceptable.

- **Developer owns verification:** the AI assistant may edit code, add unit tests, and give exact steps/commands, but must **not execute Gradle, launch the emulator/app, invoke adb, or run tests**. No `passed` claims until the developer reports results.
- Before **each commit**, the developer checks: (1) project compiles, (2) app launches, (3) changed behavior works manually on emulator, (4) all existing unit tests pass. If a check is impossible or not applicable, record that honestly instead of checking it off.
- Unit tests are added **in the step that introduces their subject**, not deferred to the last step: Step 02 byte equality + bounded read-request size; Step 03 a failing file does not stop remaining files; Step 04 optional serialization round trip; Step 05 incomplete-batch reconciliation. Step 06 UI remains manually verified; Step 07 reviews results.
- Keep JVM unit tests small. For the core copier, use in-memory `InputStream`/`OutputStream` plus a tracking stream that records maximum `read(buffer, offset, length)` size, ensuring it does not exceed `COPY_BUFFER_SIZE`. This tests the copier's read behavior, not whole-process memory usage. The byte-copy logic must be callable independently of Android framework objects.
- Do **not** add mocking frameworks only for this exercise. Simple fakes for file copier, batch store, and file-state probe are sufficient when needed. Avoid injecting artificial test code into production UI.
- Keep `docs/PROGRESS.md` as a **short current-state handoff**, not a commit journal. Update only after the developer confirms checks. Do not store commit hashes; Git already holds history.
- Follow `docs/steps/01...07` in order. Each step defines scope and acceptance criteria; early temporary UI/in-memory scaffolding is allowed but must be replaced rather than kept as a second persistence mechanism. Do not implement later steps early without a concrete need. Record actual exercise time including setup, testing, and docs.

## Tests and acceptance

- Select 2+ real documents via system picker; copy bytes and verify at least size + byte equality/hash for test fixture.
- Test small text, real binary/PDF/photo and large file (100–200 MiB or more). Main UI stays responsive.
- One failing file remains visible and later files are attempted (via a focused test/fake or controlled failure).
- Relaunch app after completed batch: same most recent batch and outcomes.
- Simulate interruption mid-copy; no automatic resume; partial files are not represented as successful copies.
- If Cancel is implemented: successful files preserved, active partial removed, remainder not attempted, explicit `CANCELLED` recorded.

## Implementation discipline

Do not add Room, Hilt, WorkManager, services, Navigation Compose, networking, or extra modules without a demonstrated requirement. Keep interfaces only where they support actual separation/tests. Do not invent progress beyond tests performed. Respect the 4-hour hard stop, including documentation. Write down deviations from this spec before implementing them.