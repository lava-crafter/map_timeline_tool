# Final Maintenance Release Plan (0.1.7)

Target: one final maintenance release before feature freeze.

Branch baseline: `precise_location`

Review status: updated after latest implementation review on 2026-05-23.

This document is now a short remaining-work checklist. Most original P0 implementation work has landed. The project should not expand scope beyond the remaining data-safety and release-verification items below.

## Final maintenance philosophy

For this release:

- data safety matters more than architecture purity,
- truthful location metadata matters more than visual polish,
- stable upgrades matter more than new features,
- avoiding regressions matters more than refactoring.

The app should avoid large risky rewrites during this pass.

## Current implementation status

Implemented or mostly implemented:

- `Point` and `PointEntity` now include location metadata fields.
- Room database is bumped to version 7 with a 6→7 migration.
- Domain/entity mappers preserve location metadata.
- `PointWriteUseCase.buildPoint()` persists accuracy, fix time, and provider.
- CSV export/import preserves location metadata.
- GeoJSON export/import preserves location metadata.
- ZIP backup uses the updated CSV schema and carries location metadata.
- `getPreciseLocation()` exists and requires accuracy.
- Manual in-app add countdown path uses precise location instead of stale best-effort fallback.
- Edit point UI displays a simple location-quality summary.
- Point-tag relations use a composite key and conflict-ignore insertion.

Known gap after review:

- Notification quick add currently calls `getBestEffortLocation()`. This should be changed to precise/current location behavior because the notification action means "record my current position now" to the user.

Remaining work should stay small and focused.

## Remaining Priority 0 items

## P0.1 Fix ZIP manifest tag count

### Problem

ZIP export writes only the tags that are actually referenced by exported points, but the manifest may still report the original full `tags.size`.

### Required fix

Track the actual number of tags written to `tags.csv` and use that value in `backup_manifest.json`.

Suggested shape:

```kotlin
var exportedTagCount = 0

if (includeTagsInArchive) {
    val filteredTags = tags.filter { usedTagIds.contains(it.id) }
    exportedTagCount = filteredTags.size
    ...
}

val manifestJson = buildBackupManifestJson(
    ...
    tagCount = if (includeTagsInArchive) exportedTagCount else 0,
    ...
)
```

This is not a data-loss bug, but it makes the backup manifest inaccurate and should be fixed before release.

## P0.2 Fix notification quick add location policy

### Problem

The notification quick-add action is user-facing and means "save my current location now". It should not silently use stale last-known or cached fallback location.

Current implementation uses `getBestEffortLocation()`, which may use last-known or stale fallback location.

### Required fix

Change notification quick add to use precise/current acquisition, matching the in-app manual add path:

```kotlin
val location = graph.locationProvider.getPreciseLocation(QUICK_ADD_LOCATION_TIMEOUT_MS)
```

or an equivalent precise flow that:

- requires a fresh/recent location,
- requires accuracy,
- rejects stale fallback,
- shows failure if no acceptable location is available,
- still saves accuracy, provider, and fix time.

### Expected behavior

If notification quick add cannot obtain an accurate current location, it should fail clearly instead of saving an old point.

## P0.3 Confirm production database version

### Required manual check

Confirm the app-store build's Room database version.

If the production build is version 6, the new 6→7 migration is enough.

If the production build is version 1 or 2, the app still needs missing migrations or a documented unsupported-upgrade policy.

Do not spend time supporting unreleased development schemas unless they were actually shipped to users.

## P0.4 Upgrade test from current app-store build

### Required test

1. Install the current app-store build.
2. Create points with tags and photos.
3. Export a ZIP backup.
4. Upgrade to the `precise_location` final build.
5. Confirm points, tags, photos remain.
6. Create a new point.
7. Confirm the new point stores accuracy, provider, and fix time.
8. Use notification quick add.
9. Confirm notification quick add also stores an accurate current location with metadata.

This is more important than broad historical migration testing.

## P0.5 ZIP backup/import round-trip test

### Required test

1. Export ZIP from the final build.
2. Install a clean copy of the app.
3. Import the ZIP.
4. Confirm points, tags, photos, and location metadata survive.
5. Import the same ZIP again.
6. Confirm repeated import does not crash or duplicate point-tag relations.

## P0.6 Verify all current-location entry points

### Required check

Every user-facing "add current point" path should use `getPreciseLocation()` or equivalent precise behavior.

This includes:

- in-app manual add,
- notification quick add.

Best-effort location is acceptable only for non-saving map camera centering or explicitly background/best-effort behavior where stale fallback is expected and metadata is saved.

## P0.7 Review location fix-age wording

### Reason

The app currently normalizes saved timestamp using location fix time. This may make displayed fix age appear as `0s` in many cases.

### Required decision

Either:

- keep the current timestamp normalization and use conservative wording such as `fix age near save`, or
- preserve the original user event time and calculate `savedAt - fixTimeMs` literally.

Do not do a large data-model redesign for this release.

## Priority 1 items

## P1.1 README and release-note updates

README should include:

- backup recommendation,
- location-accuracy disclaimer,
- note about GPS/network/device dependency,
- note that old pre-release builds may not be supported if that is the chosen policy.

Suggested text:

```text
This app is provided as-is. Please export or back up your data regularly, especially before updating. Location accuracy depends on device sensors, GPS/network availability, and Android location services.
```

Release notes:

```text
This update improves location quality recording and backup compatibility. Please export a backup before updating if you have important saved points.
```

## P1.2 Keep ZIP import rollback best-effort

The current import flow cleans up photos on failure, but database import is still not a full transaction. That is acceptable for this final maintenance release if repeated-import tests pass.

Do not start a large repository transaction refactor unless tests reveal real breakage.

## Explicitly not required for this final release

The following should not block release:

- splitting `MainActivity.kt`,
- large architecture refactors,
- version-catalog migration,
- full domain/UI separation,
- stable UUID redesign,
- rich accuracy visualization,
- new major features,
- full support for old unreleased development schemas.

The project should prefer stable maintenance over ideal architecture.

## Final publish checklist

Before publishing:

1. Fix ZIP manifest tag count.
2. Change notification quick add to precise location.
3. Confirm production DB version.
4. Run production-build upgrade test.
5. Run ZIP backup/import/repeated-import test.
6. Confirm all current-location saving paths use precise location.
7. Decide fix-age wording.
8. Confirm CSV/GeoJSON/KML/KMZ export does not crash.
9. Update README/release notes.

## Post-release policy

After this release:

- stop adding major features,
- only fix crashes, data-loss issues, or Android/app-store compatibility problems,
- avoid large refactors unless required for compatibility,
- treat the project as stable low-frequency maintenance software.
