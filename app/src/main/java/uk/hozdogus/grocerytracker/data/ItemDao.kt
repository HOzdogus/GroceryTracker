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

    // Groups

    @Query("SELECT * FROM product_groups ORDER BY name COLLATE NOCASE")
    fun observeGroups(): Flow<List<ProductGroup>>

    @Query("SELECT * FROM product_groups ORDER BY name COLLATE NOCASE")
    suspend fun getGroups(): List<ProductGroup>

    @Query("SELECT * FROM product_groups WHERE id = :id")
    fun observeGroup(id: Long): Flow<ProductGroup?>

    @Query("SELECT * FROM product_groups WHERE id = :id")
    suspend fun getGroup(id: Long): ProductGroup?

    @Insert
    suspend fun insertGroup(group: ProductGroup): Long

    @Query("UPDATE product_groups SET name = :name WHERE id = :id")
    suspend fun renameGroup(id: Long, name: String)

    @Query("UPDATE items SET groupId = :groupId WHERE id = :itemId")
    suspend fun setGroup(itemId: Long, groupId: Long?)

    @Query("UPDATE items SET groupId = NULL WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: Long)

    @Query("DELETE FROM product_groups WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    /** Groups left with no items are removed, so the list never shows an empty group. */
    @Query("DELETE FROM product_groups WHERE id NOT IN (SELECT groupId FROM items WHERE groupId IS NOT NULL)")
    suspend fun deleteEmptyGroups()

    @Query("SELECT * FROM items WHERE groupId = :groupId ORDER BY currentPrice IS NULL, currentPrice")
    fun observeGroupItems(groupId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE groupId = :groupId")
    suspend fun getGroupItems(groupId: Long): List<Item>

    @Query(
        "SELECT price_points.* FROM price_points JOIN items ON price_points.itemId = items.id " +
            "WHERE items.groupId = :groupId ORDER BY price_points.checkedAt"
    )
    fun observeGroupHistory(groupId: Long): Flow<List<PricePoint>>
}
