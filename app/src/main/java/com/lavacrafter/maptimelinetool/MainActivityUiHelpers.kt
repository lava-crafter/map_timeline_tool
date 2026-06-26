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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.lavacrafter.maptimelinetool.data.PointEntity
import com.lavacrafter.maptimelinetool.domain.model.GeoPoint
import com.lavacrafter.maptimelinetool.ui.ListScreen
import com.lavacrafter.maptimelinetool.ui.MapScreen
import com.lavacrafter.maptimelinetool.ui.ZoomButtonBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapWithListSheet(
    points: List<PointEntity>,
    selectedPointId: Long?,
    onSelectPoint: (PointEntity) -> Unit,
    onLongPressPoint: (PointEntity) -> Unit,
    onEditPointFromMap: (PointEntity) -> Unit,
    isActive: Boolean,
    zoomBehavior: ZoomButtonBehavior,
    markerScale: Float,
    downloadedOnly: Boolean,
    mapTileSourceId: String,
    onMapTileSourceChange: (String) -> Unit,
    onResolveCenterLocation: ((GeoPoint?) -> Unit) -> Unit,
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
                onMapTileSourceChange = onMapTileSourceChange,
                onResolveCenterLocation = onResolveCenterLocation
            )
        }
    }
}

@Composable
internal fun ZipExportOptionsDialog(
    includePoints: Boolean,
    includeTags: Boolean,
    includeSensors: Boolean,
    includePhotos: Boolean,
    onIncludePointsChange: (Boolean) -> Unit,
    onIncludeTagsChange: (Boolean) -> Unit,
    onIncludeSensorsChange: (Boolean) -> Unit,
    onIncludePhotosChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val canExport = includePoints || includePhotos

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_zip_options_title)) },
        text = {
            Column {
                Row {
                    Checkbox(
                        checked = includePoints,
                        onCheckedChange = onIncludePointsChange
                    )
                    Text(stringResource(R.string.export_zip_option_points))
                }
                Row {
                    Checkbox(
                        checked = includeTags,
                        onCheckedChange = onIncludeTagsChange
                    )
                    Text(stringResource(R.string.export_zip_option_tags))
                }
                Row {
                    Checkbox(
                        checked = includeSensors,
                        onCheckedChange = onIncludeSensorsChange
                    )
                    Text(stringResource(R.string.export_zip_option_sensors))
                }
                Row {
                    Checkbox(
                        checked = includePhotos,
                        onCheckedChange = onIncludePhotosChange
                    )
                    Text(stringResource(R.string.export_zip_option_photos))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = canExport
            ) {
                Text(stringResource(R.string.action_export_zip))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
internal fun PhotoPreviewDialog(
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

private fun decodePreviewBitmap(file: java.io.File): Bitmap? {
    val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val orientation = runCatching {
        ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return applyExifOrientation(decoded, orientation)
}

enum class NetworkStatus { WIFI, CELLULAR, NONE }

@Composable
internal fun observeNetworkStatus(context: Context): State<NetworkStatus> {
    val state = remember { mutableStateOf(getNetworkStatus(context)) }
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                state.value = getNetworkStatus(context)
            }

            override fun onLost(network: Network) {
                state.value = getNetworkStatus(context)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                state.value = when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.WIFI
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkStatus.CELLULAR
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> resolveVpnNetworkStatus(connectivityManager)
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

@Suppress("DEPRECATION")
private fun resolveVpnNetworkStatus(connectivityManager: ConnectivityManager): NetworkStatus {
    val underlying = connectivityManager.allNetworks.find { network ->
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        capabilities != null &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    if (underlying != null) {
        val capabilities = connectivityManager.getNetworkCapabilities(underlying)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            return NetworkStatus.WIFI
        }
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
            return NetworkStatus.CELLULAR
        }
    }
    return NetworkStatus.CELLULAR
}

private fun getNetworkStatus(context: Context): NetworkStatus {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return NetworkStatus.NONE
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkStatus.NONE
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkStatus.CELLULAR
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> resolveVpnNetworkStatus(connectivityManager)
        else -> NetworkStatus.NONE
    }
}
