package com.pbg.expensessplitter.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.pbg.expensessplitter.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PersonInUseException : Exception()

class Repo(private val ctx: Context) {
    private val db get() = AppDb.get(ctx)
    private val dao get() = db.dao()
    private val receiptsDir get() = File(ctx.filesDir, "receipts").apply { mkdirs() }

    val trips: Flow<List<TripE>> get() = dao.trips()

    private fun map(e: ExpenseWithShares) = ExpenseData(
        e.expense.id, e.expense.title, e.expense.payerId, e.expense.amountCents, e.expense.currency, e.expense.rate,
        e.expense.baseCents, e.expense.category, e.expense.dateMillis, Kind.valueOf(e.expense.kind),
        SplitRule.valueOf(e.expense.rule), e.shares.associate { it.personId to it.shareCents },
        e.shares.filter { it.value != null }.associate { it.personId to it.value!! }, e.expense.receiptPath,
    )

    fun trip(id: Long): Flow<TripData?> =
        combine(dao.trip(id), dao.persons(id), dao.expenses(id)) { t, p, e ->
            t?.let {
                TripData(it.id, it.name, it.description, it.currency,
                    p.map { x -> PersonData(x.id, x.name, x.email, x.phone) }, e.map(::map))
            }
        }

    suspend fun addTrip(name: String, desc: String, currency: String): Long =
        dao.insert(TripE(name = name.trim(), description = desc.trim(), currency = currency, createdAt = System.currentTimeMillis()))

    suspend fun updateTrip(t: TripE) = dao.update(t)
    suspend fun tripEntity(id: Long) = dao.tripsOnce().firstOrNull { it.id == id }

    suspend fun deleteTrip(id: Long) = db.withTransaction {
        val files = dao.receiptPaths(id)
        dao.deleteTrip(id)
        files.forEach { File(it).delete() }
    }

    suspend fun savePerson(tripId: Long, id: Long?, name: String, email: String, phone: String) {
        if (id == null) dao.insert(PersonE(tripId = tripId, name = name.trim(), email = email.trim(), phone = phone.trim()))
        else dao.update(PersonE(id, tripId, name.trim(), email.trim(), phone.trim()))
    }

    suspend fun deletePerson(tripId: Long, id: Long, name: String) {
        if (dao.paidCount(id) > 0 || dao.shareCount(id) > 0) throw PersonInUseException()
        dao.delete(PersonE(id, tripId, name))
    }

    /** Insert or update an expense with its shares (already validated by core). */
    suspend fun saveExpense(tripId: Long, e: ExpenseData) = db.withTransaction {
        val entity = ExpenseE(
            if (e.id == 0L) 0 else e.id, tripId, e.title.trim(), e.payerId, e.amountCents, e.currency, e.rate, e.baseCents,
            e.category, e.dateMillis, e.kind.name, e.rule.name, e.receiptPath,
        )
        val id = if (e.id == 0L) dao.insert(entity) else {
            val old = dao.receiptOf(e.id)
            if (old != null && old != e.receiptPath) File(old).delete()
            dao.update(entity); dao.deleteShares(e.id); e.id
        }
        dao.insertShares(e.shares.map { (p, c) -> ShareE(id, p, c, e.values[p]) })
    }

    suspend fun deleteExpense(id: Long) = db.withTransaction {
        dao.receiptOf(id)?.let { File(it).delete() }
        dao.deleteExpense(id)
    }

    // ---- receipts ----
    fun newReceiptFile(): File = File(receiptsDir, "r_${UUID.randomUUID()}.jpg")

    suspend fun importReceipt(uri: Uri): String = withContext(Dispatchers.IO) {
        val f = newReceiptFile()
        ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        f.absolutePath
    }

    // ---- export / import of whole data ----
    suspend fun allTrips(): List<TripData> = withContext(Dispatchers.IO) {
        dao.tripsOnce().map { t ->
            TripData(t.id, t.name, t.description, t.currency,
                dao.personsOnce(t.id).map { PersonData(it.id, it.name, it.email, it.phone) },
                dao.expensesOnce(t.id).map(::map))
        }
    }

    /** Inserts trips as new rows, remapping ids. Invalid expenses are skipped; returns number of trips added. */
    suspend fun insertTrips(trips: List<TripData>): Int = db.withTransaction {
        var n = 0
        for (t in trips) {
            val tid = dao.insert(TripE(name = t.name, description = t.description, currency = t.currency, createdAt = System.currentTimeMillis() + n))
            val pmap = HashMap<Long, Long>()
            t.persons.forEach { pmap[it.id] = dao.insert(PersonE(tripId = tid, name = it.name, email = it.email, phone = it.phone)) }
            for (e in t.expenses) {
                val payer = pmap[e.payerId] ?: continue
                if (e.shares.keys.any { it !in pmap } || e.shares.isEmpty()) continue
                val eid = dao.insert(ExpenseE(0, tid, e.title, payer, e.amountCents, e.currency, e.rate, e.baseCents,
                    e.category, e.dateMillis, e.kind.name, e.rule.name, null))
                dao.insertShares(e.shares.map { (p, c) -> ShareE(eid, pmap.getValue(p), c, e.values[p]) })
            }
            n++
        }
        n
    }
}
