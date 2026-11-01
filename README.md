# LocalBackup

A small native Android app (Kotlin, Jetpack Compose, Material 3) that makes **local backup copies** of documents the user picks with the system file picker. Files are copied byte-for-byte, in a streaming way, into the app's private storage. Each file gets its own outcome (success, failed, interrupted, cancelled), the latest batch survives app restarts, an interrupted batch is reconciled on the next launch, a running backup can be cancelled, and stored copies can be listed and deleted one by one.

## Features

- Pick one or more files of any type with the Android system picker (Storage Access Framework). No storage permissions are requested.
- Sequential copy into `filesDir/backups/<batch-id>/` using a single reusable 256 KiB buffer; large files are never loaded into memory.
- Copies are written to a `.partial` file and only renamed to the final name after a complete write, so an unfinished copy is never shown as a backup.
- Per-file status and short error message; one failing file does not stop the others.
- Progress for the current file (percentage when the size is known, indeterminate otherwise).
- The latest batch and all its outcomes are persisted and shown again after a restart.
- After a crash or force-stop mid-copy, the next launch marks unfinished files as **Interrupted**, keeps finished results and removes leftover `.partial` files. Nothing is resumed automatically.
- **Cancel** a running backup: finished copies are kept, the current partial file is removed, remaining files are marked **Cancelled**.
- **Stored backups**: list of all finished copies on disk (from every batch) with delete-with-confirmation for a single copy.

## Requirements and build

- Android Studio with Android Gradle Plugin 9.4.1 support; the Gradle 9.6.0 wrapper is included.
- The Gradle daemon uses a JDK 25 toolchain (auto-provisioned through the foojay resolver if not installed).
- Android SDK with API 37 (compileSdk/targetSdk); devices/emulators with Android 8.0 (API 26) or newer.

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # JVM unit tests
```

Or open the project in Android Studio and run the `app` configuration on an emulator.

## How to try it

1. Put a few files on the emulator, e.g. drag and drop them onto the emulator window (they land in *Downloads*), or:
   ```bash
   dd if=/dev/urandom of=large_test.bin bs=1m count=450
   adb push large_test.bin /sdcard/Download/
   ```
2. Tap **Select files**, open *Downloads* in the picker, long-press to select several files, then tap **Select**.
3. Watch the per-file statuses and progress. Use **Cancel backup** during a large copy to try cancellation.
4. Verify the copies (app-private storage, readable only via `run-as` on a debuggable build):
   ```bash
   adb shell 'sha256sum /sdcard/Download/*'
   adb shell "run-as com.example.localbackup sh -c 'ls -lR files/backups; sha256sum files/backups/*/*'"
   ```
5. Interruption: start a large batch and run `adb shell am force-stop com.example.localbackup` while it copies, then reopen the app.

Backups live in app-private storage: they are **deleted when the app is uninstalled** and are excluded from Android Auto Backup / device transfer (together with the metadata file that points to them).

## Architecture

Single module, single `ComponentActivity`, one Compose screen, manual dependency wiring in `AppContainer` (no DI framework).

```
BackupScreen (Compose) ──> BackupViewModel ──> CreateBackupUseCase ──> FileCopier / BatchStore
        ^                        │
        └── StateFlow<BackupUiState> <── BatchStore.latestBatch (DataStore Flow)
```

- **`ui/BackupScreen`, `BackupViewModel`** — stateless rendering; the ViewModel owns the single running `Job`, blocks concurrent starts, throttles transient progress, exposes Cancel and Delete, and waits for startup reconciliation before enabling new backups.
- **`backup/CreateBackupUseCase`** — the workflow for one batch: plan unique destinations, persist the initial batch, copy files sequentially, persist each outcome before the next file, mark the batch `COMPLETED`; on cancellation runs a short `NonCancellable` cleanup.
- **`copy/FileCopier` + `copyStream`** — copies one file on `Dispatchers.IO` with a 256 KiB buffer and a cancellation check before every read; `.partial` → fsync → rename; deletes the partial on any failure.
- **`backup/BatchStore` → `data/DataStoreBatchStore`** — Typed Jetpack DataStore with a `kotlinx.serialization` JSON serializer; stores **only the latest batch**; updates always transform the current value atomically; malformed data surfaces as an error instead of an empty state.
- **`backup/ReconcileBackupUseCase`** — runs once per process at startup and repairs an unfinished `IN_PROGRESS` batch.
- **`copy/BackupInventory`** — lists finalized copies on disk and deletes exactly one, with canonical-path checks that reject traversal, symlinks and anything outside `backups/`.
- Small interfaces (`BackupSources`, `BackupDestinations`, `BackupCopier`, `DestinationProbe`) keep the use cases free of Android types so they are unit-tested on the JVM with simple fakes (no mocking library).

## Status semantics

| Status | Meaning |
| --- | --- |
| `SUCCESS` | Copy fully written and finalized. |
| `FAILED` | This file could not be read or written (short reason shown); later files were still attempted. |
| `INTERRUPTED` | The copy did not finish because the app process died or the work was stopped without a user cancel (e.g. the screen was closed). Detected on the next launch or at stop time. |
| `CANCELLED` | The user tapped Cancel before this file finished. |

A batch is `COMPLETED` when every file was attempted (even if some failed), `INTERRUPTED` or `CANCELLED` otherwise. A recovered file is marked `SUCCESS` only if its finalized copy exists with the expected size — a sanity check, not a cryptographic integrity proof.

Copying runs in the ViewModel scope: leaving the app does not stop it by itself, but **completion in the background is not guaranteed** (no foreground service or WorkManager), and nothing is resumed after process death.

## Tests

JVM unit tests (`app/src/test`), run with `./gradlew testDebugUnitTest`:

- `StreamCopyTest` — exact byte equality across chunks, no read request larger than 256 KiB, empty source, progress, cancellation propagation.
- `CreateBackupUseCaseTest` — a failing file does not stop the next ones, unique destinations, user cancel → `CANCELLED`, other cancellation → `INTERRUPTED`, cancel right after a file was finalized, empty selection.
- `LatestBatchSerializerTest` — JSON round trip with success/failed outcomes, empty state, corrupted data → `CorruptionException`.
- `ReconcileBackupUseCaseTest` — unfinished → `INTERRUPTED`, existing `SUCCESS`/`FAILED` preserved, recovery by expected size, filesystem errors, terminal batches untouched.
- `BackupInventoryTest` — listing across batches excluding `.partial`, single-file delete, empty batch folder removal, traversal and symlink rejection.

UI was verified manually on an emulator (no Compose UI tests).

## Verified manually

<!-- Keep this list to what was actually observed. See docs/PROGRESS.md for details. -->
- Multi-file selection and copying of real files (PDF, JPG, MP4, ~470 MB binary); SHA-256 of copies matched the originals.
- Latest batch shown again after force-stop/relaunch; corrupted metadata shows an error, not an empty state.
- Force-stop mid-batch → unfinished files `INTERRUPTED`, earlier `SUCCESS` kept, no `.partial` files left.
- Cancel during a large copy → finished copies kept with matching hashes, remaining files `CANCELLED`, state kept after restart.
- Stored backups listed across batches; deleting copies removes them from disk.
- Not observed manually: a real per-file `FAILED` outcome (covered by unit tests), Cancel at the very last moment of a copy, Back-button stop recorded as `INTERRUPTED`.

## Limitations and future improvements

- Destination is app-private only; a user-chosen folder (`ACTION_OPEN_DOCUMENT_TREE`) would need a destination adapter.
- No guaranteed background execution or resume (would need WorkManager / a foreground service).
- Only the latest batch is persisted; older batches are visible only as files under *Stored backups*, and there is no bulk cleanup.
- Stored backups show the sanitized on-disk file name (e.g. spaces become `_`), not the original display name.
- Integrity is checked by size during recovery only; no checksum catalog.
- Corrupted metadata can only be recovered by clearing app data.
- The summary counts Interrupted/Cancelled files as "processed" (e.g. "3 of 3 files processed" on a cancelled batch), and "1 of 1 files" is not pluralized.
- No Compose UI/instrumented tests; limited edge-case testing on real devices.

## Time spent

About **3 hours** in total, measured from the initial commit (2026-11-01 14:14 CET) to the final documentation commit. This covers project setup, all implementation steps, manual emulator testing and this README. Preparing the planning documents in `docs/` before the first commit is not included.

Unfinished / not done within the timebox: Compose UI tests, manual checks of a real per-file failure, Cancel at the very last moment of a copy and the Back-button stop (see *Verified manually*), and the small summary wording issue below.

## AI assistance

Planning documents and the step-by-step specifications in `docs/` were written by the developer. Code and unit tests were written with the help of Claude (Anthropic) incrementally, one step at a time, and reviewed by the developer. All builds, unit test runs and emulator/`adb` checks were run and verified by the developer.
