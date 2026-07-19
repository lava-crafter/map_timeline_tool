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

sealed interface QuickAddOutcome {
    data class Success(val pointId: Long) : QuickAddOutcome
    data object SetupRequired : QuickAddOutcome
    data object LocationPermissionMissing : QuickAddOutcome
    data object NotificationUnavailable : QuickAddOutcome
    data object LocationServicesDisabled : QuickAddOutcome
    data object NoLocationProvider : QuickAddOutcome
    data object LocationTimeout : QuickAddOutcome
    data object LocationTooInaccurate : QuickAddOutcome
    data object AlreadyRunning : QuickAddOutcome
    data object StorageFailure : QuickAddOutcome
    data object ForegroundServiceStartFailure : QuickAddOutcome
    data object UnexpectedFailure : QuickAddOutcome
}

internal fun QuickAddOutcome.toStoredResult(): QuickAddStoredResult = when (this) {
    is QuickAddOutcome.Success -> QuickAddStoredResult.SUCCESS
    QuickAddOutcome.SetupRequired -> QuickAddStoredResult.SETUP_REQUIRED
    QuickAddOutcome.LocationPermissionMissing -> QuickAddStoredResult.PERMISSION_REQUIRED
    QuickAddOutcome.NotificationUnavailable -> QuickAddStoredResult.NOTIFICATION_REQUIRED
    QuickAddOutcome.LocationServicesDisabled -> QuickAddStoredResult.LOCATION_DISABLED
    QuickAddOutcome.NoLocationProvider -> QuickAddStoredResult.NO_PROVIDER
    QuickAddOutcome.LocationTimeout -> QuickAddStoredResult.LOCATION_TIMEOUT
    QuickAddOutcome.LocationTooInaccurate -> QuickAddStoredResult.LOCATION_INACCURATE
    QuickAddOutcome.AlreadyRunning -> QuickAddStoredResult.ALREADY_RUNNING
    QuickAddOutcome.StorageFailure -> QuickAddStoredResult.STORAGE_FAILURE
    QuickAddOutcome.ForegroundServiceStartFailure -> QuickAddStoredResult.START_FAILURE
    QuickAddOutcome.UnexpectedFailure -> QuickAddStoredResult.UNEXPECTED_FAILURE
}
