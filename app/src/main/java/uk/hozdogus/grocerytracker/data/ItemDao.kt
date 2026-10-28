package uk.hozdogus.grocerytracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Item>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observe(id: Long): Flow<Item?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): Item?

    @Query("SELECT * FROM items WHERE url = :url")
    suspend fun findByUrl(url: String): Item?

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Insert
    suspend fun insertPrice(point: PricePoint)

    @Query("SELECT * FROM price_points WHERE itemId = :itemId ORDER BY checkedAt")
    fun observeHistory(itemId: Long): Flow<List<PricePoint>>
}
