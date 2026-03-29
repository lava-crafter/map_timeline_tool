package com.lavacrafter.maptimelinetool.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.location.Location
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lavacrafter.maptimelinetool.R
import com.lavacrafter.maptimelinetool.data.PointEntity
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.INetworkAvailablityCheck
import org.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.infowindow.InfoWindow
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    points: List<PointEntity>,
    selectedPointId: Long?,
    onEditPoint: (PointEntity) -> Unit,
    isActive: Boolean,
    zoomBehavior: ZoomButtonBehavior,
    markerScale: Float,
    downloadedOnly: Boolean,
    mapTileSourceId: String,
    onMapTileSourceChange: (String) -> Unit
) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView: MapView? by remember { mutableStateOf(null) }
    val locationProvider = remember { GpsMyLocationProvider(context) }
    val orientationProvider = remember { InternalCompassOrientationProvider(context) }
    val headingOverlay = remember { HeadingLocationOverlay(context) }
    val todayOrderById = remember(points) { buildTodayOrder(points) }
    var interactionToken by remember { mutableStateOf(0) }
    var showZoomTransient by remember { mutableStateOf(false) }
    val onMapInteraction = rememberUpdatedState {
        interactionToken += 1
        showZoomTransient = true
    }

    LaunchedEffect(isActive, interactionToken) {
        if (!isActive) {
            showZoomTransient = false
            return@LaunchedEffect
        }
        if (interactionToken == 0) return@LaunchedEffect
        val token = interactionToken
        delay(3000L)
        if (interactionToken == token) {
            showZoomTransient = false
        }
    }

    LaunchedEffect(selectedPointId, mapView, points) {
        val map = mapView ?: return@LaunchedEffect
        val target = selectedPointId?.let { id -> points.find { it.id == id } } ?: return@LaunchedEffect
        map.controller.setZoom(16.0)
        map.controller.animateTo(GeoPoint(target.latitude, target.longitude))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (isActive) {
                        locationProvider.startLocationProvider(headingOverlay)
                        orientationProvider.startOrientationProvider(headingOverlay)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    locationProvider.stopLocationProvider()
                    orientationProvider.stopOrientationProvider()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationProvider.stopLocationProvider()
            orientationProvider.stopOrientationProvider()
            locationProvider.destroy()
            orientationProvider.destroy()
        }
    }

    LaunchedEffect(isActive, lifecycleOwner) {
        if (isActive && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            locationProvider.startLocationProvider(headingOverlay)
            orientationProvider.startOrientationProvider(headingOverlay)
        } else if (!isActive) {
            locationProvider.stopLocationProvider()
            orientationProvider.stopOrientationProvider()
        }
    }

    LaunchedEffect(isActive, mapView) {
        if (!isActive) return@LaunchedEffect
        while (isActive) {
            mapView?.postInvalidate()
            delay(1000L)
        }
    }

    var lastOverlaySignature by remember { mutableStateOf<List<Long>>(emptyList()) }
    var overlaysReady by remember { mutableStateOf(false) }
    var hasAutoCentered by remember { mutableStateOf(false) }
    var policyNetworkCheck by remember { mutableStateOf<PolicyAwareNetworkCheck?>(null) }
    var policyFilesystemCache by remember { mutableStateOf<PolicyAwareFilesystemCache?>(null) }
    var previousDownloadedOnly by remember { mutableStateOf(downloadedOnly) }

    Box {
        AndroidView(
            factory = { viewContext ->
                val source = mapTileSourceById(mapTileSourceId).toOsmdroidSource(viewContext)
                val networkCheck = PolicyAwareNetworkCheck(viewContext).apply {
                    allowNetwork = !downloadedOnly
                }
                val filesystemCache = PolicyAwareFilesystemCache(SqlTileWriter()).apply {
                    allowWrites = !downloadedOnly
                }
                val provider = MapTileProviderBasic(
                    SimpleRegisterReceiver(viewContext),
                    networkCheck,
                    source,
                    viewContext,
                    filesystemCache
                )
                MapView(viewContext, provider).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setTileSource(source)
                    // setUseDataConnection must be applied after setTileSource, otherwise the
                    // provider created by setTileSource may reset network behavior.
                    setUseDataConnection(!downloadedOnly)
                    tileProvider.setUseDataConnection(!downloadedOnly)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(16.0)
                    if (points.isNotEmpty()) {
                        val last = points.first()
                        controller.setCenter(GeoPoint(last.latitude, last.longitude))
                    }
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            onMapInteraction.value.invoke()
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            onMapInteraction.value.invoke()
                            return false
                        }
                    })
                    onResume()
                    policyNetworkCheck = networkCheck
                    policyFilesystemCache = filesystemCache
                    mapView = this
                }
            },
            onRelease = { map ->
                map.onPause()
                map.onDetach()
                policyNetworkCheck = null
                policyFilesystemCache = null
            },
            update = { map ->
                val targetSource = mapTileSourceById(mapTileSourceId).toOsmdroidSource(context)
                if (map.tileProvider.tileSource.name() != targetSource.name()) {
                    map.setTileSource(targetSource)
                }
                policyNetworkCheck?.allowNetwork = !downloadedOnly
                policyFilesystemCache?.allowWrites = !downloadedOnly
                // Important: setUseDataConnection must be called after setTileSource to ensure the internal provider respects it.
                map.setUseDataConnection(!downloadedOnly)
                map.tileProvider.setUseDataConnection(!downloadedOnly)
                if (!previousDownloadedOnly && downloadedOnly) {
                    // Drop in-memory tiles captured while online so the no-network behavior is visible immediately.
                    map.tileProvider.clearTileCache()
                }
                previousDownloadedOnly = downloadedOnly

                if (!hasAutoCentered) {
                    if (points.isNotEmpty()) {
                        val last = points.first()
                        map.controller.setZoom(16.0)
                        map.controller.setCenter(GeoPoint(last.latitude, last.longitude))
                        hasAutoCentered = true
                    } else if (headingOverlay.location != null) {
                        val loc = headingOverlay.location!!
                        map.controller.setZoom(16.0)
                        map.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                        hasAutoCentered = true
                    }
                }

                val signature = points.map { it.id }
                if (overlaysReady && signature == lastOverlaySignature) {
                    map.invalidate()
                    return@AndroidView
                }
                map.overlays.clear()
                points.forEach { p ->
                    val order = todayOrderById[p.id]
                    val color = if (order != null) spectrumColor(order) else DEFAULT_MARKER_COLOR
                    val lookDirection = p.toLookDirection()
                    val timeDescription = sdf.format(Date(p.timestamp))
                    val lookDescription = lookDirection?.let { direction ->
                        context.getString(
                            R.string.label_sensor_view_direction,
                            direction.azimuthDegrees,
                            direction.pitchDegrees
                        )
                    }
                    val marker = Marker(map).apply {
                        position = GeoPoint(p.latitude, p.longitude)
                        title = p.title
                        snippet = p.note
                        subDescription = if (lookDescription != null) {
                            "$timeDescription\n$lookDescription"
                        } else {
                            timeDescription
                        }
                        icon = createCounterIcon(context, order?.toString().orEmpty(), color, markerScale)
                        infoWindow = object : org.osmdroid.views.overlay.infowindow.MarkerInfoWindow(org.osmdroid.library.R.layout.bonuspack_bubble, map) {
                            override fun onOpen(item: Any?) {
                                super.onOpen(item)
                                mView.setBackgroundColor(INFO_WINDOW_BACKGROUND_COLOR)
                                mView.setOnClickListener {
                                    onEditPoint(p)
                                }
                            }
                        }
                        setOnMarkerClickListener { clicked, mapView ->
                            InfoWindow.closeAllInfoWindowsOn(mapView)
                            clicked.showInfoWindow()
                            true
                        }
                    }
                    map.overlays.add(marker)
                    if (p.id == selectedPointId) {
                        marker.showInfoWindow()
                    }
                }
                map.overlays.add(
                    MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            InfoWindow.closeAllInfoWindowsOn(map)
                            return false
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean {
                            val nearest = findNearestPoint(map, points, p, 48f)
                            if (nearest != null) {
                                onEditPoint(nearest)
                                return true
                            }
                            return false
                        }
                    })
                )
                map.overlays.add(headingOverlay)
                map.invalidate()
                lastOverlaySignature = signature
                overlaysReady = true
            }
        )

        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            onClick = {
                val map = mapView ?: return@FloatingActionButton
                val cached = readCachedLocation(context)
                if (cached == null) {
                    Toast.makeText(context, context.getString(R.string.toast_location_loading), Toast.LENGTH_SHORT).show()
                }
                val targetLocation = cached?.let { GeoPoint(it.latitude, it.longitude) }
                val targetPoint = selectedPointId?.let { id -> points.find { it.id == id } } ?: points.firstOrNull()
                val target = targetLocation ?: targetPoint?.let { GeoPoint(it.latitude, it.longitude) }
                target?.let {
                    map.controller.setZoom(16.0)
                    map.controller.setCenter(it)
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = stringResource(R.string.action_center)
            )
        }

        val shouldShowZoom = when (zoomBehavior) {
            ZoomButtonBehavior.HIDE -> false
            ZoomButtonBehavior.WHEN_ACTIVE -> isActive && showZoomTransient
            ZoomButtonBehavior.ALWAYS -> true
        }

        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(44.dp),
            onClick = {
                if (mapTileSources.isEmpty()) return@FloatingActionButton
                val currentIndex = mapTileSources.indexOfFirst { it.id == mapTileSourceId }
                    .takeIf { it >= 0 }
                    ?: 0
                val nextIndex = (currentIndex + 1) % mapTileSources.size
                onMapTileSourceChange(mapTileSources[nextIndex].id)
            }
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = stringResource(R.string.action_cycle_map_layer)
            )
        }

        if (shouldShowZoom) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 68.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    modifier = Modifier.size(44.dp),
                    onClick = { mapView?.controller?.zoomIn() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In"
                    )
                }
                FloatingActionButton(
                    modifier = Modifier.size(44.dp),
                    onClick = { mapView?.controller?.zoomOut() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out"
                    )
                }
            }
        }
    }

}

private fun createCounterIcon(
    context: android.content.Context,
    text: String,
    color: Int,
    scale: Float
): android.graphics.drawable.BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val normalized = scale.coerceIn(0.3f, 1.75f)
    val size = (24 * density * normalized)
        .toInt()
        .coerceIn((10 * density).toInt(), (70 * density).toInt())
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.WHITE
        textSize = 32f * normalized
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, circlePaint)
    if (text.isNotBlank()) {
        val y = (size / 2f) - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, size / 2f, y, textPaint)
    }
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

private class PolicyAwareNetworkCheck(context: android.content.Context) : INetworkAvailablityCheck {
    private val delegate = NetworkAvailabliltyCheck(context)

    @Volatile
    var allowNetwork: Boolean = true

    override fun getNetworkAvailable(): Boolean {
        return allowNetwork && delegate.getNetworkAvailable()
    }

    override fun getWiFiNetworkAvailable(): Boolean {
        return allowNetwork && delegate.getWiFiNetworkAvailable()
    }

    override fun getCellularDataNetworkAvailable(): Boolean {
        return allowNetwork && delegate.getCellularDataNetworkAvailable()
    }

    @Deprecated("Deprecated by osmdroid INetworkAvailablityCheck; kept for interface compatibility.")
    @Suppress("DEPRECATION")
    override fun getRouteToPathExists(hostAddress: Int): Boolean {
        return allowNetwork && delegate.getRouteToPathExists(hostAddress)
    }
}

private class PolicyAwareFilesystemCache(
    private val delegate: IFilesystemCache
) : IFilesystemCache {
    @Volatile
    var allowWrites: Boolean = true

    override fun saveFile(
        pTileSource: ITileSource,
        pMapTileIndex: Long,
        pStream: InputStream,
        pExpirationTime: Long?
    ): Boolean {
        if (!allowWrites) {
            return false
        }
        return delegate.saveFile(pTileSource, pMapTileIndex, pStream, pExpirationTime)
    }

    override fun exists(pTileSource: ITileSource, pMapTileIndex: Long): Boolean {
        return delegate.exists(pTileSource, pMapTileIndex)
    }

    override fun onDetach() {
        delegate.onDetach()
    }

    override fun remove(pTileSource: ITileSource, pMapTileIndex: Long): Boolean {
        return delegate.remove(pTileSource, pMapTileIndex)
    }

    override fun getExpirationTimestamp(pTileSource: ITileSource, pMapTileIndex: Long): Long? {
        return delegate.getExpirationTimestamp(pTileSource, pMapTileIndex)
    }

    override fun loadTile(pTileSource: ITileSource, pMapTileIndex: Long): android.graphics.drawable.Drawable? {
        return delegate.loadTile(pTileSource, pMapTileIndex)
    }
}

private val SPECTRUM_COLORS = listOf(
    Color.parseColor("#F44336"),
    Color.parseColor("#FF9800"),
    Color.parseColor("#FFEB3B"),
    Color.parseColor("#4CAF50"),
    Color.parseColor("#00BCD4"),
    Color.parseColor("#2196F3"),
    Color.parseColor("#9C27B0")
)

private val DEFAULT_MARKER_COLOR = Color.parseColor("#2E7D32")
private val INFO_WINDOW_BACKGROUND_COLOR = Color.parseColor("#FCFCFC")

private fun spectrumColor(order: Int): Int {
    val index = (order - 1).coerceAtLeast(0) % SPECTRUM_COLORS.size
    return SPECTRUM_COLORS[index]
}

private fun readCachedLocation(context: android.content.Context): Location? {
    val prefs = context.getSharedPreferences(HeadingLocationOverlay.LOCATION_PREFS, android.content.Context.MODE_PRIVATE)
    if (!prefs.contains(HeadingLocationOverlay.KEY_LAT) || !prefs.contains(HeadingLocationOverlay.KEY_LON)) {
        return null
    }
    return Location("cached").apply {
        latitude = prefs.getFloat(HeadingLocationOverlay.KEY_LAT, 0f).toDouble()
        longitude = prefs.getFloat(HeadingLocationOverlay.KEY_LON, 0f).toDouble()
        time = prefs.getLong(HeadingLocationOverlay.KEY_TIME, System.currentTimeMillis())
    }
}

private fun findNearestPoint(map: MapView, points: List<PointEntity>, target: GeoPoint, maxDp: Float): PointEntity? {
    val projection = map.projection ?: return null
    val targetPx = projection.toPixels(target, Point())
    val density = map.context.resources.displayMetrics.density
    val maxPx = maxDp * density
    var nearest: PointEntity? = null
    var minDist = Float.MAX_VALUE
    points.forEach { p ->
        val px = projection.toPixels(GeoPoint(p.latitude, p.longitude), Point())
        val dx = (px.x - targetPx.x).toFloat()
        val dy = (px.y - targetPx.y).toFloat()
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist < minDist && dist <= maxPx) {
            minDist = dist
            nearest = p
        }
    }
    return nearest
}
