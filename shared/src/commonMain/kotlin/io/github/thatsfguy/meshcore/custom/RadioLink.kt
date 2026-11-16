package io.github.thatsfguy.meshcore.custom

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Terrain-based LoRa link estimation (MeshCore Custom).
 *
 * This is a PREDICTION, not a measurement. It models free-space loss, Earth curvature
 * (effective radius k = 4/3) and single/multi knife-edge diffraction over the supplied
 * terrain profile. It does NOT know about buildings, trees, weather or antenna patterns,
 * so [Confidence] never goes above MEDIUM. Missing elevation samples lower confidence
 * and are reported; they are never invented.
 */
data class RadioParams(
    val frequencyMHz: Double = 869.525,
    val bandwidthKHz: Double = 250.0,
    val spreadingFactor: Int = 11,
    val txPowerDbm: Double = 14.0,
    val txAntennaGainDbi: Double = 2.0,
    val rxAntennaGainDbi: Double = 2.0,
    val txHeightM: Double = 2.0,
    val rxHeightM: Double = 2.0,
    val cableLossDb: Double = 0.0,
    val noiseFigureDb: Double = 6.0,
)

/** One terrain sample. [elevationM] is null where no elevation data was available. */
data class TerrainPoint(val distanceM: Double, val elevationM: Double?)

enum class Quality { GOOD, FAIR, WEAK, VERY_WEAK, UNKNOWN }
enum class Confidence { MEDIUM, LOW, NONE }

data class LinkResult(
    val distanceKm: Double,
    val lineOfSight: Boolean?,
    val freeSpaceLossDb: Double,
    val diffractionLossDb: Double,
    val totalLossDb: Double,
    val rxPowerDbm: Double,
    val sensitivityDbm: Double,
    val marginDb: Double,
    val quality: Quality,
    val confidence: Confidence,
    val worstFresnelClearance: Double?,
    val missingSamples: Int,
    val totalSamples: Int,
    val notes: List<String>,
)

object RadioLink {
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val K_FACTOR = 4.0 / 3.0

    /** Required SNR (dB) per spreading factor, from Semtech SX126x datasheets. */
    fun snrLimitDb(sf: Int): Double = when (sf) {
        5 -> -2.5; 6 -> -5.0; 7 -> -7.5; 8 -> -10.0; 9 -> -12.5
        10 -> -15.0; 11 -> -17.5; 12 -> -20.0
        else -> throw IllegalArgumentException("Spreading factor must be 5..12")
    }

    /** Receiver sensitivity: thermal noise floor + noise figure + required SNR. */
    fun sensitivityDbm(p: RadioParams): Double =
        -174.0 + 10 * log10(p.bandwidthKHz * 1000.0) + p.noiseFigureDb + snrLimitDb(p.spreadingFactor)

    fun freeSpaceLossDb(distanceKm: Double, frequencyMHz: Double): Double {
        require(distanceKm > 0 && frequencyMHz > 0)
        return 32.44 + 20 * log10(distanceKm) + 20 * log10(frequencyMHz)
    }

    /** ITU-R P.526 knife-edge loss for Fresnel–Kirchhoff parameter [v] (0 if v <= -0.78). */
    fun knifeEdgeLossDb(v: Double): Double =
        if (v <= -0.78) 0.0 else 6.9 + 20 * log10(sqrt((v - 0.1) * (v - 0.1) + 1.0) + v - 0.1)

    fun quality(marginDb: Double): Quality = when {
        marginDb >= 20.0 -> Quality.GOOD
        marginDb >= 10.0 -> Quality.FAIR
        marginDb >= 0.0 -> Quality.WEAK
        else -> Quality.VERY_WEAK
    }

    /**
     * Analyses a link along [profile] (sorted by distance, first = transmitter, last =
     * receiver). Antenna heights are above the ground at each end. Needs the two end
     * elevations; otherwise the result is UNKNOWN with no confidence.
     */
    fun analyse(profile: List<TerrainPoint>, p: RadioParams): LinkResult {
        require(profile.size >= 2) { "Profile needs at least two points" }
        val total = profile.last().distanceM
        require(total > 0) { "Zero-length path" }
        val dKm = total / 1000.0
        val fspl = freeSpaceLossDb(dKm, p.frequencyMHz)
        val sens = sensitivityDbm(p)
        val missing = profile.count { it.elevationM == null }
        val e0 = profile.first().elevationM
        val e1 = profile.last().elevationM
        val eirp = p.txPowerDbm + p.txAntennaGainDbi - p.cableLossDb
        val notes = mutableListOf(
            "Terrain-only model: buildings, vegetation and weather are not included.",
            "Effective Earth radius factor k = 4/3; knife-edge diffraction (ITU-R P.526).",
        )
        if (e0 == null || e1 == null) {
            notes += "Elevation is missing at an endpoint, so no terrain result is given."
            val rx = eirp + p.rxAntennaGainDbi - fspl
            return LinkResult(dKm, null, fspl, 0.0, fspl, rx, sens, rx - sens, Quality.UNKNOWN,
                Confidence.NONE, null, missing, profile.size, notes)
        }
        val h0 = e0 + p.txHeightM
        val h1 = e1 + p.rxHeightM
        val lambda = 299.792458 / p.frequencyMHz   // metres
        // Terrain height above the straight line between antennas, with Earth bulge added.
        var worstV = Double.NEGATIVE_INFINITY
        var worstClearRatio: Double? = null
        var los: Boolean? = true
        for (pt in profile) {
            val e = pt.elevationM ?: continue
            val d1 = pt.distanceM
            val d2 = total - d1
            if (d1 <= 0.0 || d2 <= 0.0) continue
            val bulge = d1 * d2 / (2 * K_FACTOR * EARTH_RADIUS_M)
            val lineH = h0 + (h1 - h0) * d1 / total
            val h = e + bulge - lineH          // >0: terrain pokes above the line
            val v = h * sqrt(2 * total / (lambda * d1 * d2))
            if (v > worstV) worstV = v
            val f1 = sqrt(lambda * d1 * d2 / total)   // first Fresnel radius
            val clear = (lineH - (e + bulge)) / f1
            if (worstClearRatio == null || clear < worstClearRatio) worstClearRatio = clear
            if (h > 0) los = false
        }
        val diffraction = if (worstV == Double.NEGATIVE_INFINITY) 0.0 else knifeEdgeLossDb(worstV)
        val loss = fspl + diffraction
        val rx = eirp + p.rxAntennaGainDbi - loss
        val margin = rx - sens
        val missingFrac = missing.toDouble() / profile.size
        val conf = when {
            missingFrac > 0.25 -> Confidence.LOW
            missingFrac > 0.0 || profile.size < 20 -> Confidence.LOW
            else -> Confidence.MEDIUM
        }
        if (missing > 0) notes += "$missing of ${profile.size} elevation samples are missing and were skipped."
        if (profile.size < 20) notes += "Few profile samples; narrow ridges may be missed."
        if (worstClearRatio != null && worstClearRatio < 0.6 && los == true) {
            notes += "Line of sight is clear but the first Fresnel zone is partly blocked."
        }
        return LinkResult(dKm, los, fspl, diffraction, loss, rx, sens, margin, quality(margin), conf,
            worstClearRatio, missing, profile.size, notes)
    }

    // ── Geometry helpers ──

    /** Great-circle distance in metres. */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = (PI / 180.0)
        val dLat = (lat2 - lat1) * r
        val dLon = (lon2 - lon1) * r
        val a = sin(dLat / 2).let { it * it } + cos(lat1 * r) * cos(lat2 * r) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /** Point [distanceM] away from the start along [bearingDeg] (degrees from north). */
    fun destination(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): Pair<Double, Double> {
        val r = PI / 180.0
        val ang = distanceM / EARTH_RADIUS_M
        val b = bearingDeg * r
        val la1 = lat * r
        val lo1 = lon * r
        val la2 = asin(sin(la1) * cos(ang) + cos(la1) * sin(ang) * cos(b))
        val lo2 = lo1 + atan2(sin(b) * sin(ang) * cos(la1), cos(ang) - sin(la1) * sin(la2))
        var lonDeg = lo2 / r
        lonDeg = ((lonDeg + 540.0) % 360.0) - 180.0
        return la2 / r to lonDeg
    }

    /** Samples a straight profile between two points using [elevation] (null = no data). */
    fun profileBetween(
        lat1: Double, lon1: Double, lat2: Double, lon2: Double, samples: Int,
        elevation: (Double, Double) -> Double?,
    ): List<TerrainPoint> {
        require(samples >= 2)
        val total = distanceM(lat1, lon1, lat2, lon2)
        return (0 until samples).map { i ->
            val f = i.toDouble() / (samples - 1)
            // Linear interpolation of coordinates is adequate for paths of a few tens of km.
            val la = lat1 + (lat2 - lat1) * f
            val lo = lon1 + (lon2 - lon1) * f
            TerrainPoint(total * f, elevation(la, lo))
        }
    }

    @Suppress("unused")
    private fun unusedLn() = ln(1.0)
}

/** One coverage sample: where it is, and the predicted margin there. */
data class CoverageCell(
    val lat: Double, val lon: Double, val distanceM: Double,
    val marginDb: Double?, val quality: Quality,
)

object CoverageModel {
    /**
     * Predicts coverage around a transmitter by casting [bearings] rays up to [radiusM],
     * analysing the terrain profile to each sample point. Cost is O(bearings × steps²),
     * so keep both modest; call from a background thread and poll [cancelled].
     * Returns an empty list if cancelled.
     */
    fun compute(
        lat: Double, lon: Double, radiusM: Double, bearings: Int, steps: Int, params: RadioParams,
        elevation: (Double, Double) -> Double?,
        cancelled: () -> Boolean = { false },
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<CoverageCell> {
        require(bearings in 4..360 && steps in 2..200 && radiusM > 0)
        val cells = ArrayList<CoverageCell>(bearings * steps)
        for (b in 0 until bearings) {
            if (cancelled()) return emptyList()
            val bearing = 360.0 * b / bearings
            val ray = (0..steps).map { s ->
                val d = radiusM * s / steps
                val (la, lo) = if (s == 0) lat to lon else RadioLink.destination(lat, lon, bearing, d)
                TerrainPoint(d, elevation(la, lo)) to (la to lo)
            }
            for (s in 2..steps) {
                val profile = ray.subList(0, s + 1).map { it.first }
                val (la, lo) = ray[s].second
                val result = RadioLink.analyse(profile, params)
                cells += CoverageCell(
                    la, lo, profile.last().distanceM,
                    if (result.quality == Quality.UNKNOWN) null else result.marginDb,
                    result.quality,
                )
            }
            onProgress(b + 1, bearings)
        }
        return cells
    }
}
