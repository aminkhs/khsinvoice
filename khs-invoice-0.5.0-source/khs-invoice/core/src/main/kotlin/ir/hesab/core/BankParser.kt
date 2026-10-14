package ir.hesab.core

import java.security.MessageDigest

/** [aliases] are Latin fragments of sender names; [names] are Persian names found in message bodies. */
data class BankFormat(val id: String, val title: String, val aliases: List<String> = emptyList(), val names: List<String> = emptyList(), val verified: Boolean = false)
object Banks {
    private fun bank(id: String, name: String, vararg aliases: String, title: String = "بانک $name") = BankFormat(id, title, aliases.toList(), listOf(name))
    // No sender is trusted by default; the owner picks senders, by hand or from the inbox scan.
    val all = listOf(
        bank("mellat", "ملت", "mellat"), bank("melli", "ملی", "melli", "bmi"), bank("saman", "سامان", "saman"),
        bank("khavarmianeh", "خاورمیانه", "khavarmianeh", "middleeast", "mebank"), bank("pasargad", "پاسارگاد", "pasargad", "bpi"),
        bank("tejarat", "تجارت", "tejarat"), bank("saderat", "صادرات", "saderat", "bsi"), bank("sepah", "سپه", "sepah"),
        bank("parsian", "پارسیان", "parsian"), bank("ayandeh", "آینده", "ayandeh"), bank("resalat", "رسالت", "resalat"),
        bank("keshavarzi", "کشاورزی", "keshavarzi", "bki"), bank("maskan", "مسکن", "maskan"), bank("refah", "رفاه", "refah"),
        bank("shahr", "شهر", "shahr", "citybank"), bank("eghtesad", "اقتصاد نوین", "enbank", "eghtesad"), bank("sina", "سینا", "sina"),
        bank("postbank", "پست بانک", "postbank", title = "پست بانک"), bank("karafarin", "کارآفرین", "karafarin"),
        bank("dey", "دی", "deybank", "bankdey"), bank("melal", "ملل", "melal"), bank("mehriran", "مهر ایران", "mehr", "qmb"),
        bank("tosee", "توسعه تعاون", "tosee", "ttbank"), bank("gardeshgari", "گردشگری", "gardeshgari", "tourism"),
        bank("iranzamin", "ایران زمین", "iranzamin"), bank("sarmayeh", "سرمایه", "sarmayeh"), bank("blu", "بلو", "blu", title = "بلوبانک"),
        BankFormat("other", "سایر بانک‌ها")
    )
    fun title(id: String?) = all.find { it.id == id }?.title
    /** Best-effort bank for a sender; the owner can always change it. */
    fun guess(sender: String, bodies: List<String> = emptyList()): String {
        val address = sender.lowercase().filter { it.isLetterOrDigit() }
        all.firstOrNull { bank -> bank.aliases.any { address.contains(it) } }?.let { return it.id }
        val text = bodies.joinToString("\n") { Money.normalize(it) }
        // Longest name first, so «پست بانک» is not read as another bank.
        return all.flatMap { bank -> bank.names.map { it to bank.id } }.sortedByDescending { it.first.length }.firstOrNull { (name, _) ->
            val quoted = Regex.escape(name)
            Regex("بانک\\s*$quoted(?![\\p{L}])").containsMatchIn(text) || Regex("(?m)^[^\\p{L}0-9]*$quoted(?![\\p{L}])").containsMatchIn(text)
        }?.second ?: "other"
    }
}
/**
 * [accounts] holds the digits of every account number in the message, the owner's most likely one first.
 * [label] is a short Persian description of the event, e.g. «خرید» or «برداشت خودپرداز».
 */
data class ParsedSms(val bank: String, val amountIrr: Long, val kind: Kind, val direction: Direction, val accountSuffix: String?, val cardSuffix: String?, val review: Boolean, val bankDateText: String? = null, val label: String = "", val feeIrr: Long? = null, val accounts: List<String> = emptyList(), val cards: List<String> = emptyList())
sealed interface ParseResult {
    data class Transaction(val value: ParsedSms): ParseResult
    data class Ignored(val reason: String): ParseResult
    data class Review(val reason: String): ParseResult
}
/**
 * Format-independent reader for Iranian bank messages. Banks disagree on layout, so nothing here is
 * tied to one template: balances, dates and reference numbers are removed first and the remaining
 * numbers are ranked. Bank amounts are rial unless the message says toman.
 */
class BankParser {
    private val i = RegexOption.IGNORE_CASE
    private val otp = Regex("(?<![\\p{L}])رمز|کد\\s*(تایید|تأیید|ورود|فعال)|one.time|\\botp\\b|verification", i)
    private val failed = Regex("ناموفق|انجام\\s*نشد|عدم\\s*موجودی|موجودی\\s*کافی\\s*ن|کافی\\s*نمی|برگشت\\s*خورد|چک\\s*برگشتی")
    private val promo = Regex("https?://|www\\.|قرعه|جشنواره|جایزه|تخفیف|مسابقه|برنده|تبریک|تسلیت|لغو\\s*[0-9]", i)
    private val reminder = Regex("سررسید|مهلت|معوق|یادآوری|لطفا|خواهشمند|نسبت\\s*به|اقدام|منقضی|انقضا")
    private val foreign = Regex("دلار|یورو|درهم|USD|EUR|AED", i)
    private val balanceWord = Regex("مانده|موجودی")
    private val balance = Regex("(?:باقی\\s*مانده|مانده|موجودی)[^0-9+\\-\\n]{0,30}[+\\-]?[0-9][0-9,٬./]*[+\\-]?[ ]*(?:ریال|تومان|IRR|IRT)?", i)
    private val fullDate = Regex("((?:1[34][0-9]{2}|20[0-9]{2})/[01]?[0-9]/[0-3]?[0-9])(?:[ ]*[-_,]?[ ]*([0-2]?[0-9]:[0-5][0-9])(?::[0-5][0-9])?)?")
    private val labelledRef = Regex("(?:(از|به)[ ]+)?(حساب|سپرده|کارت|شبا|شماره|پیگیری|مرجع|ارجاع|رهگیری|ترمینال|پایانه|شعبه|سند|شناسه|سریال|کد|تلفن)[ ]*(?:شماره|ش)?[ ]*:?[ ]*((?:IR)?[0-9*xX][0-9*xX.\\-/]{2,})(?![0-9*xX,٬])(?![ ]*(?:\\+|ریال|تومان))", i)
    private val maskedCard = Regex("(?<![0-9*xX\\-])(?=[0-9xX\\-]*\\*)[0-9*xX][0-9*xX\\-]{6,22}(?![0-9*xX])")
    private val directedRef = Regex("(?<![\\p{L}])(از|به)[ ]*:?[ ]*([0-9*xX][0-9*xX.\\-]{5,})(?![0-9*xX.\\-,٬/])(?![ ]*(?:ریال|تومان))")
    // Order matters: compound stamps go before plain dates and clock times.
    private val stamps = listOf(
        Regex("[0-9]{4,8}[ ]*[-_][ ]*[0-2]?[0-9]:[0-5][0-9](?::[0-5][0-9])?"),
        Regex("(?<![0-9/])[0-9]{1,2}/[0-9]{1,2}[ ]*[-_ ][ ]*[0-2]?[0-9]:[0-5][0-9](?::[0-5][0-9])?"),
        Regex("(?<![0-9,٬])[0-9]{2,4}[/.\\-][0-9]{1,2}[/.\\-][0-9]{1,2}(?![0-9])"),
        Regex("[0-2]?[0-9]:[0-5][0-9](?::[0-5][0-9])?"),
        Regex("(?<![0-9/])[0-9]{1,2}/[0-9]{1,2}(?![0-9/])")
    )
    private val number = "[0-9]{1,3}(?:[,٬][0-9]{3})+|[0-9]{1,3}(?:/[0-9]{3})+|[0-9]{1,3}(?:\\.[0-9]{3})+|[0-9]+"
    private val amountToken = Regex("(?<![0-9,٬./*+\\-])([+\\-]?)($number)(?:\\.[0-9]{1,2})?(?![0-9]|[,٬/.][0-9])(?:([+\\-])(?![0-9]))?[ ]*(ریال|تومان|IRR|IRT|Rials?|Toman)?", i)
    private val amountWord = Regex("مبلغ|برداشت|واریز|خرید|انتقال|پرداخت|دریافت|کارمزد|برگشت|بازگشت|استرداد|عودت|خودپرداز|پایا|ساتنا|چک|قبض|سود|حقوق|یارانه|قسط|کسر|شارژ|اصلاحیه|تراکنش")
    private val refundWord = Regex("برگشت|بازگشت|استرداد|عودت|اصلاحیه|ابطال")
    private val strongDebit = Regex("برداشت|خرید|خودپرداز|کسر")
    private val strongCredit = Regex("واریز")
    private val weakDebit = Regex("پرداخت|انتقال|قبض|قسط|کارمزد|شارژ|چک|پایا|ساتنا")
    private val weakCredit = Regex("دریافت|سود|حقوق|یارانه")
    private val labels = listOf("خودپرداز" to "برداشت خودپرداز", "کارت به کارت" to "کارت به کارت", "قبض" to "پرداخت قبض", "شارژ" to "شارژ", "قسط" to "قسط", "حقوق" to "حقوق", "یارانه" to "یارانه", "سود" to "سود سپرده", "پایا" to "پایا", "ساتنا" to "ساتنا", "خرید" to "خرید", "انتقال" to "انتقال", "چک" to "چک", "برداشت" to "برداشت", "واریز" to "واریز", "پرداخت" to "پرداخت", "دریافت" to "دریافت")
    private class Candidate(val value: Long, val sign: Char?, val toman: Boolean?, val fee: Boolean, val score: Int)

    private fun clean(body: String) = buildString {
        for(c in Money.normalize(body)) when(c) {
            ' ', ' ', ' ', '\t' -> append(' ')
            '−', '–', '—' -> append('-')
            '＋' -> append('+'); '：' -> append(':'); '،' -> append(',')
            'ـ', '\r', '؜', '‎', '‏', '​', '‍', '﻿' -> Unit
            in '‪'..'‮', in '⁦'..'⁩' -> Unit
            else -> append(c)
        }
    }
    private fun firstIndex(pattern: Regex, text: String) = pattern.find(text)?.range?.first
    fun parse(body: String, bankId: String): ParseResult {
        val text = clean(body)
        if(text.isBlank()) return ParseResult.Ignored("unsupported")
        if(otp.containsMatchIn(text)) return ParseResult.Ignored("authentication")
        if(failed.containsMatchIn(text)) return ParseResult.Ignored("failed")
        // A balance line marks a real account event, so advertising words alone do not reject it.
        val hasBalance = balanceWord.containsMatchIn(text)
        if(!hasBalance && promo.containsMatchIn(text)) return ParseResult.Ignored("promotional")
        if(!hasBalance && reminder.containsMatchIn(text)) return ParseResult.Ignored("reminder")
        if(foreign.containsMatchIn(text)) return ParseResult.Review("unknown_currency")
        val date = fullDate.find(text)?.let { hit -> hit.groupValues[1] + hit.groupValues[2].let { if(it.isEmpty()) "" else " $it" } }
        val plainAccounts = mutableListOf<String>(); val sourceAccounts = mutableListOf<String>(); val targetAccounts = mutableListOf<String>(); val cards = mutableListOf<String>()
        fun account(token: String, side: String) { val digits = token.filter { it.isDigit() }; if(digits.length >= 3) when(side) { "از" -> sourceAccounts.add(digits); "به" -> targetAccounts.add(digits); else -> plainAccounts.add(digits) } }
        fun card(token: String) { val tail = token.takeLastWhile { it.isDigit() }; if(tail.length >= 4) cards.add(tail.takeLast(4)) }
        var work = balance.replace(text, " ")
        work = labelledRef.replace(work) { hit -> when(hit.groupValues[2]) { "حساب", "سپرده" -> account(hit.groupValues[3], hit.groupValues[1]); "کارت" -> card(hit.groupValues[3]) }; " " }
        work = maskedCard.replace(work) { hit -> card(hit.value); " " }
        work = directedRef.replace(work) { hit -> account(hit.groupValues[2], hit.groupValues[1]); " " }
        val stamped = stamps.any { it.containsMatchIn(work) }
        for(stamp in stamps) work = stamp.replace(work, " ")
        // A balance, an account or card, or a time stamp shows the message reports something that happened.
        val anchored = hasBalance || stamped || plainAccounts.size + sourceAccounts.size + targetAccounts.size + cards.size > 0

        val candidates = mutableListOf<Candidate>()
        for(line in work.lineSequence()) {
            var previousEnd = 0
            for(hit in amountToken.findAll(line)) {
                val label = line.substring(previousEnd, hit.range.first)
                previousEnd = hit.range.last + 1
                val raw = hit.groupValues[2]
                val digits = raw.filter { it.isDigit() }
                // Amounts never start with zero; phone prefixes and codes do.
                val value = digits.toLongOrNull()?.takeIf { it > 0 && digits.length <= 15 && !digits.startsWith("0") } ?: continue
                val sign = (hit.groupValues[1] + hit.groupValues[3]).firstOrNull()
                val unit = hit.groupValues[4]
                val grouped = raw.length != digits.length
                val worded = amountWord.containsMatchIn(label)
                val accepted = worded || label.trimEnd().endsWith(":") || sign != null || unit.isNotEmpty() || (grouped && line.trim() == hit.value.trim())
                if(!accepted) continue
                val score = (if(label.contains("مبلغ")) 4 else 0) + (if(worded) 3 else 0) + (if(sign != null) 2 else 0) + (if(unit.isNotEmpty()) 2 else 0) + (if(grouped) 1 else 0)
                candidates.add(Candidate(value, sign, unit.takeIf { it.isNotEmpty() }?.let { it == "تومان" || it.equals("IRT", true) || it.equals("Toman", true) }, label.contains("کارمزد"), score))
            }
        }
        val eventText = balance.replace(text, " ")
        val transactional = listOf(strongDebit, strongCredit, weakDebit, weakCredit, refundWord).any { it.containsMatchIn(eventText) }
        val mains = candidates.filter { !it.fee }; val fees = candidates.filter { it.fee }
        if(candidates.isEmpty()) return if(transactional) ParseResult.Review("ambiguous_amount") else ParseResult.Ignored(if(hasBalance) "balance_only" else "unsupported")
        val feeOnly = mains.isEmpty()
        val top = if(feeOnly) fees.take(1) else mains.maxOf { it.score }.let { best -> mains.filter { it.score == best } }
        val chosen = top.first()
        val ambiguous = top.map { it.value }.distinct().size > 1

        val refund = refundWord.containsMatchIn(eventText)
        val direction = when {
            chosen.sign == '-' -> Direction.DEBIT
            chosen.sign == '+' -> Direction.CREDIT
            feeOnly -> Direction.DEBIT
            refund -> Direction.CREDIT
            else -> {
                val debit = firstIndex(strongDebit, eventText); val credit = firstIndex(strongCredit, eventText)
                when {
                    debit != null && credit != null -> if(debit <= credit) Direction.DEBIT else Direction.CREDIT
                    debit != null -> Direction.DEBIT
                    credit != null -> Direction.CREDIT
                    else -> {
                        val weakD = firstIndex(weakDebit, eventText); val weakC = firstIndex(weakCredit, eventText)
                        if(weakD == null && weakC == null) return ParseResult.Ignored(if(hasBalance) "balance_only" else "unsupported")
                        // Words like «انتقال» also appear in notices about limits and services.
                        if(!anchored) return ParseResult.Review("unknown_direction")
                        if(weakC == null || weakD != null && weakD <= weakC) Direction.DEBIT else Direction.CREDIT
                    }
                }
            }
        }
        val kind = when {
            feeOnly -> Kind.FEE
            direction == Direction.CREDIT && refund -> if(eventText.contains("ابطال")) Kind.REVERSAL else Kind.REFUND
            direction == Direction.CREDIT -> Kind.INCOME
            else -> Kind.EXPENSE
        }
        // Every amount of one message shares a unit; rial is the banking default.
        val messageToman = text.contains("تومان") && !text.contains("ریال")
        fun irr(candidate: Candidate) = Money.parse(candidate.value.toString(), candidate.toman ?: messageToman)
        val amount = irr(chosen) ?: return ParseResult.Review("invalid_amount")
        val fee = if(feeOnly) null else fees.firstOrNull()?.let(::irr)
        val accounts = (if(direction == Direction.DEBIT) sourceAccounts + plainAccounts + targetAccounts else targetAccounts + plainAccounts + sourceAccounts).distinct()
        val label = when {
            feeOnly -> "کارمزد"
            kind == Kind.REFUND || kind == Kind.REVERSAL -> "برگشت وجه"
            else -> labels.firstOrNull { eventText.contains(it.first) }?.second ?: ""
        }
        return ParseResult.Transaction(ParsedSms(bankId, amount, kind, direction, accounts.firstOrNull()?.takeLast(4)?.takeIf { it.length == 4 }, cards.firstOrNull(), ambiguous, date, label, fee, accounts, cards.distinct()))
    }
}
object Fingerprint {
    fun of(sender: String, body: String, timestamp: Long): String {
        val canonical = "${sender.trim().uppercase()}\u0000$timestamp\u0000${Money.normalize(body).trim()}"
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
