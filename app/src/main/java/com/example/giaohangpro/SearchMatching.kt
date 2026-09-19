package com.example.giaohangpro

import java.text.Normalizer
import java.util.Locale

private val searchMarks = Regex("\\p{M}+")
private val searchSpaces = Regex("[\\s\\u00A0]+")

internal fun normalizeCustomerSearch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(searchMarks, "")
        .lowercase(Locale.ROOT)
        .replace('đ', 'd')
        .replace(searchSpaces, " ")
        .trim()

private fun normalizedVietnamPhoneDigits(value: String): String {
    val digits = value.filter(Char::isDigit)
    return when {
        digits.startsWith("0084") && digits.length > 4 -> digits.drop(4)
        digits.startsWith("84") && digits.length > 2 -> digits.drop(2)
        digits.startsWith("0") && digits.length > 1 -> digits.drop(1)
        else -> digits
    }
}

internal fun matchesPhoneSearch(phone: String, query: String): Boolean {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty() || cleanQuery.any(Char::isLetter)) return false
    val queryDigits = normalizedVietnamPhoneDigits(cleanQuery)
    if (queryDigits.isEmpty()) return false
    return normalizedVietnamPhoneDigits(phone).contains(queryDigits)
}

private fun codSearchDigits(value: String): String {
    val clean = value.trim().lowercase(Locale.ROOT)
        .replace(Regex("(?:vnd|vnđ|đ|₫|d)$"), "").trim()
    if (clean.isEmpty() || clean.any { !it.isDigit() && it !in "., " && !it.isWhitespace() && it != '\u00A0' }) return ""
    return clean.filter { it in '0'..'9' }
}

internal fun matchesCodSearch(amount: String, query: String): Boolean {
    val digits = codSearchDigits(query)
    return digits.isNotEmpty() && codSearchDigits(amount).contains(digits)
}
