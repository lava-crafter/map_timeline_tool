# Release Blockers and Fast-Track Plan for 0.1.7

Target: small-scale testing and promotion to production channel.

Branch baseline: `precise_location`

This document focuses only on the highest-priority work required before wider release. The goal is not perfect architecture, but a reliable and trustworthy location recording app that will not silently lose, corrupt, or misrepresent user location data.

## Release philosophy

For this release, correctness and trustworthiness are more important than feature count.

A point that clearly says:

```text
Network · ±82 m · fixed 8 s ago
```

is better than a point that looks precise but silently came from an old cached location.

The app must never mislead users about point quality.

## Priority 0 (must complete before wider release)

## P0.1 Persist precise location metadata

### Problem

`GeoPoint` now contains:

- `accuracyMeters`
- `fixTimeMs`
- `provider`

But saved points still discard this information.

### Required changes

### Domain model

Update `Point.kt`:

```kotlin
val locationAccuracyMeters: Float? = null
val locationFixTimeMs: Long? = null
val locationProvider: String? = null
```

### Room entity

Update `PointEntity.kt` with matching fields.

### Database migration

Increase DB version to 7.

Add migration:

```kotlin
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE points ADD COLUMN locationAccuracyMeters REAL")
        db.execSQL("ALTER TABLE points ADD COLUMN locationFixTimeMs INTEGER")
        db.execSQL("ALTER TABLE points ADD COLUMN locationProvider TEXT")
    }
}
```

### Mapper layer

Update:

- `PointEntity.toDomain()`
- `Point.toEntity()`

### Point creation

Update `PointWriteUseCase.buildPoint()`:

```kotlin
locationAccuracyMeters = location.accuracyMeters,
locationFixTimeMs = location.fixTimeMs,
locationProvider = location.provider,
```

### Export/import

Update:

- CSV export/import
- ZIP backup/import
- GeoJSON export
- KML/KMZ export

The app must preserve precision metadata through backup/restore.

### Required tests

- Room migration 6→7.
- Point save/load round-trip.
- ZIP backup/import round-trip.
- CSV round-trip.

## P0.2 Separate manual precise location from best-effort location

### Problem

`getBestEffortLocation()` is currently used for auto-add and may return stale fallback locations.

This is acceptable for automatic background behavior, but not for a user pressing a manual "save precise location" action.

### Required behavior

### Manual precise point

Policy:

- Try fresh location first.
- Reject stale fallback.
- Reject locations without accuracy.
- If no acceptable location is available, show an error or retry prompt.

### Best-effort quick add

Policy:

- Allow recent last-known.
- Allow stale fallback only if clearly marked.
- Save provider/accuracy/fix time.

### Implementation suggestion

Add separate methods:

```kotlin
suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint?
suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint?
```

or add explicit policy objects.

### Required UI behavior

If precise acquisition fails:

```text
Unable to obtain a recent accurate location.
Try again outdoors or enable GPS.
```

Do not silently save a stale fallback point.

## P0.3 Reject no-accuracy locations for precise mode

### Problem

Current logic accepts locations with no accuracy field.

### Required change

For precise/manual point creation:

```kotlin
if (!location.hasAccuracy()) return false
```

Best-effort/background modes may still allow them, but the UI and exports must preserve that fact.

## P0.4 Display location quality in UI

### Problem

Once metadata is persisted, users still need to see it.

### Required minimum UI

Every point detail/edit screen should display:

```text
Provider: GPS
Accuracy: ±12 m
Fix age: 3 s before save
```

Fallback example:

```text
Provider: cached_overlay
Accuracy: unknown
Fix age: 2 h before save
```

### Optional but recommended

- Warning icon for stale points.
- Marker style differences.
- Accuracy circle.

## P0.5 Migration compatibility

### Problem

Current DB only defines migrations:

- 3→4
- 4→5
- 5→6

Older upgrade paths are unclear.

### Required action

Decide one of:

### Option A — support all historical versions

Add missing migrations.

### Option B — unsupported legacy versions

Document clearly:

```text
Versions before X are considered unsupported pre-release builds.
```

If using option B:

- State it in release notes.
- State it in README.
- State it in testing docs.

## P0.6 ZIP import safety

### Problem

Imported photos may survive failed imports.

### Required behavior

- Extract imported photos into temp directory.
- Commit DB transaction first.
- Move photos only after success.
- Delete temp files on failure.

### Required tests

- Simulated import failure leaves no orphaned files.

## P0.7 Point-tag duplicate safety

### Required checks

Ensure:

```kotlin
@Entity(primaryKeys = ["pointId", "tagId"])
```

and:

```kotlin
@Insert(onConflict = OnConflictStrategy.IGNORE)
```

Repeated imports must not duplicate relationships or crash.

## Priority 1 (strongly recommended before production)

## P1.1 Add stable point UUID

Current deduplication uses:

```text
(timestamp, latitude, longitude)
```

This is not reliable long-term.

Recommended:

```kotlin
val uuid: String
```

Generate on point creation and preserve across backup/import.

## P1.2 Improve release/testing documentation

Update:

- README.md
- docs/architecture.md
- docs/backup-format.md
- docs/testing.md
- docs/release-checklist.md

## P1.3 Dependency version management

### Add version catalog

Move versions into:

```text
gradle/libs.versions.toml
```

### Add dependency update workflow

Suggested plugin:

```kotlin
plugins {
    id("com.github.ben-manes.versions") version "0.52.0"
}
```

Run:

```bash
./gradlew dependencyUpdates
```

before release builds.

## Priority 2 (can happen after wider testing)

## P2.1 MainActivity split

The app should eventually split:

- permissions,
- import/export,
- location policy,
- photo management,
- dialogs,
- navigation,
- map behavior

into separate controllers/state holders.

This is important for maintainability but should not block the first stable release.

## P2.2 Rich map accuracy visualization

Possible future additions:

- accuracy circles,
- low-confidence markers,
- provider-specific marker icons,
- stale point overlays.

## P2.3 Full domain/UI separation

Eventually stop exposing Room entities directly to UI.

## Small-scale testing checklist

Before opening testing to external users:

### Manual location tests

- Outdoor GPS test.
- Indoor network location test.
- GPS disabled test.
- Airplane mode test.
- Cold-start location test.
- Android 13+ notification permission flow.
- Permission denied flow.

### Backup tests

- ZIP export/import.
- CSV export/import.
- GeoJSON export.
- KML/KMZ export.
- Repeated import.
- Backup compatibility with old backups.

### Upgrade tests

- Fresh install.
- Upgrade from current production version.
- Upgrade with existing photos.
- Upgrade with tags.

### Failure tests

- Import interrupted.
- Storage full.
- Permission revoked during use.
- Missing photo file.

## Suggested release order

### Release candidate 1

Must include:

- persisted location metadata,
- migration 6→7,
- manual precise vs best-effort split,
- no-accuracy rejection for precise mode,
- UI quality display,
- export/import metadata preservation.

### Small-scale testing

Collect:

- real-world GPS accuracy reports,
- battery usage,
- indoor behavior,
- stale point reports,
- import/export compatibility reports.

### Production release

Only after:

- migration verified,
- backup verified,
- no silent stale point behavior remains.
