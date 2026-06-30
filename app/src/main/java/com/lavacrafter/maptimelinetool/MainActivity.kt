/*
Copyright 2026 Muchen Jiang (lava-crafter)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.lavacrafter.maptimelinetool

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.lavacrafter.maptimelinetool.createPendingPointPhotoFile
import com.lavacrafter.maptimelinetool.deletePointPhotoFile
import com.lavacrafter.maptimelinetool.resolvePointPhotoFile
import com.lavacrafter.maptimelinetool.toStoredPhotoPath
import com.lavacrafter.maptimelinetool.data.toDomain
import com.lavacrafter.maptimelinetool.data.toUi
import com.lavacrafter.maptimelinetool.export.CsvExporter
import com.lavacrafter.maptimelinetool.export.CsvImporter
import com.lavacrafter.maptimelinetool.export.GeoJsonExporter
import com.lavacrafter.maptimelinetool.export.KmlExporter
import com.lavacrafter.maptimelinetool.export.KmzExporter
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
import com.lavacrafter.maptimelinetool.ui.MapCachePolicy
import com.lavacrafter.maptimelinetool.ui.TagDetailScreen
import com.lavacrafter.maptimelinetool.ui.TagListScreen
import com.lavacrafter.maptimelinetool.ui.TagSelectionDialog
import com.lavacrafter.maptimelinetool.ui.SettingsRoute
import com.lavacrafter.maptimelinetool.ui.SettingsScreen
import com.lavacrafter.maptimelinetool.ui.SettingsStore
import com.lavacrafter.maptimelinetool.ui.SettingsViewModel
import com.lavacrafter.maptimelinetool.ui.downloadTileSourceById
import com.lavacrafter.maptimelinetool.ui.ZoomButtonBehavior
import com.lavacrafter.maptimelinetool.ui.applyMapCachePolicy
import com.lavacrafter.maptimelinetool.ui.applyLanguagePreference
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSaveDecision
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSaveFlow
import com.lavacrafter.maptimelinetool.domain.usecase.LocationSaveQuality
import com.lavacrafter.maptimelinetool.notification.ACTION_QUICK_ADD
import com.lavacrafter.maptimelinetool.notification.performQuickAdd
import com.lavacrafter.maptimelinetool.notification.syncQuickAddNotification
import com.lavacrafter.maptimelinetool.ui.theme.MapTimelineToolTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : AppCompatActivity() {
    private val graph by lazy { applicationContext.appGraph() }
    private val quickAddRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
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
                var pendingLocationPermissionAction by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
                var previewPhotoPath by remember { mutableStateOf<String?>(null) }
                var remainingSeconds by remember { mutableStateOf(settingsState.timeoutSeconds) }
                var isCountdownPaused by remember { mutableStateOf(false) }
                var lastTypingTime by remember { mutableStateOf<Long?>(null) }
                val scaffoldState = rememberBottomSheetScaffoldState()
                val sheetState = scaffoldState.bottomSheetState
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
                var pendingManualSaveConfirmation by remember { mutableStateOf<PendingManualSaveConfirmation?>(null) }
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
                val exportGeoJsonLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/geo+json")
                ) { uri ->
                    val pending = pendingExportPayload
                    if (uri == null || pending == null) {
                        pendingExportPayload = null
                        return@rememberLauncherForActivityResult
                    }
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val pointTagNameMap = buildPointTagNameMap(viewModel, pending.points)
                                context.contentResolver.openOutputStream(uri)?.use { output ->
                                    GeoJsonExporter.writeGeoJson(
                                        points = pending.points,
                                        outputStream = output,
                                        includeSensors = true,
                                        pointTagNamesByPointId = pointTagNameMap,
                                        photoRelPathResolver = { null }
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
                val exportKmlLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")
                ) { uri ->
                    val pending = pendingExportPayload
                    if (uri == null || pending == null) {
                        pendingExportPayload = null
                        return@rememberLauncherForActivityResult
                    }
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val pointTagNameMap = buildPointTagNameMap(viewModel, pending.points)
                                context.contentResolver.openOutputStream(uri)?.use { output ->
                                    KmlExporter.writeKml(
                                        points = pending.points,
                                        outputStream = output,
                                        includeSensors = true,
                                        pointTagNamesByPointId = pointTagNameMap,
                                        photoRelPathResolver = { null }
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
                val exportKmzLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/vnd.google-earth.kmz")
                ) { uri ->
                    val pending = pendingExportPayload
                    if (uri == null || pending == null) {
                        pendingExportPayload = null
                        return@rememberLauncherForActivityResult
                    }
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val pointTagNameMap = buildPointTagNameMap(viewModel, pending.points)
                                context.contentResolver.openOutputStream(uri)?.use { output ->
                                    KmzExporter.export(
                                        points = pending.points,
                                        outputStream = output,
                                        resolvePhotoFile = { photoPath ->
                                            resolvePointPhotoFile(context, photoPath)
                                        },
                                        includeSensors = true,
                                        includePhotos = true,
                                        pointTagNamesByPointId = pointTagNameMap
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
                                        pointTagIdsByPointId = pending.pointTagIdsByPointId,
                                        settingsJsonProvider = { SettingsStore.exportBackupJson(context) },
                                        appVersion = packageManager.getPackageInfo(packageName, 0).versionName
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
                        val importedPhotoPaths = mutableListOf<String>()
                        runCatching {
                            val imported = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    ZipImporter.importZip(input) { entryName, photoInput ->
                                        runCatching {
                                            val extension = entryName.substringAfterLast('.', "").lowercase(Locale.US)
                                            val safeExt = extension.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "jpg"
                                            val importedPhotoFile = java.io.File(getPointPhotoDir(context), "point_photo_${UUID.randomUUID()}.$safeExt")
                                            importedPhotoFile.outputStream().buffered().use { output -> photoInput.copyTo(output) }
                                            toStoredPhotoPath(importedPhotoFile).also { importedPhotoPaths += it }
                                        }.getOrNull()
                                    }
                                } ?: ZipImporter.ImportStats(emptyList(), emptyList(), emptyList(), 0, 0, null)
                            }
                            viewModel.importZipData(imported)
                            imported.settingsJson?.let { json ->
                                val restored = SettingsStore.importBackupJson(context, json)
                                if (restored) {
                                    settingsViewModel.reloadFromStore()
                                    applyLanguagePreference(settingsViewModel.uiState.value.languagePreference)
                                }
                            }
                            Toast.makeText(context, context.getString(R.string.toast_import_success, imported.points.size), Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            scope.launch(Dispatchers.IO) {
                                importedPhotoPaths.forEach { deletePointPhotoFile(context, it) }
                            }
                            Toast.makeText(context, context.getString(R.string.toast_import_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        settingsViewModel.setQuickAddNotificationEnabled(true)
                    } else {
                        settingsViewModel.setQuickAddNotificationEnabled(false)
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_quick_add_notification_permission_denied),
                            Toast.LENGTH_SHORT
                        ).show()
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
                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    val pendingAction = pendingLocationPermissionAction
                    pendingLocationPermissionAction = null
                    if (granted && pendingAction != null) {
                        scope.launch { pendingAction() }
                    } else if (!granted) {
                        Toast.makeText(context, context.getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(settingsState.quickAddNotificationEnabled) {
                    context.syncQuickAddNotification(settingsState.quickAddNotificationEnabled)
                }

                LaunchedEffect(settingsState.cachePolicy, settingsState.satelliteCachePolicy, settingsState.mapTileSourceId, networkStatus) {
                    applyMapCachePolicy(context, settingsState.mapTileSourceId)
                }
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
                fun hasLocationPermission(): Boolean {
                    return ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                }

                fun requestLocationPermission() {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }

                fun resetPendingAddDialogState(clearPendingPhoto: Boolean = false) {
                    if (clearPendingPhoto) {
                        val pathToDelete = pendingAddPhotoPath
                        pendingAddPhotoPath = null
                        pendingAddPhotoUri = null
                        deletePhotoOnIo(pathToDelete)
                    } else {
                        pendingAddPhotoPath = null
                        pendingAddPhotoUri = null
                    }
                    pendingManualSaveConfirmation = null
                    showDialog = false
                    pendingTimestamp = null
                    newPointSelectedTagIds = emptySet()
                    showTagPickerForAdd = false
                    remainingSeconds = settingsState.timeoutSeconds
                    isCountdownPaused = false
                    lastTypingTime = null
                    newPointTitle = ""
                    newPointNote = ""
                }

                fun saveToastRes(quality: LocationSaveQuality, autoSaved: Boolean): Int {
                    return when (quality) {
                        LocationSaveQuality.PRECISE_FRESH -> {
                            if (autoSaved) R.string.toast_point_auto_saved else R.string.toast_point_added
                        }
                        LocationSaveQuality.FRESH_BUT_LOW_ACCURACY -> {
                            if (autoSaved) R.string.toast_point_auto_saved_approximate else R.string.toast_point_added_approximate
                        }
                        LocationSaveQuality.LAST_KNOWN_RECENT -> {
                            if (autoSaved) R.string.toast_point_auto_saved_last_known else R.string.toast_point_added_last_known
                        }
                        LocationSaveQuality.LAST_KNOWN_STALE -> {
                            if (autoSaved) R.string.toast_point_auto_saved_stale else R.string.toast_point_added_stale
                        }
                        LocationSaveQuality.UNAVAILABLE -> {
                            if (autoSaved) R.string.toast_auto_save_location_failed else R.string.toast_location_unavailable_save_failed
                        }
                    }
                }

                suspend fun finalizeAddDialogSave(
                    title: String,
                    note: String,
                    createdAt: Long,
                    selectedTags: Set<Long>,
                    photoPath: String?,
                    decision: LocationSaveDecision,
                    autoSaved: Boolean
                ) {
                    val location = decision.location ?: return
                    val persistedPhotoPath = preparePhotoPathForPersist(photoPath)
                    viewModel.addPointWithTags(
                        title = title.trim(),
                        note = note.trim(),
                        location = location,
                        timestamp = createdAt,
                        tagIds = selectedTags,
                        photoPath = persistedPhotoPath
                    )
                    vibrateOnce(context)
                    Toast.makeText(
                        context,
                        context.getString(saveToastRes(decision.quality, autoSaved)),
                        Toast.LENGTH_SHORT
                    ).show()
                    resetPendingAddDialogState()
                }

                fun runWithLocationPermission(onGranted: suspend () -> Unit) {
                    if (hasLocationPermission()) {
                        scope.launch { onGranted() }
                    } else {
                        pendingLocationPermissionAction = onGranted
                        requestLocationPermission()
                    }
                }

                fun requestCenterLocation(onResolved: (com.lavacrafter.maptimelinetool.domain.model.GeoPoint?) -> Unit) {
                    runWithLocationPermission {
                        onResolved(viewModel.getBestEffortLocation(5_000L))
                    }
                }

                LaunchedEffect(Unit) {
                    if (!hasLocationPermission()) {
                        requestLocationPermission()
                    }
                }

                LaunchedEffect(Unit) {
                    quickAddRequests.collectLatest {
                        runWithLocationPermission {
                            context.performQuickAdd()
                        }
                    }
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
                BackHandler(pendingManualSaveConfirmation != null) { pendingManualSaveConfirmation = null }
                BackHandler(showTagPickerForEdit) { showTagPickerForEdit = false }
                BackHandler(showTagPickerForAdd) { showTagPickerForAdd = false }
                BackHandler(editingPoint != null) {
                    clearUnsavedEditingPhoto()
                    resetEditingPointState()
                }
                BackHandler(editingTag != null) { editingTag = null }
                BackHandler(showDialog && pendingManualSaveConfirmation == null && !showTagPickerForAdd && !showTagPickerForEdit) {
                    viewModel.cancelAutoAdd()
                    resetPendingAddDialogState(clearPendingPhoto = true)
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
                        val decision = graph.locationSaveResolver.resolve(LocationSaveFlow.AUTO_SAVE, 5_000L)
                        if (!decision.canSave || decision.location == null) {
                            Toast.makeText(context, context.getString(R.string.toast_auto_save_location_failed), Toast.LENGTH_SHORT).show()
                            remainingSeconds = settingsState.timeoutSeconds
                            isCountdownPaused = true
                            lastTypingTime = null
                        } else {
                            finalizeAddDialogSave(
                                title = title,
                                note = note,
                                createdAt = createdAt,
                                selectedTags = newPointSelectedTagIds,
                                photoPath = addPhotoPath,
                                decision = decision,
                                autoSaved = true
                            )
                        }
                    }
                }
                val pointsState = viewModel.points.collectAsState().value
                LaunchedEffect(pointsState, editingPoint?.id) {
                    val editingPointId = editingPoint?.id ?: return@LaunchedEffect
                    val latestPoint = pointsState.firstOrNull { it.id == editingPointId } ?: return@LaunchedEffect
                    if (latestPoint != editingPoint) {
                        editingPoint = latestPoint
                    }
                }
                LaunchedEffect(pendingExportSelection, pointsState) {
                    val sel = pendingExportSelection ?: return@LaunchedEffect
                    val pointsToExport = when (sel.kind) {
                        is ExportKind.All -> pointsState
                        is ExportKind.ByTag -> {
                            val tagId = sel.kind.tagId
                            viewModel.observePointsForTag(tagId).first()
                        }
                        is ExportKind.ByTime -> {
                            pointsState.filter { it.timestamp in sel.kind.fromMs..sel.kind.toMs }
                        }
                        is ExportKind.Manual -> {
                            val ids = sel.kind.ids
                            pointsState.filter { ids.contains(it.id) }
                        }
                    }
                    val pointsToExportDomain = pointsToExport.map { it.toDomain() }
                    val pending = pendingExportPayload
                    if (pending != null) {
                        pendingExportSelection = null
                        return@LaunchedEffect
                    }
                    val baseName = buildExportBaseName()
                    val payload = buildStandardExportPayload(pointsToExportDomain, ExportFileKind.CSV)
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
                val exportGeoJson: () -> Unit = {
                    scope.launch {
                        val allPointsDomain = pointsState.map { it.toDomain() }
                        val pending = pendingExportPayload
                        if (pending != null) return@launch
                        val baseName = buildExportBaseName()
                        pendingExportPayload = buildStandardExportPayload(allPointsDomain, ExportFileKind.GEOJSON)
                        exportGeoJsonLauncher.launch("$baseName.geojson")
                    }
                }
                val exportKml: () -> Unit = {
                    scope.launch {
                        val allPointsDomain = pointsState.map { it.toDomain() }
                        val pending = pendingExportPayload
                        if (pending != null) return@launch
                        val baseName = buildExportBaseName()
                        pendingExportPayload = buildStandardExportPayload(allPointsDomain, ExportFileKind.KML)
                        exportKmlLauncher.launch("$baseName.kml")
                    }
                }
                val exportKmz: () -> Unit = {
                    scope.launch {
                        val allPointsDomain = pointsState.map { it.toDomain() }
                        val pending = pendingExportPayload
                        if (pending != null) return@launch
                        val baseName = buildExportBaseName()
                        pendingExportPayload = buildStandardExportPayload(allPointsDomain, ExportFileKind.KMZ)
                        exportKmzLauncher.launch("$baseName.kmz")
                    }
                }
                val exportZip: () -> Unit = {
                    zipIncludePoints = true
                    zipIncludeTags = true
                    zipIncludeSensors = true
                    zipIncludePhotos = true
                    showZipExportOptions = true
                }
                val shareBackupZip: () -> Unit = {
                    scope.launch {
                        runCatching {
                            val totalPointCount = pointsState.size
                            val shareUri = withContext(Dispatchers.IO) {
                                val allPointsDomain = pointsState.map { it.toDomain() }
                                val pointTagMap = mutableMapOf<Long, List<Long>>()
                                allPointsDomain.forEach { point ->
                                    val tagIds = viewModel.getTagIdsForPoint(point.id)
                                    if (tagIds.isNotEmpty()) {
                                        pointTagMap[point.id] = tagIds
                                    }
                                }
                                val usedTagIds = pointTagMap.values.flatten().toSet()
                                val zipTags = tagsState
                                    .filter { usedTagIds.contains(it.id) }
                                    .map { ZipExporter.TagRecord(it.id, it.name) }
                                val backupDir = java.io.File(context.filesDir, "shared_backups").apply { mkdirs() }
                                val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                                val backupZip = java.io.File(backupDir, "map_timeline_backup_${sdf.format(java.util.Date())}.zip")
                                backupZip.outputStream().buffered().use { output ->
                                    ZipExporter.export(
                                        points = allPointsDomain,
                                        outputStream = output,
                                        resolvePhotoFile = { photoPath ->
                                            resolvePointPhotoFile(context, photoPath)
                                        },
                                        options = ZipExporter.ExportOptions(
                                            includePoints = true,
                                            includeTags = true,
                                            includeSensors = true,
                                            includePhotos = true
                                        ),
                                        tags = zipTags,
                                        pointTagIdsByPointId = pointTagMap,
                                        settingsJsonProvider = { SettingsStore.exportBackupJson(context) },
                                        appVersion = packageManager.getPackageInfo(packageName, 0).versionName
                                    )
                                }
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupZip)
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    context.getString(R.string.action_share_backup_zip)
                                )
                            )
                            Toast.makeText(context, context.getString(R.string.toast_export_success, totalPointCount), Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        if (tab != 2) {
                            TopAppBar(
                                title = { Text(stringResource(R.string.app_name)) },
                                actions = { }
                            )
                        }
                    },
                    floatingActionButton = {
                        if (tab == 0) {
                            ExtendedFloatingActionButton(
                                modifier = Modifier.height(64.dp),
                                onClick = {
                                    val requestedAt = System.currentTimeMillis()
                                    runWithLocationPermission {
                                        pendingTimestamp = requestedAt
                                        newPointSelectedTagIds = settingsState.defaultTagIds
                                        showDialog = true
                                    }
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
                                downloadedOnly = run {
                                    val isSatellite = settingsState.mapTileSourceId == "eox_sentinel2_cloudless_2024"
                                    val policy = if (isSatellite) settingsState.satelliteCachePolicy else settingsState.cachePolicy
                                    policy == MapCachePolicy.DISABLED || (policy == MapCachePolicy.WIFI_ONLY && networkStatus != NetworkStatus.WIFI)
                                },
                                mapTileSourceId = settingsState.mapTileSourceId,
                                onMapTileSourceChange = settingsViewModel::setMapTileSourceId,
                                onResolveCenterLocation = ::requestCenterLocation,
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
                                    downloadThreadCount = settingsState.downloadThreadCount,
                                    downloadedOnly = run {
                                        val isSatellite = downloadTileSource.id.contains("satellite", true) || downloadTileSource.id.contains("eox", true)
                                        val policy = if (isSatellite) settingsState.satelliteCachePolicy else settingsState.cachePolicy
                                        policy == MapCachePolicy.DISABLED || (policy == MapCachePolicy.WIFI_ONLY && networkStatus != NetworkStatus.WIFI)
                                    },
                                    onResolveCenterLocation = ::requestCenterLocation
                                )
                            } else {
                                SettingsScreen(
                                    isDarkTheme = settingsState.isDarkTheme,
                                    onDarkThemeChange = settingsViewModel::setDarkTheme,
                                    followSystemTheme = settingsState.followSystemTheme,
                                    onFollowSystemThemeChange = settingsViewModel::setFollowSystemTheme,
                                    languagePreference = settingsState.languagePreference,
                                    onLanguagePreferenceChange = { preference ->
                                        if (preference != settingsState.languagePreference) {
                                            settingsViewModel.setLanguagePreference(preference)
                                            restartApp()
                                        }
                                    },
                                    quickAddNotificationEnabled = settingsState.quickAddNotificationEnabled,
                                    quickAddNotificationPermissionRequested = settingsState.quickAddNotificationPermissionRequested,
                                    onQuickAddNotificationEnabledChange = { enabled ->
                                        if (!enabled) {
                                            settingsViewModel.setQuickAddNotificationEnabled(false)
                                        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                            settingsViewModel.setQuickAddNotificationEnabled(true)
                                        } else if (
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            settingsViewModel.setQuickAddNotificationEnabled(true)
                                        } else if (!settingsState.quickAddNotificationPermissionRequested) {
                                            settingsViewModel.setQuickAddNotificationPermissionRequested(true)
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            settingsViewModel.setQuickAddNotificationEnabled(false)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_quick_add_notification_permission_required),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
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
                                    onExportGeoJson = exportGeoJson,
                                    onExportKml = exportKml,
                                    onExportKmz = exportKmz,
                                    onExportZip = exportZip,
                                    onShareBackupZip = shareBackupZip,
                                    onImportCsv = { importCsvLauncher.launch("text/*") },
                                    onImportZip = { importZipLauncher.launch("application/zip") },
                                    onClearCache = {
                                        if (settingsState.downloadedAreas.isNotEmpty()) {
                                            Toast.makeText(context, context.getString(R.string.toast_cache_skip_downloaded), Toast.LENGTH_SHORT).show()
                                        } else {
                                            val config = Configuration.getInstance()
                                            val cacheDirs = buildList {
                                                config.osmdroidTileCache?.let { add(it) }
                                                config.osmdroidBasePath?.let { basePath ->
                                                    val legacyTilesDir = java.io.File(basePath, "tiles")
                                                    add(legacyTilesDir)
                                                }
                                            }.distinctBy { it.absolutePath }

                                            val purgedByWriter = runCatching {
                                                SqlTileWriter().purgeCache()
                                            }.getOrDefault(false)

                                            val deleted = runCatching {
                                                cacheDirs.all { dir ->
                                                    if (!dir.exists()) true else dir.deleteRecursively()
                                                }
                                            }.getOrDefault(false)

                                            val recreated = if (deleted) {
                                                runCatching {
                                                    cacheDirs.all { dir -> dir.mkdirs() || dir.exists() }
                                                }.getOrDefault(false)
                                            } else {
                                                false
                                            }

                                            val messageRes = if (purgedByWriter && deleted && recreated) {
                                                R.string.toast_cache_cleared
                                            } else {
                                                R.string.toast_cache_clear_failed
                                            }
                                            Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
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
                            ZipExportOptionsDialog(
                                includePoints = zipIncludePoints,
                                includeTags = zipIncludeTags,
                                includeSensors = zipIncludeSensors,
                                includePhotos = zipIncludePhotos,
                                onIncludePointsChange = { checked ->
                                    zipIncludePoints = checked
                                    if (!checked) {
                                        zipIncludeTags = false
                                        zipIncludeSensors = false
                                    }
                                },
                                onIncludeTagsChange = { checked ->
                                    zipIncludeTags = checked
                                    if (checked) zipIncludePoints = true
                                },
                                onIncludeSensorsChange = { checked ->
                                    zipIncludeSensors = checked
                                    if (checked) zipIncludePoints = true
                                },
                                onIncludePhotosChange = { checked ->
                                    zipIncludePhotos = checked
                                },
                                onConfirm = {
                                    scope.launch {
                                        val pending = pendingExportPayload
                                        if (pending != null) return@launch
                                        val allPointsDomain = pointsState.map { it.toDomain() }
                                        val includePoints = zipIncludePoints
                                        val includePhotos = zipIncludePhotos
                                        val includeTags = includePoints && zipIncludeTags
                                        val includeSensors = includePoints && zipIncludeSensors
                                        pendingExportPayload = buildZipExportPayload(
                                            points = allPointsDomain,
                                            includePoints = includePoints,
                                            includeTags = includeTags,
                                            includeSensors = includeSensors,
                                            includePhotos = includePhotos,
                                            viewModel = viewModel,
                                            tags = tagsState
                                        )
                                        exportZipLauncher.launch("${buildExportBaseName()}.zip")
                                        showZipExportOptions = false
                                    }
                                },
                                onDismiss = { showZipExportOptions = false }
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
                            resetPendingAddDialogState(clearPendingPhoto = true)
                        },
                        onConfirm = { title, note, createdAt, selectedTags ->
                            scope.launch {
                                val addPhotoPath = pendingAddPhotoPath
                                val decision = graph.locationSaveResolver.resolve(LocationSaveFlow.MANUAL_ADD, 5_000L)
                                if (!decision.canSave || decision.location == null) {
                                    Toast.makeText(context, context.getString(R.string.toast_location_unavailable_save_failed), Toast.LENGTH_SHORT).show()
                                } else if (decision.requiresManualConfirmation) {
                                    pendingManualSaveConfirmation = PendingManualSaveConfirmation(
                                        title = title,
                                        note = note,
                                        createdAt = createdAt,
                                        selectedTags = selectedTags,
                                        photoPath = addPhotoPath,
                                        decision = decision
                                    )
                                } else {
                                    finalizeAddDialogSave(
                                        title = title,
                                        note = note,
                                        createdAt = createdAt,
                                        selectedTags = selectedTags,
                                        photoPath = addPhotoPath,
                                        decision = decision,
                                        autoSaved = false
                                    )
                                }
                            }
                        }
                    )
                }

                pendingManualSaveConfirmation?.let { confirmation ->
                    AlertDialog(
                        onDismissRequest = { pendingManualSaveConfirmation = null },
                        title = { Text(stringResource(R.string.dialog_location_confirmation_title)) },
                        text = {
                            Text(
                                stringResource(
                                    when (confirmation.decision.quality) {
                                        LocationSaveQuality.LAST_KNOWN_STALE -> R.string.dialog_location_confirmation_stale
                                        else -> R.string.dialog_location_confirmation_approximate
                                    }
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val request = confirmation
                                    pendingManualSaveConfirmation = null
                                    scope.launch {
                                        finalizeAddDialogSave(
                                            title = request.title,
                                            note = request.note,
                                            createdAt = request.createdAt,
                                            selectedTags = request.selectedTags,
                                            photoPath = request.photoPath,
                                            decision = request.decision,
                                            autoSaved = false
                                        )
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.action_save))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingManualSaveConfirmation = null }) {
                                Text(stringResource(R.string.action_cancel))
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


                LaunchedEffect(editingPoint?.id) {
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
        handleQuickAddIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickAddIntent(intent)
    }

    private fun handleQuickAddIntent(intent: Intent?) {
        if (intent?.action != ACTION_QUICK_ADD) {
            return
        }
        quickAddRequests.tryEmit(Unit)
        intent.action = null
        setIntent(intent)
    }

    private fun restartApp() {
        val restartIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            ?: Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        startActivity(restartIntent)
        finish()
    }
}

private fun vibrateOnce(context: Context) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(50)
    }
}
