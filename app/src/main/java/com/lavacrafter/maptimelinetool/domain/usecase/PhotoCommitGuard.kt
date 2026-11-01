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

package com.lavacrafter.maptimelinetool.domain.usecase

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared by core photo-reference commits and reference-checked file cleanup, never by sampling/extraction. */
class PhotoCommitGuard(private val photoExists: (String) -> Boolean = { true }) {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    /** Call inside the guard immediately before writing a photo reference. */
    fun requirePhoto(path: String?) {
        if (path != null) require(path.isNotBlank() && photoExists(path)) {
            "Photo is no longer available; select or capture it again"
        }
    }
}
