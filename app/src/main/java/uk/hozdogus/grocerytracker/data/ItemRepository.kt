package uk.hozdogus.grocerytracker.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.hozdogus.grocerytracker.fetch.FetchResult
import uk.hozdogus.grocerytracker.fetch.PageFetcher
import uk.hozdogus.grocerytracker.fetch.ProductUrl

class ItemRepository(
    context: Context,
    private val dao: ItemDao,
    private val scope: CoroutineScope,
) {
    private val fetcher = PageFetcher(context)
    private val prefs = context.getSharedPreferences("checks", Context.MODE_PRIVATE)
    private val checkLock = Mutex()

    sealed class AddState {
        data object Idle : AddState()
        data class Working(val shop: Shop) : AddState()
        data class Added(val item: Item) : AddState()
        data class AlreadyTracked(val item: Item) : AddState()
        data class Rejected(val reason: String, val received: String) : AddState()
        data class Failed(val title: String, val reason: String, val details: String) : AddState()
    }

    private val _addState = MutableStateFlow<AddState>(AddState.Idle)

    /** Progress and outcome of the latest add. The UI calls [consumeAddState] after showing an outcome. */
    val addState: StateFlow<AddState> = _addState

    fun consumeAddState() {
        if (_addState.value !is AddState.Working) _addState.value = AddState.Idle
    }

    /** Adds the product linked in [text], which can be a bare URL or shared text containing one. */
    fun add(text: String) {
        if (_addState.value is AddState.Working) return
        val parsed = when (val r = ProductUrl.parse(text)) {
            is ProductUrl.Result.Invalid -> {
                _addState.value = AddState.Rejected(r.reason, text.trim())
                return
            }
            is ProductUrl.Result.Ok -> r
        }
        _addState.value = AddState.Working(parsed.shop)
        scope.launch {
            _addState.value = try {
                addParsed(parsed)
            } catch (e: Exception) {
                AddState.Failed("Couldn't add item", "Something went wrong.", "${parsed.url}\n$e")
            }
        }
    }

    private suspend fun addParsed(parsed: ProductUrl.Result.Ok): AddState {
        dao.findByUrl(parsed.url)?.let { return AddState.AlreadyTracked(it) }

        return when (val result = fetcher.fetch(parsed.url)) {
            is FetchResult.Success -> {
                val now = System.currentTimeMillis()
                val item = Item(
                    url = parsed.url,
                    shop = parsed.shop,
                    name = result.name,
                    imageUrl = result.imageUrl,
                    currentPrice = result.price,
                    lowestPrice = result.price,
                    highestPrice = result.price,
                    addedAt = now,
                    lastCheckedAt = now,
                )
                val id = dao.insert(item)
                dao.insertPrice(PricePoint(itemId = id, price = result.price, checkedAt = now))
                AddState.Added(item.copy(id = id))
            }
            is FetchResult.Blocked -> AddState.Failed(
                "${parsed.shop.label} blocked the check",
                "The page showed a block or robot check instead of the product. Try again in a few minutes.",
                result.details,
            )
            is FetchResult.Failed -> AddState.Failed("Couldn't add item", result.reason, result.details)
        }
    }

    suspend fun delete(item: Item) = dao.delete(item)

    suspend fun setTarget(item: Item, pence: Int?) = dao.update(item.copy(targetPrice = pence))

    sealed class CheckState {
        data object Idle : CheckState()
        data class Running(val done: Int, val total: Int, val name: String) : CheckState()
        data class Finished(val summary: String) : CheckState()
    }

    private val _checkState = MutableStateFlow<CheckState>(CheckState.Idle)

    /** Progress of the latest "Check now". The UI calls [consumeCheckState] after showing the summary. */
    val checkState: StateFlow<CheckState> = _checkState

    fun consumeCheckState() {
        if (_checkState.value is CheckState.Finished) _checkState.value = CheckState.Idle
    }

    private val _lastRunAt = MutableStateFlow(prefs.getLong(KEY_LAST_RUN, 0L).takeIf { it > 0 })

    /** When every item was last checked together. */
    val lastRunAt: StateFlow<Long?> = _lastRunAt

    /** Checks every item now, showing progress through [checkState]. */
    fun checkAllNow() = checkNow(null)

    /** Checks one item now, showing progress through [checkState]. */
    fun checkItemNow(id: Long) = checkNow(id)

    private fun checkNow(id: Long?) {
        if (_checkState.value is CheckState.Running) return
        _checkState.value = CheckState.Running(0, 0, "")
        scope.launch {
            _checkState.value = try {
                val items = if (id == null) dao.getAll() else listOfNotNull(dao.get(id))
                CheckState.Finished(summarise(checkItems(items, recordRun = id == null)))
            } catch (e: Exception) {
                CheckState.Finished("Check failed: $e")
            }
        }
    }

    /**
     * Checks [items] one at a time and saves the results. A failed page leaves the item's prices
     * alone and marks it "couldn't check".
     */
    suspend fun checkItems(items: List<Item>, recordRun: Boolean): List<CheckOutcome> = checkLock.withLock {
        val outcomes = mutableListOf<CheckOutcome>()
        items.forEachIndexed { index, item ->
            _checkState.value = CheckState.Running(index, items.size, item.name)
            val result = fetcher.fetch(item.url)
            // Re-read so an edit made during the fetch (such as a new target price) isn't lost.
            val latest = dao.get(item.id) ?: return@forEachIndexed
            val outcome = applyCheck(latest, result, System.currentTimeMillis())
            dao.update(outcome.item)
            outcome.point?.let { dao.insertPrice(it) }
            outcomes += outcome
        }
        if (recordRun) {
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_RUN, now).apply()
            _lastRunAt.value = now
        }
        outcomes
    }

    private fun summarise(outcomes: List<CheckOutcome>): String {
        if (outcomes.isEmpty()) return "Nothing to check."
        val cheaper = outcomes.count { it.dropped }
        val failed = outcomes.count { it.point == null }
        val parts = mutableListOf("Checked ${outcomes.size} item${if (outcomes.size == 1) "" else "s"}")
        parts += if (cheaper == 0) "no drops" else "$cheaper cheaper"
        if (failed > 0) parts += "$failed couldn't be checked"
        return parts.joinToString(" · ")
    }

    private companion object {
        const val KEY_LAST_RUN = "last_run_at"
    }
}
