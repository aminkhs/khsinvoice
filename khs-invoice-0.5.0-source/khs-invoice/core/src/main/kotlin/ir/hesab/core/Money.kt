package ir.hesab.core

import java.math.BigInteger

object Money {
    fun normalize(value: String): String = buildString {
        value.forEach { c -> append(when(c) {
            in '۰'..'۹' -> '0' + (c - '۰')
            in '٠'..'٩' -> '0' + (c - '٠')
            'ي', 'ى' -> 'ی'; 'ك' -> 'ک'; '\u200c' -> ' '; else -> c
        }) }
    }
    fun parse(value: String, toman: Boolean): Long? = runCatching {
        val digits = normalize(value).replace(Regex("[,٬\\s]"), "")
        require(digits.matches(Regex("[0-9]+")))
        val amount = digits.toLong()
        require(amount > 0)
        if (toman) Math.multiplyExact(amount, 10L) else amount
    }.getOrNull()
    fun persian(value: String) = value.map { if(it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")
    fun format(irr: Long, toman: Boolean = true): String {
        val value = BigInteger.valueOf(irr).abs()
        val whole = if(toman) value.divide(BigInteger.TEN) else value
        val grouped = whole.toString().reversed().chunked(3).joinToString("٬").reversed()
        val remainder = if(toman) value.mod(BigInteger.TEN).toInt() else 0
        return persian((if(irr < 0) "−" else "") + grouped + (if(remainder != 0) "٫$remainder" else "")) + if(toman) " تومان" else " ریال"
    }
    fun sum(values: Iterable<Long>) = values.fold(0L, Math::addExact)
}

enum class Kind { EXPENSE, INCOME, TRANSFER, FEE, REFUND, REVERSAL, BORROW, LEND, DEBT_REPAYMENT }
enum class Direction { DEBIT, CREDIT }
enum class Status { PENDING, CONFIRMED, REVIEW }
data class FinanceItem(val amount: Long, val kind: Kind, val direction: Direction, val category: String? = null, val business: Boolean = false)
data class Totals(val spending: Long, val income: Long, val cashFlow: Long, val unclassified: Long)
object Finance {
    fun totals(items: List<FinanceItem>): Totals {
        val spending = Money.sum(items.map { when(it.kind) { Kind.EXPENSE, Kind.FEE -> it.amount; Kind.REFUND, Kind.REVERSAL -> -it.amount; else -> 0L } })
        val income = Money.sum(items.filter { it.kind == Kind.INCOME }.map { it.amount })
        val cash = Money.sum(items.filter { it.kind != Kind.TRANSFER }.map { if(it.direction == Direction.CREDIT) it.amount else -it.amount })
        return Totals(spending, income, cash, Money.sum(items.filter { it.category == null && it.kind in setOf(Kind.EXPENSE, Kind.FEE) }.map { it.amount }))
    }
    fun crossedThresholds(spent: Long, limit: Long): List<Int> {
        require(limit > 0)
        return listOf(70, 90, 100).filter { BigInteger.valueOf(spent).multiply(BigInteger.valueOf(100)) >= BigInteger.valueOf(limit).multiply(BigInteger.valueOf(it.toLong())) }
    }
}
