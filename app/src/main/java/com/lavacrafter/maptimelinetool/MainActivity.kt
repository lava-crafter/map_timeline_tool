package com.lavacrafter.maptimelinetool

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lavacrafter.maptimelinetool.export.CsvExporter
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
import com.lavacrafter.maptimelinetool.ui.SettingsStore
import com.lavacrafter.maptimelinetool.ui.TagDetailScreen
import com.lavacrafter.maptimelinetool.ui.TagListScreen
import com.lavacrafter.maptimelinetool.ui.TagSelectionDialog
import com.lavacrafter.maptimelinetool.ui.SettingsRoute
import com.lavacrafter.maptimelinetool.ui.SettingsScreen
import com.lavacrafter.maptimelinetool.ui.downloadTileSourceById
import com.lavacrafter.maptimelinetool.ui.mapTileSources
import com.lavacrafter.maptimelinetool.ui.ZoomButtonBehavior
import com.lavacrafter.maptimelinetool.ui.applyMapCachePolicy
import com.lavacrafter.maptimelinetool.ui.applyLanguagePreference
import com.lavacrafter.maptimelinetool.ui.theme.MapTimelineToolTheme
import com.lavacrafter.maptimelinetool.notification.QuickAddService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.osmdroid.config.Configuration

private enum class PhotoCaptureTarget {
    NewPoint,
    EditPoint
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyLanguagePreference(SettingsStore.getLanguagePreference(this))

        setContent {
            var isDarkTheme by remember { mutableStateOf(false) }
            var followSystemTheme by remember { mutableStateOf(SettingsStore.getFollowSystemTheme(this)) }
            val isSystemDark = isSystemInDarkTheme()
            MapTimelineToolTheme(darkTheme = if (followSystemTheme) isSystemDark else isDarkTheme) {
                val context = LocalContext.current
                var timeoutSeconds by remember { mutableStateOf(SettingsStore.getTimeoutSeconds(context)) }
                var cachePolicy by remember { mutableStateOf(SettingsStore.getCachePolicy(context)) }
                var pinnedTagIds by remember { mutableStateOf(SettingsStore.getPinnedTagIds(context).toSet()) }
                var recentTagIds by remember { mutableStateOf(SettingsStore.getRecentTagIds(context)) }
                var zoomBehavior by remember { mutableStateOf(SettingsStore.getZoomButtonBehavior(context)) }
                var markerScale by remember { mutableStateOf(SettingsStore.getMarkerScale(context)) }
                var showTagPickerForAdd by remember { mutableStateOf(false) }
                var showTagPickerForEdit by remember { mutableStateOf(false) }
                var showMapDownload by remember { mutableStateOf(false) }
                var showExportFlow by remember { mutableStateOf(false) }
                var settingsRoute by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Main) }
                var defaultTagIds by remember { mutableStateOf(SettingsStore.getDefaultTagIds(context).toSet()) }
                var downloadedAreas by remember { mutableStateOf(SettingsStore.getDownloadedAreas(context)) }
                var downloadTileSourceId by remember { mutableStateOf(SettingsStore.getDownloadTileSourceId(context)) }
                var downloadMultiThreadEnabled by remember { mutableStateOf(SettingsStore.getDownloadMultiThreadEnabled(context)) }
                var downloadThreadCount by remember { mutableStateOf(SettingsStore.getDownloadThreadCount(context)) }
                var mapTileSourceId by remember { mutableStateOf(mapTileSources.first().id) }
                var newPointSelectedTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
                var showPinLimitDialog by remember { mutableStateOf(false) }
                var showExitDialog by remember { mutableStateOf(false) }
                var newPointTitle by remember { mutableStateOf("") }
                var newPointNote by remember { mutableStateOf("") }
                var newPointPhotoPath by remember { mutableStateOf<String?>(null) }
                var newPointPendingPhotoPath by remember { mutableStateOf<String?>(null) }
                var remainingSeconds by remember { mutableStateOf(timeoutSeconds) }
                var isCountdownPaused by remember { mutableStateOf(false) }
                var lastTypingTime by remember { mutableStateOf<Long?>(null) }
                val scaffoldState = rememberBottomSheetScaffoldState()
                val sheetState = scaffoldState.bottomSheetState

                val recordRecentTag: (Long) -> Unit = { tagId ->
                    recentTagIds = SettingsStore.addRecentTagId(context, tagId)
                }
                val toggleDefaultTag: (Long) -> Unit = { tagId ->
                    defaultTagIds = if (defaultTagIds.contains(tagId)) {
                        defaultTagIds - tagId
                    } else {
                        defaultTagIds + tagId
                    }
                    SettingsStore.setDefaultTagIds(context, defaultTagIds.toList())
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
                var pendingCsv by remember { mutableStateOf<String?>(null) }
                var pendingExportSelection by remember { mutableStateOf<ExportSelection?>(null) }
                var cameraTarget by remember { mutableStateOf<PhotoCaptureTarget?>(null) }
                var cameraCapturePath by remember { mutableStateOf<String?>(null) }
                val networkStatus by observeNetworkStatus(context)
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/csv")
                ) { uri ->
                    val csv = pendingCsv
                    if (uri == null || csv == null) return@rememberLauncherForActivityResult
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(csv.toByteArray(Charsets.UTF_8))
                        }
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
                    }
                    pendingCsv = null
                }
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        runCatching {
                            val csv = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            if (csv != null) {
                                val importedPoints = com.lavacrafter.maptimelinetool.export.CsvImporter.parseCsv(csv)
                                viewModel.importPoints(importedPoints)
                                Toast.makeText(context, context.getString(R.string.toast_import_success, importedPoints.size), Toast.LENGTH_SHORT).show()
                            }
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

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                    startService(Intent(context, QuickAddService::class.java))
                }

                LaunchedEffect(cachePolicy) {
                    applyMapCachePolicy(context, cachePolicy)
                }


                var tab by remember { mutableStateOf(0) }
                var showAbout by remember { mutableStateOf(false) }
                var showDialog by remember { mutableStateOf(false) }
                var pendingTimestamp by remember { mutableStateOf<Long?>(null) }
                var selectedPointId by remember { mutableStateOf<Long?>(null) }
                var editingPoint by remember { mutableStateOf<com.lavacrafter.maptimelinetool.data.PointEntity?>(null) }
                var editingPointTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
                var editingPointPhotoPath by remember { mutableStateOf<String?>(null) }
                var editingPointPendingPhotoPath by remember { mutableStateOf<String?>(null) }
                var editingTag by remember { mutableStateOf<com.lavacrafter.maptimelinetool.data.TagEntity?>(null) }
                var selectedTag by remember { mutableStateOf<com.lavacrafter.maptimelinetool.data.TagEntity?>(null) }
                val takePictureLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.TakePicture()
                ) { success ->
                    val capturedPath = cameraCapturePath
                    when {
                        success && capturedPath != null && cameraTarget == PhotoCaptureTarget.NewPoint -> {
                            deletePointPhoto(newPointPendingPhotoPath)
                            newPointPendingPhotoPath = capturedPath
                            isCountdownPaused = true
                        }

                        success && capturedPath != null && cameraTarget == PhotoCaptureTarget.EditPoint -> {
                            deletePointPhoto(editingPointPendingPhotoPath)
                            editingPointPendingPhotoPath = capturedPath
                        }

                        else -> deletePointPhoto(capturedPath)
                    }
                    if (!success && showDialog && !showTagPickerForAdd) {
                        isCountdownPaused = false
                    }
                    cameraTarget = null
                    cameraCapturePath = null
                }
                val launchCameraFor: (PhotoCaptureTarget) -> Unit = { target ->
                    runCatching {
                        val photoFile = createPointPhotoFile(context)
                        cameraTarget = target
                        cameraCapturePath = photoFile.absolutePath
                        if (target == PhotoCaptureTarget.NewPoint) {
                            isCountdownPaused = true
                        }
                        takePictureLauncher.launch(getPointPhotoUri(context, photoFile))
                    }.onFailure {
                        cameraTarget = null
                        cameraCapturePath = null
                        Toast.makeText(context, context.getString(R.string.toast_camera_failed), Toast.LENGTH_SHORT).show()
                        if (showDialog && !showTagPickerForAdd) {
                            isCountdownPaused = false
                        }
                    }
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
                    if (editingPointPhotoPath != editingPoint?.photoPath) {
                        deletePointPhoto(editingPointPhotoPath)
                    }
                    deletePointPhoto(editingPointPendingPhotoPath)
                    editingPoint = null
                    editingPointTagIds = emptySet()
                    editingPointPhotoPath = null
                    editingPointPendingPhotoPath = null
                }
                BackHandler(editingTag != null) { editingTag = null }
                BackHandler(showDialog && !showTagPickerForAdd && !showTagPickerForEdit) {
                    viewModel.cancelAutoAdd()
                    deletePointPhoto(newPointPhotoPath)
                    deletePointPhoto(newPointPendingPhotoPath)
                    showDialog = false
                    pendingTimestamp = null
                    newPointSelectedTagIds = emptySet()
                    showTagPickerForAdd = false
                    newPointTitle = ""
                    newPointNote = ""
                    newPointPhotoPath = null
                    newPointPendingPhotoPath = null
                    remainingSeconds = timeoutSeconds
                    isCountdownPaused = false
                    lastTypingTime = null
                }
                BackHandler(showAbout) { showAbout = false }
                BackHandler(showMapDownload) { showMapDownload = false }
                BackHandler(tab == 2 && settingsRoute != SettingsRoute.Main) { settingsRoute = SettingsRoute.Main }
                BackHandler(selectedTag != null) { selectedTag = null }
                BackHandler(!showDialog && !showTagPickerForAdd && !showTagPickerForEdit && editingPoint == null && editingTag == null && !showAbout && selectedTag == null && sheetState.currentValue != SheetValue.Expanded) {
                    showExitDialog = true
                }
                BackHandler(showExitDialog) {
                    finishAffinity()
                }
                LaunchedEffect(showDialog, pendingTimestamp, timeoutSeconds) {
                    if (showDialog && pendingTimestamp != null) {
                        remainingSeconds = timeoutSeconds
                        isCountdownPaused = false
                        lastTypingTime = null
                        newPointTitle = ""
                        newPointNote = ""
                        newPointPhotoPath = null
                        newPointPendingPhotoPath = null
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
                    scope.launch {
                        val loc = viewModel.getLastKnownLocation() ?: viewModel.getFreshLocation(2000L)
                        if (loc == null) {
                            Toast.makeText(context, context.getString(R.string.toast_location_failed), Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addPointWithTags(title, note, loc, createdAt, newPointSelectedTagIds, newPointPhotoPath)
                            vibrateOnce(context)
                            Toast.makeText(context, context.getString(R.string.toast_point_added), Toast.LENGTH_SHORT).show()
                        }
                        showDialog = false
                        pendingTimestamp = null
                        newPointSelectedTagIds = emptySet()
                        showTagPickerForAdd = false
                        newPointTitle = ""
                        newPointNote = ""
                        newPointPhotoPath = null
                        newPointPendingPhotoPath = null
                        remainingSeconds = timeoutSeconds
                        isCountdownPaused = false
                        lastTypingTime = null
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
                    val csv = withContext(Dispatchers.Default) {
                        CsvExporter.buildCsv(pointsToExport)
                    }
                    val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    val fileName = "map_timeline_${sdf.format(java.util.Date())}.csv"
                    pendingCsv = csv
                    exportLauncher.launch(fileName)
                    pendingExportSelection = null
                    showExportFlow = false
                    tab = 2
                    settingsRoute = SettingsRoute.Main
                }
                val tagsState = viewModel.tags.collectAsState().value
                val quickTags = remember(tagsState, pinnedTagIds, recentTagIds) {
                    val pinned = tagsState.filter { pinnedTagIds.contains(it.id) }
                    val recent = recentTagIds.mapNotNull { id -> tagsState.firstOrNull { it.id == id } }
                    (pinned + recent).distinctBy { it.id }.take(3)
                }
                val downloadTileSource = remember(downloadTileSourceId) { downloadTileSourceById(downloadTileSourceId) }
                val exportCsv: () -> Unit = {
                    // Open export flow UI
                    showExportFlow = true
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
                                    newPointSelectedTagIds = defaultTagIds
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
                                zoomBehavior = zoomBehavior,
                                markerScale = markerScale,
                                downloadedOnly = downloadedAreas.isNotEmpty(),
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
                                        pinnedTagIds = pinnedTagIds,
                                        onAddTag = { name -> viewModel.addTag(name) },
                                        onOpenTag = { tag -> selectedTag = tag },
                                        onEditTag = { tag -> editingTag = tag },
                                        onTogglePin = { tag, shouldPin ->
                                            if (shouldPin && pinnedTagIds.size >= 3) {
                                                showPinLimitDialog = true
                                            } else {
                                                pinnedTagIds = if (shouldPin) pinnedTagIds + tag.id else pinnedTagIds - tag.id
                                                SettingsStore.setPinnedTagIds(context, pinnedTagIds.toList())
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
                                        downloadedAreas = SettingsStore.addDownloadedArea(context, area)
                                    },
                                    tileSource = downloadTileSource,
                                    useMultiThreadDownload = downloadMultiThreadEnabled,
                                    downloadThreadCount = downloadThreadCount
                                )
                            } else {
                                SettingsScreen(
                                    isDarkTheme = isDarkTheme,
                                    onDarkThemeChange = { isDarkTheme = it },
                                    followSystemTheme = followSystemTheme,
                                    onFollowSystemThemeChange = {
                                        followSystemTheme = it
                                        SettingsStore.setFollowSystemTheme(context, it)
                                    },
                                    timeoutSeconds = timeoutSeconds,
                                    onTimeoutSecondsChange = {
                                        timeoutSeconds = it
                                        SettingsStore.setTimeoutSeconds(context, it)
                                    },
                                    cachePolicy = cachePolicy,
                                    onCachePolicyChange = {
                                        cachePolicy = it
                                        SettingsStore.setCachePolicy(context, it)
                                    },
                                    networkStatus = networkStatus,
                                    selectedDownloadTileSourceId = downloadTileSourceId,
                                    onDownloadTileSourceChange = {
                                        downloadTileSourceId = it
                                        SettingsStore.setDownloadTileSourceId(context, it)
                                    },
                                    downloadMultiThreadEnabled = downloadMultiThreadEnabled,
                                    onDownloadMultiThreadEnabledChange = {
                                        downloadMultiThreadEnabled = it
                                        SettingsStore.setDownloadMultiThreadEnabled(context, it)
                                    },
                                    downloadThreadCount = downloadThreadCount,
                                    onDownloadThreadCountChange = {
                                        downloadThreadCount = it
                                        SettingsStore.setDownloadThreadCount(context, it)
                                    },
                                    mapTileSourceId = mapTileSourceId,
                                    onMapTileSourceChange = { mapTileSourceId = it },
                                    zoomBehavior = zoomBehavior,
                                    onZoomBehaviorChange = {
                                        zoomBehavior = it
                                        SettingsStore.setZoomButtonBehavior(context, it)
                                    },
                                    markerScale = markerScale,
                                    onMarkerScaleChange = {
                                        markerScale = it
                                        SettingsStore.setMarkerScale(context, it)
                                    },
                                    onOpenMapDownload = { showMapDownload = true },
                                    downloadedAreas = downloadedAreas,
                                    onRemoveDownloadedArea = { area ->
                                        downloadedAreas = SettingsStore.removeDownloadedArea(context, area)
                                    },
                                    onDeduplicateDownloadedAreas = {
                                        downloadedAreas = SettingsStore.dedupeDownloadedAreas(context)
                                    },
                                    onExportCsv = exportCsv,
                                    onImportCsv = { importLauncher.launch("text/*") },
                                    onClearCache = {
                                        if (downloadedAreas.isNotEmpty()) {
                                            Toast.makeText(context, context.getString(R.string.toast_cache_skip_downloaded), Toast.LENGTH_SHORT).show()
                                        } else {
                                            val cacheDir = Configuration.getInstance().osmdroidTileCache
                                            runCatching { cacheDir?.deleteRecursively() }
                                            Toast.makeText(context, context.getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onOpenAbout = { showAbout = true },
                                    defaultTags = tagsState,
                                    selectedDefaultTagIds = defaultTagIds,
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
                    }
                }

                LaunchedEffect(showTagPickerForAdd || showTagPickerForEdit) {
                    if (showTagPickerForAdd || showTagPickerForEdit) {
                        isCountdownPaused = true
                    }
                }

                if (showDialog && pendingTimestamp != null) {
                    AddPointDialog(
                        createdAt = pendingTimestamp!!,
                        quickTags = quickTags,
                        tags = tagsState,
                        selectedTagIds = newPointSelectedTagIds,
                        title = newPointTitle,
                        note = newPointNote,
                        photoPath = newPointPhotoPath,
                        pendingPhotoPath = newPointPendingPhotoPath,
                        remainingSeconds = remainingSeconds,
                        isCountdownPaused = isCountdownPaused,
                        onTitleChange = { newPointTitle = it },
                        onNoteChange = { newPointNote = it },
                        onUserTyping = onUserTyping,
                        onToggleTag = toggleNewPointTag,
                        onOpenTagPicker = { showTagPickerForAdd = true },
                        onCapturePhoto = { launchCameraFor(PhotoCaptureTarget.NewPoint) },
                        onRemovePhoto = {
                            deletePointPhoto(newPointPhotoPath)
                            newPointPhotoPath = null
                        },
                        onConfirmPendingPhoto = {
                            deletePointPhoto(newPointPhotoPath)
                            newPointPhotoPath = newPointPendingPhotoPath
                            newPointPendingPhotoPath = null
                            isCountdownPaused = false
                        },
                        onRetakePendingPhoto = {
                            deletePointPhoto(newPointPendingPhotoPath)
                            newPointPendingPhotoPath = null
                            launchCameraFor(PhotoCaptureTarget.NewPoint)
                        },
                        onCancelPendingPhoto = {
                            deletePointPhoto(newPointPendingPhotoPath)
                            newPointPendingPhotoPath = null
                            isCountdownPaused = false
                        },
                        onDismiss = {
                            deletePointPhoto(newPointPhotoPath)
                            deletePointPhoto(newPointPendingPhotoPath)
                            showDialog = false
                            pendingTimestamp = null
                            newPointSelectedTagIds = emptySet()
                            showTagPickerForAdd = false
                            remainingSeconds = timeoutSeconds
                            isCountdownPaused = false
                            lastTypingTime = null
                            newPointTitle = ""
                            newPointNote = ""
                            newPointPhotoPath = null
                            newPointPendingPhotoPath = null
                        },
                        onConfirm = { title, note, createdAt, selectedTags ->
                            scope.launch {
                                val loc = viewModel.getFreshLocation(5000L)
                                if (loc == null) {
                                    Toast.makeText(context, context.getString(R.string.toast_location_failed), Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addPointWithTags(title, note, loc, createdAt, selectedTags, newPointPhotoPath)
                                    vibrateOnce(context)
                                    Toast.makeText(context, context.getString(R.string.toast_point_added), Toast.LENGTH_SHORT).show()
                                }
                                showDialog = false
                                pendingTimestamp = null
                                newPointSelectedTagIds = emptySet()
                                showTagPickerForAdd = false
                                remainingSeconds = timeoutSeconds
                                isCountdownPaused = false
                                lastTypingTime = null
                                newPointTitle = ""
                                newPointNote = ""
                                newPointPhotoPath = null
                                newPointPendingPhotoPath = null
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
                        editingPointPendingPhotoPath = null
                    }
                }

                if (editingPoint != null) {
                    val point = editingPoint!!
                    EditPointDialog(
                        point = point,
                        quickTags = quickTags,
                        tags = tagsState,
                        selectedTagIds = editingPointTagIds,
                        photoPath = editingPointPhotoPath,
                        pendingPhotoPath = editingPointPendingPhotoPath,
                        onToggleTag = toggleEditingPointTag,
                        onOpenTagPicker = { showTagPickerForEdit = true },
                        onCapturePhoto = { launchCameraFor(PhotoCaptureTarget.EditPoint) },
                        onRemovePhoto = {
                            if (editingPointPhotoPath != point.photoPath) {
                                deletePointPhoto(editingPointPhotoPath)
                            }
                            editingPointPhotoPath = null
                        },
                        onConfirmPendingPhoto = {
                            if (editingPointPhotoPath != point.photoPath) {
                                deletePointPhoto(editingPointPhotoPath)
                            }
                            editingPointPhotoPath = editingPointPendingPhotoPath
                            editingPointPendingPhotoPath = null
                        },
                        onRetakePendingPhoto = {
                            deletePointPhoto(editingPointPendingPhotoPath)
                            editingPointPendingPhotoPath = null
                            launchCameraFor(PhotoCaptureTarget.EditPoint)
                        },
                        onCancelPendingPhoto = {
                            deletePointPhoto(editingPointPendingPhotoPath)
                            editingPointPendingPhotoPath = null
                        },
                        onSave = { title, note ->
                            viewModel.updatePoint(point, title, note, editingPointPhotoPath)
                            deletePointPhoto(editingPointPendingPhotoPath)
                            editingPoint = null
                            editingPointTagIds = emptySet()
                            editingPointPhotoPath = null
                            editingPointPendingPhotoPath = null
                        },
                        onDelete = {
                            if (editingPointPhotoPath != point.photoPath) {
                                deletePointPhoto(editingPointPhotoPath)
                            }
                            deletePointPhoto(editingPointPendingPhotoPath)
                            viewModel.deletePoint(point)
                            editingPoint = null
                            editingPointTagIds = emptySet()
                            editingPointPhotoPath = null
                            editingPointPendingPhotoPath = null
                        },
                        onDismiss = {
                            if (editingPointPhotoPath != point.photoPath) {
                                deletePointPhoto(editingPointPhotoPath)
                            }
                            deletePointPhoto(editingPointPendingPhotoPath)
                            editingPoint = null
                            editingPointTagIds = emptySet()
                            editingPointPhotoPath = null
                            editingPointPendingPhotoPath = null
                        }
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
                downloadedOnly = downloadedOnly
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

private fun getNetworkStatus(context: Context): NetworkStatus {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return NetworkStatus.NONE
    val caps = connectivityManager.getNetworkCapabilities(network) ?: return NetworkStatus.NONE
    return when {
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.WIFI
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkStatus.CELLULAR
        else -> NetworkStatus.NONE
    }
}
