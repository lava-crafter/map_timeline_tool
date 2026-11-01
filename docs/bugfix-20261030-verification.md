# Bug Review 修复验收记录（2026-11-01）

对应计划：`docs/plans/bug-review-fix-plan-20261030.md`（阶段 A–G）。分支 `bugfix_20260925`；基线提交 A `f5e8aa6` … G（部分）`96bfea6`，本记录与本轮补充修复在同一提交中。

## 环境

| 设备 | 用途 |
| --- | --- |
| Samsung SM-S938U，Android 16（API 36），有 Play 服务 | 完整 connected 套件、>2 GiB 归档、进程死亡、性能基线、实机方位角 |
| 模拟器 `api29_noplay`（`system-images;android-29;default;x86_64`，无 Play 服务） | B1 回退定位 |

设备测试只使用 `com.lavacrafter.maptimelinetool.debug`；正式包未安装，未被 clear/覆盖。

## 结果汇总

| 检查 | 结果 |
| --- | --- |
| `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --rerun-tasks` | 通过。JVM 201/201；lint 0 active errors，9 个 warning 与上轮相同（原有 7 个 + 2 个联网版本检查），baseline 未改、无新增 suppression |
| `ANDROID_SERIAL=<S938U> ./gradlew :app:connectedDebugAndroidTest` | 117 个用例：107 通过、0 失败、10 跳过（下文的可选测试，默认不跑） |

首次 connected 运行为 104/106。2 个失败都是测试过期，产品代码无回归：
- `DataIntegrityVerificationTest.failedTagInsertDoesNotLeavePointOrPartialTags`：用的 tag 1、2 在库里不存在，A1 之后会被 `filterExistingTagIds` 过滤掉，注入的失败不会触发。改为先建 tag，并补断言 `point_tags` 为空。
- `SettingsStoreBackupJsonTest.exportBackupJson_…`：D4 把下载区域移到设备本地 preferences 后，清空可移植 preferences 不应再清掉它们。断言改为设备本地值保留。

## 本轮补充修复

| 项 | 问题 | 修复 | 回归 |
| --- | --- | --- | --- |
| G4 自动保存去重 | Add 已提交、但 UI 还没重置时进程死亡，`rememberSaveable` 恢复出同一 `pendingTimestamp` 的弹窗，会再次自动保存出重复点。只记 SavedState 标记不够：快照可能早于提交 | `PointWriteOperations` 执行 Add 前按 event time 查库（新增 `findByTimestamp`，走 `index_points_import_key` 前缀）。已存在则直接返回该行的 Success（`quality = null`，提示"已添加"），不定位、不写入；草稿照片未被引用时删除 | `PointWriteOperationsTest.resubmitted add after process death …` |
| C10 中发现：10k 点 OOM | 每个 Marker 各建一个 `MarkerInfoWindow`。osmdroid 6.1.20 的 `InfoWindow(int, MapView)` 在构造时就 inflate 气泡布局，并注册到 `MapViewRepository`（已核对字节码），10k 点即耗尽 256 MB 堆 | 每个 MapView 共用一个气泡，`onOpen` 从 `Marker.relatedObject` 取当前 Point。Marker 内容变化时会重建，所以总是最新的 Point。`Marker.onDetach` 在 `onDestroy` 前先 `setInfoWindow(null)`，不会误销毁共享气泡 | `MapMarkerInfoWindowTest`；性能基线 10k 不再 OOM |

## 设备验证

### G4 >2 GiB 归档（`LargeArchiveRestoreTest`，`-e largeArchive true`）
在设备上运行时生成 20 张 115 MiB 照片（有效 JPEG + EOI 后的随机填充，无法压缩），不提交 fixture。
- 归档 2,412,475,241 字节（> 2 GiB）；导出 68.9 s，恢复 27.0 s。
- 20 张照片的大小与 SHA-256 全部一致；`point_photo_imports` 为空；删除归档后，沙盒内只剩恢复出的照片字节。
- 未覆盖：空间不足时的拒绝路径（需人为占满存储）。

### G4 提交边界的真实进程死亡（`RestoreProcessDeathTest`）
在 debug 进程内由测试注入 `Process.killProcess`，生产代码不变。第二次 `am instrument` 是新进程，在其中执行与启动时相同的 `ZipRestoreCoordinator.recover()`：

```bash
R=com.lavacrafter.maptimelinetool.debug.test/androidx.test.runner.AndroidJUnitRunner
C=com.lavacrafter.maptimelinetool.export.RestoreProcessDeathTest
adb shell am instrument -w -e class $C#killDuringRestore -e killBoundary <b> $R          # 预期 "Process crashed"
adb shell am instrument -w -e class $C#verifyAfterProcessDeath -e verifyBoundary <b> $R   # 预期 OK
```

| 边界 `<b>` | 杀死后磁盘状态 | 恢复后 |
| --- | --- | --- |
| `staging`（暂存完成，未写 journal） | staging 中有 2 张照片 | DB 与原照片不变，staging 清空 |
| `transaction`（Room 事务内，已移动 1 张照片） | `point_photos` 多 1 张 + journal | 事务回滚；移入的照片被删；原行与原照片完整 |
| `committed`（DB 已提交，未清 journal） | 2 张新照片 + journal | 新数据保留，所有引用可解析，被替换的旧照片已清理 |

三个边界均通过。未进行断电测试，也不宣称跨断电的文件系统原子性。

### C10 性能基线（`MapPerformanceBaselineTest`，`-e perfBaseline true`）
数据点均为当天，带序号图标；离线策略。时间单位 ms，单点更新与平移各取中位数。

| 点数 | 首屏（全部 Marker 挂载） | 单点更新 | 每次更新新建 Marker | 平移 + 整图 draw（中位/p90） | 最近点搜索（中位/p90） |
| --- | --- | --- | --- | --- | --- |
| 1,000 | 246 | 57.6 | 1 | 4.0 / 7.2 | 1.0 / 1.1 |
| 5,000 | 609 | 68.4 | 1 | 16.7 / 20.6 | 1.7 / 2.6 |
| 10,000 | 1,230 | 292.9 | 1 | 29.0 / 34.3 | 3.6 / 3.6 |

修复共享气泡前：1k 首屏 525、5k 首屏 1,941，10k 直接 OOM。

结论：单点更新只新建 1 个 Marker，满足常数级要求；最近点搜索在 10k 时 < 4 ms，暂不需要空间索引。10k 时平移绘制约 29 ms，超过 16.7 ms 的帧预算；单点更新约 0.3 s，主要是整表 `points.map { toOverlaySignature() }` 加上 overlay 重排。按计划，这些只记录、不在本轮引入聚类或分页，列为延期观察项。

### B1 无 Play 服务的回退定位（API 29 模拟器，`LegacyLocationFallbackTest`）
- `-e legacyLocation true`（mock provider）：`graph.locationProvider.getFreshLocation` 拿到 fresh fix；`LocationSaveResolver(MANUAL_ADD)` 判定可保存。请求进行中禁用 provider 时，请求立即以 null 结束，不等 20 s 超时；同时被动监听收到 `onProviderDisabled`，无 `AbstractMethodError`。
- `-e emulatorGeoFix true`：主机循环执行 `adb emu geo fix 121.4737 31.2304`（走模拟器 GNSS HAL，属于真实定位）时，Quick Add 得到 `SAVED_FROM_FRESH_REQUEST`。Quick Add 按设计拒绝 mock 定位，所以不能用 mock provider 验证它。
- logcat 中无 `AbstractMethodError` / FATAL。

### F2 实机方位角（`LiveHeadingCrossCheckTest`，`-e liveSensors true`）
与平台融合的 `TYPE_ROTATION_VECTOR` 按同一指向轴规则对比，100 个样本。设备平放时 pitch 中位数为 −0.7°，|应用 − 平台| 中位 1.3°、p90 3.0°。
只覆盖了平放姿态；竖持与屏幕旋转仍需人工手持验证。

## 状态

| 项 | 状态 |
| --- | --- |
| A1–A6、B1–B12、C1–C9、D1–D5、E1–E9、F1–F2、G1、G3 | 已修复（见计划执行状态）。B1 已在 API 29 无 Play 环境验证 |
| C10 | 已完成基线；发现 10k OOM 并修复。平移帧时间、10k 单点更新延迟列为延期观察项 |
| G4 | 已完成：>2 GiB 归档、三个提交边界的进程死亡、自动保存去重 |
| G5 | 本文档 |
| F2 人工姿态验证 | 未验证（竖持 / 屏幕旋转需人工） |
| D6 | 未做：Google Play 后台定位政策需人工核查，是发布门槛 |
| E10（GeoJSON 流式）、F3–F4（含 Room 8→9）、G2 | 冻结延期 |
| 离线下载底层模型、专业级测量校准、地图大规模架构优化 | 按计划列为延期 |
