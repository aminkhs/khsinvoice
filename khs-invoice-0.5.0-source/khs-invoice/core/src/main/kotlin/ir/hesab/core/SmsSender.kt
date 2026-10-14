package ir.hesab.core

import java.util.Locale

/** Keep digit order and leading zeroes; bidi affects display, never parsing order. */
object SmsSender {
    fun normalize(value: String): String {
        val clean=Money.normalize(value).filterNot {
            it=='؜' || it=='‎' || it=='‏' || it in '‪'..'‮' || it in '⁦'..'⁩'
        }.trim()
        fun separator(c: Char) = c.isWhitespace() || Character.isSpaceChar(c) || c=='-' || c in "​‌‍﻿"
        if(clean.any { it in '0'..'9' } && clean.all { it in '0'..'9' || it=='+' || separator(it) }) {
            return clean.filterNot(::separator)
        }
        return clean.uppercase(Locale.ROOT)
    }
    /**
     * Matching key. Phones and operators show one sender as +98…, 0098…, 98… or 0…; all of them
     * compare equal here. Names ignore case, spaces and punctuation.
     */
    fun key(value: String): String {
        val normal=normalize(value)
        if(normal.isEmpty()) return normal
        if(normal.all { it in '0'..'9' || it=='+' }) {
            var digits=normal.filter { it in '0'..'9' }
            if(digits.startsWith("00")) digits=digits.drop(2)
            if(digits.startsWith("98") && digits.length>4) digits=digits.drop(2)
            return digits.trimStart('0').ifEmpty { normal }
        }
        return normal.filter { it.isLetterOrDigit() }.ifEmpty { normal }
    }
}
