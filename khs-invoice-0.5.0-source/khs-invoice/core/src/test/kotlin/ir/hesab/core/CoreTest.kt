package ir.hesab.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CoreTest {
    @Test fun numericSmsSendersKeepOrderAndLeadingZeroes() {
        val expected="09999920000"
        listOf("0999 992 0000","۰۹۹۹ ۹۹۲ ۰۰۰۰","٠٩٩٩\u00a0٩٩٢\u202f٠٠٠٠","\u20670999 992 0000\u2069","0999\u200f 992\u200e 0000").forEach { assertEquals(expected,SmsSender.normalize(it)) }
        assertNotEquals(expected,SmsSender.normalize("0000 992 0999"))
        assertEquals("+98999920000",SmsSender.normalize("+98 999 920 000"))
        assertEquals("BANK A",SmsSender.normalize("Bank A"))
        assertNotEquals(SmsSender.normalize("Bank A"),SmsSender.normalize("BankA"))
    }
    @Test fun explicitBankDateDeterminesReportMonth() { val sms=LocalDate.of(2025,3,21).atStartOfDay(Jalali.zone).toInstant().toEpochMilli(); val event=SmsTime.resolve("1403/12/30 23:59",sms)!!; assertEquals(PersianDate(1403,12,30),Jalali.at(event)); assertNull(SmsTime.resolve("30/12/03",sms)); assertNull(SmsTime.resolve("1404/12/30",sms)) }
    @Test fun xlsxStoresFormulaAsText() { val bytes=Xlsx.encode(listOf(listOf("=SUM(A1)",Long.MAX_VALUE.toString()))); val files=mutableMapOf<String,String>(); java.util.zip.ZipInputStream(bytes.inputStream()).use { zip -> while(true) { val entry=zip.nextEntry ?: break; files[entry.name]=zip.readBytes().toString(Charsets.UTF_8) } }; assertTrue(files.containsKey("[Content_Types].xml")); assertTrue(files.getValue("xl/worksheets/sheet1.xml").contains("t=\"inlineStr\"")); assertFalse(files.getValue("xl/worksheets/sheet1.xml").contains("<f>")); assertTrue(files.getValue("xl/worksheets/sheet1.xml").contains(Long.MAX_VALUE.toString())) }
    private val parser=BankParser()
    @Test fun persianAndArabicDigits() { assertEquals("1234567890",Money.normalize("۱۲۳۴۵۶۷۸۹۰")); assertEquals("1234567890",Money.normalize("١٢٣٤٥٦٧٨٩٠")) }
    @Test fun exactToman() { assertEquals(4500000L,Money.parse("۴۵۰٬۰۰۰",true)); assertEquals(1234L,Money.parse("1,234",false)); assertEquals("۱٫۱ تومان",Money.format(11)); assertNull(Money.parse(Long.MAX_VALUE.toString(),true)); assertNull(Money.parse("12.5",true)) }
    @Test fun balanceNeverUsedAsAmount() { val result=parser.parse("خرید: ۴۵۰٬۰۰۰ تومان\nمانده: ۹۰۰٬۰۰۰ تومان","mellat") as ParseResult.Transaction; assertEquals(4500000L,result.value.amountIrr) }
    @Test fun authenticationNeverRetained() { assertTrue(parser.parse("رمز پویا خرید 123456 مبلغ 1000 ریال","melli") is ParseResult.Ignored); assertTrue(parser.parse("OTP 1234 خرید 1000 ریال","saman") is ParseResult.Ignored) }
    private fun event(body: String,bank: String="mellat") = (parser.parse(body,bank) as ParseResult.Transaction).value
    @Test fun currencyAndUncertainty() {
        assertTrue(parser.parse("خرید 100 دلار","mellat") is ParseResult.Review)
        // Bank amounts are rial unless the message says toman.
        assertEquals(1000L,event("خرید 1000").amountIrr)
        assertEquals(450000L,event("برداشت 45,000 تومان\nمانده 100,000 تومان").amountIrr)
        assertEquals(450000L,event("برداشت 45,000\nمانده 100,000 تومان").amountIrr)
        val withFee=event("خرید مبلغ 1000 ریال\nکارمزد 100 ریال"); assertEquals(1000L,withFee.amountIrr); assertEquals(100L,withFee.feeIrr); assertFalse(withFee.review)
        assertTrue(parser.parse("برداشت از حساب شما انجام شد","mellat") is ParseResult.Review)
    }
    @Test fun syntheticAllBanks() { Banks.all.forEach { bank -> val result=parser.parse("برداشت: ١٢٣٬٤٥٦ ریال\nحساب: ****4321\nمانده: 999 ریال",bank.id) as ParseResult.Transaction; assertEquals(123456L,result.value.amountIrr); assertEquals("4321",result.value.accountSuffix); assertFalse(bank.verified) } }
    @Test fun recognizesTransfersRefundsFeesBalances() {
        // A transfer in a bank message is money leaving or entering; only the owner knows if it was internal.
        val transfer=event("انتقال مبلغ 1000 ریال\nحساب 1234"); assertEquals(Kind.EXPENSE,transfer.kind); assertEquals("انتقال",transfer.label)
        assertTrue(parser.parse("مشتری گرامی سقف انتقال روزانه شما 100,000,000 ریال است","mellat") is ParseResult.Review)
        assertEquals(Kind.REFUND,event("استرداد مبلغ 1000 ریال").kind); assertEquals(Direction.CREDIT,event("برگشت خرید: 1,000").direction)
        assertEquals(Kind.FEE,event("کارمزد 1000 ریال").kind)
        assertTrue(parser.parse("موجودی 10000 ریال","mellat") is ParseResult.Ignored); assertTrue(parser.parse("مانده حساب: 1,000,000 ریال","mellat") is ParseResult.Ignored)
    }
    // The layouts below follow the shapes banks really use; every number in them is made up.
    @Test fun compactLinesWithoutCurrency() {
        val debit=event("حساب1234567890\nبرداشت1,500,000\nمانده25,340,000\n040712-13:45")
        assertEquals(1500000L,debit.amountIrr); assertEquals(Kind.EXPENSE,debit.kind); assertEquals("7890",debit.accountSuffix); assertFalse(debit.review)
        val credit=event("حساب 1234567890\nواریز 2,000,000\nمانده 27,340,000\n0712-13:45")
        assertEquals(2000000L,credit.amountIrr); assertEquals(Kind.INCOME,credit.kind); assertEquals(Direction.CREDIT,credit.direction)
    }
    @Test fun signedAmountsDecideDirection() {
        val out=event("بانك ملي ايران\nانتقال:1,000,000-\nحساب:67006\nمانده:25,123,456\n0712-13:45","melli")
        assertEquals(1000000L,out.amountIrr); assertEquals(Direction.DEBIT,out.direction); assertEquals(Kind.EXPENSE,out.kind); assertEquals(listOf("67006"),out.accounts)
        val salary=event("واريزحقوق:+50,000,000\nحساب:67006\nمانده:75,123,456\n0725-08:02","melli")
        assertEquals(50000000L,salary.amountIrr); assertEquals(Kind.INCOME,salary.kind); assertEquals("حقوق",salary.label)
        val incoming=event("انتقالي:+3,000,000\nحساب:67006\nمانده:78,123,456","melli"); assertEquals(Direction.CREDIT,incoming.direction)
        val bare=event("10.1234567.1\n-500,000\nمانده: 2,000,000","resalat"); assertEquals(500000L,bare.amountIrr); assertEquals(Direction.DEBIT,bare.direction)
        val fee=event("برداشت:1,000,000-\nكارمزد:7,200-\nحساب:67006\nمانده:5,000,000","melli"); assertEquals(1000000L,fee.amountIrr); assertEquals(7200L,fee.feeIrr)
    }
    @Test fun sentencesAndAccountNumbers() {
        val saman=event("بانک سامان\nبرداشت مبلغ 500,000 ریال از حساب 849-800-123456-1\nمانده: 1,234,567 ریال\n1404/07/12 13:45","saman")
        assertEquals(500000L,saman.amountIrr); assertEquals(listOf("8498001234561"),saman.accounts); assertEquals("1404/07/12 13:45",saman.bankDateText)
        val dotted=event("برداشت از 123.8000.12345678.1\n500,000 ریال\nمانده 1,234,567 ریال\n1404/07/12 13:45","pasargad")
        assertEquals(500000L,dotted.amountIrr); assertEquals(Direction.DEBIT,dotted.direction); assertEquals("6781",dotted.accountSuffix)
        val oneLine=event("برداشت از حساب 123456789 مبلغ 500,000 ريال مانده 1,000,000 ريال 1404/07/12 13:45","keshavarzi")
        assertEquals(500000L,oneLine.amountIrr); assertEquals("6789",oneLine.accountSuffix)
        val deposit=event("مبلغ 2,000,000 ریال به حساب 123456789 واریز شد. مانده: 3,000,000 ریال","tejarat")
        assertEquals(2000000L,deposit.amountIrr); assertEquals(Kind.INCOME,deposit.kind)
        val card=event("خرید\nمبلغ: 2,350,000 ریال\nکارت: 6104-33**-****-1234\nمانده: 12,000,000 ریال"); assertEquals(2350000L,card.amountIrr); assertEquals("1234",card.cardSuffix)
        val transfer=event("انتقال از حساب 111222333 به حساب 444555666 مبلغ 750,000 ریال"); assertEquals(listOf("111222333","444555666"),transfer.accounts)
        assertEquals(1500000L,event("برداشت مبلغ 1/500/000 ریال").amountIrr)
        assertEquals("برداشت خودپرداز",event("برداشت از خودپرداز\nمبلغ:2,000,000\nمانده:1,000,000").label)
    }
    @Test fun nonEventsAreNotTransactions() {
        assertTrue(parser.parse("رمز پویا: 123456\nمبلغ: 1,000,000 ریال\nخرید از فروشگاه","mellat") is ParseResult.Ignored)
        assertTrue(parser.parse("مشتری گرامی، با خرید بالای 500,000 ریال در قرعه کشی شرکت کنید. لغو11","mellat") is ParseResult.Ignored)
        assertTrue(parser.parse("تراکنش ناموفق: خرید مبلغ 1000 ریال","mellat") is ParseResult.Ignored)
        assertTrue(parser.parse("سررسید قسط شما به مبلغ 5,000,000 ریال فردا است. لطفا نسبت به پرداخت اقدام کنید","mellat") is ParseResult.Ignored)
        assertTrue(parser.parse("سلام، فردا ساعت 10 جلسه داریم","mellat") is ParseResult.Ignored)
        // Tracking numbers, dates and times never become the amount.
        assertEquals(120000L,event("خرید 120,000 ریال\nپیگیری: 987654321\n1404/07/12 09:15\nمانده 880,000 ریال").amountIrr)
    }
    @Test fun senderKeysIgnorePrefixSpellings() {
        assertEquals("700717",SmsSender.key("+98700717")); assertEquals(SmsSender.key("98700717"),SmsSender.key("0098 700 717"))
        assertEquals(SmsSender.key("09999920000"),SmsSender.key("+989999920000")); assertEquals(SmsSender.key("09999920000"),SmsSender.key("9999920000"))
        assertNotEquals(SmsSender.key("09999920000"),SmsSender.key("0000 992 0999"))
        assertEquals(SmsSender.key("Bank Mellat"),SmsSender.key("BANK-MELLAT")); assertNotEquals(SmsSender.key("Bank Mellat"),SmsSender.key("Bank Melli"))
    }
    @Test fun bankGuessedFromSenderOrBody() {
        assertEquals("mellat",Banks.guess("Bank Mellat")); assertEquals("melli",Banks.guess("BMI")); assertEquals("khavarmianeh",Banks.guess("MiddleEastBank"))
        assertEquals("melli",Banks.guess("+98700717",listOf("بانك ملي ايران\nانتقال:1,000-"))); assertEquals("sepah",Banks.guess("3000",listOf("*بانک سپه*\nبرداشت")))
        assertEquals("other",Banks.guess("12345",listOf("برداشت 1,000")))
    }
    @Test fun sameAmountTwiceNotMerged() { val body="خرید 1000 ریال"; assertEquals(Fingerprint.of("Bank",body,1),Fingerprint.of("Bank",body,1)); assertNotEquals(Fingerprint.of("Bank",body,1),Fingerprint.of("Bank",body,2)); assertNotEquals(Fingerprint.of("Bank",body,1),Fingerprint.of("Other",body,1)) }
    @Test fun transfersExcludedAndRefundsNet() { val items=listOf(FinanceItem(1000,Kind.EXPENSE,Direction.DEBIT),FinanceItem(200,Kind.REFUND,Direction.CREDIT),FinanceItem(5000,Kind.TRANSFER,Direction.DEBIT),FinanceItem(5000,Kind.TRANSFER,Direction.CREDIT),FinanceItem(3000,Kind.INCOME,Direction.CREDIT)); assertEquals(Totals(800,3000,2200,1000),Finance.totals(items)) }
    @Test fun debtCashFlowNotIncome() { val totals=Finance.totals(listOf(FinanceItem(100,Kind.BORROW,Direction.CREDIT),FinanceItem(30,Kind.LEND,Direction.DEBIT))); assertEquals(0L,totals.spending); assertEquals(0L,totals.income); assertEquals(70L,totals.cashFlow) }
    @Test fun budgetThresholdsExact() { assertEquals(listOf(70,90),Finance.crossedThresholds(90,100)); assertEquals(emptyList<Int>(),Finance.crossedThresholds(69,100)); assertEquals(listOf(70,90,100),Finance.crossedThresholds(Long.MAX_VALUE,Long.MAX_VALUE)) }
    @Test fun nowruzAndLeap() { assertEquals(PersianDate(1403,1,1),Jalali.fromGregorian(LocalDate.of(2024,3,20))); assertEquals(PersianDate(1404,1,1),Jalali.fromGregorian(LocalDate.of(2025,3,21))); assertTrue(Jalali.leap(1403)); assertFalse(Jalali.leap(1404)); assertEquals(LocalDate.of(2025,3,20),Jalali.toGregorian(PersianDate(1403,12,30))) }
    @Test fun yearsAreShownInImperialCountAndTypedInEither() {
        assertEquals("۲۵۸۵/۰۷/۱۹",PersianDate(1405,7,19).toString()); assertEquals("۲۵۸۵/۷",Jalali.shownMonth(1405,7))
        assertEquals(1405,Jalali.storedYear(2585)); assertEquals(1405,Jalali.storedYear(1405)); assertEquals(1403,Jalali.storedYear(Jalali.shownYear(1403)))
        assertEquals("۲۵۸۴/۰۷/۱۲ ۱۳:۴۵",Jalali.shownDateText("1404/07/12 13:45")); assertEquals("۲۵۸۴/۱۲/۳۰",Jalali.shownDateText("۱۴۰۴/۱۲/۳۰"))
        // A Gregorian year or a two-digit year is not a year this rule knows; it is left as the bank wrote it.
        assertEquals("۲۰۲۵/۱۰/۰۴",Jalali.shownDateText("2025/10/04")); assertEquals("۳۰/۱۲/۰۳",Jalali.shownDateText("30/12/03"))
        // Bank messages keep their own year; only what the owner sees changes.
        assertEquals(PersianDate(1404,7,12),Jalali.at(SmsTime.resolve("1404/07/12 13:45",0)!!))
    }
    @Test fun roundTripAcrossYears() { var day=LocalDate.of(1970,1,1); repeat(30000) { assertEquals(day,Jalali.toGregorian(Jalali.fromGregorian(day))); day=day.plusDays(1) } }
    @Test fun jalaliMonthBoundary() { val time=LocalDate.of(2025,3,21).atStartOfDay(Jalali.zone).toInstant().toEpochMilli(); val range=Jalali.monthRange(time); assertTrue(time in range); assertFalse(time-1 in range); assertEquals(PersianDate(1404,2,1),Jalali.at(range.last+1)) }
    @Test fun csvPreventsFormulaInjection() { assertEquals("\"'=SUM(A1)\"",Csv.cell("=SUM(A1)")); assertEquals("\"'  @evil\"",Csv.cell("  @evil")); assertEquals("\"a\"\"b\"",Csv.cell("a\"b")); assertTrue(Csv.encode(listOf(listOf("سلام"))).startsWith("\uFEFF")) }
}
