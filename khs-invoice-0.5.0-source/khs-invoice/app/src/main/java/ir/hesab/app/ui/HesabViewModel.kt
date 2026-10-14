package ir.hesab.app.ui

import androidx.lifecycle.*
import ir.hesab.app.HesabApplication
import ir.hesab.app.data.*
import ir.hesab.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AppState(val preferences: UserPreferences = UserPreferences(),val transactions: List<Transaction> = emptyList(),val accounts: List<FinancialAccount> = emptyList(),val categories: List<Category> = emptyList(),val budgets: List<Budget> = emptyList(),val loading: Boolean = true,val cards: List<Card> = emptyList())
class HesabViewModel(private val app: HesabApplication): ViewModel() {
    val state=combine(combine(app.preferences.flow,app.db.dao().observeTransactions(),app.db.dao().observeAccounts(),app.db.dao().observeCategories(),app.db.dao().observeBudgets()) { p,t,a,c,b -> AppState(p,t,a,c,b,false) },app.db.dao().observeCards()) { s,cards -> s.copy(cards=cards) }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),AppState())
    val reviewCount=app.db.dao().observeReviewCount().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),0)
    val lastImport=app.db.dao().observeLastImport().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val message=MutableStateFlow<String?>(null)
    val editId=MutableStateFlow<String?>(null)
    private fun work(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { try { block() } catch(e: Exception) { message.value=e.message ?: "عملیات انجام نشد" } } }
    fun preference(key: String,value: Boolean) = work { app.preferences.set(key,value) }
    fun theme(value: String) = work { app.preferences.theme(value) }
    fun sender(value: String,bank: String) = work { app.preferences.sender(value,bank) }
    fun removeSender(value: String) = work { app.preferences.removeSender(value) }
    /** Senders are stored before the import starts, because the import reads them once at the beginning. */
    fun setupSms(senders: List<Pair<String,String>>) = work {
        app.preferences.set("sms",true)
        senders.forEach { (address,bank) -> runCatching { app.preferences.sender(address,bank) } }
        ir.hesab.app.sms.HistoryWorker.enqueue(app)
        message.value="فرستنده‌ها اضافه شدند و خواندن پیامک‌های قبلی شروع شد. نتیجه در «وضعیت پردازش» و صفحه تراکنش‌ها دیده می‌شود."
    }
    suspend fun detectSenders() = withContext(Dispatchers.IO) { ir.hesab.app.sms.SmsInbox.detectSenders(app) }
    suspend fun unreadSms() = withContext(Dispatchers.IO) { ir.hesab.app.sms.SmsInbox.unread(app,app.preferences.flow.first()) }
    fun save(t: Transaction,remember: Boolean) = work { app.repository.save(t,remember); editId.value=null }
    fun delete(id: String,restore: Boolean=false) = work { app.repository.delete(id,restore) }
    fun account(value: FinancialAccount) = work { require(value.nickname.isNotBlank()); require(value.suffix.matches(Regex("[0-9]{0,4}"))); app.db.dao().save(value) }
    fun category(title: String,deposit: Boolean=false) = work { require(title.isNotBlank()); app.db.dao().save(Category((if(deposit) DEPOSIT_CATEGORY_PREFIX else "")+java.util.UUID.randomUUID().toString(),title,"•")) }
    fun card(account: String,lastFour: String) = work { require(lastFour.matches(Regex("[0-9]{4}"))); app.db.dao().save(Card(java.util.UUID.randomUUID().toString(),account,lastFour)) }
    fun budget(amount: Long,category: String?,rollover: Boolean=false) = work {
        val date=Jalali.at(System.currentTimeMillis()); val period="${date.year}-${date.month}"; val scope=category ?: "ALL"
        val old=app.db.dao().budgets().find { it.period==period && it.scope==scope }
        app.db.dao().save(Budget(id=old?.id ?: java.util.UUID.randomUUID().toString(),period=period,scope=scope,limitIrr=amount,rollover=rollover))
        app.repository.checkBudgets(System.currentTimeMillis())
    }
    fun transfer(from: String,to: String,amount: Long) = work { app.repository.transfer(from,to,amount,System.currentTimeMillis()) }
    fun matchTransfer(from: String,to: String) = work { app.repository.matchTransfer(from,to) }
    suspend fun export(account: String?,category: String?,range: LongRange?,xlsx: Boolean=false) = withContext(Dispatchers.IO) { Portability(app.db).csv(account,category,range,xlsx) }
    suspend fun backup(password: CharArray) = withContext(Dispatchers.IO) { Portability(app.db).backup(password) }
    suspend fun restore(data: ByteArray,password: CharArray) = withContext(Dispatchers.IO) { Portability(app.db).restore(data,password) }
}
