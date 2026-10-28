package uk.hozdogus.grocerytracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import uk.hozdogus.grocerytracker.databinding.ActivityMainBinding
import uk.hozdogus.grocerytracker.ui.ItemAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.subtitle = "v${BuildConfig.VERSION_NAME}"

        val adapter = ItemAdapter(onClick = { /* item detail comes in milestone 3 */ })
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        val dao = (application as GroceryApp).db.items()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.observeAll().collect { items ->
                    adapter.submitList(items)
                    binding.empty.isVisible = items.isEmpty()
                }
            }
        }
    }
}
