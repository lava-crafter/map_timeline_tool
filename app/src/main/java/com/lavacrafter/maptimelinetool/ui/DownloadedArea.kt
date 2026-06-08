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

data class DownloadedArea(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun boundsKey(): String {
        fun round(value: Double): String = String.format("%.5f", value)
        return listOf(
            round(north),
            round(south),
            round(east),
            round(west),
            minZoom.toString(),
            maxZoom.toString()
        ).joinToString("|")
    }
}
