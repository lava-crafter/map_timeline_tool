# Development History

> This summary is based on git history across all branches in this repository. The current `github_pages` branch mainly keeps the public-facing page and verification assets, while the Android app itself was developed on `main` and the feature branches.

## 1. Project bootstrap and core prototype

The app started on 2026-01-16 with a full Android project scaffold: Gradle wrapper support, settings, build files, manifest, application class, README, strings, and Compose theming. The first functional layer followed immediately after: the map screen, point dialog, main activity, ViewModel, Room entity/DAO/repository, file provider paths, and a GPX exporter.

On 2026-01-17, the project moved from a skeleton into a usable tracking tool. The first wave of feature work added tag support, list/map interaction, local-time presentation, today highlighting, timeout and cache settings, notification keep-alive behavior, background point handling, and auto-save/countdown behavior. Early housekeeping also cleaned build artifacts and kept the README in sync.

## 2. Interaction polish and localization

From 2026-01-18 through 2026-01-20, the focus shifted to usability. The tag workflow was unified across add and edit dialogs, zoom controls were improved, and language switching was added with locale persistence and restart handling. Auto-save, system theme following, import CSV, default tags, point-size control, and better add-page highlighting gradually rounded out the main workflow.

The settings screen also became much more capable in this period. Map download controls were expanded with visible-area selection, zoom range limits, cache policy controls, tile source selection, multi-thread download settings, downloaded-area management, and network status hints. Export flows improved as well, including date-based CSV range selection, while About/help pages and dark mode styling were polished.

## 3. Stability work and feature expansion

In February and March, the repo concentrated on reliability and broader data capture. One important fix was Android 15 Kotlin compatibility around `removeLast`, followed by Windows Gradle build fixes. The app then gained optional photo capture for points, sensor data logging, EXIF-aware photo preview, and cleaner photo action layouts.

At the same time, the architecture was tightened. Point writing logic was separated through use cases and repository gateways, and tag/settings management was also split out to reduce coupling. Backup and restore became a first-class feature: ZIP import/export was added for points, photos, tags, and related metadata, with legacy compatibility preserved. Default photo compression options and release version bumps followed this work.

## 4. Localization, publishing, and launch hardening

From late March into early April, the app was tuned for release. Language loading was fixed on startup, language-switch restarts were stabilized, and about/licenses copy was localized more completely. Location accuracy and quick-add background behavior were refined, and later commits focused on quick-add notifications, ZIP export tests, crash fixes, and location-fetching logic.

This stage also covered public release work. A closed-testing call-to-action and instruction block were added, contact information was simplified, a webpage and Google verification file were introduced, the repository layout was cleaned, and the project license was switched to Apache 2.0. Version codes continued to move forward through version code 19 as the app was hardened.

## 5. Overall trajectory

The project evolved from a simple map-based point logger into a fuller timeline tool with tags, photos, sensor data, export/import, offline maps, localization, backup/restore, and release-ready web assets.
