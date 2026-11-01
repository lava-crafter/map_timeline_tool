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

## Phase 9 — 小范围维护债清理

本节对应同一稳定性计划的 Phase 9，保留已有数据库版本、repository transaction 和导出格式，不新增 GPX 功能、依赖或架构层。

- **9.1 GPX：**全项目引用检索只发现 `GpxExporter` 自身定义，没有调用或导出入口；删除 dead file。
- **9.2 N+1：**GeoJSON / KML / KMZ 的标签名称映射及普通 ZIP 的标签组装改用一次 all-relations query，与 tags 在同一 Room snapshot 中读取。普通 ZIP 仍只包含选中 Point 使用的 Tags，禁用 Tags 时不查询标签/关系；Full Backup 已有的全部关系快照保持不变。
- **9.3 CrossRef：**不加 foreign keys、不改 schema；`PointRepositoryIntegrityTest` 验证删除 Point、删除 Tag 均清掉其全部关系，且保留无关数据；另验证删除 Point 失败时关系清理回滚。测试使用独立 debug 包及 in-memory Room。
- **9.4 SAF：**五种 CreateDocument 导出共用 `writeCreatedDocument`，在完整写入、flush、正常 close 后才返回成功。exporter 可关闭自己的 Writer / ZIP wrapper，但底层目标流只由 helper 关闭一次；仍直接 streaming 写目标，不复制大型 ZIP。打开、准备 payload、写入、flush、close 失败或取消后，在 NonCancellable IO 中 best-effort 删除刚创建的 URI；清理失败不会覆盖原始异常，取消继续传播。Full Backup 分享也不再吞掉取消。
- 对真正的 document URI 使用 `DocumentsContract.deleteDocument`；其他 ContentProvider 使用 `ContentResolver.delete`，避免 provider 忽略 document call 却返回“成功”而留下残片。
- `CreatedDocumentExportTest` 覆盖流的完成顺序、失败/取消与清理异常；`CreatedDocumentExportInstrumentedTest` 用 debug FileProvider 的真实 ContentResolver 流验证全部五种格式及失败/取消删除，不打开系统 picker、不使用用户文件。
- `MainActivityExportSupportTest` 用 1,201 Points 验证导出只读取一次标签/关系，不允许逐 Point query；`ZipRestorePreflightTest` 新增 1,201 个识别照片 entries 的 parser/budget 验证，以及 20,001 总 entries 的拒绝验证（隔离独立的 unknown-entry ceiling）。这些 parser entry fixtures 不冒充真实图片验证；真实 JPEG 恢复和高清 sampled decode 由 connected 用例覆盖。

### 最终完整验证（2026-09-30）

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --rerun-tasks` 通过，57 个 task 实际重跑。随后对最终代码再次执行 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest`，全部通过。
- 最终结果 XML：**JVM 161/161、connected 80/80 通过，0 failures / errors / skipped**；Phase 9 定向 connected 也为 6/6 通过。
- 全量执行包含：1,201 行 CSV 原子导入；1,001 Points / unattached Tag / 501 relations / 302 个真实 JPEG / portable Settings 的 Full Backup round-trip；4096×3072 JPEG sampled decode；大量 ZIP entries；checksum、metadata、DB、照片 move 故障和提交前取消；journal recovery 及 Activity recreation；DB2/3/4/5/历史无 index 的 DB6/后期 DB6/DB7 → DB8。
- 所有大型 fixture 运行时生成并 teardown，没有提交 binary fixture。中断恢复以遗留 journal 模拟验证；没有宣称真实进程 kill 后断点续传，也未运行数 GB archive 实测。
- lint 门禁 **0 active errors / 7 个既有 warnings**（3 `PluralsCandidate`、3 `UnusedResources`、1 `UsableSpace`）；既有 baseline 未改动，没有新增 suppression 或放宽检查。
- 设备为 SM-X730 / API 36，测试前 `/data` 可用约 39 GiB；`ANDROID_SERIAL` 明确限定该设备。APK ID / instrumentation target 分别为 `.debug` / `.debug`，test package 为 `.debug.test`。正式包只做只读 `pm path` 检查，完整测试前后的 APK 路径完全一致，没有 clear、uninstall、覆盖安装或启动正式 App。connected 完成后 debug 包已不再安装；restore fixture 内的 staging/journal 清理由断言、recovery 和 teardown 覆盖。
- runner 有一条 `androidx.test.services` 未安装导致 appops 设置失败的环境提示，但全部 80 个 connected 用例完成且无跳过，整体 `BUILD SUCCESSFUL`；不将该提示当成产品失败或跳过测试的理由。
- 最终 `git diff --check` 通过，generated OSS resources 和 Room schema 未产生额外 source-tree 修改。

## 剩余修复阶段 1 — ZIP 照片完整性与恢复清理（2026-10-02）

对应 [剩余问题修复计划](https://github.com/lava-crafter/map_timeline_tool/issues/39#issuecomment-5957251841) 的第一阶段，基于 `ffe29b3` 工作树实施；未改 Room schema 或现有 ZIP Point/Tag 幂等匹配规则。

- 普通 ZIP 不能确认仅照片导出；exporter 在写任何输出前拒绝该选项。Importer 对 v1/v2 manifest 与无 manifest 的照片归档都要求 Points，并拒绝未被 Point 引用的照片；合法空 Full Backup 仍可导出恢复。旧 GeoJSON 照片引用保留 archive 路径至最终解析，避免二次映射丢失。
- `importZipData` 在事务中从实际匹配的旧行收集退休照片路径；coordinator 在 move/Room commit 前持久化 v2 journal，分开记录新文件与退休旧文件。提交、回滚和 recovery 都在共享锁下按全库引用清理，兼容 v1 journal，并保护共享照片。
- 提交后清理失败不撤销正确恢复的数据；返回明确的照片清理 warning，保留 journal，供下一次启动/restore 重试。回滚清理失败保留原错误（清理错误作为 suppressed）及 journal。整个 journal 的路径验证通过后才允许删除。
- `AppGraph` 共用 `PhotoCommitGuard`，接入 Add/Edit/Delete/CSV 核心提交、Restore/Recovery 及 UI 候选照片清理。定位、采样、noise、解压不持有该锁；提交前检查照片仍存在。Edit/Delete 读取当前 DB 行的照片路径，不以过期草稿决定清理对象。
- JVM 回归包含 exporter 拒绝且零输出、无 manifest/manifest 仅照片拒绝、未引用照片拒绝、空备份、旧 GeoJSON 单次映射、共享照片、旧草稿、并发清理与采样/noise 不占提交锁。
- 新增 connected 回归包含连续三次含照片 ZIP 恢复、共享旧文件、move/DB 故障、v2 提交前/后遗留 journal、清理重试、原异常保护、危险 journal、恶意归档不改原数据、空 Full Backup、Restore 与旧候选提交并发，以及导出选项 UI 正反例。

本轮最终执行：

```bash
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

- **BUILD SUCCESSFUL**；最终 JVM XML 为 **171/171，0 failures / errors / skipped**。其中 `PointWriteUseCaseTest` 16/16、`ZipExportImportTest` 18/18、`ZipRestorePreflightTest` 9/9、`ArchiveBoundaryVerificationTest` 7/7。
- Debug 与 AndroidTest APK 构建、设备测试 Kotlin 编译均通过；APK ID 分别核对为 `com.lavacrafter.maptimelinetool.debug`、`.debug.test`。
- lint 为 **0 active errors / 7 个既有 warnings**；未修改 baseline 或新增 suppression。`git diff --check` 通过，未产生额外 schema/generated source 修改。

**首次本地验证的设备边界：**当时 `adb devices -l` 无设备，设备测试仅编译；后续设备接入结果见下节。现存 2026-09-30 的 80/80 connected 结果不覆盖本轮改动。未进行真实进程 kill 或突然断电测试，也不宣称跨断电文件系统原子性。

### 设备接入后的补充验收（2026-10-02）

- SM-X730 / API 36 已连接，使用 `ANDROID_SERIAL=localhost:54461` 限定目标。APK badging 与 instrumentation manifest 分别确认 application ID 和 targetPackage 均为 `com.lavacrafter.maptimelinetool.debug`，测试包为 `.debug.test`。
- 第一阶段定向 connected：`ZipRestoreCoordinatorTest`、`ZipExportOptionsDialogTest`、`DataIntegrityVerificationTest`、`PointPhotoUtilsTest`，原始结果 XML 为 **54/54，0 failures / errors / skipped**，Gradle **BUILD SUCCESSFUL**。
- 随后执行完整 `:app:connectedDebugAndroidTest`，原始结果 XML 为 **93/93，0 failures / errors / skipped**，Gradle **BUILD SUCCESSFUL**。包含新增照片恢复/并发清理用例及既有迁移、Activity recreation、地图、导出、传感器与设置回归。
- 运行器提示定向仅取到 4/54、全量仅取到 2/93 用例的 logcat，但全部测试完成且通过；这是日志采集限制，不是跳过或失败。
- 测试前后对正式包仅执行只读 `pm path com.lavacrafter.maptimelinetool`，均无输出（该设备没有已安装的正式包），保存的前后结果 `cmp` 一致；未 clear/uninstall/覆盖安装或启动正式包。最终 `git diff --check` 通过。
- 第一阶段本地/设备自动验收完成：**JVM 171/171 + connected 93/93**。进程中断覆盖仍是 journal 边界模拟，不宣称真实 kill/断电验证；第二阶段尚未实施。

## 剩余修复阶段 2 — 写入重建、权限续办与事件时间

基于 `6268706` 的干净工作树实施；下列改动尚未提交，不进入阶段 3–6。

- Add/Edit/Delete 请求复制标签集合，提交即同步设置 operation ID/Running，由 `AppViewModel.viewModelScope` 内的 `PointWriteOperations` 持有定位、照片 prepare、核心提交、可选 noise 和清理任务。Compose 只提交请求、观察状态、重置相应草稿及按 ID acknowledge；重建不会重置 saving guard。成功状态尚未被 UI 消费时禁止再次提交。
- 核心事务开始后，以 `NonCancellable` 完成事务及 core-commit callback（定位、采样、等锁仍可取消）；取消发生在提交后时终态保持成功，不能变成可重复重试的失败。照片 adapter 仅持 application context，复用阶段 1 guard 和全库引用检查；失败回滚生成照片，保留源候选供重试。
- 前台权限续办保存动作 enum、UUID 和点击时间，并分别保存 foreground/notification/settings result 的请求 ID；先清除待办再按匹配 ID 派发。缺元数据、失配或拒绝安全取消。覆盖 New Point、主地图/冻结下载页面定位中心、Quick Add 启用与既有 `ACTION_QUICK_ADD` 入口；不保存任意 lambda。入口请求由 ViewModel 保存，避免 Compose 尚未订阅时 SharedFlow 丢失；中心结果由新 MapView 消费，不调用旧地图闭包。
- 手动位置确认在 `SavedStateHandle` 保存轻量草稿和 fix metadata，点击确认重新评估 fix 年龄，过期/缺 fix 不提交。共享自动保存 gate 在确认、相机未返回、写入及失败暂停时同时禁止倒计时推进和自动提交。
- 普通 Add 及旧定时入口不再使用 `max(eventTime, fixTime)`；新 Point timestamp 和默认标题使用 event time，fix 单独存入既有 `locationFixTimeMs`。Quick Add 保留 click-time，权限等待不重置该时间。历史数据与 Room schema 不变。
- 导出 subset route、tag ID、manual IDs、日期 epochDay、日期 picker 开放状态及 ZIP options 保存恢复；pending selection 只保存 discriminator/IDs/range，并从 repository 重新解析，避免重建时 UI 空快照产生空导出。没有实施阶段 3 的 LazyColumn/Marker/日期架构。

新增覆盖：JVM 可控挂起定位/采样/事务返回/optional noise、取消前后状态、重复提交、DB 故障照片回滚与重试、不可变标签、确认恢复过期、auto-save gate、event 早于 fix；connected 为真实 MainActivity + 注入的保留 ViewModel/in-memory Room 的保存中重建及失败草稿重试，确认参数序列化、ActivityResultRegistry 可控权限结果恢复及匹配检查，以及 subset selection save/restore。

**验证边界：**权限结果用真实 ActivityResultRegistry 控制派发，不宣称操作 OEM 系统权限窗口；照片/标签候选重试的一项设备测试使用不可变请求快照与 fake prepare，未操作真实相机；时间 picker 覆盖恢复开放状态与默认范围，非自定义日期完整组合。进程 kill/突然断电与真实外部相机返回时序仍未验证；自动重建测试不能替代这些边界。

### 阶段 2 最终验证

```bash
ANDROID_SERIAL=localhost:54461 ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

- **BUILD SUCCESSFUL**；JVM XML **180/180**（`PointWriteOperationsTest` 9/9），完整 connected XML **104/104**，均为 **0 failures / errors / skipped**。完整设备回归包含停止 UI 后成功提交、重建后消费终态且不重试的新增用例；此前定向 17/17 结果不替代最终全量报告。
- SM-X730 / API 36；APK badging 与 instrumentation target 均核对为 `.debug`，测试包 `.debug.test`。正式包仅只读 `pm path` 检查，前后保存结果 `cmp` 一致；未 clear/uninstall/覆盖或启动正式包。
- lint **0 active errors / 7 个既有 warnings**，baseline 未改，无新增 suppression。曾遇到一次 lint 分析器异常；稳定重试正常完成，暴露的资源读取错误与 SavedStateHandle 测试构造器警告已修正。编译仍提示测试 API/窗口 flags 的弃用，不影响通过结果。
- runner 只采集到 8/104 用例的 logcat，但所有测试均完成且通过；日志采集限制不是 skipped。
- 原始日志：`/tmp/opencode/issue39-phase2-verified.log`。最终 `git diff --check` 通过，未修改 schema/generated source；改动尚未提交，未开始阶段 3。
