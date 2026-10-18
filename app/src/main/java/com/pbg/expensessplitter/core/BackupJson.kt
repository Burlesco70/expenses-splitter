package com.pbg.expensessplitter.core

import org.json.JSONArray
import org.json.JSONObject

const val BACKUP_SCHEMA = 1
const val BACKUP_FORMAT = "com.pbg.expensessplitter.backup"

class BackupException(message: String) : Exception(message)

fun tripsToJson(trips: List<TripData>, exportedAt: Long): String {
    val root = JSONObject()
    root.put("format", BACKUP_FORMAT)
    root.put("schemaVersion", BACKUP_SCHEMA)
    root.put("exportedAt", exportedAt)
    val ta = JSONArray()
    for (t in trips) {
        val to = JSONObject()
        to.put("id", t.id).put("name", t.name).put("description", t.description).put("currency", t.currency)
        val pa = JSONArray()
        t.persons.forEach {
            pa.put(JSONObject().put("id", it.id).put("name", it.name).put("email", it.email).put("phone", it.phone))
        }
        to.put("persons", pa)
        val ea = JSONArray()
        t.expenses.forEach { e ->
            val sa = JSONArray()
            e.shares.forEach { (p, c) ->
                sa.put(JSONObject().put("personId", p).put("cents", c).put("value", e.values[p] ?: JSONObject.NULL))
            }
            ea.put(
                JSONObject()
                    .put("id", e.id).put("title", e.title).put("payerId", e.payerId)
                    .put("amountCents", e.amountCents).put("currency", e.currency).put("rate", e.rate)
                    .put("baseCents", e.baseCents).put("category", e.category).put("dateMillis", e.dateMillis)
                    .put("kind", e.kind.name).put("rule", e.rule.name).put("hasReceipt", e.receiptPath != null)
                    .put("shares", sa)
            )
        }
        to.put("expenses", ea)
        ta.put(to)
    }
    root.put("trips", ta)
    return root.toString(2)
}

fun tripsFromJson(text: String): List<TripData> {
    val root = try { JSONObject(text) } catch (e: Exception) { throw BackupException("not a JSON backup") }
    if (root.optString("format") != BACKUP_FORMAT) throw BackupException("unknown format")
    val ver = root.optInt("schemaVersion", -1)
    if (ver < 1 || ver > BACKUP_SCHEMA) throw BackupException("unsupported schemaVersion $ver")
    try {
        val out = ArrayList<TripData>()
        val ta = root.getJSONArray("trips")
        for (i in 0 until ta.length()) {
            val t = ta.getJSONObject(i)
            val pa = t.getJSONArray("persons")
            val persons = (0 until pa.length()).map { pa.getJSONObject(it) }.map {
                PersonData(it.getLong("id"), it.getString("name"), it.optString("email"), it.optString("phone"))
            }
            val ids = persons.map { it.id }.toSet()
            val ea = t.getJSONArray("expenses")
            val expenses = (0 until ea.length()).map { ea.getJSONObject(it) }.map { e ->
                val sa = e.getJSONArray("shares")
                val shares = LinkedHashMap<Long, Long>()
                val values = LinkedHashMap<Long, Long>()
                for (k in 0 until sa.length()) {
                    val s = sa.getJSONObject(k)
                    val p = s.getLong("personId")
                    if (p !in ids) throw BackupException("share references unknown person")
                    shares[p] = s.getLong("cents")
                    if (!s.isNull("value")) values[p] = s.getLong("value")
                }
                val payer = e.getLong("payerId")
                if (payer !in ids) throw BackupException("expense references unknown payer")
                val base = e.getLong("baseCents")
                if (shares.values.sum() != base) throw BackupException("shares do not sum to expense total")
                ExpenseData(
                    e.getLong("id"), e.getString("title"), payer, e.getLong("amountCents"), e.getString("currency"),
                    e.getDouble("rate"), base, e.getString("category"), e.getLong("dateMillis"),
                    Kind.valueOf(e.getString("kind")), SplitRule.valueOf(e.getString("rule")), shares, values, null,
                )
            }
            out += TripData(
                t.getLong("id"), t.getString("name"), t.optString("description"), t.getString("currency"),
                persons, expenses,
            )
        }
        return out
    } catch (e: BackupException) {
        throw e
    } catch (e: Exception) {
        throw BackupException("corrupted backup: ${e.message}")
    }
}
