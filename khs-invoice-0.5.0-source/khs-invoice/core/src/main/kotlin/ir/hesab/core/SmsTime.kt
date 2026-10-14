package ir.hesab.core

import java.time.*

object SmsTime {
    /** Only unambiguous four-digit-year dates are interpreted. SMS event time remains the dedup key. */
    fun resolve(value: String?,smsTime: Long): Long? {
        if(value==null) return smsTime
        return runCatching {
            val pieces=Money.normalize(value).trim().split(Regex("\\s+"))
            val date=pieces[0].split('/').map(String::toInt)
            require(date.size==3 && date[0] in 1300..2099)
            val local=if(date[0]<1700) Jalali.toGregorian(PersianDate(date[0],date[1],date[2])) else LocalDate.of(date[0],date[1],date[2])
            val time=if(pieces.size>1) LocalTime.parse(pieces[1].padStart(5,'0')) else Instant.ofEpochMilli(smsTime).atZone(Jalali.zone).toLocalTime()
            local.atTime(time).atZone(Jalali.zone).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
