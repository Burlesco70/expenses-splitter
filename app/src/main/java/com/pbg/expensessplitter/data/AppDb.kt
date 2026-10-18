package com.pbg.expensessplitter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TripE::class, PersonE::class, ExpenseE::class, ShareE::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        const val NAME = "expenses_splitter.db"
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, NAME).build().also { instance = it }
        }

        /** Closes and forgets the singleton (used before replacing the database file). */
        @Synchronized fun reset() { instance?.close(); instance = null }
    }
}
