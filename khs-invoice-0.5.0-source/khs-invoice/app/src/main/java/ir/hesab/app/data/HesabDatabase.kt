package ir.hesab.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao interface HesabDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC") fun observeTransactions(): Flow<List<Transaction>>
    @Query("SELECT * FROM accounts ORDER BY nickname") fun observeAccounts(): Flow<List<FinancialAccount>>
    @Query("SELECT * FROM categories") fun observeCategories(): Flow<List<Category>>
    @Query("SELECT * FROM budgets") fun observeBudgets(): Flow<List<Budget>>
    @Query("SELECT * FROM cards") fun observeCards(): Flow<List<Card>>
    @Query("SELECT COUNT(*) FROM sms_records WHERE outcome LIKE 'REVIEW:%'") fun observeReviewCount(): Flow<Int>
    @Query("SELECT * FROM import_batches ORDER BY startedAt DESC LIMIT 1") fun observeLastImport(): Flow<ImportBatch?>
    @Query("SELECT * FROM transactions WHERE id=:id") suspend fun transaction(id: String): Transaction?
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<Transaction>
    @Query("SELECT * FROM accounts") suspend fun accounts(): List<FinancialAccount>
    @Query("SELECT * FROM categories") suspend fun categories(): List<Category>
    @Query("SELECT * FROM cards") suspend fun cards(): List<Card>
    @Query("SELECT * FROM budgets") suspend fun budgets(): List<Budget>
    @Query("SELECT * FROM rules") suspend fun rules(): List<CategorizationRule>
    @Query("SELECT * FROM sms_records WHERE fingerprint=:key") suspend fun record(key: String): SMSProcessingRecord?
    @Query("SELECT * FROM audit_events WHERE eventKey=:key") suspend fun audit(key: String): AuditEvent?
    @Query("SELECT TOTAL(amountIrr) FROM transactions WHERE deletedAt IS NULL") suspend fun activeTotal(): Double
    @Query("DELETE FROM sms_records WHERE fingerprint=:key") suspend fun forget(key: String)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(value: Transaction): Long
    @Upsert suspend fun save(value: Transaction)
    @Upsert suspend fun save(value: FinancialAccount)
    @Upsert suspend fun save(value: Category)
    @Upsert suspend fun save(value: Bank)
    @Upsert suspend fun save(value: Card)
    @Upsert suspend fun save(value: Budget)
    @Upsert suspend fun save(value: CategorizationRule)
    @Upsert suspend fun save(value: TransferMatch)
    @Upsert suspend fun save(value: ImportBatch)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun save(value: SMSProcessingRecord): Long
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun save(value: AuditEvent): Long
}
@Database(entities=[Bank::class,FinancialAccount::class,Card::class,Transaction::class,Merchant::class,Category::class,Tag::class,TransactionTag::class,Budget::class,CategorizationRule::class,TransferMatch::class,RecurringTransaction::class,SMSProcessingRecord::class,ImportBatch::class,AuditEvent::class],version=2,exportSchema=true)
@TypeConverters(Converters::class)
abstract class HesabDatabase: RoomDatabase() {
    abstract fun dao(): HesabDao
    companion object {
        val MIGRATION_1_2 = object: Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE transactions ADD COLUMN bankDateText TEXT") }
        }
        fun open(context: Context) = Room.databaseBuilder(context,HesabDatabase::class.java,"hesab.db").addMigrations(MIGRATION_1_2).build()
    }
}
