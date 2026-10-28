package uk.hozdogus.grocerytracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
        b.shopBadge.text = item.shop.label
        b.shopBadge.background.setTint(
            ContextCompat.getColor(
                ctx,
                if (item.shop == Shop.ASDA) R.color.asda_green else R.color.sainsburys_orange,
            )
        )
        b.name.text = item.name
        b.price.text = formatPence(item.currentPrice)
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
