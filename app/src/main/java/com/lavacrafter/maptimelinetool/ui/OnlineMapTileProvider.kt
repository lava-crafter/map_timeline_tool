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

import android.content.Context
import android.graphics.drawable.Drawable
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.INetworkAvailablityCheck
import org.osmdroid.tileprovider.modules.MapTileDownloader
import org.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import java.io.InputStream

internal class OnlineMapTileProvider(
    context: Context,
    source: ITileSource,
    networkCheck: INetworkAvailablityCheck,
    filesystemCache: IFilesystemCache
) : MapTileProviderBasic(SimpleRegisterReceiver(context), networkCheck, source, context, filesystemCache) {
    override fun createDownloaderProvider(
        networkCheck: INetworkAvailablityCheck,
        tileSource: ITileSource
    ): MapTileDownloader = OnlineMapTileDownloader(tileSource, tileWriter, networkCheck)
}

internal class PolicyAwareNetworkCheck(context: Context) : INetworkAvailablityCheck {
    private val delegate = NetworkAvailabliltyCheck(context)

    @Volatile
    var allowNetwork: Boolean = true

    override fun getNetworkAvailable(): Boolean = allowNetwork && delegate.getNetworkAvailable()

    override fun getWiFiNetworkAvailable(): Boolean = allowNetwork && delegate.getWiFiNetworkAvailable()

    override fun getCellularDataNetworkAvailable(): Boolean = allowNetwork && delegate.getCellularDataNetworkAvailable()

    @Deprecated("Deprecated by osmdroid INetworkAvailablityCheck; kept for interface compatibility.")
    @Suppress("DEPRECATION")
    override fun getRouteToPathExists(hostAddress: Int): Boolean = allowNetwork && delegate.getRouteToPathExists(hostAddress)
}

internal class PolicyAwareFilesystemCache(
    private val delegate: IFilesystemCache
) : IFilesystemCache {
    @Volatile
    var allowWrites: Boolean = true

    override fun saveFile(
        pTileSource: ITileSource,
        pMapTileIndex: Long,
        pStream: InputStream,
        pExpirationTime: Long?
    ): Boolean = allowWrites && delegate.saveFile(pTileSource, pMapTileIndex, pStream, pExpirationTime)

    override fun exists(pTileSource: ITileSource, pMapTileIndex: Long): Boolean = delegate.exists(pTileSource, pMapTileIndex)

    override fun onDetach() = delegate.onDetach()

    override fun remove(pTileSource: ITileSource, pMapTileIndex: Long): Boolean = delegate.remove(pTileSource, pMapTileIndex)

    override fun getExpirationTimestamp(pTileSource: ITileSource, pMapTileIndex: Long): Long? =
        delegate.getExpirationTimestamp(pTileSource, pMapTileIndex)

    override fun loadTile(pTileSource: ITileSource, pMapTileIndex: Long): Drawable? = delegate.loadTile(pTileSource, pMapTileIndex)
}
