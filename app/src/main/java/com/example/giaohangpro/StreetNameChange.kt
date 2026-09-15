package com.example.giaohangpro

internal data class StreetNameChange(
    val names: List<String>,
    val customers: List<Customer>,
    val lessons: List<List<StreetRouteLearning.Visit>>
) {
    companion object {
        fun prepare(
            oldName: String, newName: String, names: List<String>, customers: List<Customer>,
            lessons: List<List<StreetRouteLearning.Visit>>
        ): StreetNameChange {
            val old = oldName.trim()
            val clean = newName.trim()
            fun matches(value: String) = value.trim().equals(old, ignoreCase = true)
            require(old.isNotBlank() && names.any(::matches)) { "Tên đường này không còn tồn tại." }
            require(clean.isNotBlank()) { "Vui lòng nhập tên đường." }
            require(names.none { !matches(it) && it.trim().equals(clean, ignoreCase = true) }) {
                "Tên đường đã tồn tại. Hãy chọn tên khác."
            }
            val affected = customers.filter { matches(it.streetName) }.map { it.id }.toSet()
            return StreetNameChange(
                names.map { if (matches(it)) clean else it },
                customers.map { if (it.id in affected) it.copy(streetName = clean) else it },
                lessons.map { route -> route.map { visit ->
                    if (visit.customerId in affected && matches(visit.street)) visit.copy(street = clean)
                    else visit
                } }
            )
        }
    }
}
