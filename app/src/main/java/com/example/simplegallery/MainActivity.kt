package com.example.simplegallery

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.simplegallery.databinding.ActivityMainBinding
import java.util.concurrent.Executors

/** Сетка миниатюр одной папки-альбома. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val items = ArrayList<MediaItem>()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var bucketId: Long = -1L
    private lateinit var adapter: MediaAdapter
    private var albumTitle = ""

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        Log.d(TAG, "deleteLauncher result: ${result.resultCode}")
        if (result.resultCode == RESULT_OK) {
            adapter.exitSelectionMode()
            loadMedia()
        } else {
            Toast.makeText(this, R.string.delete_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bucketId = intent.getLongExtra(EXTRA_BUCKET_ID, -1L)
        albumTitle = intent.getStringExtra(EXTRA_BUCKET_NAME) ?: getString(R.string.app_name)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.title = albumTitle
        binding.toolbar.setNavigationOnClickListener {
            if (adapter.isSelectionMode) exitSelectionMode() else finish()
        }

        adapter = MediaAdapter(
            items,
            onClick = { position ->
                val intent = Intent(this, ViewerActivity::class.java).apply {
                    putParcelableArrayListExtra(ViewerActivity.EXTRA_ITEMS, items)
                    putExtra(ViewerActivity.EXTRA_POSITION, position)
                }
                startActivity(intent)
            },
            onLongClick = {},
            onSelectionChanged = { count -> updateSelectionUI(count) }
        )

        binding.recycler.layoutManager = GridLayoutManager(this, 3)
        binding.recycler.adapter = adapter
        binding.deleteSelectedButton.setOnClickListener { confirmDelete() }
    }

    private fun updateSelectionUI(count: Int) {
        if (adapter.isSelectionMode) {
            binding.toolbar.title = getString(R.string.selected_count, count)
            binding.selectionBar.visibility = View.VISIBLE
            binding.deleteSelectedButton.text = getString(R.string.delete_count, count)
        } else {
            binding.toolbar.title = albumTitle
            binding.selectionBar.visibility = View.GONE
        }
    }

    private fun exitSelectionMode() {
        adapter.exitSelectionMode()
        updateSelectionUI(0)
    }

    private fun confirmDelete() {
        if (adapter.selectedPositions.isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.yes) { _, _ -> performDelete() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun performDelete() {
        val uris = adapter.selectedPositions.sorted().map { items[it].uri }
        Log.d(TAG, "performDelete: ${uris.size} files, SDK=${Build.VERSION.SDK_INT}")
        if (uris.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: система показывает один диалог на все выбранные файлы
            try {
                val request = MediaStore.createDeleteRequest(contentResolver, uris)
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } catch (e: Exception) {
                Log.e(TAG, "createDeleteRequest failed", e)
                Toast.makeText(this, R.string.delete_error, Toast.LENGTH_SHORT).show()
            }
        } else {
            directDelete(uris)
        }
    }

    private fun directDelete(uris: List<Uri>) {
        var deleted = 0
        for (uri in uris) {
            try {
                val rows = contentResolver.delete(uri, null, null)
                Log.d(TAG, "delete $uri → $rows rows")
                if (rows > 0) deleted++
            } catch (e: Exception) {
                Log.e(TAG, "delete failed for $uri", e)
            }
        }
        Toast.makeText(
            this,
            if (deleted > 0) getString(R.string.deleted_count, deleted) else getString(R.string.delete_error),
            Toast.LENGTH_SHORT
        ).show()
        exitSelectionMode()
        loadMedia()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (adapter.isSelectionMode) exitSelectionMode() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        loadMedia()
    }

    private fun loadMedia() {
        ioExecutor.execute {
            val loaded = MediaRepository.queryMedia(this, if (bucketId == -1L) null else bucketId)
            runOnUiThread {
                items.clear()
                items.addAll(loaded)
                binding.recycler.adapter?.notifyDataSetChanged()
                binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                if (items.isEmpty()) binding.emptyView.text = getString(R.string.empty)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }

    companion object {
        private const val TAG = "Gallery"
        const val EXTRA_BUCKET_ID = "extra_bucket_id"
        const val EXTRA_BUCKET_NAME = "extra_bucket_name"
    }
}
