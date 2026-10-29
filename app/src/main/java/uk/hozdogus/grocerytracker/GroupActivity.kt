package uk.hozdogus.grocerytracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uk.hozdogus.grocerytracker.data.Item
import uk.hozdogus.grocerytracker.data.ItemRepository.AddState
import uk.hozdogus.grocerytracker.data.ItemRepository.CheckState
import uk.hozdogus.grocerytracker.data.PricePoint
import uk.hozdogus.grocerytracker.databinding.ActivityGroupBinding
import uk.hozdogus.grocerytracker.ui.EntryAdapter
import uk.hozdogus.grocerytracker.ui.ListEntry
import uk.hozdogus.grocerytracker.ui.PriceChartView
import uk.hozdogus.grocerytracker.ui.askGroupName
import uk.hozdogus.grocerytracker.ui.cheapest
import uk.hozdogus.grocerytracker.ui.formatPence
import uk.hozdogus.grocerytracker.ui.showAddDialog

/** One product across shops: which is cheapest now, each shop's price, and their history together. */
class GroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupBinding
    private val app by lazy { application as GroceryApp }
    private val repository by lazy { app.repository }
    private var groupId = -1L
    private var groupName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        groupId = intent.getLongExtra(EXTRA_ID, -1)
        val dao = app.db.items()

        val adapter = EntryAdapter(
            onClick = { (it as? ListEntry.Single)?.let { e -> startActivity(ItemDetailActivity.intent(this, e.item.id)) } },
            markCheapest = true,
        )
        binding.shops.layoutManager = LinearLayoutManager(this)
        binding.shops.adapter = adapter

        binding.checkNow.setOnClickListener { repository.checkGroupNow(groupId) }
        binding.addShop.setOnClickListener {
            showAddDialog(getString(R.string.add_shop)) { repository.add(it, groupId) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dao.observeGroup(groupId).collect { group ->
                        // The group goes when its last item is deleted or moved out.
                        if (group == null) finish() else {
                            groupName = group.name
                            supportActionBar?.title = group.name
                        }
                    }
                }
                launch {
                    dao.observeGroupItems(groupId).combine(dao.observeGroupHistory(groupId)) { items, history ->
                        items to history
                    }.collect { (items, history) ->
                        adapter.submitItems(items)
                        showSummary(items)
                        showChart(items, history)
                    }
                }
                launch { repository.checkState.collect(::showCheckState) }
                launch { repository.addState.collect(::showAddState) }
            }
        }
    }

    private fun showSummary(items: List<Item>) {
        val low = cheapest(items)
        val price = low.firstOrNull()?.currentPrice
        binding.cheapest.text = when {
            price == null -> "No price yet"
            low.size > 1 -> "${formatPence(price)} at ${low.joinToString(" and ") { it.shop.label }}"
            else -> "${formatPence(price)} at ${low.single().shop.label}"
        }
        val dearest = items.mapNotNull { it.currentPrice }.maxOrNull()
        binding.saving.text = when {
            items.size < 2 -> "Add another shop's link to compare prices."
            price == null || dearest == null || dearest == price -> "Same price everywhere."
            else -> "${formatPence(dearest - price)} cheaper than the dearest shop."
        }
    }

    private fun showChart(items: List<Item>, history: List<PricePoint>) {
        val byItem = history.groupBy { it.itemId }
        val series = items.map { item ->
            PriceChartView.Series(byItem[item.id].orEmpty(), item.shop.color)
        }
        binding.chart.setSeries(series, target = null)
    }

    private fun showCheckState(state: CheckState) {
        val running = state is CheckState.Running
        binding.checkNow.isEnabled = !running
        binding.checkNow.text = getString(if (running) R.string.checking else R.string.check_now)
        if (state is CheckState.Finished) {
            Snackbar.make(binding.root, state.summary, Snackbar.LENGTH_LONG).show()
            repository.consumeCheckState()
        }
    }

    private fun showAddState(state: AddState) {
        binding.addShop.isEnabled = state !is AddState.Working
        binding.addShop.text = if (state is AddState.Working) {
            "Reading price from ${state.shop.label}…"
        } else {
            getString(R.string.add_shop)
        }
        when (state) {
            AddState.Idle, is AddState.Working -> return
            is AddState.Added ->
                Snackbar.make(binding.root, "Added ${state.item.shop.label} at ${formatPence(state.item.currentPrice)}", Snackbar.LENGTH_LONG).show()
            is AddState.AlreadyTracked -> offerMove(state.item)
            is AddState.Rejected -> message(
                "Not a product link",
                "${state.reason}\n\nReceived:\n${state.received.ifBlank { "(nothing)" }}",
            )
            is AddState.Failed -> message(state.title, "${state.reason}\n\n${state.details}")
        }
        repository.consumeAddState()
    }

    /** The link is already tracked, perhaps on its own or in another group: offer to move it here. */
    private fun offerMove(item: Item) {
        if (item.groupId == groupId) {
            Snackbar.make(binding.root, "That shop is already in this group", Snackbar.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Already tracking this")
            .setMessage("Move \"${item.name}\" into $groupName?")
            .setPositiveButton("Move") { _, _ -> lifecycleScope.launch { repository.setGroup(item, groupId) } }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun message(title: String, text: String) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(text).setPositiveButton(R.string.ok, null).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_RENAME, Menu.NONE, R.string.rename_group)
        menu.add(Menu.NONE, MENU_UNGROUP, Menu.NONE, R.string.ungroup)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_RENAME -> {
            askGroupName(getString(R.string.rename_group), groupName) { name ->
                lifecycleScope.launch { repository.renameGroup(groupId, name) }
            }
            true
        }
        MENU_UNGROUP -> {
            MaterialAlertDialogBuilder(this)
                .setTitle("Ungroup $groupName?")
                .setMessage("Each shop's item stays, as its own row on the list.")
                .setPositiveButton(R.string.ungroup) { _, _ -> lifecycleScope.launch { repository.ungroup(groupId) } }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        private const val EXTRA_ID = "group_id"
        private const val MENU_RENAME = 1
        private const val MENU_UNGROUP = 2

        fun intent(context: Context, id: Long): Intent =
            Intent(context, GroupActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
