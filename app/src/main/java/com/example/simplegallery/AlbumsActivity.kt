package com.example.simplegallery

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.simplegallery.databinding.ActivityAlbumsBinding
import java.util.concurrent.Executors

/** Стартовый экран: список папок-альбомов. */
class AlbumsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlbumsBinding
    private val albums = ArrayList<Album>()
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            loadAlbums()
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

        binding.recycler.layoutManager = GridLayoutManager(this, 2)
        binding.recycler.adapter = AlbumAdapter(albums) { album ->
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_BUCKET_ID, album.bucketId)
                putExtra(MainActivity.EXTRA_BUCKET_NAME, album.name)
            }
            startActivity(intent)
        }

        if (hasPermissions()) {
            loadAlbums()
            requestManageMediaIfNeeded()
        } else {
            permissionLauncher.launch(MediaRepository.requiredPermissions())
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

    override fun onResume() {
        super.onResume()
        if (hasPermissions()) loadAlbums()
    }

    private fun hasPermissions(): Boolean =
        MediaRepository.requiredPermissions().all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    private fun loadAlbums() {
        ioExecutor.execute {
            val loaded = MediaRepository.queryAlbums(this)
            runOnUiThread {
                albums.clear()
                albums.addAll(loaded)
                binding.recycler.adapter?.notifyDataSetChanged()
                showMessage(if (albums.isEmpty()) getString(R.string.empty) else null)
            }
        }
    }

    private fun showMessage(message: String?) {
        if (message == null) {
            binding.emptyView.visibility = View.GONE
        } else {
            binding.emptyView.text = message
            binding.emptyView.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }
}
