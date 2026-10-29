package uk.hozdogus.grocerytracker

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uk.hozdogus.grocerytracker.data.Item
import uk.hozdogus.grocerytracker.data.ProductGroup
import uk.hozdogus.grocerytracker.data.ItemRepository.CheckState
import uk.hozdogus.grocerytracker.databinding.ActivityDetailBinding
import uk.hozdogus.grocerytracker.databinding.DialogTargetBinding
import uk.hozdogus.grocerytracker.ui.ImageLoader
import uk.hozdogus.grocerytracker.ui.formatChange
import uk.hozdogus.grocerytracker.ui.formatPence
import uk.hozdogus.grocerytracker.ui.formatWhen
import uk.hozdogus.grocerytracker.ui.offerText
import uk.hozdogus.grocerytracker.ui.parsePence
import uk.hozdogus.grocerytracker.ui.pickGroup
import uk.hozdogus.grocerytracker.ui.recentChange

class ItemDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val app by lazy { application as GroceryApp }
    private val repository by lazy { app.repository }
    private var item: Item? = null
    private var loadedImage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val id = intent.getLongExtra(EXTRA_ID, -1)
        val dao = app.db.items()

        binding.setTarget.setOnClickListener { item?.let(::editTarget) }
        binding.checkNow.setOnClickListener { repository.checkItemNow(id) }
        binding.openWebsite.setOnClickListener {
            item?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) }
        }
        binding.delete.setOnClickListener { item?.let(::confirmDelete) }
        binding.changeGroup.setOnClickListener {
            item?.let { pickGroup(repository, it, getString(R.string.change_group)) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(dao.observe(id), dao.observeHistory(id), dao.observeGroups()) { item, history, groups ->
                        Triple(item, history, groups)
                    }.collect { (item, history, groups) ->
                        if (item == null) {
                            finish()
                        } else {
                            show(item)
                            showGroup(groups.firstOrNull { it.id == item.groupId })
                            binding.chart.setData(history, item.targetPrice)
                        }
                    }
                }
                launch { repository.checkState.collect(::showCheckState) }
            }
        }
    }

    private fun show(item: Item) {
        this.item = item
        supportActionBar?.title = item.shop.label
        binding.shopBadge.text = item.shop.label
        binding.shopBadge.backgroundTintList = ColorStateList.valueOf(item.shop.color)
        binding.name.text = item.name
        binding.price.text = formatPence(item.currentPrice)

        val change = recentChange(item, System.currentTimeMillis())
        binding.change.isVisible = change != null
        if (change != null) {
            binding.change.text = "${formatChange(change)} from ${formatPence(item.previousPrice)}"
            binding.change.setTextColor(
                ContextCompat.getColor(this, if (change < 0) R.color.price_down else R.color.price_up)
            )
        }

        val offer = buildList {
            item.wasPrice?.let { add("Offer — was ${formatPence(it)}") }
            item.nectarPrice?.let { add("${formatPence(it)} with Nectar") }
        }
        binding.offer.isVisible = offerText(item) != null
        binding.offer.text = offer.joinToString("\n")

        binding.range.text = "Lowest ${formatPence(item.lowestPrice)} · Highest ${formatPence(item.highestPrice)}"
        binding.lastChecked.text = buildString {
            item.lastCheckedAt?.let { append("Last checked ").append(formatWhen(it)) }
            if (!item.lastCheckOk) append(" · ⚠ couldn't check")
        }
        binding.checkError.isVisible = !item.lastCheckOk && item.lastCheckError != null
        binding.checkError.text = item.lastCheckError

        binding.target.text = item.targetPrice?.let { "Target: ${formatPence(it)}" } ?: "No target price"
        binding.setTarget.text = getString(if (item.targetPrice == null) R.string.set_target else R.string.edit_target)

        val image = item.imageUrl
        if (image != null && image != loadedImage) {
            loadedImage = image
            lifecycleScope.launch { ImageLoader.load(image)?.let(binding.image::setImageBitmap) }
        }
        binding.image.isVisible = image != null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_DIAGNOSE, Menu.NONE, R.string.diagnostics)
        return true
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId != MENU_DIAGNOSE) return super.onOptionsItemSelected(menuItem)
        val item = item ?: return true
        val progress = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.diagnostics)
            .setMessage("Reading the ${item.shop.label} page… (up to 30 seconds)")
            .setCancelable(false)
            .show()
        lifecycleScope.launch {
            val report = repository.diagnose(item)
            progress.dismiss()
            val dialog = MaterialAlertDialogBuilder(this@ItemDetailActivity)
                .setTitle(R.string.diagnostics)
                .setMessage(report)
                .setPositiveButton(R.string.ok, null)
                .show()
            dialog.findViewById<android.widget.TextView>(android.R.id.message)?.setTextIsSelectable(true)
        }
        return true
    }

    private fun showGroup(group: ProductGroup?) {
        binding.groupLabel.text = group?.let { "Group: ${it.name}" } ?: "Not in a group"
        binding.changeGroup.text = getString(if (group == null) R.string.add_to_group_short else R.string.change)
    }

    private fun showCheckState(state: CheckState) {
        val running = state is CheckState.Running
        binding.checkNow.isEnabled = !running
        binding.checkNow.text = if (running) getString(R.string.checking) else getString(R.string.check_now)
        if (state is CheckState.Finished) {
            Snackbar.make(binding.root, state.summary, Snackbar.LENGTH_LONG).show()
            repository.consumeCheckState()
        }
    }

    private fun editTarget(item: Item) {
        val dialogBinding = DialogTargetBinding.inflate(layoutInflater)
        item.targetPrice?.let { dialogBinding.targetInput.setText(formatPence(it).removePrefix("£")) }
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.set_target)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.ok) { _, _ ->
                val pence = parsePence(dialogBinding.targetInput.text?.toString().orEmpty())
                lifecycleScope.launch { repository.setTarget(item, pence) }
            }
            .setNegativeButton(R.string.cancel, null)
        if (item.targetPrice != null) {
            builder.setNeutralButton(R.string.clear) { _, _ ->
                lifecycleScope.launch { repository.setTarget(item, null) }
            }
        }
        builder.show()
    }

    private fun confirmDelete(item: Item) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete item?")
            .setMessage(item.name)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repository.delete(item)
                    finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val EXTRA_ID = "item_id"
        private const val MENU_DIAGNOSE = 1

        fun intent(context: Context, id: Long): Intent =
            Intent(context, ItemDetailActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
