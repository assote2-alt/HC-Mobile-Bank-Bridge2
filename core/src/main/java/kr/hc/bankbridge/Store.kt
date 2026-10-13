package kr.hc.bankbridge

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Store(context: Context) : SQLiteOpenHelper(context, "bridge.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE queue (id TEXT PRIMARY KEY, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE logs (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER, message TEXT)")
        db.execSQL("CREATE TABLE seen (id TEXT PRIMARY KEY, time INTEGER)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    @Synchronized fun enqueue(id: String, payload: String, dedup: String): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("seen", "time < ?", arrayOf((System.currentTimeMillis() - 86400000).toString()))
            val inserted = db.insertWithOnConflict("seen", null, ContentValues().apply {
                put("id", dedup); put("time", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_IGNORE)
            if (inserted == -1L) { db.setTransactionSuccessful(); return false }
            if (count() >= 200) {
                db.execSQL("DELETE FROM queue WHERE rowid IN (SELECT rowid FROM queue ORDER BY rowid LIMIT 1)")
                log("대기열 200건 초과: 가장 오래된 이벤트 삭제")
            }
            db.insertOrThrow("queue", null, ContentValues().apply { put("id", id); put("payload", payload) })
            log("수신 · 로컬 저장: " + id)
            db.setTransactionSuccessful()
            return true
        } finally { db.endTransaction() }
    }
    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM queue", null).use { it.moveToFirst(); it.getInt(0) }
    fun first(): Pair<String, String>? = readableDatabase.rawQuery("SELECT id,payload FROM queue ORDER BY rowid LIMIT 1", null).use {
        if (it.moveToFirst()) it.getString(0) to it.getString(1) else null
    }
    fun sent(id: String) { writableDatabase.delete("queue", "id=?", arrayOf(id)); log("전송 성공: " + id) }
    fun log(message: String) {
        writableDatabase.insert("logs", null, ContentValues().apply { put("time", System.currentTimeMillis()); put("message", message) })
        writableDatabase.execSQL("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY id DESC LIMIT 300)")
    }
    fun logs(): String = readableDatabase.rawQuery("SELECT time,message FROM logs ORDER BY id DESC LIMIT 300", null).use {
        val result = StringBuilder()
        val date = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.KOREA)
        while (it.moveToNext()) result.append(date.format(java.util.Date(it.getLong(0)))).append("  ").append(it.getString(1)).append("\n\n")
        result.toString().ifEmpty { "전송 로그가 없습니다." }
    }
}
