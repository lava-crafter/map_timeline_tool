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

package com.lavacrafter.maptimelinetool.quickadd

import android.os.Build

internal enum class QuickAddEnableAction {
    ENABLE,
    REQUEST_NOTIFICATION_PERMISSION,
    REQUEST_FOREGROUND_LOCATION_PERMISSION,
    OPEN_SETTINGS_FOR_PRECISE_LOCATION,
    OPEN_SETTINGS_FOR_BACKGROUND_LOCATION
}

internal fun requiresBackgroundLocationForQuickAdd(sdkInt: Int): Boolean {
    return sdkInt >= Build.VERSION_CODES.Q
}

internal fun hasRequiredLocationPermissionsForQuickAdd(
    sdkInt: Int,
    hasPreciseLocationPermission: Boolean,
    hasBackgroundLocationPermission: Boolean
): Boolean {
    if (!hasPreciseLocationPermission) {
        return false
    }
    return !requiresBackgroundLocationForQuickAdd(sdkInt) || hasBackgroundLocationPermission
}

internal fun isQuickAddExecutionAllowed(
    enabled: Boolean,
    sdkInt: Int,
    hasPreciseLocationPermission: Boolean,
    hasBackgroundLocationPermission: Boolean
): Boolean {
    return enabled && hasRequiredLocationPermissionsForQuickAdd(
        sdkInt = sdkInt,
        hasPreciseLocationPermission = hasPreciseLocationPermission,
        hasBackgroundLocationPermission = hasBackgroundLocationPermission
    )
}

internal fun resolveQuickAddEnableAction(
    sdkInt: Int,
    notificationsGranted: Boolean,
    preciseLocationGranted: Boolean,
    coarseLocationGranted: Boolean,
    backgroundLocationGranted: Boolean
): QuickAddEnableAction {
    return when {
        !notificationsGranted -> QuickAddEnableAction.REQUEST_NOTIFICATION_PERMISSION
        !preciseLocationGranted && !coarseLocationGranted -> QuickAddEnableAction.REQUEST_FOREGROUND_LOCATION_PERMISSION
        !preciseLocationGranted -> QuickAddEnableAction.OPEN_SETTINGS_FOR_PRECISE_LOCATION
        requiresBackgroundLocationForQuickAdd(sdkInt) && !backgroundLocationGranted -> {
            QuickAddEnableAction.OPEN_SETTINGS_FOR_BACKGROUND_LOCATION
        }
        else -> QuickAddEnableAction.ENABLE
    }
}
