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

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val generatedShareBackupName = Regex("map_timeline_backup_\\d{8}_\\d{6}(?:_[0-9a-f]{32})?\\.zip")
private val unfinishedShareBackupName = Regex("map_timeline_backup_pending_.*\\.part")

/** Keep the new share URI usable after the chooser closes; remove old archives on the next share. */
internal fun createSharedBackupFile(directory: File, write: (OutputStream) -> Unit): File {
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create backup share directory")
    val pending = File.createTempFile("map_timeline_backup_pending_", ".part", directory)
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val destination = File(directory, "map_timeline_backup_${timestamp}_${UUID.randomUUID().toString().replace("-", "")}.zip")
    try {
        pending.outputStream().buffered().use(write)
        if (!pending.renameTo(destination)) throw IOException("Cannot finalize shared backup")
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file != destination &&
                (generatedShareBackupName.matches(file.name) || unfinishedShareBackupName.matches(file.name))) {
                file.delete()
            }
        }
        return destination
    } finally {
        pending.delete()
    }
}
