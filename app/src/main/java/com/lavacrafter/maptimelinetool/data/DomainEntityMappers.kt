package com.lavacrafter.maptimelinetool.data

import com.lavacrafter.maptimelinetool.domain.model.Point
import com.lavacrafter.maptimelinetool.domain.model.Tag

fun PointEntity.toDomain(): Point = Point(
    id = id,
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
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
