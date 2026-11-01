# Step 03 — CreateBackupUseCase and file outcomes

**Goal:** Move multi-file business workflow out of UI/ViewModel into a cohesive UseCase, with independent failures.

## Tasks

1. Add `CreateBackupUseCase` that receives selected URIs, validates baseline preconditions, prepares one immutable `BackupBatch` with unique `BackupFile` IDs and destination URIs, then copies files **sequentially** using `FileCopier`.
2. Each file proceeds `PENDING -> COPYING -> SUCCESS/FAILED`; record useful `errorMessage` for failures. A failed item must not prevent attempting the next one. `CancellationException` must propagate (not be reported as `FAILED`).
3. Introduce `BackupViewModel` with `viewModelScope`, one active job, and a duplicate-start guard. `BackupScreen` forwards URI selection and renders basic results; ViewModel must **not** own the `for (file ...)` loop.
4. Before persistence exists (Step 04), use an intentionally temporary in-memory batch state exposed to UI. Put state publishing behind a small API so Step 04 can replace it with the BatchStore Flow without duplicating sources of truth.
5. Mark overall batch `COMPLETED` after all files attempted, including batches with some `FAILED` outcomes.

## JVM unit test to add now

Use a simple fake copier and in-memory temporary batch state. For A, B, C, simulate an `IOException` on B. Assert that all three files were attempted in order, A/C become `SUCCESS`, B becomes `FAILED`, and the batch becomes `COMPLETED`. No Android/emulator file fixture is needed. Keep the fakes small; no mocking framework required.

## Acceptance criteria

- Three-file selection runs sequentially and shows per-file statuses.
- Simulated failure of one item does not skip later items (test fake/focused test is fine).
- Repeated Select clicks cannot launch concurrent operations; no batch loop in ViewModel.

## Not now

No persisted results yet (this comes immediately next); no queue, background service, deletion, automatic resumption or extra UseCases.

## Verify and handoff

The **developer** runs build, app and existing unit tests; manually validates statuses and duplicate-start behavior. AI supplies instructions but does not run them. Record only developer-confirmed results in progress. Suggested commit: `feat: coordinate sequential backup batch`.
