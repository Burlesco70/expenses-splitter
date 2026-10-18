package com.pbg.expensessplitter.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.pbg.expensessplitter.R
import com.pbg.expensessplitter.core.*
import java.io.File

fun categoryLabel(ctx: Context, key: String): String = when (key) {
    "food" -> ctx.getString(R.string.cat_food)
    "transport" -> ctx.getString(R.string.cat_transport)
    "lodging" -> ctx.getString(R.string.cat_lodging)
    "leisure" -> ctx.getString(R.string.cat_leisure)
    "shopping" -> ctx.getString(R.string.cat_shopping)
    else -> ctx.getString(R.string.cat_other)
}

fun ruleLabel(ctx: Context, r: SplitRule) = ctx.getString(
    when (r) { SplitRule.EQUAL -> R.string.rule_equal; SplitRule.FIXED -> R.string.rule_fixed; SplitRule.PERCENT -> R.string.rule_percent }
)

fun reportLabels(c: Context) = ReportLabels(
    report = c.getString(R.string.report), trip = c.getString(R.string.trip), currency = c.getString(R.string.currency),
    expenses = c.getString(R.string.expenses), balances = c.getString(R.string.balance), settle = c.getString(R.string.settle_suggestions),
    paid = c.getString(R.string.paid), owed = c.getString(R.string.owed), balance = c.getString(R.string.balance),
    total = c.getString(R.string.total), pays = c.getString(R.string.pays), allSettled = c.getString(R.string.all_settled),
    payer = c.getString(R.string.payer), date = c.getString(R.string.date), description = c.getString(R.string.description),
    category = c.getString(R.string.category), amount = c.getString(R.string.amount), rate = c.getString(R.string.rate),
    amountIn = c.getString(R.string.amount_in), type = c.getString(R.string.type), split = c.getString(R.string.split_rule),
    participants = c.getString(R.string.people), expense = c.getString(R.string.expense), payment = c.getString(R.string.payment),
    from = c.getString(R.string.payment_from), to = c.getString(R.string.payment_to),
    rules = SplitRule.entries.associateWith { ruleLabel(c, it) },
    categories = CATEGORIES.associateWith { categoryLabel(c, it) },
    credit = c.getString(R.string.report_credit),
)

/** Writes the report to cache and opens the system share sheet (chat, e-mail, Drive...). */
fun shareReport(ctx: Context, trip: TripData, csv: Boolean) {
    val labels = reportLabels(ctx)
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val safe = trip.name.replace(Regex("""[^\p{L}\p{N}._-]+"""), "_").trim('_').ifEmpty { "report" }
    val f = File(dir, "$safe.${if (csv) "csv" else "txt"}")
    f.writeText(if (csv) buildCsvReport(trip, labels) else buildTxtReport(trip, labels), Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (csv) "text/csv" else "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "${labels.report}: ${trip.name}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share)))
}
