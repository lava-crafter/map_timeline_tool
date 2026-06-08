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
import com.lavacrafter.maptimelinetool.R
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex

data class DownloadTileSource(
    val id: String,
    val labelRes: Int,
    val attributionRes: Int,
    val templateUrl: String,
    val policyFlags: Int,
    val tileExtension: String = ".png",
    val minZoom: Int = 0,
    val maxZoom: Int = 19
) {
    fun toOsmdroidSource(context: Context): OnlineTileSourceBase {
        return object : OnlineTileSourceBase(
            context.getString(labelRes),
            minZoom,
            maxZoom,
            256,
            tileExtension,
            arrayOf(templateUrl),
            context.getString(attributionRes),
            TileSourcePolicy(
                4,
                policyFlags
            )
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                return templateUrl
                    .replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
                    .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
                    .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())
            }
        }
    }
}

val downloadTileSources = listOf(
    DownloadTileSource(
        id = "mapnik",
        labelRes = R.string.tile_source_mapnik,
        attributionRes = R.string.tile_source_attribution_openstreetmap,
        templateUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        policyFlags = TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
    )
)

fun downloadTileSourceById(id: String): DownloadTileSource {
    return downloadTileSources.firstOrNull { it.id == id } ?: downloadTileSources.first()
}

val mapTileSources = downloadTileSources + listOf(
    DownloadTileSource(
        id = "eox_sentinel2_cloudless_2024",
        labelRes = R.string.tile_source_eox_sentinel2_cloudless_2024,
        attributionRes = R.string.tile_source_attribution_eox_sentinel2_cloudless,
        templateUrl = "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2024_3857/default/g/{z}/{y}/{x}.jpg",
        policyFlags = TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL,
        tileExtension = ".jpg"
    )
)

fun mapTileSourceById(id: String): DownloadTileSource {
    return mapTileSources.firstOrNull { it.id == id } ?: mapTileSources.first()
}