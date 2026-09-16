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
