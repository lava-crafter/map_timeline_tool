# Precise Location Branch Review

Branch reviewed: `precise_location`

Date: 2026-05-23

This document reviews the current state of the `precise_location` branch as a full product/code review, not only as a diff against `main`. It focuses on the app's core purpose: reliably recording map timeline points with enough metadata to make those points trustworthy later.

## Executive summary

The branch improves the previous location behavior in an important way: fresh location acquisition no longer silently falls back to an old cached location, and `GeoPoint` now carries `accuracyMeters`, `fixTimeMs`, and `provider` during the location acquisition step.

However, the implementation is still incomplete for a production release. The new precision metadata is not persisted into `Point`, `PointEntity`, backup/export formats, or the UI. As a result, the app can temporarily know whether a point is precise, but that information is lost as soon as the point is saved.

The most important production blockers are:

1. Location quality metadata is not stored in the database.
2. Manual precise point creation and best-effort/background point creation are not clearly separated.
3. The database migration chain is still incomplete for older app versions.
4. ZIP import/export and point-tag restore still need stronger data-safety guarantees.
5. `MainActivity.kt` remains too large and mixes UI, permissions, import/export, photos, location flow, and app-level orchestration.
6. Existing documentation should be updated to reflect the current architecture, data model, release policy, and backup format.

## Core feature review

### Manual map point creation

Current status:

- `LocationUtils.getFreshLocation()` now validates fix age and accuracy before accepting a fresh point.
- `GeoPoint` includes `accuracyMeters`, `fixTimeMs`, and `provider`.
- `AndroidLocationProvider` maps Android `Location` metadata into `GeoPoint`.

Remaining issue:

- `PointWriteUseCase.buildPoint()` only writes latitude and longitude into the domain point.
- `Point` has no location quality fields.
- `PointEntity` has no location quality fields.
- Therefore, saved points cannot show or export accuracy, provider, or fix age.

Required model:

```kotlin
data class Point(
    val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val locationAccuracyMeters: Float? = null,
    val locationFixTimeMs: Long? = null,
    val locationProvider: String? = null,
    ...
)
```

The database entity should mirror the same fields:

```kotlin
val locationAccuracyMeters: Float? = null
val locationFixTimeMs: Long? = null
val locationProvider: String? = null
```

The mapper must preserve these fields both ways.

### Fresh location policy

Current status:

- Fresh point acquisition uses max age and max accuracy thresholds.
- This is a major improvement over blindly accepting old last-known locations.

Remaining issue:

- A location without `accuracy` currently passes the `isLocationAcceptable()` check because the code only rejects locations where `hasAccuracy()` is true and `accuracy > maxAccuracyMeters`.
- For precise manual point creation, locations without accuracy should normally be rejected.

Recommended policy split:

| Use case | Fresh required | Last-known allowed | Stale/cached allowed | No-accuracy location |
|---|---:|---:|---:|---:|
| Manual precise point | Yes | Only if very recent | No | Reject |
| Quick add / auto add | Prefer fresh | Yes | Only with metadata | Allow only with visible warning/metadata |
| Map initial camera | No | Yes | Yes | Accept |

The location API should make this explicit instead of hiding behavior inside one `getBestEffortLocation()` method.

### Best-effort location

Current status:

`getBestEffortLocation()` currently does:

1. acceptable last-known location,
2. fresh location,
3. stale last-known fallback.

Problem:

- This can choose a 30-minute-old acceptable last-known location before trying fresh location.
- This is reasonable for fast background operations, but not for a user-facing precise manual point.

Recommendation:

Create separate policies:

```kotlin
sealed class PointLocationPolicy {
    data object ManualPrecise : PointLocationPolicy()
    data object QuickAddBestEffort : PointLocationPolicy()
    data object MapCameraBestEffort : PointLocationPolicy()
}
```

or use explicit parameters:

```kotlin
LocationRequestPolicy(
    freshTimeoutMs = 8000,
    maxFreshAgeMs = 15000,
    maxFreshAccuracyMeters = 100f,
    allowRecentLastKnown = true,
    maxRecentLastKnownAgeMs = 30000,
    allowStaleFallback = false,
    allowCachedOverlay = false,
    requireAccuracy = true
)
```

### UI trust indicators

The UI should display location trust information for every point once the metadata is persisted.

Minimum display:

```text
GPS · ±12 m · fixed 3 s before save
Network · ±85 m · fixed 12 s before save
Cached overlay · accuracy unknown · fixed 2 h ago
```

For low-quality or fallback points, the UI should show a warning icon or secondary text. This is more important than visual polish because users need to know whether a point is trustworthy.

### Map marker accuracy

The current marker model appears to treat all points equally. After persisting accuracy, the map should optionally render an accuracy circle or at least distinguish low-quality points.

Suggested behavior:

- `accuracy <= 25m`: normal marker.
- `25m < accuracy <= 100m`: normal marker with secondary accuracy text.
- `accuracy > 100m` or null: warning marker or muted style.

## Data safety review

### Database migrations

Current status:

- Database version is 6.
- Migrations exist for 3→4, 4→5, and 5→6.
- Migrations for 1→2 and 2→3 are not present.

Risk:

- Users upgrading from old versions can hit migration failures.
- For a location/timeline app, destructive migration is dangerous because points are user data.

Required before formal release:

1. Add missing migrations for old versions, or explicitly document that pre-release data is unsupported.
2. Add migration tests for all supported paths.
3. When adding location metadata fields, bump database to version 7 and add a 6→7 migration.

Suggested new migration:

```kotlin
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE points ADD COLUMN locationAccuracyMeters REAL")
        db.execSQL("ALTER TABLE points ADD COLUMN locationFixTimeMs INTEGER")
        db.execSQL("ALTER TABLE points ADD COLUMN locationProvider TEXT")
    }
}
```

### Backup/import/export consistency

The app supports ZIP backup, CSV, GeoJSON, KML/KMZ, and photo export. This is valuable, but it also means every new field needs a clear compatibility rule.

When adding location quality fields:

- ZIP `points.csv` or backup point payload must include the new fields.
- CSV export should include accuracy/fix time/provider.
- GeoJSON should include these as properties.
- KML/KMZ should include them in extended data or description.
- Importers should tolerate missing fields for older backups.

### ZIP import photo safety

Current risk from previous review:

- ZIP import saves photos before the database import fully succeeds.
- A failed import can leave orphaned photo files.

Recommendation:

- Extract imported photos into a temp import directory first.
- Import database records in a transaction.
- Move photos into the final photo directory only after DB success.
- On failure, delete the temp directory.

### Point-tag import safety

Current risk from previous review:

- Point-tag pairs may be duplicated across repeated imports if the DB constraint or DAO conflict strategy is not strict.

Recommendation:

- Ensure `PointTagCrossRef` has composite primary key `(pointId, tagId)`.
- Change insert to `@Insert(onConflict = OnConflictStrategy.IGNORE)`.
- Add tests for repeated ZIP import.

### Deduplication model

Current point import deduplication uses `(timestamp, latitude, longitude)`.

Problem:

- This can incorrectly merge points created at the same time and coordinates.
- It cannot robustly track the same point across devices or backups.

Recommendation:

- Add a stable `uuid` field to points before the backup format is considered stable.
- Use `uuid` as the primary import/export identity.
- Keep `(timestamp, latitude, longitude)` only as legacy fallback.

## Source management and architecture review

### MainActivity is overgrown

`MainActivity.kt` currently acts as the central coordinator for too many responsibilities:

- Navigation state.
- Runtime permissions.
- Quick add service startup.
- Location acquisition entry points.
- CSV/GeoJSON/KML/KMZ/ZIP export launchers.
- CSV/ZIP import launchers.
- Photo capture and compression/deletion.
- Settings restore.
- Map cache cleanup.
- Dialog state for add/edit/export/import.
- Back handlers.

This makes regressions more likely. Location policy changes should not require editing a huge Activity file.

Recommended split:

- `MainRoute.kt` or `AppScaffold.kt`: high-level screen composition only.
- `LocationPointController.kt`: manual/quick/auto point creation flows.
- `ExportController.kt`: export option state and document launchers.
- `ImportController.kt`: import launchers and result handling.
- `PhotoCaptureController.kt`: photo capture, compression, final path management.
- `PermissionController.kt`: permission state and request order.
- `PointEditorState.kt`: add/edit dialog state.
- `MapCacheController.kt`: cache clearing/download operations.

### Domain/entity separation

The README architecture says the app is moving toward domain-first architecture, but UI still observes `PointEntity` through `AppViewModel.points`.

Recommendation:

- Keep Room entities internal to the data layer.
- UI should observe `PointUiModel` or domain `Point`.
- Mappers should live at clear boundaries.

### Location policy ownership

Location policy should not be hardcoded in multiple UI paths. It should live in one domain/usecase layer.

Suggested objects:

- `AcquirePointLocationUseCase`
- `PointLocationPolicy`
- `LocationQuality`
- `LocationAcquisitionResult`

Example:

```kotlin
data class LocationAcquisitionResult(
    val point: GeoPoint?,
    val quality: LocationQuality,
    val failureReason: LocationFailureReason? = null
)
```

This lets UI show meaningful messages instead of silently returning `null`.

### Dependency management

Current dependencies are declared directly in `app/build.gradle.kts`.

Recommendation:

- Move versions to `gradle/libs.versions.toml`.
- Add Gradle Versions Plugin or another dependency update workflow.
- Track Android Gradle Plugin, Kotlin, Compose compiler/plugin, AndroidX, Room, osmdroid, and Google Play Services versions together.

Suggested plugin:

```kotlin
plugins {
    id("com.github.ben-manes.versions") version "0.52.0"
}
```

Then run:

```bash
./gradlew dependencyUpdates
```

## Documentation update review

The repository documentation should be updated before small-scale testing.

### README.md

Needed updates:

- Ensure English and Chinese version numbers match.
- Add a short explanation of location accuracy behavior.
- Explain when the app uses fresh GPS/network/fused location versus best-effort fallback.
- Explain that low-accuracy/fallback points may be marked in the UI once implemented.
- Add backup/export compatibility notes.

### docs/architecture.md

Needed updates:

- Add location data flow:

```text
permission → provider selection → fresh/current location → quality validation → point creation policy → persisted point → export/backup
```

- Document `LocationProvider`, `AcquirePointLocationUseCase`, and the difference between domain `GeoPoint`, persisted `Point`, and UI model.
- Document where runtime permissions are handled and where business policy is handled.

### docs/data-model.md or new docs/location-data-model.md

Add a dedicated data model document if one does not exist.

Required content:

- Point fields.
- Location metadata fields.
- Semantics of timestamp vs location fix time.
- Accuracy units.
- Provider values.
- Import/export compatibility.
- Migration policy.

### docs/backup-format.md

Needed updates:

- ZIP manifest schema.
- Points schema.
- Tags schema.
- Point-tag schema.
- Photos layout.
- New location metadata fields.
- Backward compatibility rules for old backups.
- Failure/rollback expectations.

### docs/release-checklist.md

Add or update release checklist:

- Migration tests pass.
- Backup round-trip tests pass.
- CSV import/export round-trip tests pass.
- Manual location test on real device.
- Indoor/network location test.
- Airplane mode/offline map test.
- Permission denied test.
- Android 13+ notification permission test.
- Fresh install test.
- Upgrade install test.

### docs/testing.md

Needed tests:

- Location freshness policy tests.
- Accuracy rejection tests.
- No-accuracy rejection tests.
- Stale fallback tests.
- Room migration tests.
- ZIP repeated import tests.
- ZIP photo rollback tests.
- Export schema compatibility tests.

## Release risk assessment

### Do not push to formal channel until fixed

- Location metadata persistence.
- Manual precise vs best-effort location behavior.
- Room migration for new fields.
- Backup/export compatibility for new fields.

### Acceptable for small internal test after fixed

- UI may be simple as long as accuracy/provider/fix time are visible somewhere.
- Dependency versions can be updated after the location model lands, as long as no known security issue is present.
- MainActivity split can be postponed if tests cover critical flows.

### Should not block small test, but should be tracked

- Full architecture split.
- Stable point UUID.
- Version catalog migration.
- Rich map accuracy circle rendering.

## Recommended implementation order

1. Persist location metadata in `Point`, `PointEntity`, mappers, and Room migration.
2. Update `PointWriteUseCase.buildPoint()` to copy metadata from `GeoPoint`.
3. Split manual precise and best-effort location policies.
4. Reject no-accuracy locations for precise manual point creation.
5. Show location quality in point detail/list/edit UI.
6. Update CSV/GeoJSON/KML/KMZ/ZIP export and import schemas.
7. Add tests for location policy and migration.
8. Fix ZIP import photo rollback and point-tag conflict handling.
9. Update README and docs.
10. Run small-scale real-device testing.
