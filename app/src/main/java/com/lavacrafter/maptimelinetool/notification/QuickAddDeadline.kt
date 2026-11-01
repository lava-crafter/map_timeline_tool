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

package com.lavacrafter.maptimelinetool.notification

import kotlinx.coroutines.withTimeoutOrNull

/** Covers permissions, location, sensor snapshot, optional noise and core DB save. */
internal suspend fun <T> runQuickAddWithinDeadline(
    timeoutMs: Long,
    committedResult: () -> T?,
    work: suspend () -> T?
): T? = withTimeoutOrNull(timeoutMs) { work() } ?: committedResult()
