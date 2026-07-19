# Final Maintenance Review for Precise Location

Branch reviewed: `precise_location`

Date: 2026-05-23

This document replaces the earlier broad architecture-focused review. The project goal is now **final maintenance and stabilization**, not long-term feature expansion. The app has already been in production for about two months with a small user base, and the current app-store build is effectively the only production version. Therefore, this review prioritizes the current production upgrade path, data safety, and truthful location recording.

## Maintenance decision

The project should enter feature freeze after one final maintenance release.

The final maintenance release should only fix issues that affect:

1. user data safety,
2. production-version upgrade safety,
3. backup/export correctness,
4. location trustworthiness,
5. app-store/release documentation.

It should **not** spend more work on large architecture cleanup, `MainActivity` splitting, full domain/UI separation, or new feature development unless a change is directly required for the items above.

## Executive summary

The `precise_location` branch has already improved location acquisition:

- Fresh location acquisition no longer silently falls back to an old cached location.
- `GeoPoint` carries `accuracyMeters`, `fixTimeMs`, and `provider` during acquisition.
- Current-location fixes are filtered by age and accuracy.

The remaining critical problem is that the new precision metadata is not persisted into saved points. The app can know that a location was GPS/network/cached and how accurate it was, but that information is lost once the point is stored.

For a final maintenance release, this is the highest-value fix because it directly affects whether users can trust saved map points.

## Release scope

### In scope for the final maintenance release

- Persist location metadata: accuracy, fix time, provider.
- Add the migration needed by the current production version.
- Preserve location metadata in ZIP/CSV/GeoJSON/KML/KMZ export/import where practical.
- Make manual precise location different from best-effort/background location.
- Avoid silently saving stale/cached locations as if they were precise.
- Add minimal UI text showing location quality.
- Fix obvious backup/import data-safety issues.
- Update README and release notes.

### Out of scope

- Splitting `MainActivity.kt`.
- Large architecture refactors.
- Full domain/UI separation.
- New major features.
- Rich map accuracy circles.
- Full dependency/version-catalog migration.
- Support for old pre-release schemas that were never in the app store.
- Perfect long-term backup schema redesign.

## Core feature review

### Manual map point creation

Current status:

- `LocationUtils.getFreshLocation()` validates fix age and accuracy before accepting a fresh point.
- `GeoPoint` includes `accuracyMeters`, `fixTimeMs`, and `provider`.
- `AndroidLocationProvider` maps Android `Location` metadata into `GeoPoint`.

Remaining release blocker:

- `PointWriteUseCase.buildPoint()` only writes latitude and longitude into the domain point.
- `Point` has no location quality fields.
- `PointEntity` has no location quality fields.
- Therefore, saved points cannot show or export accuracy, provider, or fix age.

Final-release requirement:

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

`PointEntity` should mirror the same fields, and the mapper must preserve them both ways.

### Fresh location policy

Current status:

- Fresh point acquisition uses max age and max accuracy thresholds.
- This is a major improvement over blindly accepting old last-known locations.

Remaining issue:

- Locations without `accuracy` currently pass the acceptability check because the code only rejects locations where `hasAccuracy()` is true and `accuracy > maxAccuracyMeters`.

Final-release requirement:

- For manual precise point creation, reject locations without accuracy.
- For best-effort/background point creation, a no-accuracy location may be allowed only if metadata is preserved and the UI/export does not pretend that it is precise.

### Manual precise vs best-effort location

For the final maintenance release, avoid complex policy architecture. A simple split is enough:

```kotlin
suspend fun getPreciseLocation(timeoutMs: Long): GeoPoint?
suspend fun getBestEffortLocation(timeoutMs: Long): GeoPoint?
```

Recommended behavior:

| Flow | Behavior |
|---|---|
| Manual precise add | Fresh or very recent accurate location only. No stale fallback. |
| Quick add / auto add | Best-effort allowed, but metadata must be saved. |
| Map camera centering | Cached/last-known is fine because it does not create user data. |

If precise manual acquisition fails, show a clear message instead of silently saving a stale point:

```text
Unable to obtain a recent accurate location. Try again outdoors or enable GPS.
```

### Minimal UI trust indicators

Do not build complex map overlays for this release. Just show location quality in point detail/edit/list where practical.

Minimum display examples:

```text
GPS · ±12 m · fixed 3 s before save
Network · ±85 m · fixed 12 s before save
Cached overlay · accuracy unknown · fixed 2 h ago
```

This is enough for the final maintenance release. Accuracy circles and special marker styles can be left out.

## Data safety review

### Database migrations

Current status:

- Database version is 6.
- Migrations exist for 3→4, 4→5, and 5→6.
- Migrations for 1→2 and 2→3 are missing.

Production reality:

- The app-store version is effectively the only production version.
- There are likely very few users.
- It is not worth spending the final maintenance budget supporting every old development schema if those builds were not publicly shipped.

Final-release requirement:

- Guarantee migration from the current app-store version to the final maintenance version.
- Add a safe migration for location metadata, likely 6→7.
- If older pre-release versions are not supported, document that clearly.

Suggested migration:

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

When adding location quality fields:

- ZIP backup should include the new fields.
- CSV export should include the new fields if possible.
- CSV import should tolerate missing fields from older exports.
- GeoJSON should include the new fields as properties.
- KML/KMZ should include them in description or extended data.

This does not need to be a perfect long-term schema redesign. The goal is that final-version backups preserve final-version data.

### ZIP import photo safety

Previous risk:

- ZIP import may save photos before the database import fully succeeds.
- A failed import can leave orphaned photo files.

Final-release recommendation:

- Prefer temp extraction and cleanup if simple.
- If full transactional import is too invasive, at least add best-effort cleanup on failure and document the behavior.

### Point-tag import safety

Final-release requirement:

- Ensure repeated imports do not crash or duplicate point-tag relationships.
- Use a composite primary key and/or `OnConflictStrategy.IGNORE`.

### Deduplication model

A stable point UUID would be better long-term, but it is not mandatory for this final maintenance release unless repeated import bugs are otherwise hard to fix.

For final maintenance:

- Keep current `(timestamp, latitude, longitude)` deduplication if it works well enough.
- Do not introduce UUID unless it is low-risk.

## Source management review

### MainActivity

`MainActivity.kt` is large and mixes many responsibilities. That is a real maintainability issue, but it is **not a final-release blocker**.

Do not split `MainActivity` during this final maintenance pass unless a very small extraction is needed to safely fix data or location behavior.

Reason:

- The app is already in production.
- The feature set is nearly complete.
- Large refactors create regression risk.
- The goal is stable final maintenance, not long-term expansion.

### Domain/entity separation

Room entities leaking into UI is not ideal, but it should not block the final release.

Only touch model boundaries where required to persist location metadata.

### Dependency management

A full version-catalog migration is not required for this final maintenance pass.

Recommended minimal action:

- Run a dependency update check manually.
- Only update dependencies if there is a clear compatibility/security reason.
- Do not do broad dependency upgrades immediately before the final release unless testing time is available.

## Documentation updates required

### README.md

Update README to include:

- Current version number in all languages.
- Backup recommendation.
- Location accuracy disclaimer.
- Note that accuracy depends on GPS/network/device/Android services.
- Note that pre-release/dev builds may not have guaranteed migration support if that is the chosen policy.

Suggested text:

```text
This app is provided as-is. Please export or back up your data regularly, especially before updating. Location accuracy depends on device sensors, GPS/network availability, and Android location services.
```

### Release notes / app-store changelog

Add:

```text
This update improves location quality recording and backup compatibility. Please export a backup before updating if you have important saved points.
```

### Existing docs

Only update docs that help maintain or release the final version:

- location data fields,
- backup/export format,
- production upgrade policy,
- final testing checklist.

Do not spend time writing large architecture documents that will not guide future work.

## Final testing checklist

Before publishing:

1. Install current app-store build.
2. Create points with tags and photos.
3. Export ZIP backup.
4. Upgrade to final maintenance build.
5. Confirm points, tags, photos remain.
6. Confirm new points store accuracy/fix time/provider.
7. Export ZIP and import it into a clean install.
8. Confirm imported points preserve location metadata.
9. Test manual precise location failure path.
10. Test quick add / auto add if still enabled.
11. Test permission denied path.
12. Test CSV/GeoJSON/KML/KMZ export does not crash.

## Final recommendation

Proceed with one final maintenance release. Do not continue adding features after that unless required by Android/app-store policy or a data-loss/crash issue.

The release is ready when:

- user points do not silently lose location quality metadata,
- current production users can upgrade safely,
- backups preserve the data users need,
- the app does not pretend stale or inaccurate locations are precise,
- README/release notes honestly describe backup and accuracy limitations.
