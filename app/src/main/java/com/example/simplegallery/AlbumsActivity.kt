package com.example.simplegallery

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.simplegallery.databinding.ActivityAlbumsBinding
import java.util.concurrent.Executors

/** Стартовый экран. Режим выбирается выпадающим списком в тулбаре. */
class AlbumsActivity : AppCompatActivity() {

    private enum class Mode { ALL, ALBUMS, BY_DATE, PHOTOS, VIDEOS }

    private lateinit var binding: ActivityAlbumsBinding
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private var currentMode = Mode.ALL

    private val albums = ArrayList<Album>()
    private val dateGroups = ArrayList<DateGroup>()
    private val flatMedia = ArrayList<MediaItem>()

    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var dateGroupAdapter: DateGroupAdapter
    private lateinit var flatMediaAdapter: MediaAdapter

    private val modeValues = listOf(Mode.ALL, Mode.ALBUMS, Mode.BY_DATE, Mode.PHOTOS, Mode.VIDEOS)

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            flatMediaAdapter.exitSelectionMode()
            updateSelectionUI(0)
            loadContent()
        } else {
            Toast.makeText(this, R.string.delete_error, Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            loadContent()
            requestManageMediaIfNeeded()
        } else {
            showMessage(getString(R.string.no_permission))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlbumsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        albumAdapter = AlbumAdapter(albums) { album ->
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_BUCKET_ID, album.bucketId)
                putExtra(MainActivity.EXTRA_BUCKET_NAME, album.name)
            })
        }

        dateGroupAdapter = DateGroupAdapter(dateGroups) { group ->
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_BUCKET_ID, -1L)
                putExtra(MainActivity.EXTRA_BUCKET_NAME, group.label)
                putExtra(MainActivity.EXTRA_DATE_FROM, group.dateFrom)
                putExtra(MainActivity.EXTRA_DATE_TO, group.dateTo)
            })
        }

        flatMediaAdapter = MediaAdapter(
            flatMedia,
            onClick = { position ->
                if (!flatMediaAdapter.isSelectionMode) {
                    startActivity(Intent(this, ViewerActivity::class.java).apply {
                        putParcelableArrayListExtra(ViewerActivity.EXTRA_ITEMS, flatMedia)
                        putExtra(ViewerActivity.EXTRA_POSITION, position)
                    })
                }
            },
            onLongClick = {},
            onSelectionChanged = { count -> updateSelectionUI(count) }
        )

        binding.recycler.setHasFixedSize(true)
        binding.deleteSelectedButton.setOnClickListener { confirmDelete() }

        setupSpinner()

        if (hasPermissions()) {
            loadContent()
            requestManageMediaIfNeeded()
        } else {
            permissionLauncher.launch(MediaRepository.requiredPermissions())
        }
    }

    private fun updateSelectionUI(count: Int) {
        val inFlatMode = currentMode == Mode.ALL || currentMode == Mode.PHOTOS || currentMode == Mode.VIDEOS
        if (inFlatMode && flatMediaAdapter.isSelectionMode) {
            binding.selectionBar.visibility = View.VISIBLE
            binding.deleteSelectedButton.text = getString(R.string.delete_count, count)
        } else {
            binding.selectionBar.visibility = View.GONE
        }
    }

    private fun confirmDelete() {
        if (flatMediaAdapter.selectedPositions.isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.yes) { _, _ -> performDelete() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun performDelete() {
        val uris = flatMediaAdapter.selectedPositions.sorted().map { flatMedia[it].uri }
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val request = MediaStore.createDeleteRequest(contentResolver, uris)
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } catch (e: Exception) {
                Log.e("Gallery", "createDeleteRequest failed", e)
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
                if (contentResolver.delete(uri, null, null) > 0) deleted++
            } catch (e: Exception) {
                Log.e("Gallery", "delete failed for $uri", e)
            }
        }
        Toast.makeText(
            this,
            if (deleted > 0) getString(R.string.deleted_count, deleted) else getString(R.string.delete_error),
            Toast.LENGTH_SHORT
        ).show()
        flatMediaAdapter.exitSelectionMode()
        updateSelectionUI(0)
        loadContent()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (flatMediaAdapter.isSelectionMode) {
            flatMediaAdapter.exitSelectionMode()
            updateSelectionUI(0)
        } else {
            super.onBackPressed()
        }
    }

    private fun setupSpinner() {
        val labels = listOf(
            getString(R.string.mode_all),
            getString(R.string.mode_albums),
            getString(R.string.mode_by_date),
            getString(R.string.mode_photos),
            getString(R.string.mode_videos)
        )
        val dp = resources.displayMetrics.density

        val adapter = object : ArrayAdapter<String>(this, 0, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? TextView) ?: TextView(context)
                tv.text = "${labels[position]} ▾"
                tv.setTextColor(Color.WHITE)
                tv.textSize = 15f
                tv.setTypeface(null, Typeface.NORMAL)
                tv.compoundDrawablePadding = 0
                tv.setPaddingRelative((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
                tv.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
                return tv
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? TextView) ?: TextView(context)
                tv.text = labels[position]
                tv.textSize = 16f
                val padH = (16 * dp).toInt()
                val padV = (14 * dp).toInt()
                tv.setPadding(padH, padV, padH, padV)
                tv.setTypeface(null,
                    if (modeValues[position] == currentMode) Typeface.BOLD else Typeface.NORMAL)
                return tv
            }
        }

        binding.modeSpinner.adapter = adapter
        binding.modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val newMode = modeValues[position]
                if (newMode == currentMode) return
                if (flatMediaAdapter.isSelectionMode) {
                    flatMediaAdapter.exitSelectionMode()
                    updateSelectionUI(0)
                }
                currentMode = newMode
                applyMode(newMode)
                if (hasPermissions()) loadContent()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun applyMode(mode: Mode) {
        when (mode) {
            Mode.ALBUMS, Mode.BY_DATE -> {
                binding.recycler.layoutManager = GridLayoutManager(this, 2)
                binding.recycler.adapter = if (mode == Mode.ALBUMS) albumAdapter else dateGroupAdapter
            }
            Mode.ALL, Mode.PHOTOS, Mode.VIDEOS -> {
                binding.recycler.layoutManager = GridLayoutManager(this, 3)
                binding.recycler.adapter = flatMediaAdapter
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPermissions()) loadContent()
    }

    private fun hasPermissions(): Boolean =
        MediaRepository.requiredPermissions().all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    private fun loadContent() {
        applyMode(currentMode)
        when (currentMode) {
            Mode.ALL -> loadFlatMedia(null)
            Mode.ALBUMS -> loadAlbums()
            Mode.BY_DATE -> loadDateGroups()
            Mode.PHOTOS -> loadFlatMedia(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
            Mode.VIDEOS -> loadFlatMedia(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        }
    }

    private fun loadAlbums() {
        ioExecutor.execute {
            val loaded = MediaRepository.queryAlbums(this)
            runOnUiThread {
                albums.clear()
                albums.addAll(loaded)
                albumAdapter.notifyDataSetChanged()
                showMessage(if (albums.isEmpty()) getString(R.string.empty) else null)
            }
        }
    }

    private fun loadDateGroups() {
        ioExecutor.execute {
            val loaded = MediaRepository.queryMediaGroupedByDate(this)
            runOnUiThread {
                dateGroups.clear()
                dateGroups.addAll(loaded)
                dateGroupAdapter.notifyDataSetChanged()
                showMessage(if (dateGroups.isEmpty()) getString(R.string.empty) else null)
            }
        }
    }

    private fun loadFlatMedia(mediaType: Int?) {
        ioExecutor.execute {
            val loaded = MediaRepository.queryMedia(this, null, mediaType)
            runOnUiThread {
                flatMedia.clear()
                flatMedia.addAll(loaded)
                flatMediaAdapter.notifyDataSetChanged()
                showMessage(if (flatMedia.isEmpty()) getString(R.string.empty) else null)
            }
        }
    }

    private fun requestManageMediaIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (MediaStore.canManageMedia(this)) return
        val prefs = getSharedPreferences("gallery_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("manage_media_asked", false)) return
        prefs.edit().putBoolean("manage_media_asked", true).apply()
        AlertDialog.Builder(this)
            .setMessage(R.string.manage_media_rationale)
            .setPositiveButton(R.string.settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun showMessage(message: String?) {
        binding.emptyView.visibility = if (message == null) View.GONE else View.VISIBLE
        if (message != null) binding.emptyView.text = message
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }
}
