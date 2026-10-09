package com.example.giaohangpro

/** Keep coordinates as supplied: never round, average or combine axes from different fixes. */
internal object CustomerCoordinateSafety {
    private const val NUMBER = "[-+]?\\d+(?:[.,]\\d+)?(?:[eE][-+]?\\d+)?"
    private val single = Regex("^$NUMBER$")
    private val pair = Regex("^($NUMBER)(?:\\s*[,;]\\s*|\\s+)($NUMBER)$")

    fun input(raw: String, latitudeField: Boolean, lat: String, lng: String): Pair<String, String> {
        val text = raw.trim()
        // A complete single number wins over pair parsing, including decimal commas/exponents.
        if (!single.matches(text)) {
            val plain = text.replace(Regex("(?i)(vĩ độ|kinh độ|latitude|longitude)\\s*:\\s*"), "").trim()
            val match = pair.matchEntire(plain)
            if (match != null) {
                val a = match.groupValues[1].replace(',', '.')
                val b = match.groupValues[2].replace(',', '.')
                if (valid(a, b)) return a to b
                if (valid(b, a)) return b to a
            }
        }
        val value = text.replace(',', '.')
        return if (latitudeField) value to lng else lat to value
    }

    fun valid(lat: String, lng: String): Boolean {
        val a = lat.toDoubleOrNull() ?: return false
        val b = lng.toDoubleOrNull() ?: return false
        return a.isFinite() && b.isFinite() && a in -90.0..90.0 && b in -180.0..180.0
    }

    fun fillEmptyPair(lat: String, lng: String, incomingLat: String, incomingLng: String): Pair<String, String> =
        if (lat.isBlank() && lng.isBlank() && valid(incomingLat, incomingLng)) incomingLat to incomingLng
        else lat to lng

    fun positions(customer: Customer): List<Pair<String, String>> =
        listOf(customer.latitude to customer.longitude) + customer.extraAddresses.map { it.latitude to it.longitude }

    fun preferredCoordinates(customer: Customer?, orderLat: String, orderLng: String): Pair<String, String> = when {
        customer != null && valid(customer.latitude, customer.longitude) -> customer.latitude to customer.longitude
        valid(orderLat, orderLng) -> orderLat to orderLng
        else -> "" to ""
    }

    fun updatePhoto(current: Customer, uri: String): Customer = current.copy(photoUri = uri)

    fun canSave(current: Customer?, original: Customer): Boolean = current == original

    fun unchangedSinceImport(current: List<Customer>, original: List<Customer>): Boolean = current == original
}
