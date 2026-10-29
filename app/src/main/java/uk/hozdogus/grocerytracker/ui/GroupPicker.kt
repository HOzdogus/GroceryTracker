package uk.hozdogus.grocerytracker.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import uk.hozdogus.grocerytracker.R
import uk.hozdogus.grocerytracker.data.Item
import uk.hozdogus.grocerytracker.data.ItemRepository
import uk.hozdogus.grocerytracker.databinding.DialogTextBinding

/**
 * Lets the user put [item] into an existing group, a new one, or no group.
 * [title] differs between "just added" and "change group".
 */
fun AppCompatActivity.pickGroup(repository: ItemRepository, item: Item, title: String) {
    lifecycleScope.launch {
        val groups = repository.groups()
        val labels = groups.map { if (it.id == item.groupId) "✓ ${it.name}" else it.name } +
            getString(R.string.new_group) +
            getString(if (item.groupId == null) R.string.no_group else R.string.remove_from_group)
        MaterialAlertDialogBuilder(this@pickGroup)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which < groups.size -> lifecycleScope.launch { repository.setGroup(item, groups[which].id) }
                    which == groups.size -> askGroupName(getString(R.string.new_group), shortName(item.name)) { name ->
                        lifecycleScope.launch { repository.createGroup(name, item) }
                    }
                    item.groupId != null -> lifecycleScope.launch { repository.setGroup(item, null) }
                }
            }
            .show()
    }
}

/** Asks for a group name, starting from [initial]. Blank names are ignored. */
fun AppCompatActivity.askGroupName(title: String, initial: String, onName: (String) -> Unit) {
    val binding = DialogTextBinding.inflate(layoutInflater)
    binding.textInput.setText(initial.removeSuffix("…"))
    binding.textInput.selectAll()
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(binding.root)
        .setPositiveButton(R.string.ok) { _, _ ->
            binding.textInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(onName)
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
    binding.textInput.requestFocus()
}
