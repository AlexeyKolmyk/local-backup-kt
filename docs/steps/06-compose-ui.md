# Step 06 — Clean Material 3 screen, progress, optional Cancel

**Goal:** Minimal polished single-screen UI with no architectural shortcuts.

## UI layout

- Light, clean Material 3 visual style; neutral surface, restrained blue accent, 8dp spacing grid, moderate rounded corners (12–16dp), one outlined card around summary, thin row separators.
- Header: simple file/backup icon + `Local Backup`.
- Empty state: one friendly icon/text and prominent `Select files` button.
- Active state: summary (processed count and optional percentage), current filename, bounded-progress bar or indeterminate progress if source size unavailable, list with status icons.
- Finished state: summary counts and file rows showing Success/Failed/Interrupted/Cancelled, relevant short error text.
- `Select files` disabled while an operation runs. No extra screens/navigation.
- Use Material 3 `Scaffold`, `Column`/`LazyColumn`, `Card`, `Button`, `OutlinedButton`, `LinearProgressIndicator`, `Icon`, vector drawable Material Symbols via Android Studio if needed. Test at least light theme and readable small screen.

## Tasks

1. Compose UI collects `BackupUiState` via `collectAsStateWithLifecycle()`; recomposition does not start new copies or reconciliation.
2. Present errors clearly; don't show a misleading `100%` for incomplete/failed batches. Throttle volatile progress updates if a callback reports every chunk.
3. **Time permitting:** add `Cancel` button. Cancel active Job cooperatively; retain successful copies, clean current partial, mark current/remaining file states and batch `CANCELLED` (using short non-cancellable persistence cleanup after cancellation if needed). Avoid accidental replacement by startup recovery during live cancellation.
4. Do not implement Delete: cancelling is not deleting data.

## Tests

No Compose UI automation required within this timebox. The developer manually checks Empty / Copying / Results / Error screens and, if implemented, Cancel. Run all existing JVM tests before commit.

## Acceptance criteria

- UI clean and responsive in Empty / Copying / Results / Error states.
- Correct status icons, accessible text labels, no weird layout clipping.
- If Cancel included, verify no further files attempted and prior successful copies remain.

## Not now

No animation/navigation framework, custom icon package, graph dashboard, share/export, or foreground notifications.

## Verify and handoff

The **developer** runs build, app and existing JVM tests, and records which UI/optional actions worked. AI does not run commands. Suggested commit: `feat: polish backup screen`.
