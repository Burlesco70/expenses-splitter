package com.pbg.expensessplitter.core

import java.time.LocalDateTime
import java.time.ZoneId

private val MONTHS = mapOf(
    "gen" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "mag" to 5, "giu" to 6,
    "lug" to 7, "ago" to 8, "set" to 9, "ott" to 10, "nov" to 11, "dic" to 12,
    "jan" to 1, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10,
)

private val LEGACY_DATE = Regex("""^\s*(\d{1,2})\s+(\p{L}+)\.?\s+(\d{4})(?:\s+(\d{1,2}):(\d{2}))?""")

/** Legacy `dateadded` text like "09 mag 2017 19:00". Returns epoch millis or null if unparsable. */
fun parseLegacyDate(text: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
    if (text.isNullOrBlank()) return null
    val m = LEGACY_DATE.find(text) ?: return null
    val (d, mon, y, h, mi) = m.destructured
    val month = MONTHS[mon.lowercase().take(3)] ?: return null
    return runCatching {
        LocalDateTime.of(y.toInt(), month, d.toInt(), h.ifEmpty { "12" }.toInt(), mi.ifEmpty { "0" }.toInt())
            .atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()
}

fun isLegacyTransferName(name: String): Boolean {
    val n = name.trim().lowercase()
    return n == "transfer" || n == "_moneytransfer_"
}
