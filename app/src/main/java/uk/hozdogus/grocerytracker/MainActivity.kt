package uk.hozdogus.grocerytracker

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import uk.hozdogus.grocerytracker.data.Item
import uk.hozdogus.grocerytracker.data.ItemRepository.AddState
import uk.hozdogus.grocerytracker.databinding.ActivityMainBinding
import uk.hozdogus.grocerytracker.databinding.DialogAddBinding
import uk.hozdogus.grocerytracker.fetch.ProductUrl
import uk.hozdogus.grocerytracker.ui.ItemAdapter
import uk.hozdogus.grocerytracker.ui.formatPence

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repository by lazy { (application as GroceryApp).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.subtitle = "v${BuildConfig.VERSION_NAME}"

        val adapter = ItemAdapter(
            onClick = { /* item detail comes in milestone 3 */ },
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
                        adapter.submitList(items)
                        binding.empty.isVisible = items.isEmpty()
                    }
                }
                launch { repository.addState.collect(::showAddState) }
            }
        }

        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
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
        binding.progressBanner.isVisible = state is AddState.Working
        when (state) {
            AddState.Idle -> Unit
            is AddState.Working ->
                binding.progressText.text = "Reading price from ${state.shop.label}… (up to 30 seconds)"
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
