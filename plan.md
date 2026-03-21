# Project Roadmap

## 1. Objectives

Keep the app small, local-first, and responsive while improving maintainability.

Primary goals:
- Reduce cold-start work on the main thread.
- Remove background work that stays alive longer than necessary.
- Keep Compose recomposition and map redraws event-driven rather than periodic.
- Make data and settings flows explicit and cheap.
- Preserve the current offline-first behavior and device-local storage model.

Secondary goals:
- Finish localization coverage for the settings screens.
- Keep photo handling reliable and safe.
- Maintain backwards compatibility for existing data.

## 2. Current Architecture Snapshot

The app already follows a reasonable structure:
- `MainActivity` hosts the single-activity Compose UI.
- `AppViewModel` handles point CRUD, tagging, import/export, and location access.
- `SettingsViewModel` owns configuration state.
- `MapTimelineApp` provides app-level wiring through a lightweight graph.
- Room is used for persistent point and tag data.
- Settings are stored separately from the Room database.
- osmdroid provides the map layer and tile caching.

This is a solid base. The remaining work is mostly about tightening the expensive paths rather than rewriting the architecture.

## 3. Performance Priorities

### 3.1 Cold Start

Observed risk:
- The current startup path eagerly constructs the app graph and can reach Room setup early.

Plan:
1. Defer any repository or database work that is not needed for the first frame.
2. Keep `Application.onCreate()` minimal.
3. Move nonessential prewarming to a background coroutine.
4. Avoid synchronous reads in `MainActivity.onCreate()` unless they are required to render the first screen.

Target outcome:
- The UI appears as soon as possible, with data loading happening after the first frame.

### 3.2 Long-Running Work

Observed risk:
- The quick-add foreground service stays alive and repeats work on a fixed interval.

Plan:
1. Re-evaluate whether the service needs to remain active outside the quick-add feature.
2. Remove the 15-second self-rescheduling keepalive loop if the feature can function without it.
3. If the service must stay, make its lifetime narrower and explicitly tied to user action.
4. Prefer one-shot background work over persistent loops wherever possible.

Target outcome:
- Lower battery use and less background churn.

### 3.3 Map Rendering

Observed risk:
- The map screen invalidates every second while active.
- Map overlays are cleared and rebuilt when the point signature changes.

Plan:
1. Replace fixed-interval invalidation with event-driven invalidation where possible.
2. Keep the 1-second refresh only if it is needed for a visible feature.
3. Split stable overlays from dynamic overlays so unchanged markers are not recreated.
4. Cache marker icons or other expensive drawing artifacts when the inputs do not change.
5. Review whether the overlay rebuild can be reduced to incremental updates.

Target outcome:
- Lower CPU use during long map sessions and smoother interaction under large datasets.

### 3.4 List and Ordering Logic

Observed risk:
- `buildTodayOrder()` filters and sorts the full list.
- List rows also perform per-item date comparisons.

Plan:
1. Keep today-order calculation out of per-row code paths when possible.
2. Consider computing today group/order once in the ViewModel or repository layer.
3. Avoid repeated `Calendar` creation for each item if the list grows.
4. Use immutable derived state only when the underlying data changes.

Target outcome:
- Scrolling remains cheap even if the point count grows significantly.

## 4. Data and Storage

### 4.1 Room and Repository Boundaries

Plan:
1. Keep Room access behind repository interfaces.
2. Avoid synchronous database reads on the main thread.
3. Keep migration logic explicit and small.
4. Prefer `Flow` for UI-observable collections and suspend functions for one-shot reads/writes.

### 4.2 Photo Storage

Status:
- Photo handling already exists and is separate from point metadata.

Plan:
1. Keep file IO off the main thread.
2. Continue using cleanup for unused or orphaned photo files.
3. If path normalization is introduced, keep backward compatibility for old records.
4. Do not move binary image data into Room.

### 4.3 Cache Policy

Status:
- Cache size depends on the selected tile source.

Plan:
1. Keep the cache policy logic explicit and small.
2. Remove dead dependencies from recomposition if the selected settings do not affect runtime behavior.
3. If cache policy becomes user-facing in the runtime path, make the policy application happen once per actual change.

## 5. Startup and Initialization

Plan:
1. Keep `MapTimelineApp` as a thin wiring layer.
2. Initialize osmdroid configuration only once.
3. Make `MainActivity` responsible for UI bootstrap, not background setup.
4. Move any scanning, cleanup, or prefetch tasks to background coroutines triggered after UI startup.
5. Audit `LaunchedEffect(Unit)` blocks so they only start essential work.

## 6. Compose and State Management

Plan:
1. Keep state hoisted to `ViewModel` whenever it is shared or persisted.
2. Keep transient UI state local to the screen.
3. Use `remember` only for derived values that are expensive or stable across recompositions.
4. Avoid side effects in recomposition unless they are guarded by stable keys.
5. Review `observeNetworkStatus()` and similar helpers to ensure they register listeners only while the screen is visible.

## 7. Localization and Settings

Plan:
1. Finish coverage for all settings strings in the supported locales.
2. Keep the language picker sorted by English names.
3. Keep the selected language persisted and applied through the app locale APIs.
4. Continue verifying that release packaging does not strip non-English resources.

Acceptance for localization:
- No user-visible settings screen should fall back to English unless the locale genuinely has no translation.

## 8. Reliability and Safety

Plan:
1. Keep file cleanup and photo deletion fail-safe.
2. Never let orphan cleanup block the main thread or crash startup.
3. Preserve old data during any migration.
4. Prefer conservative fallbacks when a feature cannot complete.
5. Add logging only where it helps diagnose startup, cleanup, or import/export failures.

## 9. Concrete Work Phases

### Phase 1: Startup budget
Deliverables:
- Identify everything currently running during startup.
- Move nonessential work off the main path.
- Verify the app reaches first frame faster.

### Phase 2: Long-running cleanup
Deliverables:
- Remove or narrow the quick-add service keepalive behavior.
- Make background work event-driven.
- Verify battery and process activity improve.

### Phase 3: Map rendering optimization
Deliverables:
- Reduce periodic invalidation.
- Minimize overlay rebuild work.
- Cache expensive map artifacts.

### Phase 4: Data-flow cleanup
Deliverables:
- Move repeated list derivations out of composables where appropriate.
- Keep repository and ViewModel boundaries explicit.
- Tighten IO threading rules.

### Phase 5: Localization completion
Deliverables:
- Finish untranslated settings strings.
- Validate every supported locale.
- Confirm packaging includes the full resource set.

### Phase 6: Photo pipeline hardening
Deliverables:
- Ensure all photo IO is asynchronous.
- Keep orphan cleanup safe.
- Preserve backward compatibility for existing photo records.

## 10. Risks

- Risk: removing the quick-add keepalive could break notification behavior.
  - Mitigation: verify feature requirements before changing lifecycle rules.

- Risk: incremental map updates may be more complex than rebuilding overlays.
  - Mitigation: only optimize when point counts or redraw cost justify the complexity.

- Risk: path migration can break old photo references.
  - Mitigation: keep a compatibility resolver until the migration is proven safe.

- Risk: startup prewarming can move work instead of reducing it.
  - Mitigation: measure first-frame time before and after each change.

## 11. Acceptance Criteria

The plan is complete when:
- The first screen renders without unnecessary synchronous work.
- Long-running background behavior is limited to what the user actually needs.
- The map does not redraw on a fixed timer unless a visible feature requires it.
- Photo and file IO are off the main thread.
- Localization is complete for the supported languages.
- Existing user data still loads correctly.

## 12. Suggested Implementation Order

1. Reduce startup work in `MainActivity` and the app graph.
2. Remove or redesign the quick-add service keepalive loop.
3. Optimize map invalidation and overlay rebuild behavior.
4. Move derived list/grouping work out of composables where practical.
5. Finish localization and resource coverage checks.
6. Keep the photo pipeline safe and backward compatible.

## 13. Files Most Likely to Change

- `app/src/main/java/com/lavacrafter/maptimelinetool/MainActivity.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/MapTimelineApp.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/data/AppDatabase.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/ui/AppViewModel.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/ui/MapScreen.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/ui/ListScreen.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/ui/DayOrderUtils.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/notification/QuickAddService.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/notification/QuickAddReceiver.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/PointPhotoUtils.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/ui/SettingsStore.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh/strings.xml`

## 14. Verification Checklist

- Build succeeds with `./gradlew assembleDebug`.
- Launch time is acceptable on a cold start.
- Quick add still works after any service changes.
- Map interactions remain smooth after redraw changes.
- List ordering still matches the current behavior.
- No localized settings labels fall back unexpectedly.
- Existing photos and points still display correctly.
