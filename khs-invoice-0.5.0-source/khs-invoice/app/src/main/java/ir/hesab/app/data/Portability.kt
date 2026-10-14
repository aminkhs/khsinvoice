package ir.hesab.app.data

import androidx.room.withTransaction
import ir.hesab.app.security.BackupCrypto
import ir.hesab.core.Csv
import org.json.*

class Portability(private val db: HesabDatabase) {
    private val tables=listOf("banks","accounts","cards","categories","merchants","transactions","tags","transaction_tags","budgets","rules","transfer_matches","recurring","sms_records","import_batches","audit_events")
    suspend fun csv(account: String?=null,category: String?=null,range: LongRange?=null,xlsx: Boolean=false): ByteArray {
        val names=db.dao().accounts().associate { it.id to it.nickname }
        val categories=db.dao().categories().associate { it.id to it.title }
        val rows=db.dao().transactions().filter { it.deletedAt==null && (account==null || it.accountId==account) && (category==null || it.categoryId==category) && (range==null || it.timestamp in range) }
        val document=listOf(listOf("شناسه","زمان UTC","مبلغ IRR","نوع","حساب","دسته","پذیرنده","یادداشت"))+rows.map { listOf(it.id,java.time.Instant.ofEpochMilli(it.timestamp).toString(),it.amountIrr.toString(),it.kind.name,names[it.accountId] ?: "نقدی",categories[it.categoryId] ?: "",it.merchant,it.note) }
        return if(xlsx) ir.hesab.core.Xlsx.encode(document) else Csv.encode(document).toByteArray(Charsets.UTF_8)
    }
    suspend fun backup(password: CharArray): ByteArray {
        val root=JSONObject().put("format",1).put("schema",2)
        db.withTransaction {
            for(table in tables) {
                val rows=JSONArray()
                db.openHelper.readableDatabase.query("SELECT * FROM `$table`").use { cursor ->
                    while(cursor.moveToNext()) {
                        val row=JSONObject()
                        cursor.columnNames.forEachIndexed { i,name -> row.put(name,when(cursor.getType(i)) { android.database.Cursor.FIELD_TYPE_NULL -> JSONObject.NULL; android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i); else -> cursor.getString(i) }) }
                        rows.put(row)
                    }
                }
                root.put(table,rows)
            }
        }
        return BackupCrypto.encrypt(root.toString().toByteArray(Charsets.UTF_8),password)
    }
    suspend fun restore(data: ByteArray,password: CharArray): Int {
        val root=JSONObject(BackupCrypto.decrypt(data,password).toString(Charsets.UTF_8))
        require(root.getInt("format")==1 && root.getInt("schema")==2)
        val sql=db.openHelper.writableDatabase
        val columns=tables.associateWith { table -> sql.query("PRAGMA table_info(`$table`)").use { c -> buildSet { while(c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) } } }
        var count=0
        // Validate the complete document before any insert. Unknown columns and types are rejected.
        for(table in tables) {
            val rows=root.getJSONArray(table)
            require(rows.length()<=100000)
            for(i in 0 until rows.length()) {
                val row=rows.getJSONObject(i)
                require(row.keys().asSequence().toSet()==columns.getValue(table))
                for(name in columns.getValue(table)) { val value=row.get(name); require(value==JSONObject.NULL || value is String || value is Int || value is Long); if(value is String) require(value.length<=4096) }
                if(table=="transactions") {
                    require(row.getLong("amountIrr")>0)
                    ir.hesab.core.Kind.valueOf(row.getString("kind")); ir.hesab.core.Direction.valueOf(row.getString("direction")); ir.hesab.core.Status.valueOf(row.getString("status"))
                    java.util.UUID.fromString(row.getString("id"))
                    ir.hesab.core.Jalali.at(row.getLong("timestamp"))
                }
                if(table=="budgets") require(row.getLong("limitIrr")>0)
                if(table=="cards") require(row.getString("lastFour").matches(Regex("[0-9]{4}")))
                if(table=="accounts") require(row.getString("suffix").matches(Regex("[0-9]{0,4}")))
            }
        }
        db.withTransaction {
            for(table in tables) {
                val names=columns.getValue(table).toList()
                val rows=root.getJSONArray(table)
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i)
                    val values=names.map { if(row.isNull(it)) null else row.get(it) }.toTypedArray()
                    sql.execSQL("INSERT OR IGNORE INTO `$table` (${names.joinToString(",") { "`$it`" }}) VALUES (${names.joinToString(",") { "?" }})",values)
                    if(table=="transactions") count++
                }
            }
            // Reject orphaned finance references, including those from untrusted backups.
            sql.query("SELECT COUNT(*) FROM transactions t WHERE (t.accountId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id=t.accountId)) OR (t.categoryId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.id=t.categoryId))").use { c -> c.moveToFirst(); require(c.getLong(0)==0L) }
            ir.hesab.core.Money.sum(db.dao().transactions().filter { it.deletedAt==null }.map { it.amountIrr })
        }
        return count
    }
}
