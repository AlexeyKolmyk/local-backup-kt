# Step 01 — Project setup and domain models

**Goal:** A clean buildable Compose project with agreed models and basic screen shell.

## Tasks

1. **The developer first creates** a single-module Android Studio **Empty Activity** project using Kotlin + Jetpack Compose, runs the generated app manually on the emulator, and copies `CLAUDE.md` plus `docs/` into the repository. Suggested Android Studio project name: `LocalBackup`, package `com.example.localbackup`; repository name may be `local-backup-kt`. Keep generated `compileSdk`, `targetSdk`, Kotlin/Gradle versions unless a verified incompatibility appears; a reasonable `minSdk` is 26.
2. **Claude then reviews** the generated Gradle configuration, version catalog, package structure and missing dependencies. Edit only what Step 01 requires. Do not blindly upgrade toolchain versions.
3. Add only dependencies actually needed for this step; plan later Typed DataStore, Kotlin serialization plugin/JSON, lifecycle ViewModel/Compose and coroutines. Use version catalog if present.
4. Create serializable immutable `BackupBatch`, `BackupFile`, `BackupStatus`, `BackupBatchStatus` exactly as agreed in `ARCHITECTURE.md`.
5. Keep `MainActivity` and a basic `BackupScreen` with a title and placeholder Select files button. No copy/persistence yet.
6. Ensure package names and imports compile.

## Acceptance criteria

- Gradle sync/build succeeds.
- App launches in emulator showing basic Compose screen.
- Models and enums compile; no broad storage permissions are added.

## Not now

No picker, storage access, DataStore, cancellation, or polished UI. Do not generate speculative layers.

## Verify and handoff

The developer performs all Gradle/build/emulator checks and confirms the basic UI. No unit tests are required yet. Claude must provide verification instructions, **not execute** them, and ask for results. Only after explicit developer confirmation update `docs/PROGRESS.md`; the developer then commits. An ordinary descriptive message such as `Set up Compose project and domain models` is fine; Conventional Commits are optional.