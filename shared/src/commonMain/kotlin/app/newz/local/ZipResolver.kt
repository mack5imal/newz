package app.newz.local

import app.newz.catalog.Catalog
import app.newz.catalog.CatalogLoader
import app.newz.catalog.Metro
import app.newz.catalog.ZipData
import kotlin.io.encoding.Base64
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** What a typed zip code resolves to (SPEC S29). */
sealed interface ZipResult {
    /** The zip lies in [metro]. */
    data class InMetro(
        val metro: Metro,
    ) : ZipResult

    /** The zip lies in no covered metro; [metro] is the closest one, [km] away. */
    data class Nearest(
        val metro: Metro,
        val km: Int,
    ) : ZipResult

    data class Invalid(
        val reason: ZipError,
    ) : ZipResult
}

enum class ZipError {
    /** The input is not exactly five ASCII digits. */
    NOT_FIVE_DIGITS,

    /** Five digits, but not a zip code in the bundled Census table. */
    NOT_FOUND,
}

/**
 * Resolves a zip code to a metro entirely on the device, from the Census table bundled at build
 * time: no network, no location permission, and the zip never leaves the phone (SPEC S29).
 */
class ZipResolver(
    private val catalog: Catalog = CatalogLoader.load(),
) {
    fun resolve(input: String): ZipResult {
        val zip = input.trim()
        if (zip.length != 5 || zip.any { it !in '0'..'9' }) return ZipResult.Invalid(ZipError.NOT_FIVE_DIGITS)
        val table = ZipTable.bundled
        val row = table.indexOf(zip.toInt())
        if (row < 0) return ZipResult.Invalid(ZipError.NOT_FOUND)
        table.metroId(row)?.let { id -> catalog.metro(id)?.let { return ZipResult.InMetro(it) } }
        return nearestMetro(table.lat(row), table.lon(row), catalog.metros)
            ?.let { (metro, km) -> ZipResult.Nearest(metro, km.roundToInt()) }
            ?: ZipResult.Invalid(ZipError.NOT_FOUND)
    }
}

/** The metro closest to the point by great-circle distance (km), ties going to the larger population. */
internal fun nearestMetro(
    lat: Double,
    lon: Double,
    metros: List<Metro>,
): Pair<Metro, Double>? =
    metros
        .map { it to haversineKm(lat, lon, it.lat, it.lon) }
        .minWithOrNull(compareBy<Pair<Metro, Double>> { it.second }.thenByDescending { it.first.population })

private const val EARTH_RADIUS_KM = 6371.0088

/** Great-circle distance in km between two points given in degrees. */
internal fun haversineKm(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val dLat = (lat2 - lat1).toRadians()
    val dLon = (lon2 - lon1).toRadians()
    val sinLat = sin(dLat / 2)
    val sinLon = sin(dLon / 2)
    val a = sinLat * sinLat + cos(lat1.toRadians()) * cos(lat2.toRadians()) * sinLon * sinLon
    return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
}

private fun Double.toRadians(): Double = this * kotlin.math.PI / 180.0

/** The decoded zip table: parallel arrays sorted by zip. See [ZipData] for the packed layout. */
internal class ZipTable(
    private val zips: IntArray,
    private val lats: IntArray,
    private val lons: IntArray,
    private val metros: ByteArray,
    private val metroIds: List<String>,
) {
    val size: Int get() = zips.size

    /** The row holding [zip], or -1. */
    fun indexOf(zip: Int): Int {
        var low = 0
        var high = zips.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val value = zips[mid]
            when {
                value < zip -> low = mid + 1
                value > zip -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    fun zip(row: Int): Int = zips[row]

    fun lat(row: Int): Double = lats[row] / SCALE

    fun lon(row: Int): Double = lons[row] / SCALE

    fun metroId(row: Int): String? = metros[row].toInt().takeIf { it >= 0 }?.let { metroIds[it] }

    companion object {
        private const val SCALE = 10_000.0

        /** Decoded on first use, then kept. */
        val bundled: ZipTable by lazy { decode() }

        /** Decodes [ZipData] afresh. */
        fun decode(): ZipTable {
            val bytes = ByteArray(ZipData.COUNT * ZipData.RECORD_BYTES)
            var offset = 0
            for (chunk in ZipData.chunks()) {
                offset += Base64.decodeIntoByteArray(chunk, bytes, offset)
            }
            check(offset == bytes.size) { "zip table decoded to $offset bytes, expected ${bytes.size}" }
            val zips = IntArray(ZipData.COUNT)
            val lats = IntArray(ZipData.COUNT)
            val lons = IntArray(ZipData.COUNT)
            val metros = ByteArray(ZipData.COUNT)
            for (row in 0 until ZipData.COUNT) {
                val at = row * ZipData.RECORD_BYTES
                zips[row] = bytes.intAt(at)
                lats[row] = bytes.intAt(at + 4)
                lons[row] = bytes.intAt(at + 8)
                metros[row] = bytes[at + 12]
            }
            return ZipTable(zips, lats, lons, metros, ZipData.METRO_IDS)
        }

        private fun ByteArray.intAt(at: Int): Int =
            (this[at].toInt() and 0xFF shl 24) or
                (this[at + 1].toInt() and 0xFF shl 16) or
                (this[at + 2].toInt() and 0xFF shl 8) or
                (this[at + 3].toInt() and 0xFF)
    }
}
