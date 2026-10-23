package com.pbg.expensessplitter.core

data class Transfer(val from: Long, val to: Long, val cents: Long)

/** balance = paid - owed, in trip currency cents. Positive: is owed money. */
fun computeBalances(persons: List<PersonData>, expenses: List<ExpenseData>): Map<Long, Long> {
    val b = LinkedHashMap<Long, Long>()
    persons.forEach { b[it.id] = 0L }
    for (e in expenses) {
        b[e.payerId] = (b[e.payerId] ?: 0L) + e.baseCents
        for ((p, s) in e.shares) b[p] = (b[p] ?: 0L) - s
    }
    return b
}

fun totalPaid(expenses: List<ExpenseData>, personId: Long) =
    expenses.filter { it.payerId == personId }.sumOf { it.baseCents }

/** Amount spent on behalf of the person (their share of real expenses); settlement payments are excluded. */
fun totalOwed(expenses: List<ExpenseData>, personId: Long) =
    expenses.filter { it.kind == Kind.EXPENSE }.sumOf { it.shares[personId] ?: 0L }

/** Simple greedy: biggest debtor pays biggest creditor. At most (n-1) transfers for a zero-sum group. */
internal fun greedy(balances: Map<Long, Long>): List<Transfer> {
    val b = balances.filterValues { it != 0L }.toMutableMap()
    val out = ArrayList<Transfer>()
    while (b.isNotEmpty()) {
        val d = b.minByOrNull { it.value }!!
        val c = b.maxByOrNull { it.value }!!
        if (d.value >= 0 || c.value <= 0) break
        val x = minOf(-d.value, c.value)
        out += Transfer(d.key, c.key, x)
        b[d.key] = d.value + x; b[c.key] = c.value - x
        b.entries.removeAll { it.value == 0L }
    }
    return out
}

private const val EXACT_LIMIT = 20

/**
 * Minimum-transfer settlement: the optimum is (non-zero people) - (max number of disjoint zero-sum groups);
 * groups are found with a bitmask DP (exact up to [EXACT_LIMIT] people, greedy beyond).
 */
fun settle(balances: Map<Long, Long>): List<Transfer> {
    val ids = balances.filterValues { it != 0L }.keys.sorted()
    val n = ids.size
    if (n == 0) return emptyList()
    if (n > EXACT_LIMIT) return greedy(balances)
    val v = LongArray(n) { balances.getValue(ids[it]) }
    val full = (1 shl n) - 1
    val sum = LongArray(1 shl n)
    for (m in 1..full) { val low = Integer.numberOfTrailingZeros(m); sum[m] = sum[m and (m - 1)] + v[low] }
    val dp = IntArray(1 shl n)
    val from = IntArray(1 shl n)
    for (m in 1..full) {
        var best = -1; var bi = -1
        for (i in 0 until n) if (m and (1 shl i) != 0) {
            val d = dp[m xor (1 shl i)]
            if (d > best) { best = d; bi = i }
        }
        dp[m] = best + if (sum[m] == 0L) 1 else 0
        from[m] = bi
    }
    // Walk back to get insertion order, then cut at zero prefix sums.
    val order = ArrayList<Int>()
    var m = full
    while (m != 0) { order += from[m]; m = m xor (1 shl from[m]) }
    order.reverse()
    val out = ArrayList<Transfer>()
    var group = LinkedHashMap<Long, Long>()
    var mask = 0
    for (i in order) {
        mask = mask or (1 shl i)
        group[ids[i]] = v[i]
        if (sum[mask] == 0L) { out += greedy(group); group = LinkedHashMap() }
    }
    return out
}
