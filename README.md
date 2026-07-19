# Map Timeline Tool

**Language**: English | [中文](#中文说明)

Map Timeline Tool is an offline-first Android app for manually logging points. Your points, photos, and settings stay in the app sandbox; if you enable Android system backup or device transfer, Android may back up or transfer them through your device's configured system backup destination. You can also manually create a full ZIP backup from **Settings → Backup and Restore**.

- Repository: https://github.com/muchenjiang/map_timeline_tool
- Google Play: https://play.google.com/store/apps/details?id=com.lavacrafter.maptimelinetool
- Current app version: **0.1.8**

## Highlights
- Manual point logging with timestamp, coordinates, title, note, tags, optional photo, and location-quality metadata.
- Map and list views, with cached/downloaded tiles available for offline reuse.
- Optional Quick Add persistent notification for one-tap logging.
- Optional sensor capture: barometer, ambient light, accelerometer, gyroscope, magnetometer, and approximate 3-second dBFS noise.
- Export: CSV, GeoJSON, KML, KMZ, ZIP.
- Import: CSV, ZIP.
- The in-app language picker is currently maintained for **English** and **Simplified Chinese**. Older translation files may still exist for compatibility, but they are not treated as fully supported.

## Permissions
- **Location**: used for Add, Quick Add, and map centering. If a fresh precise fix is unavailable, the app may allow an approximate or last-known fallback instead of always failing.
- **Notifications** (Android 13+): only needed if you enable the optional Quick Add notification.
- **Microphone**: only needed if you enable noise capture. Stored noise is approximate dBFS, not calibrated SPL.

## Backup / Export / Import
- **ZIP** is the recommended full backup before changing devices or reinstalling. It can include points, related tags, photos, sensor fields, and settings metadata.
- **CSV** is the simplest spreadsheet-style point table.
- **GeoJSON / KML / KMZ** are map/GIS export formats for other tools.
- **CSV import** adds points only.
- **ZIP import** can restore a fuller backup package, including photos, tags, and settings when present.

## Compatibility
- Current Gradle config: `minSdk 24`, `targetSdk 37`, `compileSdk 37`.
- The current app module targets Android 7.0+ devices.

## How to Run
1. Open the project in Android Studio.
2. Run on a device or emulator with Android location services available.
3. Grant location permission to add points or center the map.
4. Optionally enable Quick Add notification and microphone-based noise capture in Settings.
5. Use **Settings → Backup and Restore** to export data or create a ZIP backup.

## Notes
- The app works offline for local data access. Network is only needed for live map tiles, opening external links, or sharing to other apps/services.
- Downloaded/cached tiles can be reused offline, but large areas or high zoom ranges can take significant time and storage.
- EOX Sentinel-2 Cloudless tiles are for non-commercial use only (BY-NC-SA).
- Old unreleased or pre-release builds may not be upgrade-compatible with this maintenance release.

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

Map Timeline Tool 是一款离线优先的 Android 手动打点应用。点位、照片和设置保存在应用沙箱中；如果你启用了 Android 系统备份或设备迁移，Android 可能通过设备已配置的系统备份目的地备份或迁移这些数据。你也可以在 **设置 → 备份与恢复** 中手动创建完整 ZIP 备份。

- 项目仓库：https://github.com/muchenjiang/map_timeline_tool
- Google Play：https://play.google.com/store/apps/details?id=com.lavacrafter.maptimelinetool
- 当前应用版本：**0.1.8**

## 主要功能
- 手动打点：记录时间、经纬度、标题、备注、标签、可选照片，以及定位质量元数据。
- 地图与列表双视图，缓存/下载后的瓦片可离线复用。
- 可选的通知栏“快速打点”常驻通知。
- 可选传感器采集：气压、环境光、加速度、陀螺仪、磁力计，以及近似 3 秒 dBFS 噪音。
- 导出：CSV、GeoJSON、KML、KMZ、ZIP。
- 导入：CSV、ZIP。
- 应用内语言选择器目前只维护 **English** 和 **简体中文**；旧翻译文件可能仍然存在，但不再视为完整支持。

## 权限说明
- **定位权限**：用于新增打点、通知栏快速打点和地图居中。如果暂时拿不到新鲜且精确的定位，应用可能会允许使用近似定位或最近一次已知定位，而不一定直接失败。
- **通知权限**（Android 13+）：仅在你启用可选的“快速打点”通知时需要。
- **麦克风权限**：仅在你启用噪音采集时需要。记录值为近似 dBFS，不是经过校准的 SPL。

## 备份 / 导出 / 导入
- **ZIP** 是更换设备或重装前最推荐的完整备份格式，可包含点位、相关标签、照片、传感器字段和设置元数据。
- **CSV** 适合表格查看或简单交换。
- **GeoJSON / KML / KMZ** 适合地图或 GIS 工具使用。
- **CSV 导入** 只添加点位。
- **ZIP 导入** 可在包内数据存在时恢复更完整的备份内容，包括照片、标签和设置。

## 兼容性
- 当前 Gradle 配置：`minSdk 24`、`targetSdk 37`、`compileSdk 37`。
- 当前 app 模块面向 Android 7.0 及以上设备。

## 运行
1. 在 Android Studio 中打开工程。
2. 运行到设备或模拟器，并确保 Android 定位服务可用。
3. 授予定位权限后即可新增点位或让地图居中。
4. 如有需要，可在设置中启用通知栏快速打点和麦克风噪音采集。
5. 通过 **设置 → 备份与恢复** 导出数据或创建 ZIP 备份。

## 说明
- 应用本地数据访问支持离线；联网主要用于在线地图瓦片、打开外部链接或分享给其他应用/服务。
- 已缓存/下载的瓦片可离线复用，但大范围或高缩放级别下载会占用较多时间和存储空间。
- EOX Sentinel-2 Cloudless 图层仅限非商业使用（BY-NC-SA）。
- 旧的未发布或预发布构建版本可能无法直接升级到此维护版本。

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
