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

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.tileprovider.MapTileRequestState
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.INetworkAvailablityCheck
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileIndex

/** Uses fake cache/network services and generated drawables, not a live tile server. */
@RunWith(AndroidJUnit4::class)
class MapTileProviderPolicyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val source get() = mapTileSourceById("mapnik").toOsmdroidSource(context)

    @Test
    fun disablingWritesPreservesExistingTilesAndDoesNotConsumeTheDownload() {
        val delegate = RecordingCache()
        val cache = PolicyAwareFilesystemCache(delegate)
        val tile = MapTileIndex.getTileIndex(2, 1, 1)
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3))

        cache.allowWrites = false
        assertFalse(cache.saveFile(source, tile, stream, 123L))
        assertEquals(0, delegate.writes)
        assertEquals(3, stream.available())
        assertTrue(cache.exists(source, tile))
        assertSame(delegate.existingTile, cache.loadTile(source, tile))
        assertEquals(123L, cache.getExpirationTimestamp(source, tile))

        cache.allowWrites = true
        assertTrue(cache.saveFile(source, tile, stream, 123L))
        assertEquals(1, delegate.writes)
        cache.allowWrites = false
        assertFalse(cache.saveFile(source, tile, stream, 123L))
        assertEquals(1, delegate.writes)
        assertSame(delegate.existingTile, cache.loadTile(source, tile))
    }

    @Test
    fun downloadedDrawableReachesTheMapMemoryCacheWithoutADiskWrite() {
        val delegate = RecordingCache()
        val cache = PolicyAwareFilesystemCache(delegate).apply { allowWrites = false }
        val provider = OnlineMapTileProvider(context, source, availableNetwork, cache)
        val downloader = OnlineMapTileDownloader(source, cache, availableNetwork)
        val tile = MapTileIndex.getTileIndex(2, 1, 1)
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val drawable = BitmapDrawable(context.resources, bitmap)
        val request = MapTileRequestState(tile, emptyList<MapTileModuleProviderBase>(), provider)
        try {
            // Exercise the real completion path; osmdroid's default implementation returns null
            // here and recycles the image, which requires a successful disk write to display it.
            downloader.completeDownloadedTile(request, drawable)

            assertSame(drawable, provider.tileCache.getMapTile(tile))
            assertFalse(bitmap.isRecycled)
            assertEquals(0, delegate.writes)
        } finally {
            downloader.detach()
            provider.detach()
        }
    }

    @Test
    fun lateOldLayerCompletionCannotPopulateTheNewLayersMemoryCache() {
        val cache = PolicyAwareFilesystemCache(RecordingCache()).apply { allowWrites = false }
        val oldProvider = OnlineMapTileProvider(context, source, availableNetwork, cache)
        val newSource = mapTileSourceById("eox_sentinel2_cloudless_2024").toOsmdroidSource(context)
        val newProvider = OnlineMapTileProvider(context, newSource, availableNetwork, cache)
        val oldDownloader = OnlineMapTileDownloader(source, cache, availableNetwork)
        val tile = MapTileIndex.getTileIndex(2, 1, 1)
        try {
            oldProvider.detach()
            val lateRequest = MapTileRequestState(tile, emptyList<MapTileModuleProviderBase>(), oldProvider)
            oldDownloader.completeDownloadedTile(lateRequest, ColorDrawable(android.graphics.Color.BLUE))

            assertNull(newProvider.tileCache.getMapTile(tile))
        } finally {
            oldDownloader.detach()
            newProvider.detach()
        }
    }

    private class RecordingCache : IFilesystemCache {
        var writes = 0
        val existingTile = ColorDrawable(android.graphics.Color.BLUE)

        override fun saveFile(source: ITileSource, tile: Long, stream: InputStream, expiration: Long?): Boolean {
            writes++
            return true
        }
        override fun exists(source: ITileSource, tile: Long): Boolean = true
        override fun loadTile(source: ITileSource, tile: Long): Drawable = existingTile
        override fun getExpirationTimestamp(source: ITileSource, tile: Long): Long = 123L
        override fun remove(source: ITileSource, tile: Long): Boolean = true
        override fun onDetach() = Unit
    }

    private val availableNetwork = object : INetworkAvailablityCheck {
        override fun getNetworkAvailable(): Boolean = true
        override fun getWiFiNetworkAvailable(): Boolean = true
        override fun getCellularDataNetworkAvailable(): Boolean = true
        @Deprecated("osmdroid compatibility")
        override fun getRouteToPathExists(hostAddress: Int): Boolean = true
    }
}
