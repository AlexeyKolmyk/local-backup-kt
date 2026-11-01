# Step 02 — Native picker and real buffered copy

**Goal:** The smallest running vertical slice: select multiple real documents and physically copy bytes to app-private storage.

## Required APIs

- `rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments())`
- `ContentResolver.openInputStream(uri)` and `query` with `OpenableColumns.DISPLAY_NAME` / nullable `SIZE`
- `File`, `Uri.fromFile`, `InputStream`/`OutputStream`, `Dispatchers.IO`, `currentCoroutineContext().ensureActive()`

## Tasks

1. Wire `Select files` to native picker accepting `arrayOf("*/*")`; empty selection => no work.
2. Read document display names/sizes via provider metadata, with safe fallbacks and nullable size.
3. Implement `FileCopier` that can copy **one** source URI to a prepared destination: single reusable buffer with `COPY_BUFFER_SIZE = 256 * 1024` (256 KiB); `withContext(Dispatchers.IO)`; cancellation checks between reads; count copied bytes if useful.
4. Generate unique file IDs/destination filenames inside `filesDir/backups/<batch-id>/` so equal display names never overwrite each other. Make an adjacent `.partial`, close and finalize it only on success. Clean up partials on ordinary failure/cancellation when possible. Guard `IOException`/stream-null from content provider.
5. Use minimal temporary UI wiring to select, call copier, and show real results. A temporary loop is allowed *only for this testable milestone*; Step 03 must move sequence into the UseCase.

## JVM unit tests to add now

If not already configured by the Android Studio template, add the minimal JUnit dependency and standard local JVM test source set (`app/src/test/java`). Only add `kotlinx-coroutines-test` if the design actually requires it. **Create the test code; do not execute tests.**

- Isolate the byte-copy loop so it can be tested with ordinary JVM streams, without a real Android `Uri` or `ContentResolver`.
- Test exact byte equality from `ByteArrayInputStream` to `ByteArrayOutputStream` across multiple chunks.
- Wrap source with a tracking `FilterInputStream`; assert maximum size of any `read(byte[], offset, length)` request is `<= COPY_BUFFER_SIZE`. A ~1 MiB test payload is sufficient; do not use a huge fixture.
- Test checks requested buffer use, not total process memory, and should not rely on filesystem test assets.

## Acceptance criteria

- Select 2+ real documents in the emulator, and verify each destination exists with matching bytes or hash.
- Large file copies without whole-file buffering and main thread stays responsive.
- Source files remain unchanged. No storage permission dialog/manifest permissions.

## Not now

No DataStore, no polished UI, no parallel copies, no foreground service. Avoid claiming `copyTo()` alone is cancellation-aware.

## Verify and handoff

The **developer** runs build, app, manual real-file copy checks (Device Explorer / `adb` if desired), and all JVM unit tests. Claude supplies exact steps/commands and asks for explicit results; it does **not** execute commands or mark the step verified. Record only developer-confirmed results in progress before committing. A simple commit message such as `Implement native picker and buffered copying` is fine.
