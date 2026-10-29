package uk.hozdogus.grocerytracker.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import uk.hozdogus.grocerytracker.R
import uk.hozdogus.grocerytracker.data.Item
import uk.hozdogus.grocerytracker.data.Shop
import uk.hozdogus.grocerytracker.databinding.ItemRowBinding

/**
 * Rows for the main list (items and groups) and for a group's shop list.
 * On a group's screen, pass [markCheapest] to label the cheapest shop's row.
 */
class EntryAdapter(
    private val onClick: (ListEntry) -> Unit,
    private val onLongClick: (ListEntry) -> Unit = {},
    private val markCheapest: Boolean = false,
) : ListAdapter<ListEntry, EntryAdapter.Holder>(Diff) {

    class Holder(val binding: ItemRowBinding) : RecyclerView.ViewHolder(binding.root)

    private var cheapestIds: Set<Long> = emptySet()

    /** Like submitList, but works out which items are cheapest for [markCheapest]. */
    @SuppressLint("NotifyDataSetChanged")
    fun submitItems(items: List<Item>) {
        // Only worth calling out when there is something to compare.
        cheapestIds = if (items.size > 1) cheapest(items).map { it.id }.toSet() else emptySet()
        // Rows whose item didn't change still need rebinding when the cheapest shop changes.
        submitList(items.map { ListEntry.Single(it) }) { notifyDataSetChanged() }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        when (entry) {
            is ListEntry.Single -> bindItem(holder.binding, entry.item)
            is ListEntry.Group -> bindGroup(holder.binding, entry)
        }
        holder.binding.root.setOnClickListener { onClick(entry) }
        holder.binding.root.setOnLongClickListener {
            onLongClick(entry)
            true
        }
    }

    private fun bindItem(b: ItemRowBinding, item: Item) {
        bindShopAndPrice(b, item.shop, item)
        b.name.text = item.name
        val subtitle = mutableListOf<String>()
        if (markCheapest && item.id in cheapestIds) {
            subtitle += if (cheapestIds.size > 1) "✓ Joint cheapest" else "✓ Cheapest"
        }
        offerText(item)?.let { subtitle += it }
        subtitle += "Lowest ${formatPence(item.lowestPrice)}"
        item.targetPrice?.let { subtitle += "target ${formatPence(it)}" }
        if (!item.lastCheckOk) subtitle += "⚠ couldn't check"
        b.subtitle.text = subtitle.joinToString(" · ")
    }

    private fun bindGroup(b: ItemRowBinding, entry: ListEntry.Group) {
        val lead = entry.lead
        bindShopAndPrice(b, lead.shop, lead)
        b.name.text = entry.group.name
        val tied = entry.tied
        val shops = entry.items.size
        val subtitle = mutableListOf<String>()
        subtitle += when {
            lead.currentPrice == null -> "No price yet"
            tied.size > 1 -> "Same price at ${tied.joinToString(" and ") { it.shop.label }}"
            else -> "Cheapest at ${lead.shop.label}"
        }
        subtitle += "$shops shop${if (shops == 1) "" else "s"}"
        if (entry.items.any { !it.lastCheckOk }) subtitle += "⚠ couldn't check"
        b.subtitle.text = subtitle.joinToString(" · ")
    }

    private fun bindShopAndPrice(b: ItemRowBinding, shop: Shop, item: Item) {
        val ctx = b.root.context
        b.shopBadge.text = shop.label
        b.shopBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, shopColor(shop)))
        b.price.text = formatPence(item.currentPrice)
        val change = recentChange(item, System.currentTimeMillis())
        b.change.isVisible = change != null
        if (change != null) {
            b.change.text = formatChange(change)
            b.change.setTextColor(ContextCompat.getColor(ctx, if (change < 0) R.color.price_down else R.color.price_up))
        }
    }

    private object Diff : DiffUtil.ItemCallback<ListEntry>() {
        override fun areItemsTheSame(a: ListEntry, b: ListEntry) = a.key == b.key
        override fun areContentsTheSame(a: ListEntry, b: ListEntry) = a == b
    }
}

fun shopColor(shop: Shop) = if (shop == Shop.ASDA) R.color.asda_green else R.color.sainsburys_orange
