package uk.hozdogus.grocerytracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One successful price reading for an item. Prices are in pence. */
@Entity(
    tableName = "price_points",
    foreignKeys = [ForeignKey(
        entity = Item::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("itemId")],
)
data class PricePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val price: Int,
    val wasPrice: Int? = null,
    val nectarPrice: Int? = null,
    val checkedAt: Long = System.currentTimeMillis(),
)
