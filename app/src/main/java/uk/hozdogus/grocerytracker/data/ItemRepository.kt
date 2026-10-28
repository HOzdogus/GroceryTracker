package uk.hozdogus.grocerytracker.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.hozdogus.grocerytracker.fetch.FetchResult
import uk.hozdogus.grocerytracker.fetch.PageFetcher
import uk.hozdogus.grocerytracker.fetch.ProductUrl

class ItemRepository(
    context: Context,
    private val dao: ItemDao,
    private val scope: CoroutineScope,
) {
    private val fetcher = PageFetcher(context)

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
}
