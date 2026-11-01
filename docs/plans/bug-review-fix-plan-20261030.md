# Map Timeline Tool — 全量 Bug Review 与修复计划（2026-10-30）

> 本文档**取代** Issue #39 中"剩余问题修复计划"的阶段 3–6，旧计划不再执行；其中仍有效的内容已并入下文。基线：`bugfix_20260925` @ `68769d4`（Issue #39 阶段 0–9 及剩余阶段 1–2 已完成）。

## 背景

项目早期由 AI agent 编写。本轮对 `app/src/main` 全量代码重新 review（UI/写入链、数据/导出、定位/地图/传感器三块）。下文每条问题都给出位置、触发场景和修复方案，行号以基线为准。核实程度不一：A1–A6、B1、B3–B5、C1–C3 已在源码中复核；其余来自 review 读码，实施前需先确认。其中依赖 osmdroid 内部行为的几条（C4、C5、C9）和依赖系统行为的几条（D3 后台录音、Android 14 通知可划掉），实施前要先查官方文档或源码。

**已确认的产品决定**
- Full Backup 遇到缺失照片：**跳过该照片并提示**，不再整体失败。
- ZIP 恢复按 (timestamp, lat, lon) 匹配后整行覆盖：**视为预期语义**（恢复以备份为准），不改。
- 范围：下列阶段 A–G 全部实施。

**通用约束**
- 每个阶段独立提交、独立验证；每条修复附回归测试。
- 除阶段 F 明确的 Room 8→9 migration 外，不改 schema。
- 新增用户可见字符串必须补齐 11 个本地化目录（`he`/`iw` 同步），lint 保持 0 active errors，不改 baseline、不加 suppression。
- 设备测试用 `ANDROID_SERIAL` 限定 debug 包；不 clear/uninstall/覆盖正式包。

---

## 阶段 A — 数据完整性（P0）

### A1. 删除 Tag 后残留 ID → 孤立 point_tags → Full Backup 永久失败
- **原因**
  - `TagManagementUseCase.deleteTag` 通过 `onTagDeleted`（`MapTimelineApp.kt:176` → `SettingsStore.removeTagId`）只改 prefs。`SettingsViewModel._uiState` 中的 `defaultTagIds/pinnedTagIds/recentTagIds` 不刷新。
  - `MainActivity.kt:761` 用旧的 `defaultTagIds` 建新点；`toggleDefaultTag`（`SettingsViewModel.kt:213`）和 `setPinnedTagIds`（`MainActivity.kt:1144`）会把已删 ID 写回 prefs；pin 上限仍计入已删 Tag。
  - `setTagForPoint`（`TagManagementUseCase.kt:47`）不校验 point/tag 是否存在；`point_tags` 无外键。`0c1ce50` 之前的删除不清关系，老用户库里可能已有孤立行。
  - `FullBackupAssembler.kt:33` 遇孤立关系直接 `check` 失败，之后每次"分享备份"都失败。
- **修复**
  1. `PointDao.deleteOrphanPointTags()`：`DELETE FROM point_tags WHERE pointId NOT IN (SELECT id FROM points) OR tagId NOT IN (SELECT id FROM tags)`，经 `PointRepositoryGateway` 暴露；在 `MapTimelineApp` 启动维护（与 restore recovery 同处）调用一次。
  2. `PointWriteUseCase.addPoint/updatePoint/importPoints`、`TagManagementUseCase.setTagForPoint` 在事务内把 tagIds 过滤为现存 Tag，并确认 point 存在。
  3. 删除 Tag 后刷新 SettingsViewModel，可二选一：调用 `reloadFromStore()`，或订阅 tags flow 自动剔除不存在的 ID。
  4. `FullBackupAssembler` 保留 check。
- **测试**：`PointRepositoryIntegrityTest` 验证孤立行清理；`PointWriteUseCaseTest`/`TagManagementUseCaseTest` 验证已删 tag 被过滤；设置态刷新单测。

### A2. 照片文件缺失时，仅改标题的编辑也无法保存
- `PointWriteUseCase.kt:95-96` 无条件执行 `requirePhoto(photoPath)`，照片没变也会拦截。
- **修复**：在事务内读到当前行后，仅当 `photoPath != current.photoPath` 时才 `requirePhoto`。
- **测试**：照片缺失 + 只改标题可以保存；换成一张缺失的新照片仍会被拒。

### A3. 编辑弹窗初始化竞态 → 清空全部标签并删除照片
- `MainActivity.kt:1487-1492` 先写 `initializedEditingPointId`，再挂起读取 tagIds。若此时被取消（重建）或用户提前点 Save，会以 `tagIds = {}`、`photoPath = null` 提交，删掉全部标签和照片。
- **修复**：先加载 tagIds，再一次性赋值所有草稿字段，最后写 `initializedEditingPointId`；加载完成前禁用 Save/Delete。

### A4. 编辑拍照回调中途重建 → 编辑弹窗卡死
- `MainActivity.kt:584-604` 的 `pendingEditPhotoUri = null` 放在 `scope.launch` 末尾。协程被取消后，该 saveable 状态恢复为非空，Back/Dismiss/Save/Delete 全部被它阻断。
- **修复**：在回调里同步清除（与 Add 路径一致）；用 `getById` 代替 `getAllPoints().firstOrNull`。

### A5. 一次清理失败永久阻断所有恢复
- `ZipRestoreCoordinator.kt:71` 在每次 restore 前执行 `recoverLocked()`。任一旧 staging 的 `cleanJournal`/`deleteRecursively` 抛错（照片删不掉、journal 损坏或为空）就让本次恢复失败，而且无法自愈。
- **修复**：`recoverLocked` 改为逐目录 try/catch。失败的目录和 journal 保留，留待下次重试，记录日志，但不阻断新的 restore。启动 recovery 行为不变。
- **测试**：`ZipRestoreCoordinatorTest` 中，损坏 journal 或删除失败时新恢复仍能成功，且旧 journal 保留。

### A6. Full Backup 遇任一缺失照片永久失败
- `FullBackupAssembler.kt:59-62` 会 throw，错误只写日志。
- **修复**：去掉 throw，对缺失/不可读照片返回 null（`ZipExporter.buildPhotoMeta:207` 对 null 已返回空 meta，该点导出时不带照片引用，manifest 计数保持一致）。`ExportStats` 增加 `missingPhotoCount`，分享完成后 toast 提示（新增本地化字符串）。
- **测试**：`FullBackupAssemblerTest`/`FullBackupRoomTest` 验证缺照片时备份成功、可恢复、计数正确。

---

## 阶段 B — 功能错误与崩溃（P1）

### B1. Android 7–10 无 Play 服务时 fresh fix 恒失败，并可能崩溃
- `LocationUtils.kt:274` 调用 `requestSingleUpdate(provider, listener, null)`，而它运行在 `Dispatchers.IO`（没有 Looper）。API<30 上会抛 `IllegalArgumentException`，且不被 catch。
- `LocationUtils.kt:196` 和 `QuickAddPassiveLocationUpdater.kt:34` 用 lambda 实现 `LocationListener`。API<30 上回调 `onProviderDisabled/onStatusChanged` 会 `AbstractMethodError`。
- **修复**：传 `Looper.getMainLooper()`；用 `object : LocationListener` 显式实现四个方法；捕获 `RuntimeException` 并以失败 resume；权限检查失败时也要 resume，避免只靠外层超时结束。
- **手动验证**：API 29 无 Play 服务模拟器上，Quick Add 和新增点都能拿到 fresh fix。

### B2. 设备时钟偏慢超过 2 s 时，所有 GNSS fix 被判为"未来时间"
- `LocationFixValidity.kt:26` 用 `Location.time`（GNSS UTC）与设备时钟比较，容差只有 2 s。
- **修复**：在 provider 层（`LocationUtils`、`GoogleFusedLocationProvider`），有 `elapsedRealtimeNanos` 时用 `now - (elapsedRealtime - fixElapsed)` 把 fix 时间归一化到设备时钟，再进入现有校验。
- 缓存位置以 Float 存经纬度、并保留原 provider 名（`LocationUtils.kt:74-101`）：改为以 Double（`Long` bits）存储，读回时 provider 标为 `CACHED_PROVIDER`。

### B3. 超时设置输入 0–4 使新增弹窗立即自动保存
- `SettingsViewModel.kt:102-105`：存储值被 clamp 到 5..300，uiState 却保存原值；`MainActivity.kt:892` 用 uiState 的值做倒计时。
- **修复**：uiState 也保存 clamp 后的值。

### B4. 恢复结果在每次 Activity 重建时重复处理
- `_restoreState` 一直停在 Success/Failure；`MainActivity.kt:474` 的 `LaunchedEffect(restoreState)` 每次重建都会再 toast、`reloadFromStore`、`applyLanguagePreference`。
- **修复**：新增 `AppViewModel.acknowledgeRestoreResult()` 把状态置回 Idle，UI 处理完后调用。

### B5. 导出时间范围：结束日期早于开始日期时不修正
- `ExportScreens.kt:230` 用的是旧的 `endDate/startDate`。
- **修复**：改为 `if (endEpochDay < startEpochDay) startEpochDay = endEpochDay`。

### B6. TagDetail"添加点"选择框不可滚动
- `TagDetailScreen.kt:111-118` 在 AlertDialog 里用 Column。
- **修复**：改成 `LazyColumn` + `heightIn(max)`。导出页的 `TagPickerScreen`（`ExportScreens.kt:140`）同样处理。

### B7. 清除缓存在主线程做大量磁盘 IO，且泄漏 SqlTileWriter
- 位置：`MainActivity.kt:1279-1291`。
- **修复**：移到 `Dispatchers.IO`；创建的 `SqlTileWriter` 用完调用 `onDetach()`；完成后回主线程 toast。删除缓存目录前先让地图 provider 释放写句柄，否则跳过正在使用的 DB 文件，只走 `purgeCache()`。

### B8. 导出 / CSV 导入 / 分享备份绑定在 `rememberCoroutineScope`
- 位置：`MainActivity.kt:330, 404, 1005`。
- 后果：旋转屏幕即取消导出，并删除刚创建的文件。CSV 导入若在提交后被取消则没有任何提示，用户重导会产生重复点。连点"分享备份"会让两个任务互删 `.part`（`SharedBackupFiles.kt:39-44`）。
- **修复**：三个任务移入 `AppViewModel.viewModelScope`，只持有 application context（`writeCreatedDocument` 可直接复用）。结果用 StateFlow + acknowledge 呈现（复用 `PointWriteOperations` 的模式）。加单飞保护：任务进行中时禁用入口。

### B9. 启动器缺少处理程序时崩溃
- 相机/SAF 的 `launch`（`MainActivity.kt:956,978,986,994,1257,1263,1351,1376,1511`）未捕获 `ActivityNotFoundException`。导出路径中 `pendingExportKind` 已经设置，失败后所有导出都会被永久拒绝。`AboutScreen.kt:98,110,122` 的外链同理。
- **修复**：抽一个 `safeLaunch` 辅助函数，失败时 toast，并回滚 `pendingExportKind`、清理相机候选文件。

### B10. 列表 sheet 展开时按 Back 直接退出 App
- `MainActivity.kt:883` 的条件排除了 Expanded，又没有其他 handler 接手。
- **修复**：增加 `BackHandler(sheetState.currentValue == Expanded) { partialExpand() }`。

### B11. 导航状态在旋转后丢失
- `showMapDownload/settingsRoute/showAbout/editingTag/selectedTag` 用的是 `remember`（`MainActivity.kt:163,166,200,215,216`）。
- **修复**：改为 `rememberSaveable`；Tag 只保存 ID，再从 tags 列表解析。`observePointsForTag(selectedTag!!.id)`（`:1119`）要 `remember(tagId)`，避免每次重组都新建 Flow。

### B12. 导出读取初始空快照，且选中点被删后整批失败
- GeoJSON/KML/KMZ/ZIP 从 `pointsState` 取 ID（`MainActivity.kt:977,985,993,1350`），而 CSV 已改为从 DB 读取。`exportPayload`（`:287`）中的 `requireNotNull(byId[id])` 会因任一选中点被删而整批失败。
- **修复**：统一从 repository 解析 ID；已删的点跳过，最终数量用 toast 告知。

---

## 阶段 C — 地图、日期与性能

### C1. 选点后，points 任意变化都会触发地图回跳
- `MapScreen.kt:138` 以 `points` 为 key；`selectedPointId` 从不清空。
- **修复**：只在选中 ID 变化，或该 Point 的坐标变化时定位。noise、备注及无关 Point 的更新不改变视口。

### C2. 地图生命周期 observer 捕获过期的 `isActive`
- 位置：`MapScreen.kt:158-166`。
- **修复**：用 `rememberUpdatedState(isActive)`。同时把 `MapView.onResume/onPause` 跟随 Activity 生命周期转发。`locationProvider/orientationProvider` 以 `lifecycleOwner` 为 key 重新创建，避免 destroy 之后被复用。

### C3. 瓦片源名称用翻译文本作缓存键
- `DownloadTileSources.kt:36-37`；`values-zh` 中 EOX 的名称与英文不同。
- 后果：切换语言后卫星缓存和离线区域失效，并被重复下载。
- **修复**：`OnlineTileSourceBase` 的 name 改用固定、不翻译的字符串（取当前英文值，保住英文用户已有缓存），显示仍用 `labelRes`；同步修改 `MapScreen.kt:259` 的比较。

### C4. Mapnik 未禁止批量下载
- `DownloadTileSources.kt:44-46,65` 的 `TileSourcePolicy` 缺 `FLAG_NO_BULK | FLAG_NO_PREVENTIVE`，违反 OSM 瓦片使用政策。
- **修复**：补上这两个 flag，与 osmdroid MAPNIK 一致。下载功能仍保持不可达。

### C5. 离线缓存的 50 MB 上限实际不生效
- `MapCachePolicyUtils.kt:33` 只设了 `tileFileSystemCacheMaxBytes`，trim 阈值仍是默认的约 500 MB。
- **修复**：同时设置 `tileFileSystemCacheTrimBytes = max × 0.8`。实施前先核对 osmdroid 6.1.20 的语义。

### C6. Marker 增量更新与 InfoWindow
- **现状**：`map.overlays.clear()`（`MapScreen.kt:295`）不关闭已打开的气泡，过期气泡点击后会用旧实体编辑。
- **修复**
  - 按 Point ID 增量更新 Marker，保留定位 overlay 和事件 overlay，只更新实际变化的坐标/文本/方向/图标。
  - InfoWindow 使用最新的 Point。删除 Marker 时 `closeInfoWindow` 并释放资源；图标缓存要有界。
  - 在完成增量更新之前，先在 clear 前调用 `InfoWindow.closeAllInfoWindowsOn(map)`。

### C7. 跨午夜 / 时区变化时 Today 状态不更新
- `ListScreen.kt:53` 的 `remember(points)` 缓存了排序结果，地图同理。
- **修复**：建立共享的"当前本地日期"状态（监听午夜、前台恢复、`ACTION_TIME_CHANGED/TIMEZONE_CHANGED`）。地图和列表的 Today 排序以日期与 Points 为 key，日期变化同时使 Marker 颜色和序号失效。

### C8. 导出手选列表
- `ExportScreens.kt:254` 改为 `LazyColumn`，选择只保存 Point ID。
- "All"类导出的 `pendingExportPointIds`（`MainActivity.kt:265,280`）改为只保存一个标记，在导出时重新解析，避免 SavedState 超过 1 MB。

### C9. MapDownloadScreen 资源与区域记录
- `AndroidView` 缺少 `onRelease { it.onDetach() }`（`MapDownloadScreen.kt:194-223`）；`update` 中重复调用 `setTileSource`。
- 下载完成时记录的 zoom 范围读取的是当前 slider 值（`:310,326,409`）。改为在任务开始时快照，并在下载期间禁用 slider。
- 不开放、也不重做 coverage/partial/provider 的底层模型。

### C10. 性能基线
- 生成 1k/5k/10k 数据，记录首屏、单点更新、平移及最近点搜索的耗时；单点更新的 Marker 分配数必须是常数级。
- O(N) 的最近点搜索和全表观察先测量，不直接引入聚类、空间索引或分页。

---

## 阶段 D — 权限、Quick Add 与设备本地设置

### D1. 移除"启动即请求定位"
- `MainActivity.kt:784-788` 每次重建都会请求，被永久拒绝后每次旋转都弹 toast。
- **修复**：无权限时仍能浏览已有 Points 和地图。Add/定位中心按需请求前台权限。Quick Add 单独说明并请求后台定位。

### D2. 保留当前的精确优先、整体 deadline 和 `finally finish()`
- 权限被撤销后，停止被动监听，并禁用不具备条件的操作。
- 被动监听的注册/注销统一到同一执行上下文（主线程或单线程 dispatcher），消除 `QuickAddPassiveLocationUpdater.kt:41-52` 的 check-then-register 竞态。
- 位置缓存不持久化；进程重启后缓存为空属于正常降级。

### D3. Quick Add 通知
- 重启后恢复通知：用户已启用且权限仍满足时，解锁启动后只同步通知和合格的被动监听；不自动记录、不主动定位、不启动服务。
- Android 14+ 用户可以划掉 ongoing 通知：增加 `deleteIntent`，或在回到前台时检查并重新同步。授权后台定位后也要重新同步（`MainActivity.kt:561` 目前只在开关变化时触发）。
- 两个 channel 同名（`QuickAddActions.kt:153-157,180-184`）：改为不同名称，并删除旧的非 `_v2` channel。
- 连点去重：`QuickAddReceiver` 在进行中时忽略新点击（进程内单飞标志）。
- `MainActivity` 已 exported，任何应用都能通过 `ACTION_QUICK_ADD` 触发记录，且进程死亡后会被重投递。处理方式：只接受来自自身 PendingIntent 的调用；处理完后在 `onSaveInstanceState` 标记已消费。
- 后台/广播中录制 noise 会拿到静音数据（`NoiseLevelRecorder.kt`，Android 9+ 后台限制）：Quick Add 路径跳过 noise 采样，或明确标为不可用，不占用时限预算。

### D4. 设备本地 preferences 隔离
- 把下载区域、下载运行参数、Quick Add 开关、权限提示状态移入独立的设备本地 preferences；`map_timeline_settings` 只保留可移植偏好。
- 迁移要可重试：目标持久化成功后才删除旧 key。
- 两份 backup XML 排除设备本地文件，保留现有的 `location_cache.xml` 排除。
- **照片目录**：cloud backup 一旦超过 25 MB，Android 会放弃整个应用备份（含数据库）。`<cloud-backup>` 和 `backup_rules.xml` 排除 `point_photos/`，`<device-transfer>` 保留；README/隐私说明同步更新。
- 旧的下载元数据不能再被当成真实瓦片覆盖的证明。普通"清缓存"使用真实、明确的删除语义，不再因旧区域 metadata 而无条件拒绝。

### D5. 权限提示刷新
- `SettingsScreen.kt:539-545` 只在组合时检查权限。改为在 `ON_RESUME` 时重新读取。

### D6. 合规
- Google Play 后台定位声明及届时有效的政策是发布门槛，需要查阅官方规则，不把代码测试当作合规证明。

---

## 阶段 E — 导出质量与恢复边界

### E1. 截断拆开代理对
- `TextInputSanitizer.kt:73-126` 按 UTF-16 截断。若截断点落在代理对中间就回退一位；restore 时重复 sanitize 也走同一逻辑。

### E2. KML 非法 XML 字符
- `KmlExporter.escapeXml` 要去除 XML 1.0 禁止的字符：<0x20（\t\n\r 除外）、U+FFFE/FFFF、孤立代理。
- 导入 `locationProvider` 时（`CsvImporter.kt:181`、`GeoJsonExporter.kt:188`）改用 `sanitizeSingleLineText` 并限长。

### E3. KML 坐标出现科学计数法
- `KmlExporter.kt:105` 的 `Double.toString` 对 |v|<1e-3 会输出 `5.0E-4`。改用 `BigDecimal(v).toPlainString()`。

### E4. CSV 没有 BOM，Excel 打开 CJK 乱码
- `CsvExporter.kt:75` 写入 `﻿`（导入端 `CsvImporter.kt:258` 已能跳过 BOM）。
- CSV 导入（`MainActivity.kt:408`）改用 `CharsetDecoder` 的 `REPORT` 模式：遇到非 UTF-8 文件明确报错，而不是静默替换成 `�`。

### E5. GeoJSON 的 `"title": null` 被导入为字符串 "null"
- `GeoJsonExporter.kt:163-170` 先判断 `isNull`，note 同样处理。

### E6. 旧版（无 manifest）备份恢复会清空 pinned/recent/default
- `BackupManifest.LEGACY` 的 tags=true，但包里没有 tags.csv 时映射为空，导致三项设置被清空。
- **修复**：manifest 为 LEGACY 且 `legacyTagIdToActualId` 为空时，传 `restoreTagSettings=false`，保留当前值。

### E7. 照片名仅大小写不同时，导入误判为重复
- `ZipExporter.kt:215` 选名时按 `lowercase(Locale.US)` 去重，与 `ZipImporter.kt:90-95` 保持一致。

### E8. 空白 Tag 名导致备份可导出、却无法恢复
- 早期版本写入的空白名 Tag 会被 `MetadataCsvParser.kt:41-42` 拒绝。
- **修复**：导出时把空白名替换为 `tag_<id>`；同时在启动维护中修复这类数据（与 A1 同处）。

### E9. 其他恢复边界
- `MetadataCsvParser`/`ZipImporter`：识别不到任何已知条目（例如重新打包时带了顶层目录）时报错，而不是返回 `Success(0)`。
- `CsvImporter.kt:94-102`：普通 CSV 导入允许开头的空行和 `sep=,` 行。
- 恢复所需余量（`ZipRestoreCoordinator.kt:77-79`）：由固定 512 MB 改为 `max(归档大小 × 2, 64 MB)`，并保留 10% 比例上限。
- journal 在 `renameTo` 之后对 staging 目录做 fsync（`ZipRestoreCoordinator.kt:203-208`）。
- Full Backup 导出期间照片被替换会导致 hash 不符：复制时重新计算 hash，并以复制时的结果写 manifest；或先把照片复制进 staging 再 hash。
- `SettingsStore.kt:480`：恢复的 `map_tile_source` 要校验是否为已知来源。
- `PointPhotoUtils.kt:54-60`：检查 `mkdirs()` 的结果。`PointPhotoUtils.kt:68-95` 的 `commitPointPhotoImport` 无调用方，删除。

### E10. 大数据量导出的内存
- GeoJSON/KML/KMZ 目前整份在内存中构建（`GeoJsonExporter.kt:46-52,121-124`、`KmlExporter.kt:35-43`）。改为直接流式写入目标 Writer，CSV/ZIP 已经是流式的。

---

## 阶段 F — 传感器采样质量与记录链（含 Room 8→9）

### F1. `buildPoint()` 采样异常会阻断核心写入
- 位置：`PointWriteUseCase.kt:54`。普通传感器失败时降级为空快照，`CancellationException` 仍然传播。

### F2. 方向计算错误
- `SensorReadable.kt:107` 的 `atan2(y, x)` 是逆时针、且以 X 轴为零点，不是北向顺时针方位角；它还忽略倾斜。`SensorReadableTest.kt:41-43` 断言的正是这个错误约定。
- **修复**：改用 `SensorManager.getRotationMatrix` + `getOrientation`，加速度和磁力计要时间对齐才计算；处理屏幕旋转（`HeadingLocationOverlay.kt:57-59,85` 同样要加上显示旋转）。明确语义为设备/屏幕朝向与俯仰，不声称是相机光轴。

### F3. 有界采样窗口
- 保持同一 looper 上注册/完成/注销，改为有界采样窗口，不再拿到每类首个读数就结束。
- 保留每类样本的 `SensorEvent.timestamp`、accuracy、有效样本数和质量状态；拒绝非有限值，标记不可靠样本。
- pressure/light 用稳健聚合，向量做异常与时间错位检测。总时长仍受现有 deadline 限制。
- noise 继续标为未经校准的 dBFS，海拔继续标为标准气压估算。

### F4. 采样 metadata 持久化（Room 8→9）
- 用一个有版本、大小受限的 metadata 对象贯穿领域模型，以 nullable TEXT 持久化。内容包括时钟锚点、样本时间、accuracy、数量、质量和融合结果。历史记录为 null，不伪造精度。持久化的 monotonic 时间不能当作跨重启的年龄。
- CSV 增加可选 metadata 列，ZIP sensor section 同步导出和恢复；旧 CSV/ZIP 仍可读；`Sensors=false` 时原始值和 metadata 都不导出；canonical metadata 损坏时 fail-closed。
- **测试**：缺传感器、注册失败、低 accuracy、NaN、时间错位、取消；合成已知姿态 + 屏幕旋转；sensor 异常仍能保存合法 Point；metadata 导出恢复完全一致；真实历史 DB2–8 全部升级到 DB9，旧字段原值不变。
- **手动验证**：debug 包上做平放/竖持/旋转的最短测试。

---

## 阶段 G — 维护与最终验收

### G1. 删除死代码
- 删除没有调用方的 `AppViewModel.scheduleAutoAdd`、`autoAdded`（`AppViewModel.kt:80-81,283-292`），以及 `MainActivity.kt:1435` 不可达的 `LAST_KNOWN_STALE` 文案。
- 保留实际在用的 `getAllPoints`、`ACTION_QUICK_ADD`。

### G2. 分层
- 把 preferences 实现移出 UI 层，消除 `data.SettingsRepository → ui.SettingsStore` 的反向依赖；preferences 名称和迁移规则不变。
- 不借机重写 MainActivity。

### G3. CI
- 增加不依赖 release secrets 的 debug/unit/lint CI；connected 测试仍由独立设备执行。

### G4. 补齐尚缺的证据
- 在空间充足的 debug 设备上程序化生成超过 2 GiB 的合法归档，验证容量、余量和清理；不提交大型 fixture。
- 只对 debug 进程注入提交边界中断，重启后验证 DB 引用、照片和 journal 的一致性。
- `PointWriteOperations` 的 Running 状态在进程死亡后丢失，可能导致重复自动保存（`PointWriteOperations.kt`）。在 SavedState 中记录已提交的 operation ID 和 event time；重启后若库中已有同 event time 的点，就不再自动保存。

### G5. 验收记录
- 更新 `docs/issue-39-verification.md`（或新建本轮验收文档），逐项标注"修复 / 冻结延期 / 未验证"，附 SHA、测试结果和性能数据。

---

## 验证命令

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
ANDROID_SERIAL=<device> ./gradlew :app:connectedDebugAndroidTest
# 最终
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --rerun-tasks
ANDROID_SERIAL=<device> ./gradlew :app:connectedDebugAndroidTest
```

**各阶段重点回归**
- A：`PointRepositoryIntegrityTest`、`PointWriteUseCaseTest`、`ZipRestoreCoordinatorTest`、`FullBackupRoomTest`、`Phase2WriteRecreationTest`（编辑初始化、拍照回调重建）。
- B：`SettingsViewModel` clamp、`ExportSelectionScreenTest`（结束日期）、`Phase2WriteRecreationTest`（恢复结果 acknowledge、导出不随重建取消）、`LocationFixValidity` 时钟偏差；手动在 API 29 无 Play 服务环境验证。
- C：`OnlineMapScreenTest`（视口不回跳、isActive 切换、tile source name 稳定）、`MapTileProviderPolicyTest`、可注入时钟的日期测试、性能基线。
- D：权限组合、并发注册、preferences 迁移、backup XML 测试；手动重启设备验证通知恢复。
- E：KML/GeoJSON/CSV 导出测试、`TextInputSanitizer` 代理对、`ZipExportImportTest`（大小写照片名、旧包标签设置、空白 Tag 名）。
- F：Reader/mapping/readable/CSV/ZIP/migration 测试。

**完成条件**
- 新增回归和旧套件全部通过，无新增 active lint error。
- 照片数量稳定，重建不重复写入，权限与测量状态可解释。
- 发布候选实际包含已验证的修复 SHA。
- 冻结的离线下载底层模型、专业级测量校准，以及未经基准证明必要的地图大规模架构优化，明确列为延期项。

---

## 执行状态（2026-10-30）

提交：A `f5e8aa6`、B `758d613`、C `f74d5ed`、D `d4b1154`、E `00db4b4`、F（部分）`1640482`，G 见本节同一提交。

**本地验证**：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin` 通过。
- JVM 200/200，0 failures / errors / skipped。
- lint 0 active errors，9 个 warning：原有 7 个，另 2 个为联网版本检查（`AndroidGradlePluginVersion`、`GradleDependency`），与本轮改动无关；baseline 未改，未新增 suppression。
- connected 测试当时未运行（无设备）；2026-11-01 已在 SM-S938U 上补跑：117 个用例，107 通过、0 失败、10 个可选测试跳过。详见 `docs/bugfix-20261030-verification.md`。

| 项 | 状态 | 说明 |
| --- | --- | --- |
| A1–A6 | 已修复 | A1 另含启动时孤立关系清理；A6 新增本地化提示 |
| B1–B12 | 已修复 | B1 已在 API 29 无 Play 模拟器验证（2026-11-01）；B2 含缓存精度与 provider 标记；B8 以 `FileTasks` 单飞任务实现，"全部点"导出改为写入时从 DB 解析 |
| C1–C9 | 已修复 | C2 未转发 `MapView.onPause/onResume`（当前 overlays 无需）；C4 依据 osmdroid 6.1.20 字节码核对 trim 语义 |
| C10 | 已完成（2026-11-01） | 基线见 `docs/bugfix-20261030-verification.md`；发现每个 Marker 各建 InfoWindow 导致 10k 点 OOM，已改为共享气泡；10k 平移帧时间与单点更新延迟列为延期观察项 |
| D1–D5 | 已修复 | D3 用"每次 resume 重新同步通知"代替 `deleteIntent`；`ACTION_QUICK_ADD` 仅在全新启动时处理 |
| D6 | 未做 | Google Play 后台定位政策需人工核查 |
| E1–E9 | 已修复 | E9 中 `getPointPhotoDir` 的 `mkdirs` 未改；`commitPointPhotoImport` 仍被设备测试使用，保留 |
| E10 | 部分 | KML/KMZ 已流式写出；GeoJSON 仍为内存 JSON 树（需手写 JSON 流式序列化，延期） |
| F1–F2 | 已修复 | 方位角改为旋转矩阵 + 倾斜补偿，缺任一传感器不显示；箭头补显示旋转。历史数据未记录屏幕旋转，按自然方向解释。实机平放与平台 rotation vector 对比中位差 1.3°；竖持/旋转人工验证未做 |
| F3–F4 | 未做 | 有界采样窗口、采样 metadata 及 Room 8→9 migration 需设备验证迁移与采样，延期 |
| G1 | 已修复 | 删除 `scheduleAutoAdd/autoAdded/cancelAutoAdd`；`LAST_KNOWN_STALE` 分支因 `when` 穷举保留 |
| G2 | 未做 | 迁移 `SettingsStore` 需连带移动多个 UI 类型，收益低，延期 |
| G3 | 已完成 | `.github/workflows/ci.yml`：debug 单测 / lint / assembleDebug / androidTest 编译，无 release 密钥 |
| G4 | 已完成（2026-11-01） | >2 GiB 归档恢复、三个提交边界的真实进程死亡、按 event time 的自动保存去重 |
| G5 | 已完成（2026-11-01） | `docs/bugfix-20261030-verification.md` |
