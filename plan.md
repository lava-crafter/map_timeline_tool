## Plan: Backup feature for the app

Use the existing ZIP export/import pipeline as the backup carrier, but extend it into a true backup system: add a versioned manifest, include app settings in the archive, support manual and scheduled full backups, and define merge-safe restore rules so backups can be re-applied without forcing a wipe.

**Implementation Method**
- Build the backup as a ZIP wrapper around the current export data, then add metadata files inside the archive instead of changing the outer file type.
- Keep old ZIP layouts readable by making the importer detect the presence of the new metadata files and fall back to the legacy layout when they are missing.
- Generate the archive first, then use either the document picker for local backup or the share sheet for external backup/share.
- Treat the new backup metadata as additive: old backups stay valid, and new backups can be imported by both the new and legacy-compatible import path as far as their contents allow.

**Steps**
1. Define the first-stage backup target, so the rest of the work is aligned.
   - Add a dedicated Export & Backup page under Settings, instead of mixing backup actions into the existing export flow.
   - Split the page into two actions: local backup/export and external backup/share.
   - Local backup/export means generating a ZIP and saving it through the existing system document picker, which is the current export path.
   - External backup/share means generating the ZIP locally first, then launching the system share sheet so the user can send it to cloud drives, messaging apps, email, or other apps.
   - Keep ZIP as the container format so the backup can be saved into a cloud drive folder or shared to another app without extra conversion.
   - Name ZIP files with the current date and time so backups are easy to identify and sort.
   - Keep backward compatibility with older ZIP packages so existing exports still import correctly.
   - Put a plain description file inside the ZIP that records the export time and the app version used to create it.
   - Record the archive contents in the description file, including point count, tag count, photo count, sensor data inclusion, and the checksum of each CSV file.
   - Store photos in a nested photo archive inside the backup ZIP if possible, so photo count and photo checksum data can be recorded cleanly.
   - Keep support for the current photo layout during import so older ZIP packages and older backups still restore correctly.
   - Add a manifest file that declares backup version, created-at timestamp, app version, and which sections are present.
   - Keep backward compatibility with the current ZIP layout so old exports still import.
   - Scope the backup content to points, tags, point-tag relations, point photos, and app settings.
   - Exclude offline map tiles/cache and downloaded area caches for now, because those can become very large and are better treated as separate storage.
   - Treat OneDrive / Google Drive as transport targets in phase 1, not as directly integrated sync providers.

2. Extend the export/import layer to carry settings in addition to the existing data.
   - Reuse `ZipExporter` and `ZipImporter` instead of creating a second backup format.
   - Add a settings snapshot representation that serializes the values currently stored in `SettingsStore` and exposed through `SettingsRepository`.
   - Add restore logic that can write those settings back through the existing settings gateway rather than mutating SharedPreferences ad hoc.
   - Include archive integrity metadata for counts and checksums, and validate it during import so a mismatch can warn that the data may be corrupted.
   - Implement dual-format import: first try the new backup metadata and nested photo archive, then fall back to the current flat ZIP layout if those entries are absent.
   - Preserve the current point/tag/photo CSV layout so the existing importer keeps working.

3. Make restore merge-safe instead of pure overwrite.
   - Add a deterministic deduplication rule for imported points, ideally based on a stable fingerprint of timestamp, coordinates, title, note, photo hash/path, and sensor fields.
   - Merge tags by normalized name, as the current importer already does, but extend it so repeated restores do not create duplicates.
   - Rebuild point-tag relations against the merged point and tag identities.
   - Decide how settings should merge: backup values should overwrite included settings, while omitted settings should leave current values intact.
   - If counts or checksums do not match on import, stop short of applying the backup and show a corruption warning instead of silently continuing.

4. Add periodic backup execution on Android.
   - Introduce WorkManager for scheduled backup jobs, because the project currently has no background scheduler dependency.
   - Create a worker that reads the current data snapshot, runs the ZIP export, and writes it to a user-chosen URI or app-managed backup location.
   - Keep a manual “Back up now” action so the user can trigger a full backup on demand.
   - Surface clear success/failure feedback, especially when storage access or permissions block the write.

5. Expose backup controls in the settings UI.
   - Add a dedicated Export & Backup page to `SettingsScreen` with clearly separated local export, local backup, and external backup/share actions.
   - Keep restore on the same page or a nearby data management page, but do not bury it inside the generic export flow.
   - Wire `MainActivity` to launch the file picker for local backup destination selection and the share sheet for external backup/share.
   - Add settings for backup cadence and, if needed, the chosen destination URI or folder.
   - Keep the current CSV export/import actions available, but label them clearly so users can distinguish ad hoc export from backup/restore.

6. Push the state and data plumbing through the app layers.
   - Extend `AppViewModel` with backup and restore entry points, including a restore flow that can load a ZIP, apply settings, import data, and report any skipped duplicates.
   - Add any missing repository/use-case methods needed to export settings cleanly and to restore them in one place.
   - If the backup UI needs a confirmation step, keep it in the UI layer and leave the actual merge logic in the view model or use case layer.

7. Cover the new behavior with tests.
   - Add round-trip tests for backup export/import with points, tags, photos, and settings.
   - Add merge tests that verify duplicate points and tags are not duplicated after re-import.
   - Add compatibility tests to ensure the current ZIP format still imports after the backup changes.
   - Add one test for scheduled backup configuration or worker wiring if WorkManager is introduced in the app module.

**Relevant files**
- `/home/lava/code/map_timeline_tool/app/src/main/java/com/lavacrafter/maptimelinetool/export/ZipExporter.kt` and `/home/lava/code/map_timeline_tool/app/src/main/java/com/lavacrafter/maptimelinetool/export/ZipImporter.kt` — primary backup archive format and import/export logic.
- `/home/lava/code/map_timeline_tool/app/src/main/java/com/lavacrafter/maptimelinetool/ui/SettingsStore.kt` and `/home/lava/code/map_timeline_tool/app/src/main/java/com/lavacrafter/maptimelinetool/data/SettingsRepository.kt` — source of truth for backed-up app settings.
- `/home/lava/code/map_timeline_tool/app/src/main/java/com/lavacrafter/maptimelinetool/ui/SettingsScreen.kt` — backup UI entry points.
- `/home/lava/code/map_timeline_tool/app/src/main/java/com/lavacrafter/maptimelinetool/MainActivity.kt` — file picker launchers and current export/import wiring.
- `/home/lava/code/map_timeline_tool/app/src/main/java/com/lavacrafter/maptimelinetool/ui/AppViewModel.kt` — import/restore orchestration and merge behavior.
- `/home/lava/code/map_timeline_tool/app/src/main/java/com/lavacrafter/maptimelinetool/data/AppDatabase.kt` — database scope and data model boundaries.
- `/home/lava/code/map_timeline_tool/app/src/test/java/com/lavacrafter/maptimelinetool/export/ZipExportImportTest.kt` — current ZIP test baseline to extend.
- `/home/lava/code/map_timeline_tool/app/build.gradle.kts` — add WorkManager if scheduled backups are implemented.

**Verification**
1. Run the ZIP export/import tests and extend them until the backup archive round-trips points, photos, tags, and settings.
2. Verify a current non-backup ZIP still imports successfully to confirm backward compatibility.
3. Manually test a restore into a non-empty database to confirm merge behavior does not duplicate points or tags.
4. If scheduled backups are added, verify the worker can run from WorkManager and that cancellation/retry behaves correctly.
5. Check the settings screen flows on device or emulator: manual backup, restore picker, and schedule toggle.

**Decisions**
- Use full scheduled backups first, not incremental backups. Incremental backup would need change tracking and a more complex restore path, so it should be a later enhancement.
- Reuse the existing ZIP format rather than inventing a second backup file type.
- Put backup actions on a dedicated Settings subpage so export, local backup, and external share are explicit and not overloaded into one button.
- Prefer nested photo packaging inside the backup ZIP if it can be added without breaking old imports; otherwise keep old photo entries and support both layouts.
- Treat settings as part of backup scope, but keep map tile caches and downloaded area caches out of scope for now.
- Keep restore merge-based, with settings overwritten only for included keys and data deduplicated by stable identity rules.

**Further Considerations**
1. Cloud backup can be done in two ways: save the ZIP into a cloud provider folder through the system file picker/Storage Access Framework, or add direct provider integration for true background upload.
2. For OneDrive and Google Drive, the lowest-risk first step is SAF-based destination selection; this works if the provider is installed and exposed by the system picker.
3. If you want downloaded areas included later, it should probably be a separate opt-in section because that data can dominate backup size.