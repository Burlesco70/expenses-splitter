package com.pbg.expensessplitter.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM trip ORDER BY createdAt DESC, id DESC") fun trips(): Flow<List<TripE>>
    @Query("SELECT * FROM trip WHERE id = :id") fun trip(id: Long): Flow<TripE?>
    @Query("SELECT * FROM person WHERE tripId = :t ORDER BY name COLLATE NOCASE") fun persons(t: Long): Flow<List<PersonE>>
    @Transaction @Query("SELECT * FROM expense WHERE tripId = :t ORDER BY dateMillis DESC, id DESC")
    fun expenses(t: Long): Flow<List<ExpenseWithShares>>

    @Query("SELECT * FROM trip ORDER BY id") suspend fun tripsOnce(): List<TripE>
    @Query("SELECT * FROM person WHERE tripId = :t ORDER BY id") suspend fun personsOnce(t: Long): List<PersonE>
    @Transaction @Query("SELECT * FROM expense WHERE tripId = :t ORDER BY id")
    suspend fun expensesOnce(t: Long): List<ExpenseWithShares>
    @Query("SELECT receiptPath FROM expense WHERE receiptPath IS NOT NULL") suspend fun receiptPaths(): List<String>
    @Query("SELECT receiptPath FROM expense WHERE tripId = :t AND receiptPath IS NOT NULL") suspend fun receiptPaths(t: Long): List<String>

    @Insert suspend fun insert(t: TripE): Long
    @Insert suspend fun insert(p: PersonE): Long
    @Insert suspend fun insert(e: ExpenseE): Long
    @Insert suspend fun insertShares(s: List<ShareE>)
    @Update suspend fun update(t: TripE)
    @Update suspend fun update(p: PersonE)
    @Update suspend fun update(e: ExpenseE)
    @Delete suspend fun delete(t: TripE)
    @Delete suspend fun delete(p: PersonE)
    @Query("DELETE FROM expense WHERE id = :id") suspend fun deleteExpense(id: Long)
    @Query("DELETE FROM expense_share WHERE expenseId = :id") suspend fun deleteShares(id: Long)
    @Query("DELETE FROM trip WHERE id = :id") suspend fun deleteTrip(id: Long)
    @Query("SELECT receiptPath FROM expense WHERE id = :id") suspend fun receiptOf(id: Long): String?
    @Query("SELECT COUNT(*) FROM expense WHERE payerId = :p") suspend fun paidCount(p: Long): Int
    @Query("SELECT COUNT(*) FROM expense_share WHERE personId = :p") suspend fun shareCount(p: Long): Int
}
