package uk.hozdogus.grocerytracker.ui

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

class ItemAdapter(
    private val onClick: (Item) -> Unit,
    private val onLongClick: (Item) -> Unit,
) : ListAdapter<Item, ItemAdapter.Holder>(Diff) {

    class Holder(val binding: ItemRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        val ctx = b.root.context
        val now = System.currentTimeMillis()
        b.shopBadge.text = item.shop.label
        b.shopBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, shopColor(item.shop)))
        b.name.text = item.name
        b.price.text = formatPence(item.currentPrice)

        val change = recentChange(item, now)
        b.change.isVisible = change != null
        if (change != null) {
            b.change.text = formatChange(change)
            b.change.setTextColor(ContextCompat.getColor(ctx, if (change < 0) R.color.price_down else R.color.price_up))
        }

        val subtitle = mutableListOf("Lowest ${formatPence(item.lowestPrice)}")
        item.targetPrice?.let { subtitle += "target ${formatPence(it)}" }
        if (!item.lastCheckOk) subtitle += "⚠ couldn't check"
        b.subtitle.text = subtitle.joinToString(" · ")

        b.root.setOnClickListener { onClick(item) }
        b.root.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    private object Diff : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(a: Item, b: Item) = a.id == b.id
        override fun areContentsTheSame(a: Item, b: Item) = a == b
    }
}

fun shopColor(shop: Shop) = if (shop == Shop.ASDA) R.color.asda_green else R.color.sainsburys_orange
