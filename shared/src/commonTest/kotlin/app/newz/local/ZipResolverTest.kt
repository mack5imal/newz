package app.newz.local

import app.newz.catalog.CatalogLoader
import app.newz.catalog.Metro
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ZipResolverTest {
    private val catalog = CatalogLoader.load()
    private val resolver = ZipResolver(catalog)

    private fun metro(cbsa: String): Metro = catalog.metros.single { it.cbsa == cbsa }

    @Test
    fun downtownZipsResolveToTheirMetro() {
        assertEquals(ZipResult.InMetro(metro("42660")), resolver.resolve("98101"))
        assertEquals("Seattle", metro("42660").name)
        assertEquals(ZipResult.InMetro(metro("16980")), resolver.resolve("60601"))
        assertEquals("Chicago", metro("16980").name)
    }

    @Test
    fun leadingZeroIsKept() {
        assertEquals(ZipResult.InMetro(metro("14460")), resolver.resolve("02108"))
        assertEquals("Boston", metro("14460").name)
        assertEquals(ZipResult.InMetro(metro("14460")), resolver.resolve("  02108\n"))
    }

    @Test
    fun zipOutsideEveryMetroResolvesToTheNearestByHaversine() {
        // Missoula, MT: the Census 2026 Gazetteer internal point of ZCTA 59801.
        val lat = 46.8547
        val lon = -114.0104

        fun km(m: Metro): Double {
            val r = 6371.0088
            val p1 = lat * PI / 180
            val p2 = m.lat * PI / 180
            val dp = (m.lat - lat) * PI / 180
            val dl = (m.lon - lon) * PI / 180
            val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * r * asin(sqrt(h))
        }
        val expected = catalog.metros.sortedWith(compareBy<Metro> { km(it) }.thenByDescending { it.population }).first()

        val result = assertIs<ZipResult.Nearest>(resolver.resolve("59801"))
        assertEquals(expected, result.metro)
        assertEquals(km(expected).roundToInt(), result.km)
        assertTrue(result.km > 100, "Missoula is far from every covered metro: ${result.km} km")
    }

    @Test
    fun malformedInputIsNotFiveDigits() {
        for (input in listOf("9810", "981011", "abcde", "", "   ", "98 01", "9810a", "٩٨١٠١")) {
            assertEquals(ZipResult.Invalid(ZipError.NOT_FIVE_DIGITS), resolver.resolve(input), "'$input'")
        }
    }

    @Test
    fun unknownZipIsNotFound() {
        assertEquals(ZipResult.Invalid(ZipError.NOT_FOUND), resolver.resolve("00000"))
        assertEquals(ZipResult.Invalid(ZipError.NOT_FOUND), resolver.resolve("99999"))
    }

    @Test
    fun tiesGoToTheLargerMetro() {
        val small = Metro("small", "00001", "Small", 1_000, 10.0, 10.0)
        val large = Metro("large", "00002", "Large", 2_000, -10.0, 10.0)
        assertEquals(large, nearestMetro(0.0, 10.0, listOf(small, large))?.first)
        assertEquals(large, nearestMetro(0.0, 10.0, listOf(large, small))?.first)
        assertEquals(small, nearestMetro(1.0, 10.0, listOf(large, small))?.first)
    }

    @Test
    fun bundledTableIsSortedCompleteAndDecodesQuickly() {
        val start = TimeSource.Monotonic.markNow()
        val table = ZipTable.decode()
        val elapsed = start.elapsedNow()
        assertTrue(elapsed < 2.seconds, "decoding the zip table took $elapsed")
        assertTrue(table.size > 33_000, "zip rows: ${table.size}")
        for (row in 1 until table.size) assertTrue(table.zip(row - 1) < table.zip(row), "unsorted at $row")
        val metroIds = catalog.metros.map { it.id }.toSet()
        val covered = (0 until table.size).mapNotNull { table.metroId(it) }.toSet()
        assertEquals(metroIds, covered, "every metro has zips, and every zip metro is a catalog metro")
        val row = table.indexOf(98101)
        assertEquals(47.6109, table.lat(row), 1e-9)
        assertEquals(-122.3364, table.lon(row), 1e-9)
    }
}
