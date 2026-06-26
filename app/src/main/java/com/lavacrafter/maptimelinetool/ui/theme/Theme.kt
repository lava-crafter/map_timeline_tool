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

package com.lavacrafter.maptimelinetool.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6B5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA7D5C6),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF756B5D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDED3C3),
    onSecondaryContainer = Color(0xFF291F13),
    background = Color(0xFFF7F4EC),
    onBackground = Color(0xFF1B1C18),
    surface = Color(0xFFF7F4EC),
    onSurface = Color(0xFF1B1C18),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404942),
    outline = Color(0xFF707973)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BC1B1),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFFA7D5C6),
    secondary = Color(0xFFC1B7A8),
    onSecondary = Color(0xFF3A3126),
    secondaryContainer = Color(0xFF52483C),
    onSecondaryContainer = Color(0xFFDED3C3),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E3DD),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE1E3DD),
    surfaceVariant = Color(0xFF404942),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8A938C)
)

@Composable
fun MapTimelineToolTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
