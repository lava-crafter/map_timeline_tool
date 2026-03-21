package com.lavacrafter.maptimelinetool

import android.Manifest
import android.content.Intent
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.lavacrafter.maptimelinetool.createPendingPointPhotoFile
import com.lavacrafter.maptimelinetool.deletePointPhotoFile
import com.lavacrafter.maptimelinetool.resolvePointPhotoFile
import com.lavacrafter.maptimelinetool.toStoredPhotoPath
import com.lavacrafter.maptimelinetool.data.toDomain
import com.lavacrafter.maptimelinetool.data.toUi
import com.lavacrafter.maptimelinetool.export.CsvExporter
import com.lavacrafter.maptimelinetool.export.CsvImporter
import com.lavacrafter.maptimelinetool.export.ZipExporter
import com.lavacrafter.maptimelinetool.export.ZipImporter
import com.lavacrafter.maptimelinetool.ui.ExportSelection
import com.lavacrafter.maptimelinetool.ui.ExportKind
import com.lavacrafter.maptimelinetool.ui.ExportScreens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.lavacrafter.maptimelinetool.ui.AboutScreen
import com.lavacrafter.maptimelinetool.ui.AddPointDialog
import com.lavacrafter.maptimelinetool.ui.AppViewModel
import com.lavacrafter.maptimelinetool.ui.EditPointDialog
import com.lavacrafter.maptimelinetool.ui.EditTagDialog
import com.lavacrafter.maptimelinetool.ui.ListScreen
import com.lavacrafter.maptimelinetool.ui.MapScreen
import com.lavacrafter.maptimelinetool.ui.MapCachePolicy
import com.lavacrafter.maptimelinetool.ui.TagDetailScreen
import com.lavacrafter.maptimelinetool.ui.TagListScreen
import com.lavacrafter.maptimelinetool.ui.TagSelectionDialog
import com.lavacrafter.maptimelinetool.ui.SettingsRoute
import com.lavacrafter.maptimelinetool.ui.SettingsScreen
import com.lavacrafter.maptimelinetool.ui.SettingsViewModel
import com.lavacrafter.maptimelinetool.ui.downloadTileSourceById
import com.lavacrafter.maptimelinetool.ui.ZoomButtonBehavior
import com.lavacrafter.maptimelinetool.ui.applyMapCachePolicy
import com.lavacrafter.maptimelinetool.ui.applyLanguagePreference
import com.lavacrafter.maptimelinetool.ui.theme.MapTimelineToolTheme
import com.lavacrafter.maptimelinetool.notification.QuickAddService
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.osmdroid.config.Configuration

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : AppCompatActivity() {
    private val graph by lazy { applicationContext.appGraph() }
    private val viewModel: AppViewModel by viewModels {
        AppViewModel.factory(application, graph)
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(application, graph.settingsManagementUseCase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsUseCase = graph.settingsManagementUseCase

        applyLanguagePreference(settingsUseCase.getLanguagePreference().toUi())

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            MapTimelineToolTheme(darkTheme = if (settingsState.followSystemTheme) isSystemDark else settingsState.isDarkTheme) {
                val context = LocalContext.current
                var showTagPickerForAdd by remember { mutableStateOf(false) }
                var showTagPickerForEdit by remember { mutableStateOf(false) }
                var showMapDownload by remember { mutableStateOf(false) }
                var showExportFlow by remember { mutableStateOf(false) }
                var showZipExportOptions by remember { mutableStateOf(false) }
                var settingsRoute by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Main) }
                var newPointSelectedTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
                var showPinLimitDialog by remember { mutableStateOf(false) }
                var showExitDialog by remember { mutableStateOf(false) }
                var newPointTitle by remember { mutableStateOf("") }
                var newPointNote by remember { mutableStateOf("") }
                var pendingAddPhotoPath by remember { mutableStateOf<String?>(null) }
                var pendingAddPhotoUri by remember { mutableStateOf<Uri?>(null) }
                var previewPhotoPath by remember { mutableStateOf<String?>(null) }
                var remainingSeconds by remember { mutableStateOf(settingsState.timeoutSeconds) }
                var isCountdownPaused by remember { mutableStateOf(false) }
                var lastTypingTime by remember { mutableStateOf<Long?>(null) }
                val scaffoldState = rememberBottomSheetScaffoldState()
                val sheetState = scaffoldState.bottomSheetState

                val recordRecentTag: (Long) -> Unit = { tagId ->
                    settingsViewModel.addRecentTagId(tagId)
                }
                val toggleDefaultTag: (Long) -> Unit = { tagId ->
                    settingsViewModel.toggleDefaultTag(tagId)
                }
                val toggleNewPointTag: (Long) -> Unit = { tagId ->
                    newPointSelectedTagIds = if (newPointSelectedTagIds.contains(tagId)) {
                        newPointSelectedTagIds - tagId
                    } else {
                        recordRecentTag(tagId)
                        newPointSelectedTagIds + tagId
                    }
                }
                val onUserTyping = {
                    lastTypingTime = System.currentTimeMillis()
                    isCountdownPaused = true
                }

                val scope = rememberCoroutineScope()
                data class PendingExportPayload(
                    val points: List<com.lavacrafter.maptimelinetool.domain.model.Point>,
                    val zip: Boolean,
                    val zipOptions: ZipExporter.ExportOptions = ZipExporter.ExportOptions(),
                    val zipTags: List<ZipExporter.TagRecord> = emptyList(),
                    val pointTagIdsByPointId: Map<Long, List<Long>> = emptyMap()
                )
                var pendingExportPayload by remember { mutableStateOf<PendingExportPayload?>(null) }
                var pendingExportSelection by remember { mutableStateOf<ExportSelection?>(null) }
                var zipIncludePoints by remember { mutableStateOf(true) }
                var zipIncludeTags by remember { mutableStateOf(true) }
                var zipIncludeSensors by remember { mutableStateOf(true) }
                var zipIncludePhotos by remember { mutableStateOf(true) }
                val networkStatus by observeNetworkStatus(context)
                val addPhotoLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.TakePicture()
                ) { isSuccess ->
                    val photoPath = pendingAddPhotoPath
                    if (!isSuccess) {
                        scope.launch(Dispatchers.IO) {
                            deletePointPhotoFile(context, photoPath)
                        }
                        pendingAddPhotoPath = null
                    }
                    pendingAddPhotoUri = null
                }
                val exportCsvLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/csv")
                ) { uri ->
                    val pending = pendingExportPayload
                    if (uri == null || pending == null) {
                        pendingExportPayload = null
                        return@rememberLauncherForActivityResult
                    }
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openOutputStream(uri)?.use { output ->
                                    CsvExporter.writeCsv(pending.points, output)
                                } ?: throw IOException("Failed to open output stream")
                            }
                        }.onSuccess {
                            Toast.makeText(context, context.getString(R.string.toast_export_success, pending.points.size), Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
                        }
                        pendingExportPayload = null
                    }
                }
                val exportZipLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/zip")
                ) { uri ->
                    val pending = pendingExportPayload
                    if (uri == null || pending == null) {
                        pendingExportPayload = null
                        return@rememberLauncherForActivityResult
                    }
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openOutputStream(uri)?.use { output ->
                                    ZipExporter.export(
                                        points = pending.points,
                                        outputStream = output,
                                        resolvePhotoFile = { photoPath ->
                                            resolvePointPhotoFile(context, photoPath)
                                        },
                                        options = pending.zipOptions,
                                        tags = pending.zipTags,
                                        pointTagIdsByPointId = pending.pointTagIdsByPointId
                                    )
                                } ?: throw IOException("Failed to open output stream")
                            }
                        }.onSuccess {
                            Toast.makeText(context, context.getString(R.string.toast_export_success, pending.points.size), Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
                        }
                        pendingExportPayload = null
                    }
                }
                val importCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        runCatching {
                            val importedPoints = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    CsvImporter.parseCsv(input.reader(Charsets.UTF_8))
                                } ?: emptyList()
                            }
                            viewModel.importPoints(importedPoints)
                            Toast.makeText(context, context.getString(R.string.toast_import_success, importedPoints.size), Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.toast_import_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                val importZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        runCatching {
                            val imported = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    ZipImporter.importZip(input) { entryName, photoInput ->
                                        runCatching {
                                            val extension = entryName.substringAfterLast('.', "").lowercase(Locale.US)
                                            val safeExt = extension.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "jpg"
                                            val importedPhotoFile = java.io.File(getPointPhotoDir(context), "point_photo_${UUID.randomUUID()}.$safeExt")
                                            importedPhotoFile.outputStream().buffered().use { output -> photoInput.copyTo(output) }
                                            toStoredPhotoPath(importedPhotoFile)
                                        }.getOrNull()
                                    }
                                } ?: ZipImporter.ImportStats(emptyList(), emptyList(), emptyList(), 0, 0)
                            }
                            viewModel.importZipData(imported)
                            Toast.makeText(context, context.getString(R.string.toast_import_success, imported.points.size), Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.toast_import_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    val granted = result.values.all { it }
                    if (!granted) {
                        Toast.makeText(context, context.getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show()
                    }
                }
                val audioPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        settingsViewModel.setNoiseEnabled(true)
                    } else {
                        settingsViewModel.setNoiseEnabled(false)
                        Toast.makeText(context, context.getString(R.string.toast_noise_permission_denied), Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.CAMERA
                        )
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                    startService(Intent(context, QuickAddService::class.java))
                }

                LaunchedEffect(settingsState.cachePolicy, settingsState.satelliteCachePolicy, settingsState.mapTileSourceId, networkStatus) {
                    applyMapCachePolicy(context, settingsState.mapTileSourceId)
                }


                var tab by remember { mutableStateOf(0) }
                var showAbout by remember { mutableStateOf(false) }
                var showDialog by remember { mutableStateOf(false) }
                var pendingTimestamp by remember { mutableStateOf<Long?>(null) }
                var selectedPointId by remember { mutableStateOf<Long?>(null) }
                var editingPoint by remember { mutableStateOf<com.lavacrafter.maptimelinetool.data.PointEntity?>(null) }
                var editingPointTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
                var editingPointPhotoPath by remember { mutableStateOf<String?>(null) }
                var editingCaptureCandidatePhotoPath by remember { mutableStateOf<String?>(null) }
                var pendingEditPhotoUri by remember { mutableStateOf<Uri?>(null) }
                var editingTag by remember { mutableStateOf<com.lavacrafter.maptimelinetool.data.TagEntity?>(null) }
                var selectedTag by remember { mutableStateOf<com.lavacrafter.maptimelinetool.data.TagEntity?>(null) }
                val editPhotoLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.TakePicture()
                ) { isSuccess ->
                    val capturedPath = editingCaptureCandidatePhotoPath
                    if (isSuccess) {
                        editingPointPhotoPath = capturedPath
                    } else {
                        scope.launch(Dispatchers.IO) { deletePointPhotoFile(context, capturedPath) }
                    }
                    editingCaptureCandidatePhotoPath = null
                    pendingEditPhotoUri = null
                }
                fun deletePhotoOnIo(path: String?) {
                    scope.launch(Dispatchers.IO) { deletePointPhotoFile(context, path) }
                }
                suspend fun preparePhotoPathForPersist(rawPhotoPath: String?): String? {
                    return preparePhotoForPersist(
                        context = context,
                        photoPath = rawPhotoPath,
                        options = PhotoPersistOptions(
                            losslessEnabled = settingsState.photoLosslessEnabled,
                            compressFormat = settingsState.photoCompressFormat,
                            compressQuality = settingsState.photoCompressQuality
                        )
                    )
                }
                val clearPendingAddPhoto = {
                    val pathToDelete = pendingAddPhotoPath
                    pendingAddPhotoPath = null
                    pendingAddPhotoUri = null
                    deletePhotoOnIo(pathToDelete)
                }
                val clearUnsavedEditingPhoto = {
                    val basePhotoPath = editingPoint?.photoPath
                    val pathToDelete = editingPointPhotoPath
                    if (!pathToDelete.isNullOrBlank() && pathToDelete != basePhotoPath) {
                        deletePhotoOnIo(pathToDelete)
                    }
                }
                val resetEditingPointState = {
                    editingPoint = null
                    editingPointTagIds = emptySet()
                    editingPointPhotoPath = null
                    editingCaptureCandidatePhotoPath = null
                    pendingEditPhotoUri = null
                }
                val toggleEditingPointTag: (Long) -> Unit = { tagId ->
                    editingPoint?.let { point ->
                        val shouldAttach = !editingPointTagIds.contains(tagId)
                        viewModel.setTagForPoint(point.id, tagId, shouldAttach)
                        editingPointTagIds = if (shouldAttach) {
                            recordRecentTag(tagId)
                            editingPointTagIds + tagId
                        } else {
                            editingPointTagIds - tagId
                        }
                    }
                }
                BackHandler(showTagPickerForEdit) { showTagPickerForEdit = false }
                BackHandler(showTagPickerForAdd) { showTagPickerForAdd = false }
                BackHandler(editingPoint != null) {
                    clearUnsavedEditingPhoto()
                    resetEditingPointState()
                }
                BackHandler(editingTag != null) { editingTag = null }
                BackHandler(showDialog && !showTagPickerForAdd && !showTagPickerForEdit) {
                    viewModel.cancelAutoAdd()
                    clearPendingAddPhoto()
                    showDialog = false
                    pendingTimestamp = null
                    newPointSelectedTagIds = emptySet()
                    showTagPickerForAdd = false
                }
                BackHandler(showAbout) { showAbout = false }
                BackHandler(showMapDownload) { showMapDownload = false }
                BackHandler(showZipExportOptions) { showZipExportOptions = false }
                BackHandler(tab == 2 && settingsRoute != SettingsRoute.Main) { settingsRoute = SettingsRoute.Main }
                BackHandler(selectedTag != null) { selectedTag = null }
                BackHandler(!showDialog && !showTagPickerForAdd && !showTagPickerForEdit && editingPoint == null && editingTag == null && !showAbout && selectedTag == null && sheetState.currentValue != SheetValue.Expanded) {
                    showExitDialog = true
                }
                BackHandler(showExitDialog) {
                    finishAffinity()
                }
                LaunchedEffect(showDialog, pendingTimestamp, settingsState.timeoutSeconds) {
                    if (showDialog && pendingTimestamp != null) {
                        remainingSeconds = settingsState.timeoutSeconds
                        isCountdownPaused = false
                        lastTypingTime = null
                        newPointTitle = ""
                        newPointNote = ""
                        pendingAddPhotoPath = null
                        pendingAddPhotoUri = null
                    }
                }
                LaunchedEffect(lastTypingTime, showDialog) {
                    val typingAt = lastTypingTime ?: return@LaunchedEffect
                    if (!showDialog) return@LaunchedEffect
                    kotlinx.coroutines.delay(3000L)
                    if (lastTypingTime == typingAt) {
                        isCountdownPaused = false
                    }
                }
                LaunchedEffect(showDialog, isCountdownPaused, remainingSeconds, pendingTimestamp) {
                    if (!showDialog || pendingTimestamp == null) return@LaunchedEffect
                    if (remainingSeconds <= 0) return@LaunchedEffect
                    if (isCountdownPaused) return@LaunchedEffect
                    kotlinx.coroutines.delay(1000L)
                    if (showDialog && !isCountdownPaused) {
                        remainingSeconds -= 1
                    }
                }
                LaunchedEffect(remainingSeconds, showDialog, pendingTimestamp) {
                    if (!showDialog || pendingTimestamp == null) return@LaunchedEffect
                    if (remainingSeconds > 0) return@LaunchedEffect
                    val createdAt = pendingTimestamp!!
                    val defaultTitle = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(createdAt))
                    val title = newPointTitle.trim().ifBlank { defaultTitle }
                    val note = newPointNote.trim()
                    val addPhotoPath = pendingAddPhotoPath
                    scope.launch {
                        val loc = viewModel.getLastKnownLocation() ?: viewModel.getFreshLocation(2000L)
                        if (loc == null) {
                            Toast.makeText(context, context.getString(R.string.toast_location_failed), Toast.LENGTH_SHORT).show()
                            withContext(Dispatchers.IO) {
                                deletePointPhotoFile(context, addPhotoPath)
                            }
                        } else {
                            val persistedPhotoPath = preparePhotoPathForPersist(addPhotoPath)
                            viewModel.addPointWithTags(title, note, loc, createdAt, newPointSelectedTagIds, persistedPhotoPath)
                            vibrateOnce(context)
                            Toast.makeText(context, context.getString(R.string.toast_point_added), Toast.LENGTH_SHORT).show()
                        }
                        showDialog = false
                        pendingTimestamp = null
                        newPointSelectedTagIds = emptySet()
                        pendingAddPhotoPath = null
                        pendingAddPhotoUri = null
                        showTagPickerForAdd = false
                    }
                }
                val pointsState = viewModel.points.collectAsState().value
                LaunchedEffect(pendingExportSelection, pointsState) {
                    val sel = pendingExportSelection ?: return@LaunchedEffect
                    val pointsToExport = when (sel.kind) {
                        is ExportKind.All -> pointsState
                        is ExportKind.ByTag -> {
                            val tagId = (sel.kind as ExportKind.ByTag).tagId
                            viewModel.observePointsForTag(tagId).first()
                        }
                        is ExportKind.ByTime -> {
                            val k = sel.kind as ExportKind.ByTime
                            pointsState.filter { it.timestamp in k.fromMs..k.toMs }
                        }
                        is ExportKind.Manual -> {
                            val ids = (sel.kind as ExportKind.Manual).ids
                            pointsState.filter { ids.contains(it.id) }
                        }
                    }
                    val pointsToExportDomain = pointsToExport.map { it.toDomain() }
                    val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    val pending = pendingExportPayload
                    if (pending != null) {
                        pendingExportSelection = null
                        return@LaunchedEffect
                    }
                    val baseName = "map_timeline_${sdf.format(java.util.Date())}"
                    val payload = PendingExportPayload(points = pointsToExportDomain, zip = false)
                    pendingExportPayload = payload
                    exportCsvLauncher.launch("$baseName.csv")
                    pendingExportSelection = null
                    showExportFlow = false
                    tab = 2
                    settingsRoute = SettingsRoute.Main
                }
                val tagsState = viewModel.tags.collectAsState().value
                val quickTags = remember(tagsState, settingsState.pinnedTagIds, settingsState.recentTagIds) {
                    val pinned = tagsState.filter { settingsState.pinnedTagIds.contains(it.id) }
                    val recent = settingsState.recentTagIds.mapNotNull { id -> tagsState.firstOrNull { it.id == id } }
                    (pinned + recent).distinctBy { it.id }.take(3)
                }
                val downloadTileSource = remember(settingsState.downloadTileSourceId) { downloadTileSourceById(settingsState.downloadTileSourceId) }
                val exportCsv: () -> Unit = {
                    // Open export flow UI
                    showExportFlow = true
                }
                val exportZip: () -> Unit = {
                    zipIncludePoints = true
                    zipIncludeTags = true
                    zipIncludeSensors = true
                    zipIncludePhotos = true
                    showZipExportOptions = true
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            actions = { }
                        )
                    },
                    floatingActionButton = {
                        if (tab == 0) {
                            ExtendedFloatingActionButton(
                                modifier = Modifier.height(64.dp),
                                onClick = {
                                    pendingTimestamp = System.currentTimeMillis()
                                    newPointSelectedTagIds = settingsState.defaultTagIds
                                    showDialog = true
                                }
                            ) {
                                Text(stringResource(R.string.action_add_point))
                            }
                        }
                    },
                    floatingActionButtonPosition = FabPosition.Center,
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0; showExportFlow = false },
                                label = { Text(stringResource(R.string.tab_map)) },
                                icon = { }
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1; showExportFlow = false },
                                label = { Text(stringResource(R.string.tab_tags)) },
                                icon = { }
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { tab = 2; showExportFlow = false },
                                label = { Text(stringResource(R.string.tab_settings)) },
                                icon = { }
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        when (tab) {
                            0 -> MapWithListSheet(
                                points = pointsState,
                                selectedPointId = selectedPointId,
                                onSelectPoint = { point ->
                                    selectedPointId = point.id
                                    scope.launch {
                                        scaffoldState.bottomSheetState.partialExpand()
                                    }
                                },
                                onLongPressPoint = { point ->
                                    editingPoint = point
                                },
                                onEditPointFromMap = { point -> editingPoint = point },
                                isActive = tab == 0,
                                zoomBehavior = settingsState.zoomBehavior,
                                markerScale = settingsState.markerScale,
                                downloadedOnly = settingsState.downloadedAreas.isNotEmpty() || run {
                                    val isSatellite = settingsState.mapTileSourceId == "eox_sentinel2_cloudless_2024"
                                    val policy = if (isSatellite) settingsState.satelliteCachePolicy else settingsState.cachePolicy
                                    policy == MapCachePolicy.DISABLED || (policy == MapCachePolicy.WIFI_ONLY && networkStatus != NetworkStatus.WIFI)
                                },
                                mapTileSourceId = settingsState.mapTileSourceId,
                                onMapTileSourceChange = settingsViewModel::setMapTileSourceId,
                                scaffoldState = scaffoldState
                            )
                            1 -> {
                                if (selectedTag != null) {
                                    val tagPoints = viewModel.observePointsForTag(selectedTag!!.id).collectAsState(emptyList()).value
                                    TagDetailScreen(
                                        tag = selectedTag!!,
                                        points = tagPoints,
                                        allPoints = pointsState,
                                        onSelectPoint = { point ->
                                            selectedPointId = point.id
                                            tab = 0
                                        },
                                        onLongPressPoint = { point -> editingPoint = point },
                                        onAddPointToTag = { point -> viewModel.setTagForPoint(point.id, selectedTag!!.id, true) },
                                        onRemovePointFromTag = { point -> viewModel.setTagForPoint(point.id, selectedTag!!.id, false) },
                                        onBack = { selectedTag = null }
                                    )
                                } else {
                                    TagListScreen(
                                        tags = tagsState,
                                        pinnedTagIds = settingsState.pinnedTagIds,
                                        onAddTag = { name -> viewModel.addTag(name) },
                                        onOpenTag = { tag -> selectedTag = tag },
                                        onEditTag = { tag -> editingTag = tag },
                                        onTogglePin = { tag, shouldPin ->
                                            if (shouldPin && settingsState.pinnedTagIds.size >= 3) {
                                                showPinLimitDialog = true
                                            } else {
                                                val updated = if (shouldPin) settingsState.pinnedTagIds + tag.id else settingsState.pinnedTagIds - tag.id
                                                settingsViewModel.setPinnedTagIds(updated)
                                            }
                                        }
                                    )
                                }
                            }
                            2 -> if (showAbout) {
                                AboutScreen(onBack = { showAbout = false })
                            } else if (showMapDownload) {
                                com.lavacrafter.maptimelinetool.ui.MapDownloadScreen(
                                    onBack = { showMapDownload = false },
                                    onAreaDownloaded = { area ->
                                        settingsViewModel.addDownloadedArea(area)
                                    },
                                    tileSource = downloadTileSource,
                                    useMultiThreadDownload = settingsState.downloadMultiThreadEnabled,
                                    downloadThreadCount = settingsState.downloadThreadCount
                                )
                            } else {
                                SettingsScreen(
                                    isDarkTheme = settingsState.isDarkTheme,
                                    onDarkThemeChange = settingsViewModel::setDarkTheme,
                                    followSystemTheme = settingsState.followSystemTheme,
                                    onFollowSystemThemeChange = settingsViewModel::setFollowSystemTheme,
                                    languagePreference = settingsState.languagePreference,
                                    onLanguagePreferenceChange = settingsViewModel::setLanguagePreference,
                                    timeoutSeconds = settingsState.timeoutSeconds,
                                    onTimeoutSecondsChange = settingsViewModel::setTimeoutSeconds,
                                    cachePolicy = settingsState.cachePolicy,
                                    onCachePolicyChange = settingsViewModel::setCachePolicy,
                                    satelliteCachePolicy = settingsState.satelliteCachePolicy,
                                    onSatelliteCachePolicyChange = settingsViewModel::setSatelliteCachePolicy,
                                    networkStatus = networkStatus,
                                    selectedDownloadTileSourceId = settingsState.downloadTileSourceId,
                                    onDownloadTileSourceChange = settingsViewModel::setDownloadTileSourceId,
                                    downloadMultiThreadEnabled = settingsState.downloadMultiThreadEnabled,
                                    onDownloadMultiThreadEnabledChange = settingsViewModel::setDownloadMultiThreadEnabled,
                                    downloadThreadCount = settingsState.downloadThreadCount,
                                    onDownloadThreadCountChange = settingsViewModel::setDownloadThreadCount,
                                    photoLosslessEnabled = settingsState.photoLosslessEnabled,
                                    onPhotoLosslessEnabledChange = settingsViewModel::setPhotoLosslessEnabled,
                                    photoCompressFormat = settingsState.photoCompressFormat,
                                    onPhotoCompressFormatChange = settingsViewModel::setPhotoCompressFormat,
                                    photoCompressQuality = settingsState.photoCompressQuality,
                                    onPhotoCompressQualityChange = settingsViewModel::setPhotoCompressQuality,
                                    pressureEnabled = settingsState.pressureEnabled,
                                    onPressureEnabledChange = settingsViewModel::setPressureEnabled,
                                    ambientLightEnabled = settingsState.ambientLightEnabled,
                                    onAmbientLightEnabledChange = settingsViewModel::setAmbientLightEnabled,
                                    accelerometerEnabled = settingsState.accelerometerEnabled,
                                    onAccelerometerEnabledChange = settingsViewModel::setAccelerometerEnabled,
                                    gyroscopeEnabled = settingsState.gyroscopeEnabled,
                                    onGyroscopeEnabledChange = settingsViewModel::setGyroscopeEnabled,
                                    magnetometerEnabled = settingsState.magnetometerEnabled,
                                    onMagnetometerEnabledChange = settingsViewModel::setMagnetometerEnabled,
                                    noiseEnabled = settingsState.noiseEnabled,
                                    onNoiseEnabledChange = { enabled ->
                                        if (!enabled) {
                                            settingsViewModel.setNoiseEnabled(false)
                                        } else if (
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            settingsViewModel.setNoiseEnabled(true)
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    mapTileSourceId = settingsState.mapTileSourceId,
                                    onMapTileSourceChange = settingsViewModel::setMapTileSourceId,
                                    zoomBehavior = settingsState.zoomBehavior,
                                    onZoomBehaviorChange = settingsViewModel::setZoomBehavior,
                                    markerScale = settingsState.markerScale,
                                    onMarkerScaleChange = settingsViewModel::setMarkerScale,
                                    onOpenMapDownload = { showMapDownload = true },
                                    downloadedAreas = settingsState.downloadedAreas,
                                    onRemoveDownloadedArea = settingsViewModel::removeDownloadedArea,
                                    onDeduplicateDownloadedAreas = settingsViewModel::dedupeDownloadedAreas,
                                    onExportCsv = exportCsv,
                                    onExportZip = exportZip,
                                    onImportCsv = { importCsvLauncher.launch("text/*") },
                                    onImportZip = { importZipLauncher.launch("application/zip") },
                                    onClearCache = {
                                        if (settingsState.downloadedAreas.isNotEmpty()) {
                                            Toast.makeText(context, context.getString(R.string.toast_cache_skip_downloaded), Toast.LENGTH_SHORT).show()
                                        } else {
                                            val cacheDir = Configuration.getInstance().osmdroidTileCache
                                            runCatching { cacheDir?.deleteRecursively() }
                                            Toast.makeText(context, context.getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onOpenAbout = { showAbout = true },
                                    defaultTags = tagsState,
                                    selectedDefaultTagIds = settingsState.defaultTagIds,
                                    onToggleDefaultTag = toggleDefaultTag,
                                    route = settingsRoute,
                                    onNavigateTo = { settingsRoute = it },
                                    onNavigateBack = { settingsRoute = SettingsRoute.Main }
                                )
                            }
                        }
                        if (showExportFlow) {
                            ExportScreens(
                                points = pointsState,
                                tags = tagsState,
                                onSelectExport = { sel -> pendingExportSelection = sel },
                                onBack = { showExportFlow = false }
                            )
                        }
                        if (showZipExportOptions) {
                            AlertDialog(
                                onDismissRequest = { showZipExportOptions = false },
                                title = { Text(stringResource(R.string.export_zip_options_title)) },
                                text = {
                                    Column {
                                        Row {
                                            Checkbox(
                                                checked = zipIncludePoints,
                                                onCheckedChange = { checked ->
                                                    zipIncludePoints = checked
                                                    if (!checked) {
                                                        zipIncludeTags = false
                                                        zipIncludeSensors = false
                                                    }
                                                }
                                            )
                                            Text(stringResource(R.string.export_zip_option_points))
                                        }
                                        Row {
                                            Checkbox(
                                                checked = zipIncludeTags,
                                                onCheckedChange = { checked ->
                                                    zipIncludeTags = checked
                                                    if (checked) zipIncludePoints = true
                                                }
                                            )
                                            Text(stringResource(R.string.export_zip_option_tags))
                                        }
                                        Row {
                                            Checkbox(
                                                checked = zipIncludeSensors,
                                                onCheckedChange = { checked ->
                                                    zipIncludeSensors = checked
                                                    if (checked) zipIncludePoints = true
                                                }
                                            )
                                            Text(stringResource(R.string.export_zip_option_sensors))
                                        }
                                        Row {
                                            Checkbox(
                                                checked = zipIncludePhotos,
                                                onCheckedChange = { checked ->
                                                    zipIncludePhotos = checked
                                                }
                                            )
                                            Text(stringResource(R.string.export_zip_option_photos))
                                        }
                                    }
                                },
                                confirmButton = {
                                    val canExport = zipIncludePoints || zipIncludePhotos
                                    TextButton(
                                        onClick = {
                                            if (!canExport) return@TextButton
                                            scope.launch {
                                                val allPointsDomain = pointsState.map { it.toDomain() }
                                                val includePoints = zipIncludePoints
                                                val includePhotos = zipIncludePhotos
                                                val includeTags = includePoints && zipIncludeTags
                                                val includeSensors = includePoints && zipIncludeSensors
                                                val pointTagMap = mutableMapOf<Long, List<Long>>()
                                                val zipTags = mutableListOf<ZipExporter.TagRecord>()
                                                if (includeTags) {
                                                    allPointsDomain.forEach { point ->
                                                        val tagIds = viewModel.getTagIdsForPoint(point.id)
                                                        if (tagIds.isNotEmpty()) {
                                                            pointTagMap[point.id] = tagIds
                                                        }
                                                    }
                                                    val usedTagIds = pointTagMap.values.flatten().toSet()
                                                    tagsState
                                                        .filter { usedTagIds.contains(it.id) }
                                                        .forEach { zipTags.add(ZipExporter.TagRecord(it.id, it.name)) }
                                                }
                                                val payload = PendingExportPayload(
                                                    points = allPointsDomain,
                                                    zip = true,
                                                    zipOptions = ZipExporter.ExportOptions(
                                                        includePoints = includePoints,
                                                        includeTags = includeTags,
                                                        includeSensors = includeSensors,
                                                        includePhotos = includePhotos
                                                    ),
                                                    zipTags = zipTags,
                                                    pointTagIdsByPointId = pointTagMap
                                                )
                                                val pending = pendingExportPayload
                                                if (pending != null) return@launch
                                                val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                                                val baseName = "map_timeline_${sdf.format(java.util.Date())}"
                                                pendingExportPayload = payload
                                                exportZipLauncher.launch("$baseName.zip")
                                                showZipExportOptions = false
                                            }
                                        },
                                        enabled = canExport
                                    ) { Text(stringResource(R.string.action_export_zip)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showZipExportOptions = false }) {
                                        Text(stringResource(R.string.action_cancel))
                                    }
                                }
                            )
                        }
                    }
                }

                LaunchedEffect(showTagPickerForAdd || showTagPickerForEdit) {
                    if (showTagPickerForAdd || showTagPickerForEdit) {
                        isCountdownPaused = true
                    }
                }

                if (showDialog && pendingTimestamp != null) {
                    val launchAddPhotoCapture = {
                        val oldPath = pendingAddPhotoPath
                        val file = createPendingPointPhotoFile(context)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        pendingAddPhotoPath = toStoredPhotoPath(file)
                        pendingAddPhotoUri = uri
                        deletePhotoOnIo(oldPath)
                        addPhotoLauncher.launch(uri)
                    }
                    AddPointDialog(
                        createdAt = pendingTimestamp!!,
                        quickTags = quickTags,
                        tags = tagsState,
                        selectedTagIds = newPointSelectedTagIds,
                        title = newPointTitle,
                        note = newPointNote,
                        remainingSeconds = remainingSeconds,
                        isCountdownPaused = isCountdownPaused,
                        onTitleChange = { newPointTitle = it },
                        onNoteChange = { newPointNote = it },
                        onUserTyping = onUserTyping,
                        onToggleTag = toggleNewPointTag,
                        onOpenTagPicker = { showTagPickerForAdd = true },
                        hasPhoto = !pendingAddPhotoPath.isNullOrBlank(),
                        onTakePhoto = launchAddPhotoCapture,
                        onRetakePhoto = launchAddPhotoCapture,
                        onRemovePhoto = {
                            val oldPath = pendingAddPhotoPath
                            pendingAddPhotoPath = null
                            pendingAddPhotoUri = null
                            deletePhotoOnIo(oldPath)
                        },
                        onViewPhoto = {
                            previewPhotoPath = pendingAddPhotoPath
                        },
                        onDismiss = {
                            clearPendingAddPhoto()
                            showDialog = false
                            pendingTimestamp = null
                            newPointSelectedTagIds = emptySet()
                            showTagPickerForAdd = false
                            remainingSeconds = settingsState.timeoutSeconds
                            isCountdownPaused = false
                            lastTypingTime = null
                            newPointTitle = ""
                            newPointNote = ""
                        },
                        onConfirm = { title, note, createdAt, selectedTags ->
                            scope.launch {
                                val addPhotoPath = pendingAddPhotoPath
                                val loc = viewModel.getFreshLocation(5000L)
                                if (loc == null) {
                                    Toast.makeText(context, context.getString(R.string.toast_location_failed), Toast.LENGTH_SHORT).show()
                                    withContext(Dispatchers.IO) {
                                        deletePointPhotoFile(context, addPhotoPath)
                                    }
                                } else {
                                    val persistedPhotoPath = preparePhotoPathForPersist(addPhotoPath)
                                    viewModel.addPointWithTags(title, note, loc, createdAt, selectedTags, persistedPhotoPath)
                                    vibrateOnce(context)
                                    Toast.makeText(context, context.getString(R.string.toast_point_added), Toast.LENGTH_SHORT).show()
                                }
                                showDialog = false
                                pendingTimestamp = null
                                newPointSelectedTagIds = emptySet()
                                pendingAddPhotoPath = null
                                pendingAddPhotoUri = null
                                showTagPickerForAdd = false
                                remainingSeconds = settingsState.timeoutSeconds
                                isCountdownPaused = false
                                lastTypingTime = null
                                newPointTitle = ""
                                newPointNote = ""
                            }
                        }
                    )
                }

                if (showTagPickerForAdd || showTagPickerForEdit) {
                    TagSelectionDialog(
                        tags = tagsState,
                        selectedTagIds = if (showTagPickerForAdd) newPointSelectedTagIds else editingPointTagIds,
                        onToggleTag = if (showTagPickerForAdd) toggleNewPointTag else toggleEditingPointTag,
                        onCreateTag = { name, onResult ->
                            viewModel.addTag(name) { tagId ->
                                onResult(tagId)
                            }
                        },
                        onDismiss = {
                            showTagPickerForAdd = false
                            showTagPickerForEdit = false
                            if (showDialog) {
                                isCountdownPaused = false
                            }
                        },
                        onConfirm = {
                            showTagPickerForAdd = false
                            showTagPickerForEdit = false
                            if (showDialog) {
                                isCountdownPaused = false
                            }
                        }
                    )
                }


                LaunchedEffect(editingPoint) {
                    editingPoint?.let { point ->
                        editingPointTagIds = viewModel.getTagIdsForPoint(point.id).toSet()
                        editingPointPhotoPath = point.photoPath
                        editingCaptureCandidatePhotoPath = null
                        pendingEditPhotoUri = null
                    }
                }

                if (editingPoint != null) {
                    val point = editingPoint!!
                    val clearReplacedEditingPhoto = {
                        val previousUnsavedPath = editingPointPhotoPath
                        if (!previousUnsavedPath.isNullOrBlank() && previousUnsavedPath != point.photoPath) {
                            deletePhotoOnIo(previousUnsavedPath)
                        }
                    }
                    val launchEditPhotoCapture = {
                        clearReplacedEditingPhoto()
                        val file = createPendingPointPhotoFile(context)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val newPath = toStoredPhotoPath(file)
                        editingCaptureCandidatePhotoPath = newPath
                        pendingEditPhotoUri = uri
                        editPhotoLauncher.launch(uri)
                    }
                    EditPointDialog(
                        point = point,
                        quickTags = quickTags,
                        tags = tagsState,
                        selectedTagIds = editingPointTagIds,
                        onToggleTag = toggleEditingPointTag,
                        onOpenTagPicker = { showTagPickerForEdit = true },
                        currentPhotoPath = editingPointPhotoPath,
                        onTakePhoto = launchEditPhotoCapture,
                        onRetakePhoto = launchEditPhotoCapture,
                        onRemovePhoto = {
                            clearReplacedEditingPhoto()
                            editingPointPhotoPath = null
                        },
                        onViewPhoto = {
                            previewPhotoPath = editingPointPhotoPath
                        },
                        onSave = { title, note, photoPath ->
                            scope.launch {
                                val persistedPhotoPath = if (photoPath == point.photoPath) {
                                    photoPath
                                } else {
                                    preparePhotoPathForPersist(photoPath)
                                }
                                viewModel.updatePoint(point, title, note, persistedPhotoPath)
                                resetEditingPointState()
                            }
                        },
                        onDelete = {
                            clearUnsavedEditingPhoto()
                            viewModel.deletePoint(point)
                            resetEditingPointState()
                        },
                        onDismiss = {
                            clearUnsavedEditingPhoto()
                            resetEditingPointState()
                        }
                    )
                }

                val activePreviewPhotoPath = previewPhotoPath
                if (!activePreviewPhotoPath.isNullOrBlank()) {
                    PhotoPreviewDialog(
                        photoPath = activePreviewPhotoPath,
                        onDismiss = { previewPhotoPath = null }
                    )
                }

                if (editingTag != null) {
                    val tag = editingTag!!
                    EditTagDialog(
                        tag = tag,
                        onRename = { name ->
                            viewModel.renameTag(tag, name)
                            editingTag = null
                        },
                        onDelete = {
                            viewModel.deleteTag(tag.id)
                            if (selectedTag?.id == tag.id) {
                                selectedTag = null
                            }
                            editingTag = null
                        },
                        onDismiss = { editingTag = null }
                    )
                }

                if (showPinLimitDialog) {
                    AlertDialog(
                        onDismissRequest = { showPinLimitDialog = false },
                        confirmButton = {
                            TextButton(onClick = { showPinLimitDialog = false }) {
                                Text(stringResource(R.string.tag_pin_limit_button))
                            }
                        },
                        title = { Text(stringResource(R.string.tags_title)) },
                        text = { Text(stringResource(R.string.tag_pin_limit_message)) }
                    )
                }

                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text(stringResource(R.string.exit_title)) },
                        text = { Text(stringResource(R.string.exit_message)) },
                        confirmButton = {
                            TextButton(onClick = { finishAffinity() }) {
                                Text(stringResource(R.string.exit_button))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitDialog = false }) {
                                Text(stringResource(R.string.exit_cancel_button))
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapWithListSheet(
    points: List<com.lavacrafter.maptimelinetool.data.PointEntity>,
    selectedPointId: Long?,
    onSelectPoint: (com.lavacrafter.maptimelinetool.data.PointEntity) -> Unit,
    onLongPressPoint: (com.lavacrafter.maptimelinetool.data.PointEntity) -> Unit,
    onEditPointFromMap: (com.lavacrafter.maptimelinetool.data.PointEntity) -> Unit,
    isActive: Boolean,
    zoomBehavior: ZoomButtonBehavior,
    markerScale: Float,
    downloadedOnly: Boolean,
    mapTileSourceId: String,
    onMapTileSourceChange: (String) -> Unit,
    scaffoldState: BottomSheetScaffoldState
) {
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 72.dp,
        sheetContent = {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(text = stringResource(R.string.tab_list))
                Spacer(modifier = Modifier.height(8.dp))
                ListScreen(
                    points = points,
                    onSelect = onSelectPoint,
                    onLongPress = onLongPressPoint
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            MapScreen(
                points = points,
                selectedPointId = selectedPointId,
                onEditPoint = onEditPointFromMap,
                isActive = isActive,
                zoomBehavior = zoomBehavior,
                markerScale = markerScale,
                downloadedOnly = downloadedOnly,
                mapTileSourceId = mapTileSourceId,
                onMapTileSourceChange = onMapTileSourceChange
            )
        }
    }
}

private fun vibrateOnce(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(50)
    }
}

@Composable
private fun PhotoPreviewDialog(
    photoPath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val photoFile = remember(photoPath) { resolvePointPhotoFile(context, photoPath) }
    val bitmap = remember(photoFile?.absolutePath) {
        photoFile?.takeIf { it.exists() && it.isFile && it.canRead() }?.let { file ->
            decodePreviewBitmap(file)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
        title = { Text(stringResource(R.string.action_view_photo)) },
        text = {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.action_view_photo),
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(stringResource(R.string.label_photo_not_added))
            }
        }
    )
}

private fun decodePreviewBitmap(file: java.io.File): android.graphics.Bitmap? {
    val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val orientation = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return applyExifOrientation(decoded, orientation)
}

enum class NetworkStatus { WIFI, CELLULAR, NONE }

@Composable
fun observeNetworkStatus(context: Context): androidx.compose.runtime.State<NetworkStatus> {
    val state = remember { mutableStateOf(getNetworkStatus(context)) }
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                state.value = getNetworkStatus(context)
            }

            override fun onLost(network: android.net.Network) {
                state.value = getNetworkStatus(context)
            }

            override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) {
                state.value = when {
                    networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.WIFI
                    networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkStatus.CELLULAR
                      networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) -> resolveVpnNetworkStatus(connectivityManager)
                      else -> NetworkStatus.NONE
                  }
              }
          }
          connectivityManager.registerDefaultNetworkCallback(callback)
          onDispose {
              runCatching { connectivityManager.unregisterNetworkCallback(callback) }
          }
      }
      return state
}

private fun resolveVpnNetworkStatus(cm: android.net.ConnectivityManager): NetworkStatus {
    val underlying = cm.allNetworks.find {
        val c = cm.getNetworkCapabilities(it)
        c != null && !c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) && c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    if (underlying != null) {
        val c = cm.getNetworkCapabilities(underlying)
        if (c?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true) return NetworkStatus.WIFI
        if (c?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true) return NetworkStatus.CELLULAR
    }
    return NetworkStatus.CELLULAR
}

private fun getNetworkStatus(context: Context): NetworkStatus {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return NetworkStatus.NONE
    val caps = connectivityManager.getNetworkCapabilities(network) ?: return NetworkStatus.NONE
    return when {
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.WIFI
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkStatus.CELLULAR
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) -> resolveVpnNetworkStatus(connectivityManager)
        else -> NetworkStatus.NONE
    }
}
