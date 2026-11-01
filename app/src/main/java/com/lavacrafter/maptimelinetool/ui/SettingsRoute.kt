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

import androidx.compose.runtime.saveable.Saver

sealed interface SettingsRoute {
    object Main : SettingsRoute
    object BackupRestore : SettingsRoute
    object Language : SettingsRoute
    object Notification : SettingsRoute
    object Sensors : SettingsRoute
    object MapOperations : SettingsRoute
    object PhotoCompression : SettingsRoute
    object Cache : SettingsRoute
    object Download : SettingsRoute
    object DefaultTags : SettingsRoute
}

private val settingsRoutes = listOf(
    SettingsRoute.Main, SettingsRoute.BackupRestore, SettingsRoute.Language, SettingsRoute.Notification,
    SettingsRoute.Sensors, SettingsRoute.MapOperations, SettingsRoute.PhotoCompression, SettingsRoute.Cache,
    SettingsRoute.Download, SettingsRoute.DefaultTags
)

/** Keeps the open settings page across Activity recreation. */
val SettingsRouteSaver: Saver<SettingsRoute, Int> = Saver(
    save = { route -> settingsRoutes.indexOf(route) },
    restore = { index -> settingsRoutes.getOrNull(index) ?: SettingsRoute.Main }
)
