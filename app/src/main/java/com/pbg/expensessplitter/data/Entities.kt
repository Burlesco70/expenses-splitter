package com.pbg.expensessplitter.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "trip")
data class TripE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val currency: String,
    val createdAt: Long,
)

@Entity(
    tableName = "person",
    foreignKeys = [ForeignKey(TripE::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class PersonE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val email: String = "",
    val phone: String = "",
)

@Entity(
    tableName = "expense",
    foreignKeys = [
        ForeignKey(TripE::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PersonE::class, ["id"], ["payerId"]),
    ],
    indices = [Index("tripId"), Index("payerId")],
)
data class ExpenseE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val title: String,
    val payerId: Long,
    val amountCents: Long,
    val currency: String,
    val rate: Double,
    val baseCents: Long,
    val category: String,
    val dateMillis: Long,
    val kind: String,
    val rule: String,
    val receiptPath: String?,
)

@Entity(
    tableName = "expense_share",
    primaryKeys = ["expenseId", "personId"],
    foreignKeys = [
        ForeignKey(ExpenseE::class, ["id"], ["expenseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PersonE::class, ["id"], ["personId"]),
    ],
    indices = [Index("personId")],
)
data class ShareE(
    val expenseId: Long,
    val personId: Long,
    val shareCents: Long,
    /** editor input: basis points for PERCENT, cents of the expense currency for FIXED; null for EQUAL */
    val value: Long?,
)

data class ExpenseWithShares(
    @Embedded val expense: ExpenseE,
    @Relation(parentColumn = "id", entityColumn = "expenseId") val shares: List<ShareE>,
)
