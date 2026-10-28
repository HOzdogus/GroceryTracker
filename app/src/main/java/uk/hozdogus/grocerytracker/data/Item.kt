package uk.hozdogus.grocerytracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A tracked product. All prices are in pence. */
@Entity(tableName = "items", indices = [Index(value = ["url"], unique = true)])
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val shop: Shop,
    val name: String,
    val imageUrl: String? = null,
    val currentPrice: Int? = null,
    val previousPrice: Int? = null,
    val lowestPrice: Int? = null,
    val highestPrice: Int? = null,
    val targetPrice: Int? = null,
    val wasPrice: Int? = null,
    val nectarPrice: Int? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastCheckedAt: Long? = null,
    val lastCheckOk: Boolean = true,
    /** Why the last check failed, shown on the detail screen. Null after a successful check. */
    val lastCheckError: String? = null,
    val lastChangeAt: Long? = null,
)
