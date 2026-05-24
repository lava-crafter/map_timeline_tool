# Final Maintenance Release Plan (0.1.7)

Target: one final maintenance release before feature freeze.

Branch baseline: `precise_location`

This document replaces the earlier broader release-blocker plan. The project is no longer aiming for major expansion or large architecture cleanup. The goal is to safely stabilize the production app with minimal-risk changes.

## Final maintenance philosophy

For this release:

- data safety matters more than architecture purity,
- truthful location metadata matters more than visual polish,
- stable upgrades matter more than new features,
- avoiding regressions matters more than refactoring.

The app should avoid large risky rewrites during this pass.

## Final release goals

The release is complete when:

1. current production users can upgrade safely,
2. saved points preserve location-quality metadata,
3. backups preserve important data,
4. the app does not silently pretend stale/cached locations are precise,
5. obvious import/export data-loss issues are fixed,
6. release notes and README honestly describe backup and accuracy behavior.

After this release, the project should enter low-frequency maintenance mode.

## Priority 0 (must complete)

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

- ZIP backup/import
- CSV export/import if practical
- GeoJSON export
- KML/KMZ export if practical

Do not redesign the entire backup system. Only ensure the final-version backup preserves final-version data.

### Required tests

- Upgrade from current production build.
- Save/load round-trip.
- ZIP backup/import round-trip.

## P0.2 Manual precise vs best-effort location

### Goal

Do not silently save stale/cached locations as if they were precise manual points.

### Required behavior

### Manual precise add

- Prefer fresh accurate location.
- Reject locations without accuracy.
- Reject stale fallback.
- Show a clear retry/error message on failure.

### Quick add / auto add

- Best-effort behavior is acceptable.
- Save provider/accuracy/fix time metadata.

### Recommended minimal implementation

Keep it simple:

```kotlin
suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint?
suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint?
```

Do not build a large policy engine during this maintenance pass.

## P0.3 Reject no-accuracy locations for precise mode

### Problem

Current logic accepts locations without accuracy.

### Required change

For manual precise location:

```kotlin
if (!location.hasAccuracy()) return false
```

Background/best-effort flows may still allow them if metadata is preserved.

## P0.4 Minimal UI location-quality display

### Goal

Users should know whether a point is trustworthy.

### Required UI

Display simple metadata somewhere practical:

```text
GPS · ±12 m · fixed 3 s before save
Network · ±85 m · fixed 12 s before save
Cached overlay · accuracy unknown · fixed 2 h ago
```

### Explicitly not required

- accuracy circles,
- advanced map overlays,
- custom marker rendering.

## P0.5 Production upgrade safety

### Production reality

The app-store build is effectively the only production schema.

### Required action

- Guarantee migration from the current app-store build to the final maintenance release.
- If old unreleased/dev schemas are unsupported, document that clearly instead of spending large effort on them.

## P0.6 Backup/import safety

### Required fixes

- Avoid obvious orphan-photo problems during failed ZIP import.
- Ensure repeated imports do not duplicate point-tag relations or crash.

### Minimal acceptable approach

- Best-effort temp cleanup is acceptable.
- `OnConflictStrategy.IGNORE` is acceptable.
- Avoid invasive large refactors.

## Priority 1 (recommended but optional)

## P1.1 README and release-note updates

README should include:

- backup recommendation,
- location-accuracy disclaimer,
- note about GPS/network/device dependency,
- note that old pre-release builds may not be supported.

Suggested text:

```text
This app is provided as-is. Please export or back up your data regularly, especially before updating. Location accuracy depends on device sensors, GPS/network availability, and Android location services.
```

### Release notes

```text
This update improves location quality recording and backup compatibility. Please export a backup before updating if you have important saved points.
```

## P1.2 Minimal dependency review

Do not perform broad dependency upgrades immediately before release.

Only:

- check for obvious security/compatibility issues,
- update dependencies if necessary for Android/app-store compatibility.

## Explicitly not required for this final release

The following should not block release:

- splitting `MainActivity.kt`,
- large architecture refactors,
- version-catalog migration,
- full domain/UI separation,
- stable UUID redesign,
- rich accuracy visualization,
- new major features.

The project should prefer stable maintenance over ideal architecture.

## Final testing checklist

Before publishing:

1. Install current app-store build.
2. Create points with tags and photos.
3. Export ZIP backup.
4. Upgrade to final maintenance build.
5. Confirm points/tags/photos remain.
6. Confirm new points preserve accuracy/provider/fix time.
7. Export ZIP and import into a clean install.
8. Confirm imported points preserve metadata.
9. Test manual precise-location failure path.
10. Test quick add / auto add if still enabled.
11. Test permission-denied path.
12. Test CSV/GeoJSON/KML/KMZ export for crashes.

## Post-release policy

After this release:

- stop adding major features,
- only fix crashes, data-loss issues, or Android/app-store compatibility problems,
- avoid large refactors unless required for compatibility,
- treat the project as stable low-frequency maintenance software.
