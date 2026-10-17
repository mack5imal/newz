package app.newz.feed

/** Byte decoding for the charsets feeds actually use: UTF-8, ISO-8859-1 and windows-1252. */
internal object Charsets {
    /** windows-1252 code points for bytes 0x80–0x9F; the five unassigned bytes map to themselves. */
    private val WINDOWS_1252_HIGH =
        intArrayOf(
            0x20AC,
            0x0081,
            0x201A,
            0x0192,
            0x201E,
            0x2026,
            0x2020,
            0x2021,
            0x02C6,
            0x2030,
            0x0160,
            0x2039,
            0x0152,
            0x008D,
            0x017D,
            0x008F,
            0x0090,
            0x2018,
            0x2019,
            0x201C,
            0x201D,
            0x2022,
            0x2013,
            0x2014,
            0x02DC,
            0x2122,
            0x0161,
            0x203A,
            0x0153,
            0x009D,
            0x017E,
            0x0178,
        )

    private val LATIN1_LABELS =
        setOf("iso-8859-1", "iso8859-1", "iso_8859-1", "latin1", "latin-1", "l1", "iso-ir-100", "cp819", "ibm819")
    private val WINDOWS_1252_LABELS = setOf("windows-1252", "cp1252", "x-cp1252", "cp-1252")

    fun windows1252HighChar(code: Int): Int = WINDOWS_1252_HIGH[code - 0x80]

    /** True when [label] names a charset this decoder supports. */
    fun isSupported(label: String): Boolean {
        val l = label.trim().lowercase()
        return l == "utf-8" || l == "utf8" || l in LATIN1_LABELS || l in WINDOWS_1252_LABELS
    }

    /**
     * Decodes [bytes] with [label]. ISO-8859-1 is decoded as windows-1252, as browsers do
     * (WHATWG Encoding), because feeds labelled Latin-1 routinely contain 1252 quotes.
     * Anything else, including UTF-8 and unknown labels, decodes as UTF-8.
     */
    fun decode(
        bytes: ByteArray,
        label: String?,
    ): String {
        val l = label?.trim()?.lowercase()
        if (l != null && (l in LATIN1_LABELS || l in WINDOWS_1252_LABELS)) return decodeWindows1252(bytes)
        val start =
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte()
            ) {
                3
            } else {
                0
            }
        return bytes.decodeToString(start, bytes.size)
    }

    private fun decodeWindows1252(bytes: ByteArray): String {
        val chars = CharArray(bytes.size)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            chars[i] = if (b in 0x80..0x9F) WINDOWS_1252_HIGH[b - 0x80].toChar() else b.toChar()
        }
        return chars.concatToString()
    }
}
