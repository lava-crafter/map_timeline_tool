# Photo Persistence Optimization Plan

## Goal
在不改变当前拍照交互体验的前提下，优化图片存储与清理机制，并将压缩能力改造成可配置选项：
- 是否压缩（开关）
- 默认压缩格式（常见格式）

## Scope
1. 将图片压缩改为设置项（而不是强制压缩）
2. 文件 IO 统一放到后台线程
3. 增加应用启动时的孤儿图片清理
4. 数据库存储从绝对路径逐步迁移为相对路径/文件名

## Non-goals
- 不改动拍照入口与确认/取消/重拍的交互流程
- 不引入云存储或跨设备同步
- 不把图片二进制存入 Room

## Design

### 1) Photo Compression Settings

#### 1.1 Data model (SettingsStore)
新增配置项：
- `photo_compress_enabled`: `Boolean`
- `photo_compress_format`: `String` (枚举字符串)

新增枚举 `PhotoCompressFormat`（建议放在 `ui` 或 `photo` package）：
- `JPEG`
- `PNG`
- `WEBP_LOSSY`
- `WEBP_LOSSLESS`

兼容策略：
- Android API >= 30：使用 `Bitmap.CompressFormat.WEBP_LOSSY` / `WEBP_LOSSLESS`
- Android API < 30：
  - `WEBP_LOSSY`/`WEBP_LOSSLESS` 回退到 `Bitmap.CompressFormat.WEBP`
  - 记录日志（debug）便于排查行为差异

默认值建议：
- `photo_compress_enabled = false`（保持当前“原图落盘”行为）
- `photo_compress_format = JPEG`

#### 1.2 Settings UI
在设置页新增“Photo”配置区（可放在 Map operations 子页，或新增子路由 `SettingsRoute.Photo`）：
- `Switch`: Enable photo compression
- `SelectionGroup`/`Radio`: Default photo format
  - JPEG
  - PNG
  - WEBP (lossy)
  - WEBP (lossless)

文案需补充中英文 strings。

#### 1.3 Capture pipeline integration
在“用户确认使用照片”时执行：
- 若 `photo_compress_enabled == false`：保持原逻辑，直接使用拍照文件
- 若 `photo_compress_enabled == true`：
  1. 读取拍照文件
  2. （可选）按 EXIF 修正方向
  3. 以用户选择格式压缩后写入新文件
  4. 用新文件替换 pending path
  5. 删除旧拍照文件

失败回退：
- 压缩失败时保留原图，提示 toast（不中断保存流程）

### 2) File IO on Background Thread
对以下路径统一改为 `Dispatchers.IO`：
- 删除图片 (`deletePointPhoto`)
- 压缩与写文件
- 启动时目录扫描与清理

建议提供 suspend API：
- `suspend fun deletePointPhotoAsync(path: String?)`
- `suspend fun optimizePendingPhotoIfNeeded(...)`
- `suspend fun cleanupOrphanPhotos(...)`

UI 层调用通过 `rememberCoroutineScope().launch` 或 `viewModelScope.launch`。

### 3) Orphan Photo Cleanup on Startup

#### 3.1 Trigger
应用启动后（MainActivity 首次进入时）异步触发一次清理任务。

#### 3.2 Algorithm
1. 扫描 `filesDir/point_photos`
2. 从数据库读取全部点的 `photoPath`
3. 将数据库路径归一化为文件名集合
4. 目录文件名集合 - 数据库引用集合 = 孤儿文件
5. 删除孤儿文件

#### 3.3 Safety rules
- 仅操作 `point_photos` 目录
- 仅删除“文件名不在数据库引用中的文件”
- 全程异常捕获，失败不影响主流程

### 4) Relative Path Migration

#### 4.1 New storage rule
`PointEntity.photoPath` 逐步改为仅存 `fileName`（如 `point_photo_123.jpg`），运行时拼接为绝对路径。

#### 4.2 Compatibility phase
读取时支持两种格式：
- 旧数据：绝对路径
- 新数据：文件名

写入时统一为文件名。

#### 4.3 Optional DB migration
若需要一次性清理历史数据，可增加 migration（可选）：
- 对绝对路径做截取，仅保留 basename
- 对异常值保持原样，避免坏迁移

## Implementation Steps

### Phase 1: Config scaffolding
1. 新增 `PhotoCompressFormat` 枚举与映射
2. 扩展 `SettingsStore` 的 get/set
3. `MainActivity` 读取并持有 photo 设置状态

### Phase 2: Settings UI
1. 在设置页面增加 Photo 配置入口（或子页）
2. 增加开关与格式选择组件
3. 新增中英文 strings

### Phase 3: Capture pipeline
1. 新增图片压缩/转码工具方法
2. 在“确认 pending photo”动作中接入可选压缩
3. 失败回退与 toast 提示

### Phase 4: IO threading
1. 将 delete/cleanup/compress 全部迁移到 `Dispatchers.IO`
2. 审核所有调用点，避免主线程直接文件操作

### Phase 5: Orphan cleanup
1. 启动时异步触发 cleanup
2. 增加日志与保护逻辑

### Phase 6: Relative path
1. 增加 path normalize/resolve 工具
2. 写路径改为文件名
3. 读取支持双格式
4. （可选）补 migration

## Risks and Mitigations
- 风险：不同 Android 版本 WebP 编码能力差异
  - 规避：按 API 分级，老版本回退 `WEBP`
- 风险：压缩失败导致用户误以为保存失败
  - 规避：压缩失败自动回退原图 + toast
- 风险：孤儿清理误删
  - 规避：仅扫描固定目录 + 文件名差集匹配 + 严格异常保护

## Acceptance Criteria
- 设置中可切换是否压缩，并可设置默认格式
- 新拍照片按配置生效（关闭压缩即原图，开启则按格式压缩）
- 所有文件操作不阻塞 UI 主线程
- 应用启动可清理未引用图片，不影响正常数据
- photoPath 新写入使用相对路径/文件名，旧数据仍可读取

## Manual Test Checklist
1. 关闭压缩，拍照并保存，确认文件存在且可预览
2. 开启压缩 + JPEG，拍照并保存，确认格式与体积变化
3. 切换 PNG / WEBP，分别验证保存与预览
4. 新增点后取消、重拍、替换，确认废弃文件被删除
5. 杀进程后重启，确认孤儿清理运行且不影响已有点
6. 编辑点替换照片、删除点，确认旧图被清理
7. 升级旧数据（含绝对路径）后仍可正常显示

## File-level Change Plan
- `app/src/main/java/com/lavacrafter/maptimelinetool/ui/SettingsStore.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/ui/SettingsScreen.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/ui/SettingsRoute.kt`（若新增子页）
- `app/src/main/java/com/lavacrafter/maptimelinetool/MainActivity.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/PointPhotoUtils.kt`
- `app/src/main/java/com/lavacrafter/maptimelinetool/ui/AppViewModel.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh/strings.xml`（若存在）
- `app/src/main/java/com/lavacrafter/maptimelinetool/data/AppDatabase.kt`（仅在需要 migration 时）

## Suggested Commit Strategy
1. feat(settings): add photo compression options (enable + default format)
2. refactor(photo): move file operations to IO dispatcher
3. feat(photo): startup orphan cleanup task
4. refactor(photo): store relative photo path with backward compatibility
