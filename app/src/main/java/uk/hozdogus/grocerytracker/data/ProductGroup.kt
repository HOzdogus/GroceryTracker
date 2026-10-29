package uk.hozdogus.grocerytracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The same product tracked at several shops, shown as one row with the cheapest price. */
@Entity(tableName = "product_groups")
data class ProductGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)
