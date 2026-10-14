package ir.hesab.app.data

import androidx.room.withTransaction
import ir.hesab.app.sms.Notifications
import ir.hesab.core.*
import kotlinx.coroutines.flow.first
import java.util.UUID

enum class Ingest { CREATED, DUPLICATE, REVIEW, IGNORED }
class Repository(val db: HesabDatabase, private val preferences: Preferences, private val notifications: Notifications) {
    suspend fun ingest(sender: String, body: String, time: Long): Boolean = ingest(sender,body,time,emptyList(),true)==Ingest.CREATED
    /**
     * [aliases] are other timestamps the same message may already be stored under: the inbox keeps
     * both the operator time and the arrival time. [notify] is off for history, which would
     * otherwise post one notification per old message.
     */
    suspend fun ingest(sender: String, body: String, time: Long, aliases: List<Long>, notify: Boolean): Ingest {
        val prefs = preferences.flow.first()
        if(!prefs.smsEnabled) return Ingest.IGNORED
        val bank = prefs.bankFor(sender) ?: return Ingest.IGNORED
        val parsed = BankParser().parse(body,bank)
        // Unrelated messages and authentication material leave no persisted record.
        if(parsed is ParseResult.Ignored) return Ingest.IGNORED
        val key = Fingerprint.of(sender,body,time)
        val keys = listOf(key)+aliases.filter { it!=time }.map { Fingerprint.of(sender,body,it) }
        var saved: Transaction? = null
        var outcome = Ingest.DUPLICATE
        db.withTransaction {
            val records = keys.mapNotNull { db.dao().record(it) }
            if(records.any { it.outcome=="CREATED" }) return@withTransaction
            if(parsed is ParseResult.Review) {
                if(records.isEmpty()) db.dao().save(SMSProcessingRecord(key,time,"REVIEW:${parsed.reason}",null))
                outcome = Ingest.REVIEW
                return@withTransaction
            }
            // An earlier version may have given up on this message; a readable result replaces that note.
            records.forEach { db.dao().forget(it.fingerprint) }
            val event = (parsed as ParseResult.Transaction).value
            // The bank's own date wins only when it agrees with the message time; otherwise it is some other date in the text.
            val eventTime = SmsTime.resolve(event.bankDateText,time)?.takeIf { kotlin.math.abs(it-time)<=3*86_400_000L } ?: time
            val everyAccount = db.dao().accounts()
            val accounts = everyAccount.filter { it.bankId==bank && it.active }
            val matched = accounts.filter { a -> a.suffix.isNotEmpty() && event.accounts.any { it.endsWith(a.suffix) } }
            val card = db.dao().cards().filter { it.lastFour in event.cards && accounts.any { a -> a.id==it.accountId } }.singleOrNull()
            var account = matched.singleOrNull() ?: card?.let { c -> accounts.singleOrNull { it.id==c.accountId } } ?: accounts.singleOrNull()
            if(account==null && everyAccount.none { it.bankId==bank }) {
                // A trusted sender is enough to start; the owner can rename or split accounts later.
                account = FinancialAccount(bankId=bank,nickname=Banks.title(bank) ?: "حساب بانکی",suffix=event.accountSuffix ?: "")
                db.dao().save(account)
            }
            val transaction = Transaction(kind=event.kind,accountId=account?.id,bankId=bank,amountIrr=event.amountIrr,direction=event.direction,timestamp=eventTime,cardId=card?.id,note=event.label,business=account?.business ?: false,status=if(event.review || account==null) Status.REVIEW else Status.PENDING,source="SMS",fingerprint=key,bankDateText=event.bankDateText)
            if(db.dao().activeTotal()+transaction.amountIrr+(event.feeIrr ?: 0L)>=Long.MAX_VALUE.toDouble()) {
                db.dao().save(SMSProcessingRecord(key,time,"REVIEW:aggregate_overflow",null))
                outcome = Ingest.REVIEW
                return@withTransaction
            }
            if(db.dao().insert(transaction)==-1L) return@withTransaction
            saved = transaction
            val fee = event.feeIrr
            if(fee!=null && event.direction==Direction.DEBIT) db.dao().insert(transaction.copy(id=UUID.randomUUID().toString(),kind=Kind.FEE,amountIrr=fee,note="کارمزد",fingerprint="$key:fee"))
            db.dao().save(SMSProcessingRecord(key,time,"CREATED",transaction.id))
            outcome = Ingest.CREATED
        }
        if(notify) saved?.let { if(it.kind in setOf(Kind.EXPENSE,Kind.FEE)) notifications.expense(it,prefs); checkBudgets(it.timestamp) }
        return outcome
    }
    suspend fun save(value: Transaction, remember: Boolean = false) {
        require(value.amountIrr > 0)
        require(value.accountId == null || db.dao().accounts().any { it.id==value.accountId })
        require(value.categoryId == null || db.dao().categories().any { it.id==value.categoryId })
        db.withTransaction {
            // A remembered category applies only on its own side: a deposit from a shop is not a purchase there.
            val rule = db.dao().rules().firstOrNull { it.merchant==value.merchant.trim() && value.merchant.isNotBlank() && it.categoryId.startsWith(DEPOSIT_CATEGORY_PREFIX)==takesDepositCategory(value.kind,value.direction) }
            val final = if(value.categoryId==null && rule!=null) value.copy(categoryId=rule.categoryId,status=Status.CONFIRMED) else value
            val previous=db.dao().transaction(value.id)
            if(previous?.transferId!=null) {
                require(final.kind==Kind.TRANSFER && final.accountId==previous.accountId && final.direction==previous.direction) { "حساب و نوع انتقال جفت‌شده قابل تغییر نیست" }
                db.dao().transactions().filter { it.transferId==previous.transferId && it.id!=previous.id }.forEach { peer -> db.dao().save(peer.copy(amountIrr=final.amountIrr,timestamp=final.timestamp,deletedAt=final.deletedAt,updatedAt=System.currentTimeMillis())) }
            }
            db.dao().save(final.copy(updatedAt=System.currentTimeMillis()))
            Money.sum(db.dao().transactions().filter { it.deletedAt==null }.map { it.amountIrr })
            if(remember && final.merchant.isNotBlank() && final.categoryId!=null) db.dao().save(CategorizationRule(merchant=final.merchant.trim(),categoryId=final.categoryId))
        }
        checkBudgets(value.timestamp)
    }
    suspend fun categorize(id: String, category: String) {
        val old = db.dao().transaction(id) ?: return
        if(old.deletedAt!=null) return
        save(old.copy(categoryId=category,status=if(old.source=="SMS" && old.accountId==null) Status.REVIEW else Status.CONFIRMED))
        notifications.cancel(id)
    }
    suspend fun delete(id: String, restore: Boolean = false) { db.dao().transaction(id)?.let { save(it.copy(deletedAt=if(restore) null else System.currentTimeMillis())) } }
    suspend fun transfer(from: String,to: String,amount: Long,time: Long) {
        require(from!=to && amount>0)
        val accounts=db.dao().accounts()
        val a=accounts.first { it.id==from }; val b=accounts.first { it.id==to }
        val link=UUID.randomUUID().toString()
        db.withTransaction {
            val debit=Transaction(kind=Kind.TRANSFER,accountId=from,bankId=a.bankId,amountIrr=amount,direction=Direction.DEBIT,timestamp=time,transferId=link,status=Status.CONFIRMED)
            val credit=debit.copy(id=UUID.randomUUID().toString(),accountId=to,bankId=b.bankId,direction=Direction.CREDIT,fingerprint=UUID.randomUUID().toString())
            db.dao().save(debit); db.dao().save(credit); db.dao().save(TransferMatch(link,debit.id,credit.id,true))
            Money.sum(db.dao().transactions().filter { it.deletedAt==null }.map { it.amountIrr })
        }
    }
    suspend fun matchTransfer(debitId: String,creditId: String) {
        db.withTransaction {
            val debit=db.dao().transaction(debitId) ?: error("تراکنش پیدا نشد")
            val credit=db.dao().transaction(creditId) ?: error("تراکنش پیدا نشد")
            require(debit.direction==Direction.DEBIT && credit.direction==Direction.CREDIT && debit.amountIrr==credit.amountIrr && debit.accountId!=null && credit.accountId!=null && debit.accountId!=credit.accountId && debit.transferId==null && credit.transferId==null)
            val id=UUID.randomUUID().toString()
            db.dao().save(debit.copy(kind=Kind.TRANSFER,transferId=id,status=Status.CONFIRMED))
            db.dao().save(credit.copy(kind=Kind.TRANSFER,transferId=id,status=Status.CONFIRMED))
            db.dao().save(TransferMatch(id,debitId,creditId,true))
        }
    }
    suspend fun checkBudgets(time: Long) {
        ensureBudgets(time)
        val date=Jalali.at(time); val period="${date.year}-${date.month}"
        val range=Jalali.monthRange(time)
        val items=db.dao().transactions().filter { it.deletedAt==null && it.timestamp in range }
        for(budget in db.dao().budgets().filter { it.period==period }) {
            val spent=Finance.totals(items.filter { budget.scope=="ALL" || it.categoryId==budget.scope }.map { it.financeItem() }).spending
            for(threshold in Finance.crossedThresholds(spent,budget.limitIrr)) {
                val key="budget:${budget.id}:$period:$threshold"
                if(db.dao().save(AuditEvent(eventKey=key,kind="BUDGET_$threshold"))!=-1L) notifications.budget(budget.id,threshold)
            }
        }
    }
    suspend fun ensureBudgets(time: Long) {
        val current=Jalali.at(time)
        val period="${current.year}-${current.month}"
        val start=Jalali.toGregorian(PersianDate(current.year,current.month,1)).atStartOfDay(Jalali.zone).toInstant().toEpochMilli()
        val previous=Jalali.at(start-1)
        val priorPeriod="${previous.year}-${previous.month}"
        val priorRange=Jalali.monthRange(start-1)
        db.withTransaction {
            val budgets=db.dao().budgets()
            val items=db.dao().transactions().filter { it.deletedAt==null && it.timestamp in priorRange }
            budgets.filter { it.period==priorPeriod && it.rollover && budgets.none { b -> b.period==period && b.scope==it.scope } }.forEach { budget ->
                val spent=Finance.totals(items.filter { budget.scope=="ALL" || it.categoryId==budget.scope }.map { it.financeItem() }).spending.coerceAtLeast(0L)
                val remaining=(budget.limitIrr-spent).coerceAtLeast(0L)
                db.dao().save(budget.copy(id=UUID.randomUUID().toString(),period=period,limitIrr=Math.addExact(budget.limitIrr,remaining)))
            }
        }
    }
}
