package com.pbg.expensessplitter.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.pbg.expensessplitter.core.BackupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Raw SQLite file backup / restore (the ".db" format). */
object DbBackup {
    suspend fun export(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val db = AppDb.get(ctx)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val f = ctx.getDatabasePath(AppDb.NAME)
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { o -> f.inputStream().use { it.copyTo(o) } }
    }

    private const val Q = "SELECT identity_hash FROM room_master_table WHERE id = 42"
    private fun identity(db: SQLiteDatabase): String? =
        runCatching { db.rawQuery(Q, null).use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
    private fun identity(db: androidx.sqlite.db.SupportSQLiteDatabase): String? =
        runCatching { db.query(Q).use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()

    /** Validates then replaces the live database. Callers must refresh their flows afterwards. */
    suspend fun restore(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "restore_${System.nanoTime()}.db")
        try {
            ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            val expected = identity(AppDb.get(ctx).openHelper.readableDatabase)
            val cand = try {
                SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: Exception) { throw BackupException("not a SQLite database") }
            cand.use {
                val ok = it.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" }
                if (!ok) throw BackupException("integrity check failed")
                if (identity(it) == null || identity(it) != expected) throw BackupException("database from a different app/schema version")
                val fk = it.rawQuery("PRAGMA foreign_key_check", null).use { c -> c.count }
                if (fk != 0) throw BackupException("foreign key violations")
            }
            AppDb.reset()
            val target = ctx.getDatabasePath(AppDb.NAME)
            File(target.path + "-wal").delete(); File(target.path + "-shm").delete()
            tmp.copyTo(target, overwrite = true)
        } finally { tmp.delete() }
    }
}
