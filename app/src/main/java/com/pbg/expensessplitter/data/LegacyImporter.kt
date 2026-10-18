package com.pbg.expensessplitter.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.pbg.expensessplitter.core.*
import java.io.File
import kotlin.math.roundToLong

data class LegacyResult(val trips: List<TripData>, val skippedExpenses: Int)

/** Reads a database file of the original "Expense Splitter" app (tbl_trips/tbl_persons/tbl_expenses/tbl_splitters). */
object LegacyImporter {
    fun read(ctx: Context, uri: Uri): LegacyResult {
        val tmp = File(ctx.cacheDir, "legacy_${System.nanoTime()}.db")
        try {
            ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            val db = try {
                SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: Exception) { throw BackupException("not a SQLite database") }
            db.use { return convert(it) }
        } finally { tmp.delete() }
    }

    internal fun convert(db: SQLiteDatabase): LegacyResult {
        val names = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
            generateSequence { if (c.moveToNext()) c.getString(0) else null }.toSet()
        }
        if (!names.containsAll(listOf("tbl_trips", "tbl_persons", "tbl_expenses", "tbl_splitters")))
            throw BackupException("not an Expense Splitter database")

        val splitters = HashMap<Long, LinkedHashSet<Long>>()
        db.rawQuery("SELECT expenseid, splitterid FROM tbl_splitters ORDER BY rowid", null).use {
            while (it.moveToNext()) splitters.getOrPut(it.getLong(0)) { LinkedHashSet() }.add(it.getLong(1))
        }
        var skipped = 0
        val trips = ArrayList<TripData>()
        db.rawQuery("SELECT _id, trip_name, trip_desc, trip_currency FROM tbl_trips ORDER BY _id", null).use { tc ->
            while (tc.moveToNext()) {
                val tid = tc.getLong(0)
                val currency = tc.getString(3)?.takeIf { it.isNotBlank() } ?: "EUR"
                val persons = ArrayList<PersonData>()
                db.rawQuery("SELECT _id, name, email, phone FROM tbl_persons WHERE tripid = ? ORDER BY _id", arrayOf("$tid")).use { c ->
                    while (c.moveToNext()) persons += PersonData(c.getLong(0), c.getString(1) ?: "?", c.getString(2) ?: "", c.getString(3) ?: "")
                }
                val ids = persons.map { it.id }.toSet()
                val expenses = ArrayList<ExpenseData>()
                db.rawQuery(
                    "SELECT _id, name, payerid, amount, currency, currencyfactor, dateadded FROM tbl_expenses WHERE tripid = ? ORDER BY _id",
                    arrayOf("$tid"),
                ).use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val title = (c.getString(1) ?: "").trim().ifEmpty { "-" }
                        val payer = c.getLong(2)
                        val cents = (c.getDouble(3) * 100).roundToLong()
                        val cur = c.getString(4)?.takeIf { it.isNotBlank() } ?: currency
                        val factor = if (cur == currency) 1.0 else c.getDouble(5).takeIf { it > 0 } ?: 1.0
                        val date = parseLegacyDate(c.getString(6)) ?: 0L
                        val sp = splitters[id].orEmpty().filter { it in ids }
                        if (payer !in ids || cents <= 0 || sp.isEmpty()) { skipped++; continue }
                        val base = toBaseCents(cents, factor)
                        val receiver = sp.firstOrNull { it != payer }
                        if (isLegacyTransferName(title) && receiver != null) {
                            expenses += ExpenseData(id, title, payer, cents, cur, factor, base, "other", date,
                                Kind.TRANSFER, SplitRule.EQUAL, mapOf(receiver to base))
                        } else {
                            val shares = computeShares(SplitRule.EQUAL, cents, base, sp, emptyMap()) ?: run { skipped++; null } ?: continue
                            expenses += ExpenseData(id, title, payer, cents, cur, factor, base, "other", date,
                                Kind.EXPENSE, SplitRule.EQUAL, shares)
                        }
                    }
                }
                trips += TripData(tid, (tc.getString(1) ?: "").trim().ifEmpty { "-" }, tc.getString(2) ?: "", currency, persons, expenses)
            }
        }
        return LegacyResult(trips, skipped)
    }
}
