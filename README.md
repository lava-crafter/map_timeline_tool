# Map Timeline Tool

**Language**: English | [中文](#中文说明)

Map Timeline Tool is a small Android app for manually logging points. It works offline, keeps data on the device, shows points on a map and in a list, and can export CSV files for other tools.

https://play.google.com/store/apps/details?id=com.lavacrafter.maptimelinetool

Current app version: **0.1.6**

## Features
- One-tap point logging — saves timestamp, latitude/longitude, title, and an optional note.
- Map view (osmdroid + OSM tiles) — markers show details; today's points are color-coded by order.
- List view — all points are listed with coordinates and a simple "nth point today" indicator.
- Quick Add notification — log a point from a persistent notification.
- UTC timestamps in exports and map details for consistency.
- CSV export via the system file picker.
- ZIP export/import (points.csv + photos) via the system file picker.
- Dark theme toggle.
- English and Chinese UI.

## How to Run
1. Open the project in Android Studio.
2. Run on a device and grant location permission.
3. Tap the center Add button to record a point.
4. Export/Import CSV or ZIP from Settings.

## Notes
- CSV is exported using the system document picker.
- Map tiles come from OpenStreetMap; only tiles need network access.
- Location and data stay on your device.

## Release signing
- The release keystore is read from `~/.android/my-release-key.jks` on both Linux and Windows, using the current user's home directory.
- Create `~/.android/release-signing.properties` with these keys:
	- `storePassword=...`
	- `keyAlias=...`
	- `keyPassword=...`
- You can also override them with Gradle properties or environment variables:
	- `RELEASE_STORE_PASSWORD`
	- `RELEASE_KEY_ALIAS`
	- `RELEASE_KEY_PASSWORD`

## Architecture
- UI state is managed in `AppViewModel`.
- Point write operations are separated into `domain/usecase/PointWriteUseCase`.
- Tag management operations are separated into `domain/usecase/TagManagementUseCase`.
- Settings access is separated through `domain/usecase/SettingsManagementUseCase` and `domain/repository/SettingsManagementGateway`.
- Domain repository interfaces (`PointRepositoryGateway` / `SettingsManagementGateway`) use domain models to avoid direct coupling to Room/UI types.
- `MapTimelineApp` provides lightweight app-level providers for shared use cases.
- `AppViewModel` consumes dependencies via factory + app providers instead of constructing repositories/use cases internally.
- Data access is abstracted via `domain/repository/PointRepositoryGateway` and implemented by `data/PointRepository`.

## Open Source & Attribution
The app's Settings and About screens list the open-source components and attribution requirements.

How the list is maintained:
- Runtime dependency licenses are generated from `releaseRuntimeClasspath` into `app/src/main/res/raw/third_party_licenses` and `app/src/main/res/raw/third_party_license_metadata`.
- Non-Maven attributions (for map/data providers) are maintained in `app/src/main/oss/manual_notices.csv` and merged into the same in-app OSS list.

Summary:
- AndroidX / Jetpack Compose (Material3 + material-icons-extended) / Room / ExifInterface / Material Components / Kotlin (Apache-2.0)
- osmdroid (Apache-2.0)
- JUnit 4 (test dependency, EPL-1.0)
- OpenStreetMap data (ODbL — attribution required)

## AI assistance
AI tools were used to help with parts of the development. Final decisions, review and integration were done by the developer.

---

# 中文说明

Map Timeline Tool 是一款用于手动记录位置点的简洁 Android 应用，支持离线使用，数据保存在设备上，可在地图与列表中查看，并能导出 CSV 文件。

当前应用版本：**0.1.5**

## 功能
- 一键打点：记录时间、经纬度、标题和可选备注。
- 地图视图（osmdroid + OSM 瓦片）：显示点位详情，当日点位按顺序用颜色区分。
- 列表视图：列出所有点位与坐标，并显示“当日第 n 个点”。
- 通知栏快速打点：可通过常驻通知记录位置。
- 导出与地图详情使用 UTC 时间以保持一致性。
- CSV 通过系统文件选择器导出。
- 支持通过系统文件选择器导出/导入 ZIP（points.csv + photos）。
- 深色模式支持。
- 中英文界面。

## 运行
1. 在 Android Studio 中打开工程。
2. 运行到设备并授予定位权限。
3. 点击底部中间的打点按钮进行记录。
4. 在设置中选择导入/导出 CSV 或 ZIP。

## 说明
- CSV 通过系统文件选择器导出。
- 地图瓦片来自 OpenStreetMap，仅加载瓦片需要联网。
- 位置与数据保存在本地。

## 架构
- `AppViewModel` 负责界面状态调度。
- 点位写入逻辑已拆分到 `domain/usecase/PointWriteUseCase`。
- 标签管理逻辑已拆分到 `domain/usecase/TagManagementUseCase`。
- 设置管理通过 `domain/usecase/SettingsManagementUseCase` 与 `domain/repository/SettingsManagementGateway` 分离。
- 领域仓储接口（`PointRepositoryGateway` / `SettingsManagementGateway`）改为使用 domain model，避免直接依赖 Room/UI 类型。
- `MapTimelineApp` 提供轻量 Provider 以复用核心 use case。
- `AppViewModel` 通过 factory + app provider 注入依赖，避免在 ViewModel 内部手动构造仓储/use case。
- 数据访问通过 `domain/repository/PointRepositoryGateway` 抽象，并由 `data/PointRepository` 实现。

## 开源与署名
应用内「设置」与「关于」页面列出了所用开源库与署名要求。

摘要：
- AndroidX / Jetpack Compose（Material3 + material-icons-extended）/ Room / ExifInterface / Material Components / Kotlin（Apache-2.0）
- osmdroid（Apache-2.0）
- JUnit 4（测试依赖，EPL-1.0）
- OpenStreetMap 数据（ODbL，需要署名）

## AI 说明
开发过程中使用了 AI 辅助工具，最终设计与决策由开发者负责。
