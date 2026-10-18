@file:OptIn(ExperimentalMaterial3Api::class)

package com.pbg.expensessplitter.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pbg.expensessplitter.BuildConfig
import com.pbg.expensessplitter.R
import com.pbg.expensessplitter.core.*
import java.text.DateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

private val ALL_CURRENCIES: List<String> by lazy {
    Currency.getAvailableCurrencies().map { it.currencyCode }.sorted()
}

fun currencyOptions(first: String? = null): List<String> =
    (listOfNotNull(first) + listOf("EUR", "USD", "GBP", "CHF") + ALL_CURRENCIES).distinct()

fun money(c: Long, cur: String) = "${formatCents(c, Locale.getDefault(), grouping = true)} $cur"

@Composable
fun ExpensesApp(vm: AppViewModel) {
    var tripId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val id = tripId
            if (id == null) TripsScreen(vm, onOpen = { tripId = it })
            else TripScreen(vm, id, onBack = { tripId = null })
        }
    }
}

// ---------------------------------------------------------------- trips

@Composable
fun TripsScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val trips by vm.trips.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var editing by remember { mutableStateOf<com.pbg.expensessplitter.data.TripE?>(null) }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportJson) }
    val exportDb = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(vm::exportDb) }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importJson) }
    val restoreDb = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirmRestore = it }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importLegacy) }
    val stamp = remember { java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date()) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(menu, { menu = false }) {
                    @Composable fun item(@androidx.annotation.StringRes r: Int, f: () -> Unit) =
                        DropdownMenuItem({ Text(stringResource(r)) }, onClick = { menu = false; f() })
                    item(R.string.menu_backup_json) { exportJson.launch("expenses-splitter-$stamp.json") }
                    item(R.string.menu_backup_db) { exportDb.launch("expenses-splitter-$stamp.db") }
                    item(R.string.menu_import_json) { importJson.launch(arrayOf("application/json", "text/*", "*/*")) }
                    item(R.string.menu_restore_db) { restoreDb.launch(arrayOf("*/*")) }
                    item(R.string.menu_import_legacy) { legacy.launch(arrayOf("*/*")) }
                    item(R.string.menu_about) { about = true }
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton({ showNew = true }) { Icon(Icons.Default.Add, stringResource(R.string.new_trip)) }
        },
    ) { pad ->
        if (trips.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.no_trips), Modifier.padding(32.dp)) }
        } else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(trips, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(t.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall)
                        Text(t.currency, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    if (showNew) TripDialog(null, true, { showNew = false }) { n, d, c -> vm.addTrip(n, d, c) { onOpen(it) }; showNew = false }
    if (about) AboutDialog { about = false }
    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.menu_restore_db)) },
            text = { Text(stringResource(R.string.restore_confirm)) },
            confirmButton = { TextButton({ vm.restoreDb(uri); confirmRestore = null }) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton({ confirmRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = { Text(stringResource(R.string.about_text, BuildConfig.VERSION_NAME)) },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
fun TripDialog(initial: TripData?, canEditCurrency: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var cur by remember { mutableStateOf(initial?.currency ?: Currency.getInstance(Locale.getDefault()).currencyCode.takeIf { it in ALL_CURRENCIES } ?: "EUR") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_trip else R.string.edit_trip)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.trip_name)) }, singleLine = true)
                OutlinedTextField(desc, { desc = it }, label = { Text(stringResource(R.string.trip_desc)) })
                Dropdown(stringResource(R.string.currency), cur, currencyOptions(cur), enabled = canEditCurrency) { cur = it }
            }
        },
        confirmButton = { TextButton({ onSave(name, desc, cur) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun Dropdown(label: String, value: String, options: List<String>, enabled: Boolean = true, display: (String) -> String = { it }, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${display(value)}", Modifier.weight(1f))
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem({ Text(display(o)) }, onClick = { open = false; onSelect(o) }) }
        }
    }
}

// ---------------------------------------------------------------- one trip

private sealed interface Editing {
    data object None : Editing
    data class Expense(val e: ExpenseData?) : Editing
    data class Payment(val e: ExpenseData?, val t: Transfer?) : Editing
}

@Composable
fun TripScreen(vm: AppViewModel, tripId: Long, onBack: () -> Unit) {
    val gen by vm.generation.collectAsState()
    val flow = remember(tripId, gen) { vm.trip(tripId) }
    val trip by flow.collectAsState(initial = null)
    val t = trip
    if (t == null) { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }; return }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Editing>(Editing.None) }
    var showEditTrip by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var personDialog by remember { mutableStateOf<PersonData?>(null) }
    var newPerson by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    BackHandler(enabled = editing != Editing.None) { editing = Editing.None }
    BackHandler(enabled = editing == Editing.None) { onBack() }

    when (val ed = editing) {
        is Editing.Expense -> {
            ExpenseEditor(vm, t, ed.e, onClose = { editing = Editing.None })
            return
        }
        else -> Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(t.name); Text(t.currency, style = MaterialTheme.typography.labelSmall) } },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text(stringResource(R.string.edit_trip)) }, onClick = { menu = false; showEditTrip = true })
                        DropdownMenuItem({ Text(stringResource(R.string.delete)) }, onClick = { menu = false; confirmDelete = true })
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab < 2) FloatingActionButton({
                if (tab == 0) { if (t.persons.isEmpty()) tab = 1 else editing = Editing.Expense(null) } else newPerson = true
            }) { Icon(Icons.Default.Add, null) }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(tab) {
                listOf(R.string.expenses, R.string.people, R.string.balance).forEachIndexed { i, r ->
                    Tab(tab == i, { tab = i }, text = { Text(stringResource(r)) })
                }
            }
            when (tab) {
                0 -> ExpensesTab(t, onEdit = { e -> editing = if (e.kind == Kind.TRANSFER) Editing.Payment(e, null) else Editing.Expense(e) })
                1 -> PeopleTab(t, onEdit = { personDialog = it })
                else -> BalanceTab(t, onPay = { editing = Editing.Payment(null, it) },
                    onShare = { csv -> shareReport(ctx, t, csv) })
            }
        }
    }

    (editing as? Editing.Payment)?.let { p ->
        PaymentDialog(t, p.e, p.t, onDismiss = { editing = Editing.None },
            onDelete = { p.e?.let { e -> vm.deleteExpense(e.id) }; editing = Editing.None },
            onSave = { vm.saveExpense(tripId, it); editing = Editing.None })
    }
    if (showEditTrip) TripDialog(t, t.expenses.isEmpty(), { showEditTrip = false }) { n, d, c -> vm.updateTrip(tripId, n, d, c); showEditTrip = false }
    if (confirmDelete) ConfirmDialog(stringResource(R.string.confirm_delete_trip), { confirmDelete = false }) { vm.deleteTrip(tripId); confirmDelete = false; onBack() }
    if (newPerson || personDialog != null) {
        PersonDialog(personDialog, onDismiss = { newPerson = false; personDialog = null },
            onSave = { n, e, p -> vm.savePerson(tripId, personDialog?.id, n, e, p); newPerson = false; personDialog = null },
            onDelete = personDialog?.let { p -> { vm.deletePerson(tripId, p); personDialog = null } })
    }
}

@Composable
fun ConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ExpensesTab(t: TripData, onEdit: (ExpenseData) -> Unit) {
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val ctx = LocalContext.current
    if (t.expenses.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(stringResource(if (t.persons.isEmpty()) R.string.no_people_hint else R.string.no_expenses), Modifier.padding(32.dp))
        }
        return
    }
    val names = t.persons.associate { it.id to it.name }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text(
                stringResource(R.string.total) + ": " + money(t.expenses.filter { it.kind == Kind.EXPENSE }.sumOf { it.baseCents }, t.currency),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            )
        }
        items(t.expenses, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth().clickable { onEdit(e) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val prefix = if (e.kind == Kind.TRANSFER) stringResource(R.string.payment) + ": " else ""
                        Text(prefix + e.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${names[e.payerId] ?: "?"} - ${df.format(Date(e.dateMillis))}" +
                                if (e.kind == Kind.EXPENSE) " - ${categoryLabel(ctx, e.category)}" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(e.baseCents, t.currency), fontWeight = FontWeight.Bold)
                        if (e.currency != t.currency) Text(money(e.amountCents, e.currency), style = MaterialTheme.typography.bodySmall)
                        if (e.receiptPath != null) Text(stringResource(R.string.receipt_short), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleTab(t: TripData, onEdit: (PersonData) -> Unit) {
    if (t.persons.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.no_people), Modifier.padding(32.dp)) }
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(t.persons, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth().clickable { onEdit(p) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    val extra = listOf(p.email, p.phone).filter { it.isNotBlank() }.joinToString(" - ")
                    if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun PersonDialog(p: PersonData?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(p?.name ?: "") }
    var email by remember { mutableStateOf(p?.email ?: "") }
    var phone by remember { mutableStateOf(p?.phone ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (p == null) R.string.add_person else R.string.edit_person)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.person_name)) }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email_optional)) }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.phone_optional)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton({ onSave(name, email, phone) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun BalanceTab(t: TripData, onPay: (Transfer) -> Unit, onShare: (Boolean) -> Unit) {
    val bal = remember(t) { computeBalances(t.persons, t.expenses) }
    val transfers = remember(t) { settle(bal) }
    val names = t.persons.associate { it.id to it.name }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text(stringResource(R.string.settle_suggestions), style = MaterialTheme.typography.titleMedium) }
        if (transfers.isEmpty()) item { Text(stringResource(R.string.all_settled)) }
        items(transfers) { tr ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${names[tr.from]} ${stringResource(R.string.pays)} ${names[tr.to]}\n${money(tr.cents, t.currency)}", Modifier.weight(1f))
                    TextButton({ onPay(tr) }) { Text(stringResource(R.string.register_payment)) }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.balance), style = MaterialTheme.typography.titleMedium) }
        items(t.persons) { p ->
            val b = bal[p.id] ?: 0
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(money(b, t.currency), color = if (b < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "${stringResource(R.string.paid)}: ${money(totalPaid(t.expenses, p.id), t.currency)}  -  " +
                            "${stringResource(R.string.owed)}: ${money(totalOwed(t.expenses, p.id), t.currency)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ onShare(false) }, enabled = t.expenses.isNotEmpty()) { Text(stringResource(R.string.share_txt)) }
                Button({ onShare(true) }, enabled = t.expenses.isNotEmpty()) { Text(stringResource(R.string.share_csv)) }
            }
        }
    }
}

/** Registers (or edits) a payment between two people, stored as a TRANSFER expense in trip currency. */
@Composable
fun PaymentDialog(t: TripData, existing: ExpenseData?, suggestion: Transfer?, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (ExpenseData) -> Unit) {
    val ids = t.persons.map { it.id }
    val names = t.persons.associate { it.id to it.name }
    var from by remember { mutableLongStateOf(existing?.payerId ?: suggestion?.from ?: ids.first()) }
    var to by remember { mutableLongStateOf(existing?.shares?.keys?.firstOrNull() ?: suggestion?.to ?: ids.getOrElse(1) { ids.first() }) }
    var amount by remember { mutableStateOf((existing?.baseCents ?: suggestion?.cents)?.let { formatCents(it) } ?: "") }
    var confirmDel by remember { mutableStateOf(false) }
    val payTitle = stringResource(R.string.payment)
    val cents = parseCents(amount)
    val valid = cents != null && cents > 0 && from != to
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.payment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown(stringResource(R.string.payment_from), from.toString(), ids.map { it.toString() }, display = { names[it.toLong()] ?: "?" }) { from = it.toLong() }
                Dropdown(stringResource(R.string.payment_to), to.toString(), ids.map { it.toString() }, display = { names[it.toLong()] ?: "?" }) { to = it.toLong() }
                OutlinedTextField(amount, { amount = it }, label = { Text("${stringResource(R.string.amount)} (${t.currency})") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton({
                val c = cents!!
                onSave(
                    ExpenseData(existing?.id ?: 0, payTitle, from, c, t.currency, 1.0, c, "other",
                        existing?.dateMillis ?: System.currentTimeMillis(), Kind.TRANSFER, SplitRule.EQUAL, mapOf(to to c), emptyMap(), null)
                )
            }, enabled = valid) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton({ confirmDel = true }) { Text(stringResource(R.string.delete)) }
                TextButton(onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
    if (confirmDel) ConfirmDialog(stringResource(R.string.confirm_delete_expense), { confirmDel = false }) { confirmDel = false; onDelete() }
}
