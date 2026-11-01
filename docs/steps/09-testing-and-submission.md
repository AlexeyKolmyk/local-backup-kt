# Step 09 — Verify, document, submit

**Goal:** Finish the required scope and present an honest, runnable submission within the exercise timebox.

> Updated after Steps 07 (Stored backups & deletion) and 08 (Cancel) were implemented, so the checklist and README content match the actual project.

## Verification ownership

The **developer** runs all commands and tests. The AI can suggest steps and write documentation, but must not build, launch, run Gradle/adb or mark checks as passed without the developer's observed results. Unit tests were added incrementally (Steps 02, 03, 04, 05, 07, 08); this final step re-runs and reviews them, it does not first add them.

## Checklist

- [ ] App builds/launches from a clean checkout (AGP 9.4.1, Gradle 9.6.0 wrapper, Gradle daemon JVM toolchain 25 (auto-provisioned via foojay), compileSdk/targetSdk 37, minSdk 26).
- [ ] System picker selects **multiple real files** without broad storage permissions.
- [ ] Copies are actual bytes in app-private storage; at least one destination verified by SHA-256 against the original.
- [ ] Large source copy (hundreds of MB) streams with a 256 KiB buffer; UI responsive with progress.
- [ ] Per-file error is displayed and next files are attempted (unit test with injected `IOException`; manual failure is hard to provoke — record honestly).
- [ ] Most recent batch persists after process restart; corrupted metadata shows an error, not Empty.
- [ ] Interrupted batch (force-stop mid-copy) is reconciled on next launch: unfinished -> INTERRUPTED, earlier SUCCESS kept, no `.partial` left; no automatic resume.
- [ ] Cancel: earlier successful files kept, active partial removed, remaining files and batch CANCELLED, persists after restart.
- [ ] Stored backups: lists finalized copies from all batches; delete with confirmation removes exactly one app-owned copy; disabled while copying; latest-batch statuses unchanged.
- [ ] All existing JVM tests run by the developer (`./gradlew testDebugUnitTest`) and actual results recorded.
- [ ] Git working tree clean; last commit builds; no secrets or large test binaries committed.

## README.md content

1. One-paragraph app purpose + key features.
2. Build/run instructions with system requirements.
3. How to test with the system picker; backup destination inside the app; app-private data deleted on uninstall; `adb` verification commands.
4. Architecture summary: Compose + `BackupViewModel`, `CreateBackupUseCase`, `FileCopier`/`copyStream`, `BatchStore` with Typed JSON DataStore, `ReconcileBackupUseCase`, `BackupInventory`.
5. Failure, interruption and cancellation semantics; no guaranteed background execution/resume.
6. Limitations / future improvements: user-selected destination folder, background execution (WorkManager/foreground service), stronger integrity checks (hash catalog), history beyond the latest batch, bulk/old-backup cleanup, UI tests.
7. **Actual** time spent and unfinished work (developer-provided).
8. Brief accurate AI-assistance disclosure (developer-confirmed).

## Delivery

Review `PROGRESS.md` for factual accuracy, ensure all acceptance requirements are either verified or called out as incomplete. Use a concise `docs:` commit for README and submission notes. Stop at four hours even if optional polish is incomplete.
