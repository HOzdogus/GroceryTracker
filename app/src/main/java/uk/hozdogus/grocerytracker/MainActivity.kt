package uk.hozdogus.grocerytracker

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
import uk.hozdogus.grocerytracker.databinding.ActivityMainBinding
import uk.hozdogus.grocerytracker.databinding.DialogAddBinding
import uk.hozdogus.grocerytracker.fetch.ProductUrl
import uk.hozdogus.grocerytracker.ui.ItemAdapter
import uk.hozdogus.grocerytracker.ui.formatPence
import uk.hozdogus.grocerytracker.ui.formatWhen
import uk.hozdogus.grocerytracker.ui.orderForList

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repository by lazy { (application as GroceryApp).repository }
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        val adapter = ItemAdapter(
            onClick = { startActivity(ItemDetailActivity.intent(this, it.id)) },
            onLongClick = ::confirmDelete,
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.addButton.setOnClickListener { showAddDialog() }

        val dao = (application as GroceryApp).db.items()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dao.observeAll().collect { items ->
                        adapter.submitList(orderForList(items, System.currentTimeMillis()))
                        binding.empty.isVisible = items.isEmpty()
                    }
                }
                launch { repository.lastRunAt.collect(::showLastRun) }
                launch {
                    repository.addState.combine(repository.checkState) { add, check -> add to check }
                        .collect { (add, check) -> showProgress(add, check) }
                }
                launch { repository.addState.collect(::showAddState) }
                launch { repository.checkState.collect(::showCheckState) }
            }
        }

        if (savedInstanceState == null) {
            handleShare(intent)
            askForNotifications()
        }
    }

    /** Android 13+ needs permission for the price-drop notifications; Android only shows the prompt twice. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_check_now -> {
            repository.checkAllNow()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showLastRun(at: Long?) {
        val checked = at?.let { "checked ${formatWhen(it)}" } ?: "not checked yet"
        supportActionBar?.subtitle = "v${BuildConfig.VERSION_NAME} · $checked"
    }

    /** One banner for whichever is running; adding takes priority because the user is waiting on it. */
    private fun showProgress(add: AddState, check: CheckState) {
        val text = when {
            add is AddState.Working -> "Reading price from ${add.shop.label}… (up to 30 seconds)"
            check is CheckState.Running && check.total > 0 ->
                "Checking ${check.done + 1} of ${check.total}: ${check.name}"
            check is CheckState.Running -> "Checking…"
            else -> null
        }
        binding.progressBanner.isVisible = text != null
        binding.progressText.text = text
    }

    private fun showCheckState(state: CheckState) {
        if (state is CheckState.Finished) {
            Snackbar.make(binding.root, state.summary, Snackbar.LENGTH_LONG).show()
            repository.consumeCheckState()
        }
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        // Stop a later recreation from adding the same share again.
        intent.action = null
        repository.add(text)
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddBinding.inflate(layoutInflater)
        clipboardText()?.takeIf { ProductUrl.parse(it) is ProductUrl.Result.Ok }?.let {
            dialogBinding.urlInput.setText(it)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.add) { _, _ ->
                repository.add(dialogBinding.urlInput.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clipboardText(): String? {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun showAddState(state: AddState) {
        when (state) {
            AddState.Idle, is AddState.Working -> Unit
            is AddState.Added -> {
                Snackbar.make(
                    binding.root,
                    "Added ${state.item.name} at ${formatPence(state.item.currentPrice)}",
                    Snackbar.LENGTH_LONG,
                ).show()
                repository.consumeAddState()
            }
            is AddState.AlreadyTracked -> {
                Snackbar.make(binding.root, "Already tracking ${state.item.name}", Snackbar.LENGTH_LONG).show()
                repository.consumeAddState()
            }
            is AddState.Rejected -> {
                showMessage(
                    "Not a product link",
                    "${state.reason}\n\nShare or paste a product page from asda.com or sainsburys.co.uk.\n\n" +
                        "Received:\n${state.received.ifBlank { "(nothing)" }}",
                )
                repository.consumeAddState()
            }
            is AddState.Failed -> {
                showMessage(state.title, "${state.reason}\n\n${state.details}")
                repository.consumeAddState()
            }
        }
    }

    private fun showMessage(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun confirmDelete(item: Item) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete item?")
            .setMessage(item.name)
            .setPositiveButton(R.string.delete) { _, _ -> lifecycleScope.launch { repository.delete(item) } }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
