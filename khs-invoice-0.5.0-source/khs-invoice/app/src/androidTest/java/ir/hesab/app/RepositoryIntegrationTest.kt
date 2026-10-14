package ir.hesab.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import ir.hesab.app.data.*
import ir.hesab.app.sms.Notifications
import ir.hesab.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {
    private lateinit var db: HesabDatabase
    private lateinit var repo: Repository
    private lateinit var prefs: Preferences
    @Before fun setup() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        db=Room.inMemoryDatabaseBuilder(context,HesabDatabase::class.java).build()
        prefs=Preferences(context); prefs.set("sms",true); prefs.sender("SYNTHETIC","mellat")
        repo=Repository(db,prefs,Notifications(context))
        db.dao().save(Bank("mellat","بانک ملت")); db.dao().save(Category("food","رستوران","🍔"))
        db.dao().save(FinancialAccount("account-a","mellat","الف","1234")); db.dao().save(FinancialAccount("account-b","mellat","ب","5678"))
    }
    @After fun close() { db.close() }
    @Test fun formattedPersianSenderMatchesCompactAddressWithoutReversing() = runBlocking {
        prefs.sender("\u2067۰۹۹۹ ۹۹۲ ۰۰۰۰\u2069","saman")
        try {
            db.dao().save(Bank("saman","بانک سامان"))
            assertTrue(repo.ingest("09999920000","خرید 1000 ریال",51))
            assertFalse(repo.ingest("00009920999","خرید 1000 ریال",52))
            // Phones show one sender with or without the leading zero or +98.
            assertTrue(repo.ingest("9999920000","خرید 1000 ریال",53))
            assertTrue(repo.ingest("+989999920000","خرید 1000 ریال",54))
            assertEquals(3,db.dao().transactions().size)
            assertTrue(db.dao().transactions().all { it.bankId=="saman" })
        } finally { prefs.removeSender("0999 992 0000") }
    }
    @Test fun historyDoesNotRepeatLiveCaptureAndOldGiveUpsAreRetried() = runBlocking {
        val body="حساب1234\nبرداشت1,500,000\nمانده25,340,000"
        // An earlier version stored this note for messages without a currency word.
        db.dao().save(SMSProcessingRecord(Fingerprint.of("SYNTHETIC",body,10),10,"REVIEW:missing_currency",null))
        assertEquals(Ingest.CREATED,repo.ingest("SYNTHETIC",body,10,emptyList(),false))
        // The inbox knows the same message by its arrival time as well.
        assertEquals(Ingest.DUPLICATE,repo.ingest("SYNTHETIC",body,99,listOf(10),false))
        assertEquals(Ingest.DUPLICATE,repo.ingest("SYNTHETIC",body,10,listOf(99),false))
        val saved=db.dao().transactions().single()
        assertEquals(1500000L,saved.amountIrr); assertEquals("account-a",saved.accountId); assertEquals("برداشت",saved.note)
    }
    @Test fun firstMessageOfBankCreatesAccountAndFeeIsSeparate() = runBlocking {
        prefs.sender("SYNTHETIC-SEPAH","sepah")
        try {
            assertEquals(Ingest.CREATED,repo.ingest("SYNTHETIC-SEPAH","برداشت:1,000,000-\nکارمزد:7,200-\nحساب:67006\nمانده:5,000,000",1,emptyList(),false))
            val account=db.dao().accounts().single { it.bankId=="sepah" }
            assertEquals("بانک سپه",account.nickname); assertEquals("7006",account.suffix)
            val rows=db.dao().transactions()
            assertEquals(2,rows.size); assertTrue(rows.all { it.accountId==account.id && it.status==Status.PENDING })
            assertEquals(1007200L,Finance.totals(rows.map { it.financeItem() }).spending)
            // One account per bank takes every message of that bank, whatever number the text shows.
            assertEquals(Ingest.CREATED,repo.ingest("SYNTHETIC-SEPAH","خرید 2,000 ریال\nکارت: 5892-10**-****-4321",2,emptyList(),false))
            assertEquals(account.id,db.dao().transactions().single { it.amountIrr==2000L }.accountId)
        } finally { prefs.removeSender("SYNTHETIC-SEPAH") }
    }
    @Test fun duplicateAndSeparateAccountsAndCategory() = runBlocking {
        val body="خرید 1000 ریال\nحساب ****1234"
        assertTrue(repo.ingest("SYNTHETIC",body,1))
        assertFalse(repo.ingest("SYNTHETIC",body,1))
        assertTrue(repo.ingest("SYNTHETIC",body,2))
        assertTrue(repo.ingest("SYNTHETIC","خرید 1000 ریال\nحساب ****5678",3))
        assertEquals(3,db.dao().transactions().size)
        assertEquals(2,db.dao().transactions().count { it.accountId=="account-a" })
        val t=db.dao().transactions().first(); repo.categorize(t.id,"food")
        assertEquals("food",db.dao().transaction(t.id)!!.categoryId)
    }
    @Test fun noUnrelatedSmsOtpOrDisabledCapture() = runBlocking {
        assertFalse(repo.ingest("UNKNOWN","خرید 1000 ریال",1))
        assertFalse(repo.ingest("SYNTHETIC","رمز خرید 1000 ریال",1))
        prefs.set("sms",false); assertFalse(repo.ingest("SYNTHETIC","خرید 1000 ریال",1))
        assertTrue(db.dao().transactions().isEmpty())
    }
    @Test fun transfersEditDeleteRestoreAtomically() = runBlocking {
        repo.transfer("account-a","account-b",1234,100)
        val t=db.dao().transactions().first(); repo.save(t.copy(amountIrr=5555))
        assertTrue(db.dao().transactions().all { it.amountIrr==5555L })
        assertEquals(0L,Finance.totals(db.dao().transactions().map { it.financeItem() }).spending)
        repo.delete(t.id); assertTrue(db.dao().transactions().all { it.deletedAt!=null })
        repo.delete(t.id,true); assertTrue(db.dao().transactions().all { it.deletedAt==null })
    }
    @Test fun backupRestoreMergesWithoutDuplicates() = runBlocking {
        repo.ingest("SYNTHETIC","خرید 1000 ریال\nحساب ****1234",1)
        val service=Portability(db); val secret="synthetic password 123".toCharArray()
        val data=service.backup(secret)
        service.restore(data,secret); service.restore(data,secret)
        assertEquals(1,db.dao().transactions().size)
    }
    @Test fun rememberedCategoryStaysOnItsOwnSide() = runBlocking {
        db.dao().save(Category("in-dong","دنگ","🤝"))
        fun entry(kind: Kind,direction: Direction,category: String?=null) = Transaction(kind=kind,accountId=null,bankId=null,amountIrr=1000,direction=direction,timestamp=1,merchant="دوست مصنوعی",categoryId=category)
        repo.save(entry(Kind.INCOME,Direction.CREDIT,"in-dong"),remember=true)
        val paid=entry(Kind.EXPENSE,Direction.DEBIT); repo.save(paid)
        val received=entry(Kind.INCOME,Direction.CREDIT); repo.save(received)
        assertNull(db.dao().transaction(paid.id)!!.categoryId)
        assertEquals("in-dong",db.dao().transaction(received.id)!!.categoryId)
        assertTrue(takesDepositCategory(Kind.INCOME,Direction.CREDIT)); assertFalse(takesDepositCategory(Kind.REFUND,Direction.CREDIT)); assertFalse(takesDepositCategory(Kind.EXPENSE,Direction.DEBIT))
    }
    @Test fun manualEntryWithoutSmsPermission() = runBlocking {
        prefs.set("sms",false)
        repo.save(Transaction(kind=Kind.EXPENSE,accountId=null,bankId=null,amountIrr=1234,direction=Direction.DEBIT,timestamp=1))
        assertEquals(1,db.dao().transactions().size)
    }
}
