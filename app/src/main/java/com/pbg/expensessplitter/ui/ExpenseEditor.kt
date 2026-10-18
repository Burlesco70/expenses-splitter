@file:OptIn(ExperimentalMaterial3Api::class)

package com.pbg.expensessplitter.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pbg.expensessplitter.R
import com.pbg.expensessplitter.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date

private val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

private fun parseRate(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

@Composable
fun ExpenseEditor(vm: AppViewModel, trip: TripData, existing: ExpenseData?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onClose() }

    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var amountText by rememberSaveable { mutableStateOf(existing?.let { formatCents(it.amountCents) } ?: "") }
    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: trip.currency) }
    var rateText by rememberSaveable { mutableStateOf(existing?.rate?.let { if (it == 1.0) "1" else it.toString() } ?: "1") }
    var payer by rememberSaveable { mutableLongStateOf(existing?.payerId ?: trip.persons.first().id) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "other") }
    var rule by rememberSaveable { mutableStateOf(existing?.rule ?: SplitRule.EQUAL) }
    var dateMillis by rememberSaveable { mutableLongStateOf(existing?.dateMillis ?: System.currentTimeMillis()) }
    var receipt by rememberSaveable { mutableStateOf(existing?.receiptPath) }
    var selected by rememberSaveable {
        mutableStateOf<List<Long>>(ArrayList(existing?.shares?.keys ?: trip.persons.map { it.id }))
    }
    var values by rememberSaveable {
        mutableStateOf<Map<String, String>>(HashMap(existing?.values?.entries?.associate { it.key.toString() to formatCents(it.value) } ?: emptyMap()))
    }
    var error by remember { mutableStateOf<Int?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { vm.importReceipt(uri)?.let { receipt = it } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCamera
        if (ok && path != null) receipt = path else path?.let { File(it).delete() }
        pendingCamera = null
    }

    val amountCents = parseCents(amountText)
    val rate = if (currency == trip.currency) 1.0 else parseRate(rateText)
    val base = if (amountCents != null && rate != null) toBaseCents(amountCents, rate) else null
    val part = trip.persons.filter { it.id in selected }.map { it.id }

    fun valueOf(id: Long) = values[id.toString()]?.let { parseCents(it) }

    val sumValues = part.sumOf { valueOf(it) ?: 0L }
    val target = if (rule == SplitRule.PERCENT) PERCENT_TOTAL else amountCents ?: 0L

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(if (existing == null) R.string.new_expense else R.string.edit_expense)) },
            navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { if (existing != null) IconButton({ confirmDel = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.description)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amountText, { amountText = it }, label = { Text(stringResource(R.string.amount)) }, singleLine = true,
                    keyboardOptions = decimal, modifier = Modifier.weight(1f))
                Box(Modifier.weight(1f).padding(top = 8.dp)) {
                    Dropdown(stringResource(R.string.currency), currency, currencyOptions(trip.currency)) { currency = it }
                }
            }
            if (currency != trip.currency) {
                OutlinedTextField(rateText, { rateText = it }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.rate_label, currency, trip.currency)) },
                    supportingText = { if (base != null) Text("= ${money(base, trip.currency)}") })
            }
            Dropdown(stringResource(R.string.payer), payer.toString(), trip.persons.map { it.id.toString() },
                display = { id -> trip.persons.firstOrNull { it.id == id.toLong() }?.name ?: "?" }) { payer = it.toLong() }
            Dropdown(stringResource(R.string.category), category, CATEGORIES, display = { categoryLabel(ctx, it) }) { category = it }
            OutlinedButton({ showDate = true }, Modifier.fillMaxWidth()) {
                Text("${stringResource(R.string.date)}: ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dateMillis))}")
            }

            Text(stringResource(R.string.split_rule), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SplitRule.entries.forEach { r ->
                    FilterChip(rule == r, { rule = r }, label = { Text(ruleLabel(ctx, r)) })
                }
            }
            Text(stringResource(R.string.participants), style = MaterialTheme.typography.titleSmall)
            trip.persons.forEach { p ->
                val on = p.id in selected
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(on, { c -> selected = if (c) selected + p.id else selected - p.id })
                    Text(p.name, Modifier.weight(1f).clickable { selected = if (on) selected - p.id else selected + p.id })
                    if (rule != SplitRule.EQUAL && on) {
                        OutlinedTextField(
                            values[p.id.toString()] ?: "", { values = values + (p.id.toString() to it) }, singleLine = true,
                            keyboardOptions = decimal, modifier = Modifier.width(120.dp),
                            suffix = { Text(if (rule == SplitRule.PERCENT) "%" else currency) },
                        )
                    }
                }
            }
            if (rule != SplitRule.EQUAL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val diff = target - sumValues
                    Text(
                        "${stringResource(R.string.remaining)}: ${formatCents(diff)}${if (rule == SplitRule.PERCENT) " %" else " $currency"}",
                        color = if (diff == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton({
                        if (part.isNotEmpty()) {
                            val dist = allocate(target, part.map { 1L })
                            values = values + part.zip(dist).associate { (id, v) -> id.toString() to formatCents(v) }
                        }
                    }) { Text(stringResource(R.string.distribute_equally)) }
                }
            }

            Text(stringResource(R.string.receipt), style = MaterialTheme.typography.titleSmall)
            receipt?.let { path ->
                val bmp by produceState<Bitmap?>(null, path) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull()
                    }
                }
                bmp?.let {
                    Image(it.asImageBitmap(), null, Modifier.heightIn(max = 220.dp).fillMaxWidth().clickable {
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(path))
                        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ gallery.launch("image/*") }) { Text(stringResource(R.string.receipt_gallery)) }
                OutlinedButton({
                    val f = vm.repo.newReceiptFile()
                    pendingCamera = f.absolutePath
                    camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
                }) { Text(stringResource(R.string.receipt_camera)) }
                if (receipt != null) TextButton({ receipt = null }) { Text(stringResource(R.string.receipt_remove)) }
            }

            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button({
                val a = amountCents
                when {
                    title.isBlank() -> error = R.string.error_title
                    a == null || a <= 0 -> error = R.string.error_amount
                    rate == null -> error = R.string.error_rate
                    part.isEmpty() -> error = R.string.error_participants
                    else -> {
                        val b = toBaseCents(a, rate)
                        val vals = if (rule == SplitRule.EQUAL) emptyMap() else part.associateWith { valueOf(it) ?: 0L }
                        val shares = computeShares(rule, a, b, part, vals)
                        if (shares == null) error = R.string.error_split
                        else {
                            vm.saveExpense(
                                trip.id,
                                ExpenseData(existing?.id ?: 0, title.trim(), payer, a, currency, rate, b, category, dateMillis,
                                    Kind.EXPENSE, rule, shares, vals, receipt),
                            )
                            onClose()
                        }
                    }
                }
            }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { picked ->
                        val zone = ZoneId.systemDefault()
                        val day = Instant.ofEpochMilli(picked).atZone(ZoneId.of("UTC")).toLocalDate()
                        val time = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalTime()
                        dateMillis = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton({ showDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
    if (confirmDel) ConfirmDialog(stringResource(R.string.confirm_delete_expense), { confirmDel = false }) {
        existing?.let { vm.deleteExpense(it.id) }; confirmDel = false; onClose()
    }
}
