package com.pbg.expensessplitter.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pbg.expensessplitter.R
import com.pbg.expensessplitter.core.*
import com.pbg.expensessplitter.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    private val gen = MutableStateFlow(0)

    val trips: StateFlow<List<TripE>> =
        gen.flatMapLatest { repo.trips }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun trip(id: Long): Flow<TripData?> = gen.flatMapLatest { repo.trip(id) }
    val generation: StateFlow<Int> = gen

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private fun say(@StringRes id: Int, vararg a: Any) {
        messages.tryEmit(getApplication<Application>().getString(id, *a))
    }

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: PersonInUseException) { say(R.string.person_in_use) }
        catch (e: BackupException) { say(R.string.operation_failed, e.message ?: "") }
        catch (e: Exception) { say(R.string.operation_failed, e.message ?: e.javaClass.simpleName) }
    }

    fun addTrip(name: String, desc: String, currency: String, onDone: (Long) -> Unit = {}) =
        run { onDone(repo.addTrip(name, desc, currency)) }

    fun updateTrip(id: Long, name: String, desc: String, currency: String) = run {
        repo.tripEntity(id)?.let { repo.updateTrip(it.copy(name = name.trim(), description = desc.trim(), currency = currency)) }
    }

    fun deleteTrip(id: Long) = run { repo.deleteTrip(id) }
    fun savePerson(tripId: Long, id: Long?, name: String, email: String, phone: String) =
        run { repo.savePerson(tripId, id, name, email, phone) }
    fun deletePerson(tripId: Long, p: PersonData) = run { repo.deletePerson(tripId, p.id, p.name) }
    fun saveExpense(tripId: Long, e: ExpenseData) = run { repo.saveExpense(tripId, e) }
    fun deleteExpense(id: Long) = run { repo.deleteExpense(id) }
    suspend fun importReceipt(uri: Uri): String? = try { repo.importReceipt(uri) } catch (e: Exception) { say(R.string.operation_failed, e.message ?: ""); null }

    fun exportJson(uri: Uri) = run {
        val text = tripsToJson(repo.allTrips(), System.currentTimeMillis())
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
        say(R.string.backup_saved)
    }

    fun importJson(uri: Uri) = run {
        val text = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
        }
        val n = repo.insertTrips(tripsFromJson(text))
        say(R.string.imported_trips, n, 0)
    }

    fun exportDb(uri: Uri) = run { DbBackup.export(getApplication(), uri); say(R.string.backup_saved) }

    fun restoreDb(uri: Uri) = run {
        DbBackup.restore(getApplication(), uri)
        gen.value += 1
        say(R.string.restore_done)
    }

    fun importLegacy(uri: Uri) = run {
        val r = withContext(Dispatchers.IO) { LegacyImporter.read(getApplication(), uri) }
        val n = repo.insertTrips(r.trips)
        say(R.string.imported_trips, n, r.skippedExpenses)
    }
}
