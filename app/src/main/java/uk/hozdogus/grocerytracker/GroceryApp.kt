package uk.hozdogus.grocerytracker

import android.app.Application
import uk.hozdogus.grocerytracker.data.AppDatabase

class GroceryApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
}
