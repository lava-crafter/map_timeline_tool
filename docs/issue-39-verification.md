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
