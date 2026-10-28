package uk.hozdogus.grocerytracker.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Minimal product-image loader with an in-memory cache. Returns null on any failure. */
object ImageLoader {
    private val cache = LruCache<String, Bitmap>(20)

    suspend fun load(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) GroceryTracker")
                conn.inputStream.use { BitmapFactory.decodeStream(it) }?.also { cache.put(url, it) }
            } catch (e: Exception) {
                null
            }
        }
    }
}
