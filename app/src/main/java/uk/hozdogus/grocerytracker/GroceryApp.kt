package uk.hozdogus.grocerytracker

import android.app.Application
import kotlinx.coroutines.MainScope
import uk.hozdogus.grocerytracker.data.AppDatabase
import uk.hozdogus.grocerytracker.data.ItemRepository

class GroceryApp : Application() {
    /** Outlives activities, so an add keeps going if the screen rotates or the user leaves. */
    private val appScope = MainScope()

    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: ItemRepository by lazy { ItemRepository(this, db.items(), appScope) }
}
