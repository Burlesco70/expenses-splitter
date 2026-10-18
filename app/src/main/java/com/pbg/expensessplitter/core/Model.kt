package com.pbg.expensessplitter.core

enum class SplitRule { EQUAL, FIXED, PERCENT }
enum class Kind { EXPENSE, TRANSFER }

val CATEGORIES = listOf("food", "transport", "lodging", "leisure", "shopping", "other")

data class PersonData(val id: Long, val name: String, val email: String = "", val phone: String = "")

/**
 * [amountCents] is in [currency]; [baseCents] is the same amount converted into the trip currency
 * (rate applied). [shares] are in trip currency and always sum to [baseCents].
 * [values] keeps the editor input (percent in basis points, fixed in cents of [currency]).
 */
data class ExpenseData(
    val id: Long,
    val title: String,
    val payerId: Long,
    val amountCents: Long,
    val currency: String,
    val rate: Double,
    val baseCents: Long,
    val category: String,
    val dateMillis: Long,
    val kind: Kind,
    val rule: SplitRule,
    val shares: Map<Long, Long>,
    val values: Map<Long, Long> = emptyMap(),
    val receiptPath: String? = null,
)

data class TripData(
    val id: Long,
    val name: String,
    val description: String,
    val currency: String,
    val persons: List<PersonData>,
    val expenses: List<ExpenseData>,
)
