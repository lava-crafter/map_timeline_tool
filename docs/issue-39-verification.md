# Issue #39 验证索引（测试先行）

本表只标记**已运行**、**仅编译/待设备**和**决策未定**；目标行为测试预期变红，不纳入全绿 CI 门禁。不要以解析器单测代替文件、Room、设置的跨层验证。

| Issue 小节 | 对应验证 | 当前状态 / 边界 |
| --- | --- | --- |
| §4.1–4.3 CSV 数据、事务、坏时间 | `DataIntegrityVerificationTest`、`BackupIntegrityVerificationTest`；新增旧 CSV 重复导入对照 | 本轮原有 JVM 诊断 7/7 为预期红灯；本轮设备未连接，新 Room 对照仅编译。坏行「跳过或整批拒绝」待决定。 |
| §4.4–4.6 ZIP 身份、索引、manifest | `BackupIntegrityVerificationTest`、`ArchiveBoundaryVerificationTest`、`DataIntegrityVerificationTest` | 本轮 6 个归档边界 JVM 用例：2 通过、4 个目标断言变红（逻辑重复条目、manifest section）。真实 Room 点标签正反例仅编译。 |
| §4.7–4.9 设置失败、跨文件/DB、重复恢复照片 | `PointPhotoUtilsTest` 新增一次提交与中途坏文件回滚；`AppViewModel.importZipData` 的 Room 失败注入测试 | 仅编译；**尚未**验证 MainActivity 的 staging→文件提交→Room→设置编排、进程中断或重复导入照片，不能宣称跨介质原子性。 |
| §4.10–4.11 Photos-only、同名 Tag | 已有 `ZipExportImportTest` 对照片独立导出作正向断言；已有 `DataIntegrityVerificationTest` 对同名不同 ID 作目标断言 | 「照片可单独导出吗」「同名是否有身份区别」需要产品契约，暂不再写互相矛盾的通过条件。 |
| §5 关系与设置残留 | `DataIntegrityVerificationTest` 新增 pinned/default/recent 残留断言、既有孤立关系断言 | 设备未连接，新增断言仅编译。 |
| §6–9 Add/Edit/相机、操作完成/Activity 重建 | 既有 Room 中途失败、point+tag 故障测试；`ZipExportOptionsDialogTest` 仅验证独立组件 | 完整 Add/Edit/SAF/Camera 没有可控 operation state 或回调注入；不能把组件点击测试当作恢复/持久化证明。 |
| §10–11 主题/设备本地数据 | `SettingsAndOfflineVerificationTest` 既有主题目标断言 | 本轮新增区域元数据删除对照仅编译；权限不会随设置恢复这一政策边界仍待定义。 |
| §12–20 Offline/地图 | `SettingsAndOfflineVerificationTest` 已有区域包络目标断言、区域删除对照 | 瓦片磁盘与导航/生命周期、署名可见性、大数据帧时没有可控替身/设备数据；禁止对标准 OSM 服务做批量预取试验。 |
| §21–26 Quick Add/传感器/时间 | `QuickAddResolverTest` 新增请求后位置年龄断言；`LocationDeadlineTest`、`SensorSnapshotMappingTest`、`NoiseLevelRecorderMathTest` | 本轮纯逻辑测试通过；未测真实 Receiver 时限、传感器时间戳/精度、GPS mock 元信息、真实 SPL/海拔或时间显示契约。 |
| §27–29 权限/CSV 选项/分享文件 | `ExportSelectionScreenTest` 测选择转发及 radio 状态；`ZipExportOptionsDialogTest` 测按钮启用 | UI 仅编译；CSV writer 是否输出 tag 与选择联动仍**没有**贯穿 SAF 的测试；分享文件清理规则待决定。 |
| §30–34 构建/架构/政策 | `assembleDebug`、AndroidTest 编译/打包及定向 JVM 运行 | 构建检查不是合规性或性能测试；无签名干净环境、Google Play 政策、osmdroid 许可/替代方案需单独核验。 |

本轮 JVM 定向运行：`./gradlew :app:testDebugUnitTest --tests '*BackupIntegrityVerificationTest'`（7 个预期红灯）；`./gradlew :app:testDebugUnitTest --tests '*ArchiveBoundaryVerificationTest' --tests '*QuickAddResolverTest'`（新增 4 个预期红灯）；`./gradlew :app:testDebugUnitTest --tests '*LocationDeadlineTest' --tests '*NoiseLevelRecorderMathTest' --tests '*SensorSnapshotMappingTest'`（通过）。Android：`./gradlew :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest`（通过）；设备重新在线后定向执行 `:app:connectedDebugAndroidTest` 的各测试类并记录实际结果。完整单测套件仍包含预期红灯，不应报告为通过。

## Phase 8 — 在线地图与下载入口收口

本节对应 [稳定性计划 Phase 8](https://github.com/lava-crafter/map_timeline_tool/issues/39#issuecomment-5846105145)，上方表格保留为初始诊断记录。

- Settings 概览不再提供下载导航入口；`MapDownloadScreen`、`DownloadSettings`、`DownloadTileSources` 及下载实现保留并继续编译。没有其他正常导航或 intent 入口设置下载 route。
- `resolveMapTileAccessPolicy` 独立输出 `allowNetwork` / `allowCacheWrites`。普通地图始终允许联网；禁用缓存禁止新瓦片持久化，Wi-Fi-only 只在 Wi-Fi 写入，Always 允许写入，包括网络暂时不可用时的策略状态。已有磁盘和内存瓦片不因关闭缓存被清除。
- osmdroid 6.1.20 默认 downloader 会丢弃刚下载的 Drawable，并依赖后续磁盘读取显示瓦片。轻量 Java adapter 将成功下载的 Drawable 直接交给内存缓存，避免禁止写入时地图空白；Java 仅用于适配其公开 factory 返回 protected 嵌套类型的 API，不使用 Kotlin 暴露类型错误 suppression。
- 切换图层时替换 provider 而不是复用旧 provider 的内存缓存，保留 MapView、视口和 overlays，并禁止旧 provider 的后续缓存写入。旧图层迟到的回调不会污染新图层的内存缓存。
- `map_attribution` semantic node 明确显示有效 tile source 的署名，切换图层同步更新；位于左上方，右侧预留图层/缩放按钮位置，长署名可换行。
- `MapTileAccessPolicyTest` 覆盖缓存策略 × 网络状态全部 9 个组合；`MapTileProviderPolicyTest` 使用生成图像/替身验证禁止写入、已有缓存读取、下载后内存显示及迟到旧图层回调；`OnlineMapScreenTest` 验证两种图层的真实 provider 策略和署名/按钮不重叠；`MapNavigationTest` 覆盖正常 Settings、地图操作和缓存导航无下载入口，返回地图仍可用。测试不以真实瓦片请求成功为通过条件。

本次验证结果：

- `:app:testDebugUnitTest`：148/148 通过，其中缓存策略全部 9 个组合通过。
- Phase 8 定向 `:app:connectedDebugAndroidTest`：SM-X730 / API 36 上 6/6 通过（`MapTileProviderPolicyTest`、`OnlineMapScreenTest`、`MapNavigationTest`）。
- 首轮全量 connected 为 72/74；两个旧 `ActivityResultRecreationTest` 停在 `STOPPED`，设备确认安全锁屏且屏幕因超时关闭。测试原有窗口可见性 flags 只应用于重建前的 Activity；改为通过测试专属 lifecycle rule 在每次创建时应用相同 flags，并在结束时注销，未改变业务逻辑或测试断言。重建测试 + 地图测试定向 8/8 通过。
- 最终 `:app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest`（`ANDROID_SERIAL` 限定 SM-X730）：构建通过，JVM 148/148、完整 connected 74/74 通过，无跳过；随后重新执行 lint，仍只有下述既有问题。
- Phase 8 验证时 `:app:lintDebug` 被 11 个既有 `MissingTranslation` 错误阻塞，均在当时未修改的 `values/strings.xml`（CSV 导入结果、恢复进度/设置警告、Quick Add 保存失败文案）。另有 7 个既有 warning；Phase 8 文件不在报告位置中，未修改 baseline 或放宽检查。后续修复见下节。
- 后来接入的 SM-S938B 运行器停在 2/5、没有最终失败结果；停止该定向 Gradle 客户端后，使用 `ANDROID_SERIAL` 将验证限定到最初确认的 SM-X730。该环境问题不计为测试通过，也没有以修改业务代码规避。
- APK / instrumentation target 已核对为 `com.lavacrafter.maptimelinetool.debug`，测试包为 `.debug.test`；正式包只执行只读 `pm path` 检查，测试前后 APK 路径完全一致，未 clear、uninstall、覆盖安装或启动正式 App。

## Lint 收口补充

- 为 11 个现有语言/地区资源目录补齐 CSV 导入汇总与错误说明、备份恢复进度与设置警告、Quick Add 保存失败文案；保留原有简体中文 Quick Add 文案。
- 保持默认英文文案、所有格式参数的位置/类型和现有语言选项不变；`he` / `iw` 两个希伯来语别名使用相同新译文。
- 不修改 lint baseline、检查级别或业务调用，不通过隐藏语言资源或关闭翻译检查规避错误。
- 11 个本地化 XML 的新资源唯一性、格式参数匹配、`he` / `iw` 一致性检查通过。
- `./gradlew :app:lintDebug :app:assembleDebug :app:testDebugUnitTest` 通过；当前 lint 门禁为 0 errors / 7 个既有 warnings（3 `PluralsCandidate`、3 `UnusedResources`、1 `UsableSpace`），baseline 中的历史存量未改动。单测任务复用 148/148 全绿结果（`UP-TO-DATE`）；Debug APK 重新构建成功。
