package com.pbg.expensessplitter.core

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportLabels(
    val report: String, val trip: String, val currency: String, val expenses: String, val balances: String,
    val settle: String, val paid: String, val owed: String, val balance: String, val total: String,
    val pays: String, val allSettled: String, val payer: String, val date: String, val description: String,
    val category: String, val amount: String, val rate: String, val amountIn: String, val type: String,
    val split: String, val participants: String, val expense: String, val payment: String,
    val from: String, val to: String,
    val rules: Map<SplitRule, String>, val categories: Map<String, String>, val credit: String,
)

private fun name(persons: List<PersonData>, id: Long) = persons.firstOrNull { it.id == id }?.name ?: "?"

fun buildTxtReport(trip: TripData, l: ReportLabels, locale: Locale = Locale.getDefault()): String {
    val sb = StringBuilder()
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    fun m(c: Long) = formatCents(c, locale, grouping = true)
    sb.appendLine("${l.report}: ${trip.name}")
    if (trip.description.isNotBlank()) sb.appendLine(trip.description)
    sb.appendLine("${l.currency}: ${trip.currency}")
    sb.appendLine()
    val bal = computeBalances(trip.persons, trip.expenses)
    sb.appendLine("== ${l.settle} ==")
    val tr = settle(bal)
    if (tr.isEmpty()) sb.appendLine(l.allSettled)
    tr.forEach {
        sb.appendLine("${name(trip.persons, it.from)} ${l.pays} ${name(trip.persons, it.to)}: ${m(it.cents)} ${trip.currency}")
    }
    sb.appendLine()
    sb.appendLine("== ${l.balances} ==")
    trip.persons.forEach {
        sb.appendLine(
            "${it.name}: ${l.paid} ${m(totalPaid(trip.expenses, it.id))}, ${l.owed} ${m(totalOwed(trip.expenses, it.id))}, " +
                "${l.balance} ${m(bal[it.id] ?: 0)} ${trip.currency}"
        )
    }
    sb.appendLine()
    sb.appendLine("== ${l.expenses} ==")
    trip.expenses.sortedBy { it.dateMillis }.forEach { e ->
        val kind = if (e.kind == Kind.TRANSFER) "[${l.payment}] " else ""
        val foreign = if (e.currency != trip.currency) " (${m(e.amountCents)} ${e.currency} @ ${e.rate})" else ""
        sb.appendLine(
            "${df.format(Date(e.dateMillis))} - $kind${e.title}: ${m(e.baseCents)} ${trip.currency}$foreign, " +
                "${l.payer} ${name(trip.persons, e.payerId)}"
        )
        sb.appendLine("    " + e.shares.entries.joinToString(", ") { "${name(trip.persons, it.key)} ${m(it.value)}" })
    }
    sb.appendLine()
    val total = trip.expenses.filter { it.kind == Kind.EXPENSE }.sumOf { it.baseCents }
    sb.appendLine("${l.total}: ${m(total)} ${trip.currency}")
    sb.appendLine()
    sb.appendLine(l.credit)
    return sb.toString()
}

private fun csv(v: String): String =
    if (v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

private fun row(vararg v: String) = v.joinToString(";") { csv(it) } + "\r\n"

/** Semicolon-separated (opens directly in Italian Excel), UTF-8 with BOM, dot as decimal separator. */
fun buildCsvReport(trip: TripData, l: ReportLabels): String {
    val sb = StringBuilder("\uFEFF")
    val iso = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
    fun m(c: Long) = formatCents(c)
    sb.append(row(l.trip, trip.name, l.currency, trip.currency))
    sb.append("\r\n")
    sb.append(row("[${l.expenses}]"))
    sb.append(
        row(
            l.date, l.type, l.description, l.category, l.payer, l.amount, l.currency, l.rate,
            "${l.amountIn} ${trip.currency}", l.split, l.participants,
        )
    )
    trip.expenses.sortedBy { it.dateMillis }.forEach { e ->
        sb.append(
            row(
                iso.format(Date(e.dateMillis)),
                if (e.kind == Kind.TRANSFER) l.payment else l.expense,
                e.title,
                l.categories[e.category] ?: e.category,
                name(trip.persons, e.payerId),
                m(e.amountCents), e.currency, e.rate.toString(), m(e.baseCents),
                l.rules[e.rule] ?: e.rule.name,
                e.shares.entries.joinToString(" | ") { "${name(trip.persons, it.key)}=${m(it.value)}" },
            )
        )
    }
    sb.append("\r\n")
    sb.append(row("[${l.balances}]"))
    sb.append(row(l.participants, l.paid, l.owed, l.balance))
    val bal = computeBalances(trip.persons, trip.expenses)
    trip.persons.forEach {
        sb.append(row(it.name, m(totalPaid(trip.expenses, it.id)), m(totalOwed(trip.expenses, it.id)), m(bal[it.id] ?: 0)))
    }
    sb.append("\r\n")
    sb.append(row("[${l.settle}]"))
    sb.append(row(l.from, l.to, l.amount))
    settle(bal).forEach { sb.append(row(name(trip.persons, it.from), name(trip.persons, it.to), m(it.cents))) }
    return sb.toString()
}
