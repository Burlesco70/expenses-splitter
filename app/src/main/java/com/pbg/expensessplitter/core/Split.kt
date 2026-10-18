package com.pbg.expensessplitter.core

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Distributes [total] proportionally to [weights] with the largest-remainder method. Sum is exact. */
fun allocate(total: Long, weights: List<Long>): List<Long> {
    val sum = weights.fold(BigInteger.ZERO) { a, w -> a + BigInteger.valueOf(w) }
    require(weights.isNotEmpty() && sum.signum() > 0 && weights.all { it >= 0 }) { "invalid weights" }
    val t = BigInteger.valueOf(total)
    val base = LongArray(weights.size)
    val rem = Array(weights.size) { BigInteger.ZERO }
    var used = 0L
    weights.forEachIndexed { i, w ->
        val (q, r) = (t * BigInteger.valueOf(w)).divideAndRemainder(sum)
        base[i] = q.toLong(); rem[i] = r; used += base[i]
    }
    var left = total - used
    val order = weights.indices.sortedWith(compareByDescending<Int> { rem[it] }.thenBy { it })
    for (i in order) { if (left <= 0L) break; base[i] += 1; left-- }
    return base.toList()
}

fun toBaseCents(amountCents: Long, rate: Double): Long =
    BigDecimal(amountCents).multiply(BigDecimal.valueOf(rate)).setScale(0, RoundingMode.HALF_UP).toLong()

const val PERCENT_TOTAL = 10_000L // basis points

/**
 * Per-participant shares in trip currency, or null when input is invalid
 * (no participants, percentages not summing to 100%, fixed amounts not summing to the total).
 */
fun computeShares(
    rule: SplitRule,
    amountCents: Long,
    baseCents: Long,
    participants: List<Long>,
    values: Map<Long, Long>,
): Map<Long, Long>? {
    if (participants.isEmpty() || amountCents <= 0) return null
    val weights: List<Long> = when (rule) {
        SplitRule.EQUAL -> participants.map { 1L }
        SplitRule.PERCENT -> participants.map { values[it] ?: 0L }.also { w ->
            if (w.sum() != PERCENT_TOTAL || w.any { it < 0 }) return null
        }
        SplitRule.FIXED -> participants.map { values[it] ?: 0L }.also { w ->
            if (w.sum() != amountCents || w.any { it < 0 }) return null
        }
    }
    val parts = allocate(baseCents, weights)
    return participants.zip(parts).toMap()
}

/** "12,50" / "12.5" -> 1250. Returns null if not a valid non-negative amount with at most 2 decimals. */
fun parseCents(text: String): Long? {
    val s = text.trim().replace(',', '.')
    if (s.isEmpty()) return null
    val d = s.toBigDecimalOrNull() ?: return null
    if (d.signum() < 0 || d.scale() > 2 && d.stripTrailingZeros().scale() > 2) return null
    return d.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
}

/** "33,33" percent -> 3333 basis points. */
fun parsePercentBp(text: String): Long? = parseCents(text)

fun formatCents(cents: Long, locale: java.util.Locale = java.util.Locale.ROOT, grouping: Boolean = false): String {
    val sign = if (cents < 0) "-" else ""
    val a = Math.abs(cents)
    val whole = a / 100
    val frac = (a % 100).toInt()
    val sym = java.text.DecimalFormatSymbols.getInstance(locale)
    val w = if (grouping) java.text.NumberFormat.getIntegerInstance(locale).format(whole) else whole.toString()
    return "$sign$w${sym.decimalSeparator}${frac.toString().padStart(2, '0')}"
}
