package ir.hesab.core

import java.time.*

/** [year] is the Hijri solar year (1405), which bank messages and stored budget periods use. */
data class PersianDate(val year: Int, val month: Int, val day: Int) {
    override fun toString() = Money.persian("${Jalali.shownYear(year)}/${month.toString().padStart(2,'0')}/${day.toString().padStart(2,'0')}")
}
object Jalali {
    val zone: ZoneId = ZoneId.of("Asia/Tehran")
    // The owner reads years in the imperial count of the same solar calendar: 1405 is shown as 2585.
    // Only the year number differs; months and days are identical.
    private const val SHOWN_YEAR_OFFSET = 1180
    fun shownYear(year: Int) = year + SHOWN_YEAR_OFFSET
    /** Accepts a typed year in either count. */
    fun storedYear(typed: Int) = if(typed >= 2400) typed - SHOWN_YEAR_OFFSET else typed
    /** A date quoted from a bank message («1404/07/12 13:45») with its year in the shown count. Other shapes pass through. */
    fun shownDateText(text: String) = Money.persian(Regex("^(1[34][0-9]{2})(?=/)").replace(Money.normalize(text).trim()) { shownYear(it.value.toInt()).toString() })
    /** Year and month for headings, e.g. «۲۵۸۵/۷». */
    fun shownMonth(year: Int, month: Int) = Money.persian("${shownYear(year)}/$month")
    private val breaks = intArrayOf(-61,9,38,199,426,686,756,818,1111,1181,1210,1635,2060,2097,2192,2262,2324,2394,2456,3178)
    // Borkowski break-year algorithm, restricted to its published supported interval.
    fun marchDay(jy: Int): Int {
        require(jy in -61..3177)
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        for(jm in breaks.drop(1)) {
            val jump = jm - jp
            if(jy < jm) break
            leapJ += jump / 33 * 8 + (jump % 33) / 4
            jp = jm
        }
        val n = jy - jp
        leapJ += n / 33 * 8 + (n % 33 + 3) / 4
        val next = breaks.first { it > jy }
        if((next - jp) % 33 == 4 && next - jp - n == 4) leapJ++
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        return 20 + leapJ - leapG
    }
    fun startYear(year: Int) = LocalDate.of(year + 621, 3, marchDay(year))
    fun leap(year: Int) = startYear(year + 1).toEpochDay() - startYear(year).toEpochDay() == 366L
    fun toGregorian(date: PersianDate): LocalDate {
        require(date.month in 1..12)
        val length = if(date.month <= 6) 31 else if(date.month <= 11 || leap(date.year)) 30 else 29
        require(date.day in 1..length)
        val offset = if(date.month <= 7) (date.month-1)*31 else 186+(date.month-7)*30
        return startYear(date.year).plusDays((offset+date.day-1).toLong())
    }
    fun fromGregorian(date: LocalDate): PersianDate {
        var year = date.year - 621
        if(date < startYear(year)) year--
        val days = (date.toEpochDay()-startYear(year).toEpochDay()).toInt()
        return if(days < 186) PersianDate(year, days/31+1, days%31+1) else PersianDate(year,(days-186)/30+7,(days-186)%30+1)
    }
    fun at(timestamp: Long) = fromGregorian(Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate())
    fun monthRange(timestamp: Long): LongRange {
        val date = at(timestamp)
        val start = toGregorian(PersianDate(date.year,date.month,1)).atStartOfDay(zone).toInstant().toEpochMilli()
        val next = if(date.month == 12) PersianDate(date.year+1,1,1) else PersianDate(date.year,date.month+1,1)
        return start until toGregorian(next).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
