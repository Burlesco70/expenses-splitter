package com.pbg.expensessplitter

import com.pbg.expensessplitter.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.util.Random

class CoreTest {
    @Test fun allocateKeepsTotal() {
        assertEquals(listOf(34L, 33L, 33L), allocate(100, listOf(1, 1, 1)))
        val r = Random(1)
        repeat(500) {
            val w = List(1 + r.nextInt(8)) { 1L + r.nextInt(1000) }
            val t = r.nextInt(1_000_000).toLong()
            assertEquals(t, allocate(t, w).sum())
        }
    }

    @Test fun sharesRules() {
        val p = listOf(1L, 2L, 3L)
        assertEquals(mapOf(1L to 34L, 2L to 33L, 3L to 33L), computeShares(SplitRule.EQUAL, 100, 100, p, emptyMap()))
        assertNull(computeShares(SplitRule.PERCENT, 100, 100, p, mapOf(1L to 5000, 2L to 3000, 3L to 1000)))
        assertEquals(mapOf(1L to 50L, 2L to 30L, 3L to 20L),
            computeShares(SplitRule.PERCENT, 100, 100, p, mapOf(1L to 5000, 2L to 3000, 3L to 2000)))
        assertNull(computeShares(SplitRule.FIXED, 100, 100, p, mapOf(1L to 50, 2L to 30, 3L to 10)))
        // foreign currency: fixed amounts in original currency are scaled to the converted total
        val s = computeShares(SplitRule.FIXED, 1000, 140, p, mapOf(1L to 500, 2L to 300, 3L to 200))!!
        assertEquals(140L, s.values.sum())
        assertEquals(70L, s[1L])
    }

    @Test fun parsing() {
        assertEquals(1250L, parseCents("12,5"))
        assertEquals(1250L, parseCents("12.50"))
        assertNull(parseCents("12.505"))
        assertNull(parseCents("abc"))
        assertNull(parseCents("-1"))
        assertEquals("12,50", formatCents(1250, java.util.Locale.ITALY))
        assertEquals("-0.05", formatCents(-5))
        assertEquals(1400L, toBaseCents(10000, 0.14))
    }

    @Test fun legacyDate() {
        val z = ZoneId.of("UTC")
        assertEquals(1494356400000L, parseLegacyDate("09 mag 2017 19:00", z))
        assertNull(parseLegacyDate("garbage", z))
        assertNull(parseLegacyDate("31 feb 2017 10:00", z))
    }

    private fun check(bal: Map<Long, Long>, expectedMax: Int? = null): List<Transfer> {
        val t = settle(bal)
        val net = HashMap(bal)
        t.forEach { net[it.from] = (net[it.from] ?: 0) + it.cents; net[it.to] = (net[it.to] ?: 0) - it.cents }
        assertTrue("all settled: $net", net.values.all { it == 0L })
        assertTrue(t.all { it.cents > 0 })
        if (expectedMax != null) assertEquals(expectedMax, t.size)
        return t
    }

    @Test fun settleMinimizes() {
        // two independent pairs: greedy would do 3 transfers, optimum is 2
        check(mapOf(1L to 50, 2L to -50, 3L to 30, 4L to -30), 2)
        check(mapOf(1L to 10, 2L to -3, 3L to -7), 2)
        check(mapOf(1L to 0, 2L to 0), 0)
        check(emptyMap(), 0)
    }

    @Test fun settleRandomNeverWorseThanGreedy() {
        val r = Random(42)
        repeat(300) {
            val n = 2 + r.nextInt(10)
            val v = MutableList(n) { (r.nextInt(2001) - 1000).toLong() }
            v[n - 1] -= v.sum()
            val bal = v.mapIndexed { i, x -> i.toLong() to x }.toMap()
            val t = check(bal)
            assertTrue(t.size <= bal.count { it.value != 0L } - 1 || t.isEmpty())
        }
    }

    private val labels = ReportLabels(
        "Report", "Trip", "Currency", "Expenses", "Balances", "Settle", "Paid", "Owed", "Balance", "Total",
        "pays", "All settled", "Payer", "Date", "Description", "Category", "Amount", "Rate", "Amount in", "Type",
        "Split", "Participants", "Expense", "Payment", "From", "To",
        SplitRule.entries.associateWith { it.name }, CATEGORIES.associateWith { it }, "credit",
    )

    private fun trip(): TripData {
        val persons = listOf(PersonData(1, "Anna"), PersonData(2, "Bob; \"B\""), PersonData(3, "Carla"))
        val e1 = ExpenseData(1, "Hotel, notte", 1, 9000, "EUR", 1.0, 9000, "lodging", 1_700_000_000_000, Kind.EXPENSE,
            SplitRule.EQUAL, mapOf(1L to 3000L, 2L to 3000L, 3L to 3000L))
        val e2 = ExpenseData(2, "Pay", 2, 1000, "EUR", 1.0, 1000, "other", 1_700_000_100_000, Kind.TRANSFER,
            SplitRule.EQUAL, mapOf(1L to 1000L))
        return TripData(1, "Grecia", "", "EUR", persons, listOf(e1, e2))
    }

    @Test fun balancesAndReports() {
        val t = trip()
        val b = computeBalances(t.persons, t.expenses)
        assertEquals(0L, b.values.sum())
        assertEquals(mapOf(1L to 5000L, 2L to -2000L, 3L to -3000L), b)
        val txt = buildTxtReport(t, labels, java.util.Locale.ITALY)
        assertTrue(txt.contains("Carla pays Anna: 30,00 EUR"))
        val csv = buildCsvReport(t, labels)
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"Bob; \"\"B\"\"\""))
        assertTrue(csv.contains("[Settle]"))
    }

    @Test fun jsonRoundTrip() {
        val t = trip()
        val back = tripsFromJson(tripsToJson(listOf(t), 1L))
        assertEquals(listOf(t), back)
        assertThrows(BackupException::class.java) { tripsFromJson("{}") }
        assertThrows(BackupException::class.java) { tripsFromJson("nope") }
        val bad = tripsToJson(listOf(t), 1L).replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        assertThrows(BackupException::class.java) { tripsFromJson(bad) }
    }
}
