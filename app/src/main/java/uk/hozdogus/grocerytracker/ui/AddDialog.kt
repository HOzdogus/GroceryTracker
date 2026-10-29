package uk.hozdogus.grocerytracker.ui

import android.content.ClipboardManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import uk.hozdogus.grocerytracker.R
import uk.hozdogus.grocerytracker.databinding.DialogAddBinding
import uk.hozdogus.grocerytracker.fetch.ProductUrl

/** Asks for a product link, pre-filled from the clipboard when it holds one. */
fun AppCompatActivity.showAddDialog(title: String, onAdd: (String) -> Unit) {
    val binding = DialogAddBinding.inflate(layoutInflater)
    clipboardText()?.takeIf { ProductUrl.parse(it) is ProductUrl.Result.Ok }?.let { binding.urlInput.setText(it) }
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(binding.root)
        .setPositiveButton(R.string.add) { _, _ -> onAdd(binding.urlInput.text?.toString().orEmpty()) }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

private fun AppCompatActivity.clipboardText(): String? {
    val clip = getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(this)?.toString()
}
