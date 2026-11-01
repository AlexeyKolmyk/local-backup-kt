# Claude Code project instructions — LocalBackup

This is a small, time-boxed native Android/Kotlin/Jetpack Compose take-home. The **developer**, not the AI, owns all execution and verification. Follow `docs/ARCHITECTURE.md` as the technical source of truth.

## Context for each step

1. Read `docs/ARCHITECTURE.md` and `docs/PROGRESS.md`.
2. Read **only the current** `docs/steps/NN-*.md` unless another step is explicitly needed for context.
3. Implement only the requested step and its in-scope JVM unit tests. Keep the project minimally buildable; do not preimplement future features.
4. Follow agreed Android APIs, buffer size, data models and limitations; do not introduce Room, Hilt, WorkManager, services, Navigation Compose or unnecessary dependencies. Discuss architectural deviations **before** making them.

## Execution boundaries (mandatory)

- **DO NOT** run Gradle (including sync/build/check), JVM tests, `adb`, emulator commands, install/launch the app, or any executable validation. Do not claim these checks passed.
- **DO NOT** run Git commands that modify repository state, create commits, push, change branches or stage files. The developer handles Git.
- You may inspect and edit project files and write focused tests as requested; provide exact commands and instructions for the developer to run.
- Never mark an acceptance check as passed based solely on code inspection or generation.

## Verification gate — after implementing each step

Before updating progress or treating a step as complete:

1. Summarize the actual code changes and files touched.
2. Give the developer clear manual emulator checks and exact applicable test command(s), such as `./gradlew testDebugUnitTest`.
3. **Ask the developer to report** whether: build succeeds, app launches, changed functionality works, and all existing JVM tests pass (or explain a nonapplicable/unverified check).
4. **STOP and await explicit developer confirmation.** Do not mark `docs/PROGRESS.md` Completed or Verified beforehand.
5. After the developer confirms, update `docs/PROGRESS.md` with **only developer-reported facts**, next step, remaining issues and actual elapsed time if reported. Never add a Git commit hash. Remind the developer to review and commit code, tests and documentation together; do not commit for them.

If a check fails, help debug the failure, then present the verification gate again. Use `Not checked` / `Not verified` for unknown outcomes, never invent successful test results.

## Testing strategy

- Add small JVM tests in the steps where functionality appears. In Step 02 specifically, test exact stream-copy byte equality and the maximum single read request against `COPY_BUFFER_SIZE = 256 * 1024` using in-memory streams.
- Keep stream-copy test logic Android-framework-free. Use simple fakes for UseCase tests rather than introducing a mocking library solely for the assignment.
- Only the developer runs the tests and actual file-copy scenarios with Android SAF.

## Timebox and deliverable

- Target **2–4 hours total including project setup, manual testing and README**. Prefer a smaller reliable implementation to optional UI/features. Optional Cancel/polish must not displace required copying, independent per-file failure handling, or persisted latest-batch results.
- The user-facing `README.md` should document actual verified capabilities and an accurate AI-assistance disclosure at the end, not promise work not completed.