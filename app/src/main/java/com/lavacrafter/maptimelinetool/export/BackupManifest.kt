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

package com.lavacrafter.maptimelinetool.export

import org.json.JSONObject

data class BackupSections(
    val points: Boolean = true,
    val tags: Boolean = false,
    val sensors: Boolean = true,
    val photos: Boolean = false,
    val settings: Boolean = false
)

data class BackupManifest(
    val version: Int,
    val sections: BackupSections
) {
    companion object {
        /** Old archives did not declare optional sections, so preserve their historical semantics. */
        val LEGACY = BackupManifest(
            version = 1,
            sections = BackupSections(points = true, tags = true, sensors = true, photos = true, settings = true)
        )

        fun parse(json: String): BackupManifest? = runCatching {
            val root = JSONObject(json)
            val version = root.optInt("backup_version", 1).coerceAtLeast(1)
            val rawSections = root.optJSONObject("sections")
            BackupManifest(
                version = version,
                sections = BackupSections(
                    points = rawSections?.optBoolean("points", true) ?: true,
                    tags = rawSections?.optBoolean("tags", false) ?: false,
                    sensors = rawSections?.optBoolean("sensors", version < 2) ?: (version < 2),
                    photos = rawSections?.optBoolean("photos", false) ?: false,
                    settings = rawSections?.optBoolean("settings", false) ?: false
                )
            )
        }.getOrNull()
    }
}

/** Bounded input budgets, not an arbitrary point-count limit. */
data class ZipImportLimits(
    val maxUnrecognizedEntries: Int = 1_024,
    val maxEntryNameLength: Int = 512,
    val maxManifestBytes: Long = 128 * 1024L,
    val maxSettingsBytes: Long = 2 * 1024 * 1024L,
    val maxDataEntryBytes: Long = 256L * 1024L * 1024L,
    val maxPhotoBytes: Long = 128L * 1024L * 1024L,
    val maxTotalBytes: Long = 2L * 1024L * 1024L * 1024L
)

class ZipImportLimitExceededException(message: String) : IllegalArgumentException(message)
