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

package com.lavacrafter.maptimelinetool.ui

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lavacrafter.maptimelinetool.LocationUtils
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.ui.DownloadTileSource
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDownloadScreen(
    onBack: () -> Unit,
    onAreaDownloaded: (DownloadedArea) -> Unit,
    tileSource: DownloadTileSource,
    useMultiThreadDownload: Boolean,
    downloadThreadCount: Int,
    downloadedOnly: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mapView: MapView? by remember { mutableStateOf(null) }
    var cacheManagerRef: CacheManager? by remember { mutableStateOf(null) }
    var minZoom by remember { mutableStateOf(8) }
    var maxZoom by remember { mutableStateOf(14) }
    var isDownloading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf(context.getString(R.string.map_download_status_idle)) }
    var showHelp by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val initialDownloadThreads = remember {
        Configuration.getInstance().tileDownloadThreads.toInt()
    }
    val scrollState = rememberScrollState()

    DisposableEffect(Unit) {
        onDispose {
            try {
                cacheManagerRef?.cancelAllJobs()
                cacheManagerRef = null
                Configuration.getInstance().setTileDownloadThreads(initialDownloadThreads.toShort())
            } catch (_: Exception) {
            }
            isDownloading = false
            statusText = context.getString(R.string.map_download_status_idle)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mapView?.onResume()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                mapView?.onPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentBbox = remember { mutableStateOf<BoundingBox?>(null) }
    
    BackHandler {
        if (isDownloading) {
            showCancelDialog = true
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_download_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clipToBounds()
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    factory = { viewContext ->
                        MapView(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setTileSource(tileSource.toOsmdroidSource(viewContext))
                            setMultiTouchControls(true)
                            disableBuiltInZoomControls()
                            setUseDataConnection(!downloadedOnly)
                            tileProvider.setUseDataConnection(!downloadedOnly)
                            controller.setZoom(12.0)
                            setOnTouchListener { v, _ ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                                false
                            }
                            mapView = this
                        }
                    },
                    update = { map ->
                        map.setTileSource(tileSource.toOsmdroidSource(context))
                        map.setUseDataConnection(!downloadedOnly)
                        map.tileProvider.setUseDataConnection(!downloadedOnly)
                        mapView = map
                    }
                )

                FloatingActionButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(44.dp),
                    onClick = {
                        val map = mapView ?: return@FloatingActionButton
                        scope.launch {
                            val loc = LocationUtils.getBestEffortLocation(context, 5_000L)
                            if (loc == null) {
                                Toast.makeText(context, context.getString(R.string.toast_location_failed), Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            map.controller.setZoom(15.0)
                            map.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.action_center)
                    )
                }
            }

            Text(text = stringResource(R.string.map_download_min_zoom, minZoom))
            Slider(
                value = minZoom.toFloat(),
                onValueChange = { value ->
                    val newValue = value.roundToInt().coerceAtMost(maxZoom)
                    minZoom = newValue
                },
                valueRange = 3f..19f,
                steps = 15
            )

            Text(text = stringResource(R.string.map_download_max_zoom, maxZoom))
            Slider(
                value = maxZoom.toFloat(),
                onValueChange = { value ->
                    val newValue = value.roundToInt().coerceAtLeast(minZoom)
                    maxZoom = newValue
                },
                valueRange = 3f..19f,
                steps = 15
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val map = mapView
                        if (map == null) {
                            Toast.makeText(context, context.getString(R.string.toast_map_not_ready), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val bbox = visibleBoundingBox(map)
                        if (bbox == null) {
                            Toast.makeText(context, context.getString(R.string.toast_map_not_ready), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        currentBbox.value = bbox
                        val cacheManager = CacheManager(map)
                        cacheManagerRef = cacheManager
                        val desiredThreads = if (useMultiThreadDownload) {
                            downloadThreadCount.coerceIn(2, 32)
                        } else {
                            initialDownloadThreads
                        }
                        Configuration.getInstance().setTileDownloadThreads(desiredThreads.toShort())
                        isDownloading = true
                        statusText = context.getString(R.string.map_download_status_running)
                        runCatching {
                            cacheManager.downloadAreaAsync(context, bbox, minZoom, maxZoom, object : CacheManager.CacheManagerCallback {
                                override fun downloadStarted() {
                                    mainHandler.post {
                                        if (cacheManagerRef !== cacheManager) return@post
                                        isDownloading = true
                                        statusText = context.getString(R.string.map_download_status_running)
                                    }
                                }

                                override fun setPossibleTilesInArea(total: Int) {
                                    mainHandler.post {
                                        if (cacheManagerRef !== cacheManager) return@post
                                        statusText = context.getString(R.string.map_download_status_running_detail, 0, minZoom)
                                    }
                                }

                                override fun onTaskComplete() {
                                    mainHandler.post {
                                        if (cacheManagerRef !== cacheManager) return@post
                                        isDownloading = false
                                        statusText = context.getString(R.string.map_download_status_done)
                                        Configuration.getInstance().setTileDownloadThreads(initialDownloadThreads.toShort())
                                        onAreaDownloaded(
                                            DownloadedArea(
                                                north = bbox.latNorth,
                                                south = bbox.latSouth,
                                                east = bbox.lonEast,
                                                west = bbox.lonWest,
                                                minZoom = minZoom,
                                                maxZoom = maxZoom
                                            )
                                        )
                                    }
                                }

                                override fun onTaskFailed(errors: Int) {
                                    mainHandler.post {
                                        if (cacheManagerRef !== cacheManager) return@post
                                        isDownloading = false
                                        statusText = context.getString(R.string.map_download_status_failed, errors)
                                        Configuration.getInstance().setTileDownloadThreads(initialDownloadThreads.toShort())
                                    }
                                }

                                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                                    mainHandler.post {
                                        if (cacheManagerRef !== cacheManager) return@post
                                        statusText = context.getString(
                                            R.string.map_download_status_running_detail,
                                            progress,
                                            currentZoomLevel
                                        )
                                    }
                                }
                            })
                        }.onFailure {
                            isDownloading = false
                            statusText = context.getString(R.string.map_download_status_failed, 1)
                            Configuration.getInstance().setTileDownloadThreads(initialDownloadThreads.toShort())
                            Toast.makeText(context, context.getString(R.string.map_download_status_failed, 1), Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isDownloading
                ) {
                    Text(text = stringResource(R.string.map_download_start))
                }
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = statusText)
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        showCancelDialog = true
                    }
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            } else {
                Text(text = statusText)
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { onBack() }) {
                Text(text = stringResource(R.string.action_back))
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.map_download_cancel_title)) },
            text = { Text(stringResource(R.string.map_download_cancel_message)) },
            confirmButton = {
                Button(onClick = {
                    showCancelDialog = false
                    try {
                        cacheManagerRef?.cancelAllJobs()
                        cacheManagerRef = null
                    } catch (e: Exception) {
                    }
                    val bbox = currentBbox.value
                    if (bbox != null) {
                        onAreaDownloaded(
                            DownloadedArea(
                                north = bbox.latNorth,
                                south = bbox.latSouth,
                                east = bbox.lonEast,
                                west = bbox.lonWest,
                                minZoom = minZoom,
                                maxZoom = maxZoom
                            )
                        )
                    }
                    isDownloading = false
                    statusText = context.getString(R.string.map_download_status_idle)
                    Configuration.getInstance().setTileDownloadThreads(initialDownloadThreads.toShort())
                }) {
                    Text(stringResource(R.string.map_download_cancel_keep))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showCancelDialog = false
                    try {
                        cacheManagerRef?.cancelAllJobs()
                        cacheManagerRef = null
                    } catch (e: Exception) {
                    }
                    isDownloading = false
                    statusText = context.getString(R.string.map_download_status_idle)
                    Configuration.getInstance().setTileDownloadThreads(initialDownloadThreads.toShort())
                }) {
                    Text(stringResource(R.string.map_download_cancel_abandon))
                }
            }
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.map_download_help_title)) },
            text = { Text(stringResource(R.string.map_download_help_body)) },
            confirmButton = {
                Button(onClick = { showHelp = false }) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }
}

@Suppress("DEPRECATION")
private fun MapView.disableBuiltInZoomControls() {
    setBuiltInZoomControls(false)
}

private fun visibleBoundingBox(map: MapView): BoundingBox? {
    if (map.width == 0 || map.height == 0) return map.boundingBox
    val paddingPx = (12 * map.resources.displayMetrics.density).toInt()
    val left = paddingPx
    val top = paddingPx
    val right = map.width - paddingPx
    val bottom = map.height - paddingPx
    val projection = map.projection ?: return map.boundingBox
    val northWest = projection.fromPixels(left, top) as? GeoPoint ?: return map.boundingBox
    val southEast = projection.fromPixels(right, bottom) as? GeoPoint ?: return map.boundingBox
    return BoundingBox.fromGeoPoints(listOf(northWest, southEast))
}
