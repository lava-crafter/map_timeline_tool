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

package com.lavacrafter.maptimelinetool.data

import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.model.Tag

fun PointEntity.toDomain(): Point = Point(
    id = id,
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    locationAccuracyMeters = locationAccuracyMeters,
    locationFixTimeMs = locationFixTimeMs,
    locationProvider = locationProvider,
    title = title,
    note = note,
    pressureHpa = pressureHpa,
    ambientLightLux = ambientLightLux,
    accelerometerX = accelerometerX,
    accelerometerY = accelerometerY,
    accelerometerZ = accelerometerZ,
    gyroscopeX = gyroscopeX,
    gyroscopeY = gyroscopeY,
    gyroscopeZ = gyroscopeZ,
    magnetometerX = magnetometerX,
    magnetometerY = magnetometerY,
    magnetometerZ = magnetometerZ,
    noiseDb = noiseDb,
    photoPath = photoPath
)

fun Point.toEntity(): PointEntity = PointEntity(
    id = id,
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    locationAccuracyMeters = locationAccuracyMeters,
    locationFixTimeMs = locationFixTimeMs,
    locationProvider = locationProvider,
    title = title,
    note = note,
    pressureHpa = pressureHpa,
    ambientLightLux = ambientLightLux,
    accelerometerX = accelerometerX,
    accelerometerY = accelerometerY,
    accelerometerZ = accelerometerZ,
    gyroscopeX = gyroscopeX,
    gyroscopeY = gyroscopeY,
    gyroscopeZ = gyroscopeZ,
    magnetometerX = magnetometerX,
    magnetometerY = magnetometerY,
    magnetometerZ = magnetometerZ,
    noiseDb = noiseDb,
    photoPath = photoPath
)

fun TagEntity.toDomain(): Tag = Tag(id = id, name = name)
fun Tag.toEntity(): TagEntity = TagEntity(id = id, name = name)
