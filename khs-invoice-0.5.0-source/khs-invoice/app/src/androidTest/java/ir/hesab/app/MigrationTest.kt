package ir.hesab.app

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import ir.hesab.app.data.HesabDatabase
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),HesabDatabase::class.java,emptyList(),FrameworkSQLiteOpenHelperFactory())
    @Test fun versionOnePreservesTransactions() {
        helper.createDatabase("migration-test",1).apply {
            execSQL("INSERT INTO transactions (id,kind,accountId,bankId,amountIrr,direction,timestamp,cardId,merchant,categoryId,note,business,status,source,refundOf,transferId,fingerprint,createdAt,updatedAt,deletedAt) VALUES ('synthetic','EXPENSE',NULL,NULL,1000,'DEBIT',1,NULL,'',NULL,'',0,'PENDING','MANUAL',NULL,NULL,'synthetic',1,1,NULL)")
            close()
        }
        helper.runMigrationsAndValidate("migration-test",2,true,HesabDatabase.MIGRATION_1_2).use { db -> db.query("SELECT amountIrr, bankDateText FROM transactions").use { c -> check(c.moveToFirst()); check(c.getLong(0)==1000L); check(c.isNull(1)) } }
    }
}
