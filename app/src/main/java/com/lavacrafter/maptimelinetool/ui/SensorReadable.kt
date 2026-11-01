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

import com.lavacrafter.maptimelinetool.data.PointEntity
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

internal enum class AmbientLightLevel {
    LOW,
    MEDIUM,
    HIGH
}

internal enum class NoiseLevel {
    QUIET,
    RELATIVELY_QUIET,
    NOISY,
    EXTREMELY_NOISY
}

internal data class ReadableSensorSummary(
    val altitudeMeters: Float?,
    val ambientLightLevel: AmbientLightLevel?,
    val noiseLevel: NoiseLevel?,
    val azimuthDegrees: Float?,
    val pitchDegrees: Float?
)

internal data class LookDirection(
    val azimuthDegrees: Float,
    val pitchDegrees: Float
)

internal fun PointEntity.toReadableSensorSummary(): ReadableSensorSummary {
    val altitude = pressureHpa?.let(::pressureToAltitudeMeters)
    val lightLevel = ambientLightLux?.let(::toAmbientLightLevel)
    val noise = noiseDb?.let(::toNoiseLevel)
    // A heading needs both gravity and the magnetic field (tilt compensation); without either it is unknown.
    val azimuth = if (accelerometerX != null && accelerometerY != null && accelerometerZ != null &&
        magnetometerX != null && magnetometerY != null && magnetometerZ != null) {
        deviceAzimuthDegrees(accelerometerX, accelerometerY, accelerometerZ, magnetometerX, magnetometerY, magnetometerZ)
    } else {
        null
    }
    val pitch = if (accelerometerY != null && accelerometerZ != null) {
        accelerometerToPitchDegrees(accelerometerY, accelerometerZ)
    } else {
        null
    }
    return ReadableSensorSummary(
        altitudeMeters = altitude,
        ambientLightLevel = lightLevel,
        noiseLevel = noise,
        azimuthDegrees = azimuth,
        pitchDegrees = pitch
    )
}

internal fun PointEntity.toLookDirection(): LookDirection? {
    val summary = toReadableSensorSummary()
    val azimuth = summary.azimuthDegrees ?: return null
    val pitch = summary.pitchDegrees ?: 0f
    return LookDirection(azimuthDegrees = azimuth, pitchDegrees = pitch)
}

internal fun pressureToAltitudeMeters(
    pressureHpa: Float,
    seaLevelPressureHpa: Float = 1013.25f
): Float {
    if (pressureHpa <= 0f || seaLevelPressureHpa <= 0f) return 0f
    return (
        STANDARD_ATMOSPHERE_SCALE_HEIGHT_METERS *
            (1f - (pressureHpa / seaLevelPressureHpa).toDouble().pow(1.0 / BAROMETRIC_EXPONENT).toFloat())
        )
}

internal fun toAmbientLightLevel(lux: Float): AmbientLightLevel =
    when {
        lux < 100f -> AmbientLightLevel.LOW
        lux < 1000f -> AmbientLightLevel.MEDIUM
        else -> AmbientLightLevel.HIGH
    }

internal fun toNoiseLevel(dbfs: Float): NoiseLevel =
    when {
        dbfs < -50f -> NoiseLevel.QUIET
        dbfs < -35f -> NoiseLevel.RELATIVELY_QUIET
        dbfs < -20f -> NoiseLevel.NOISY
        else -> NoiseLevel.EXTREMELY_NOISY
    }

/**
 * Clockwise heading from magnetic north, in degrees, of where the device points: the top edge when it lies
 * roughly flat, the back camera when it is held upright. Uses the same rotation-matrix construction as
 * SensorManager.getRotationMatrix (east = field x gravity, north = gravity x east) in the device's natural
 * orientation. Returns null when the vectors are degenerate (free fall, or field parallel to gravity).
 */
internal fun deviceAzimuthDegrees(ax: Float, ay: Float, az: Float, mx: Float, my: Float, mz: Float): Float? {
    val gx = ax.toDouble(); val gy = ay.toDouble(); val gz = az.toDouble()
    val gNorm = sqrt(gx * gx + gy * gy + gz * gz)
    var hx = my * gz - mz * gy
    var hy = mz * gx - mx * gz
    var hz = mx * gy - my * gx
    val hNorm = sqrt(hx * hx + hy * hy + hz * hz)
    if (gNorm < 0.1 || hNorm < 0.1 || !hNorm.isFinite()) return null
    hx /= hNorm; hy /= hNorm; hz /= hNorm
    val ux = gx / gNorm; val uy = gy / gNorm; val uz = gz / gNorm
    val nx = uy * hz - uz * hy
    val ny = uz * hx - ux * hz
    val nz = ux * hy - uy * hx
    // Pick whichever pointing axis is closer to horizontal: device top (+Y) or back camera (-Z).
    val topEast = hy; val topNorth = ny
    val backEast = -hz; val backNorth = -nz
    val useTop = topEast * topEast + topNorth * topNorth >= backEast * backEast + backNorth * backNorth
    val deg = if (useTop) Math.toDegrees(atan2(topEast, topNorth)) else Math.toDegrees(atan2(backEast, backNorth))
    return ((deg.toFloat() % 360f) + 360f) % 360f
}

internal fun accelerometerToPitchDegrees(y: Float, z: Float): Float {
    // Android coordinate system: Y is up towards top of screen, Z is out of screen towards user.
    // When device is flat on table: z ~= 9.8, y ~= 0 (pitch = 0).
    // When device is vertical (portrait): y ~= 9.8, z ~= 0 (pitch = 90).
    // pitch = atan2(y, z).
    return Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat().coerceIn(-89f, 89f)
}

internal fun estimateViewDistanceMeters(pitchDegrees: Float): Double {
    val normalized = ((pitchDegrees + PITCH_MIN_DEGREES) / PITCH_RANGE_DEGREES).coerceIn(0f, 1f)
    return VIEW_DISTANCE_MIN_METERS + normalized * (VIEW_DISTANCE_MAX_METERS - VIEW_DISTANCE_MIN_METERS)
}

internal fun buildViewSector(
    latitude: Double,
    longitude: Double,
    azimuthDegrees: Float,
    pitchDegrees: Float,
    halfAngleDegrees: Float = 20f
): List<Pair<Double, Double>> {
    val distance = estimateViewDistanceMeters(pitchDegrees)
    val left = destinationPoint(latitude, longitude, azimuthDegrees - halfAngleDegrees, distance)
    val center = destinationPoint(latitude, longitude, azimuthDegrees, distance)
    val right = destinationPoint(latitude, longitude, azimuthDegrees + halfAngleDegrees, distance)
    return listOf(
        latitude to longitude,
        left,
        center,
        right
    )
}

private fun destinationPoint(
    latitude: Double,
    longitude: Double,
    bearingDegrees: Float,
    distanceMeters: Double
): Pair<Double, Double> {
    val angularDistance = distanceMeters / EARTH_RADIUS_METERS
    val bearing = Math.toRadians(bearingDegrees.toDouble())
    val lat1 = Math.toRadians(latitude)
    val lon1 = Math.toRadians(longitude)

    val sinLat1 = sin(lat1)
    val cosLat1 = cos(lat1)
    val sinAd = sin(angularDistance)
    val cosAd = cos(angularDistance)

    val lat2 = kotlin.math.asin(sinLat1 * cosAd + cosLat1 * sinAd * cos(bearing))
    val lon2 = lon1 + atan2(
        sin(bearing) * sinAd * cosLat1,
        cosAd - sinLat1 * sin(lat2)
    )
    return Math.toDegrees(lat2) to Math.toDegrees(lon2)
}

private const val GYROSCOPE_SNAPSHOT_WINDOW_SECONDS = 0.5f
private const val STANDARD_ATMOSPHERE_SCALE_HEIGHT_METERS = 44330f
private const val BAROMETRIC_EXPONENT = 5.255
private const val PITCH_MIN_DEGREES = 90f
private const val PITCH_RANGE_DEGREES = 180f
private const val VIEW_DISTANCE_MIN_METERS = 40.0
private const val VIEW_DISTANCE_MAX_METERS = 260.0
private const val EARTH_RADIUS_METERS = 6_371_000.0
