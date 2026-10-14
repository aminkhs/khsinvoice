package ir.hesab.app.data

import androidx.room.*
import ir.hesab.core.*
import java.util.UUID

@Entity(tableName="banks") data class Bank(@PrimaryKey val id: String, val title: String)
@Entity(tableName="accounts", indices=[Index("bankId")]) data class FinancialAccount(@PrimaryKey val id: String = UUID.randomUUID().toString(), val bankId: String, val nickname: String, val suffix: String = "", val business: Boolean = false, val reconciledBalance: Long? = null, val reconciledAt: Long? = null, val active: Boolean = true)
@Entity(tableName="cards", indices=[Index("accountId")]) data class Card(@PrimaryKey val id: String, val accountId: String, val lastFour: String)
@Entity(tableName="categories") data class Category(@PrimaryKey val id: String, val title: String, val emoji: String)
/** Deposit categories are told apart by their id, so the stored schema and existing backups stay as they are. */
const val DEPOSIT_CATEGORY_PREFIX="in-"
val Category.deposit: Boolean get() = id.startsWith(DEPOSIT_CATEGORY_PREFIX)
val depositCategories=listOf(Category("in-income","درآمد","💰"),Category("in-dong","دنگ","🤝"),Category("in-help","کمک","🤲"),Category("in-gift","هدیه","🎁"),Category("in-other","سایر","📝"))
/** Money coming in takes deposit categories. A refund belongs to the purchase it reverses, so it keeps the spending ones. */
fun takesDepositCategory(kind: Kind,direction: Direction) = direction==Direction.CREDIT && kind!=Kind.REFUND && kind!=Kind.REVERSAL
@Entity(tableName="merchants") data class Merchant(@PrimaryKey val id: String, val name: String)
@Entity(tableName="transactions", indices=[Index(value=["fingerprint"],unique=true),Index("accountId"),Index("timestamp"),Index("categoryId"),Index("cardId")])
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val kind: Kind, val accountId: String?, val bankId: String?, val amountIrr: Long,
    val direction: Direction, val timestamp: Long, val cardId: String? = null,
    val merchant: String = "", val categoryId: String? = null, val note: String = "",
    val business: Boolean = false, val status: Status = Status.PENDING, val source: String = "MANUAL",
    val refundOf: String? = null, val transferId: String? = null,
    val fingerprint: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val deletedAt: Long? = null, val bankDateText: String? = null
) { fun financeItem() = FinanceItem(amountIrr,kind,direction,categoryId,business) }
@Entity(tableName="tags") data class Tag(@PrimaryKey val id: String, val title: String)
@Entity(tableName="transaction_tags", primaryKeys=["transactionId","tagId"]) data class TransactionTag(val transactionId: String, val tagId: String)
@Entity(tableName="budgets", indices=[Index(value=["period","scope"],unique=true)]) data class Budget(@PrimaryKey val id: String = UUID.randomUUID().toString(), val period: String, val scope: String = "ALL", val limitIrr: Long, val rollover: Boolean = false)
@Entity(tableName="rules", indices=[Index(value=["merchant"],unique=true)]) data class CategorizationRule(@PrimaryKey val id: String = UUID.randomUUID().toString(), val merchant: String, val categoryId: String)
@Entity(tableName="transfer_matches") data class TransferMatch(@PrimaryKey val id: String, val debitId: String, val creditId: String, val confirmed: Boolean)
@Entity(tableName="recurring") data class RecurringTransaction(@PrimaryKey val id: String, val accountId: String, val amountIrr: Long, val categoryId: String?, val nextAt: Long, val intervalDays: Int)
@Entity(tableName="sms_records") data class SMSProcessingRecord(@PrimaryKey val fingerprint: String, val timestamp: Long, val outcome: String, val transactionId: String?)
@Entity(tableName="import_batches") data class ImportBatch(@PrimaryKey val id: String = UUID.randomUUID().toString(), val startedAt: Long, val count: Int, val completedAt: Long?)
@Entity(tableName="audit_events", indices=[Index(value=["eventKey"],unique=true)]) data class AuditEvent(@PrimaryKey val id: String = UUID.randomUUID().toString(), val eventKey: String, val kind: String, val timestamp: Long = System.currentTimeMillis())
class Converters {
    @TypeConverter fun kind(value: String) = Kind.valueOf(value)
    @TypeConverter fun kind(value: Kind) = value.name
    @TypeConverter fun direction(value: String) = Direction.valueOf(value)
    @TypeConverter fun direction(value: Direction) = value.name
    @TypeConverter fun status(value: String) = Status.valueOf(value)
    @TypeConverter fun status(value: Status) = value.name
}
