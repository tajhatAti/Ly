package com.ahad.lyricsoverlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahad.lyricsoverlay.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MusicListAdapter
    private lateinit var settings: AppSettings
    private var rawSongs: List<Song> = emptyList()
    private var seekUser = false
    private var lastSnap: AppSettings.Snap? = null
    private lateinit var uiListener: (AppSettings.Snap) -> Unit

    private val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { loadLibrary() }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { maybeStartOverlay() }

    private val onPlayer = { refreshMini() }

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = AppSettings.init(this)
        settings.applyNightMode()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = MusicListAdapter { _, index -> playAt(index) }
        binding.recycler.adapter = adapter
        applyChrome(settings.snapshot(), animate = false)

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnGrant.setOnClickListener { requestPerms() }
        binding.btnOverlay.setOnClickListener { requestOverlay() }
        binding.btnLayout.setOnClickListener {
            settings.grid = !settings.grid
        }

        binding.includeMini.btnPlay.setOnClickListener { sendPlayer(PlayerService.ACTION_TOGGLE) }
        binding.includeMini.btnNext.setOnClickListener { sendPlayer(PlayerService.ACTION_NEXT) }
        binding.includeMini.btnPrev.setOnClickListener { sendPlayer(PlayerService.ACTION_PREV) }
        binding.includeMini.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seekUser = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekUser = false
                val dur = PlayerState.durationMs.coerceAtLeast(1)
                val pos = (seekBar?.progress ?: 0) / 1000f * dur
                startService(
                    Intent(this@MainActivity, PlayerService::class.java)
                        .setAction(PlayerService.ACTION_SEEK)
                        .putExtra(PlayerService.EXTRA_POS, pos.toLong())
                )
            }
        })

        uiListener = { snap ->
            applyChrome(snap, animate = lastSnap != null)
            lastSnap = snap
        }
        settings.addListener(uiListener)

        if (hasStorage()) loadLibrary() else {
            binding.empty.visibility = View.VISIBLE
            binding.btnGrant.visibility = View.VISIBLE
            requestPerms()
        }
    }

    override fun onResume() {
        super.onResume()
        PlayerState.addListener(onPlayer)
        refreshMini()
        if (hasStorage() && rawSongs.isEmpty()) loadLibrary()
    }

    override fun onPause() {
        PlayerState.removeListener(onPlayer)
        super.onPause()
    }

    override fun onDestroy() {
        if (this::uiListener.isInitialized) settings.removeListener(uiListener)
        super.onDestroy()
    }

    private fun applyChrome(snap: AppSettings.Snap, animate: Boolean) {
        val apply = {
            val state = binding.recycler.layoutManager?.onSaveInstanceState()
            if (snap.grid) {
                binding.recycler.layoutManager = GridLayoutManager(this, snap.gridSpan)
            } else {
                binding.recycler.layoutManager = LinearLayoutManager(this)
            }
            adapter.applyChrome(snap.grid, snap.cardStyle, snap.accent, settings.typeface())
            adapter.titleColor = ContextCompat.getColor(this, R.color.text)
            adapter.mutedColor = ContextCompat.getColor(this, R.color.muted)
            showSorted()
            binding.recycler.layoutManager?.onRestoreInstanceState(state)
            tintAccent(snap.accent)
            val tf = settings.typeface()
            binding.tvTitle.typeface = tf
            binding.tvCount.typeface = tf
            binding.empty.typeface = tf
            binding.includeMini.tvMiniTitle.typeface = tf
            binding.includeMini.tvMiniArtist.typeface = tf
        }
        if (animate) {
            binding.recycler.animate().alpha(0f).setDuration(120).withEndAction {
                apply()
                binding.recycler.animate().alpha(1f).setDuration(160).start()
            }.start()
        } else {
            apply()
        }
    }

    private fun tintAccent(accent: Int) {
        binding.btnLayout.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
        binding.btnOverlay.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
        binding.btnSettings.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
        binding.btnGrant.backgroundTintList = ColorStateList.valueOf(accent)
        binding.includeMini.btnPlay.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
        binding.includeMini.seekBar.progressTintList = ColorStateList.valueOf(accent)
        binding.includeMini.seekBar.thumbTintList = ColorStateList.valueOf(accent)
        val cardTint = ColorUtils.setAlphaComponent(accent, 40)
        val miniBg = ColorUtils.compositeColors(cardTint, ContextCompat.getColor(this, R.color.mini))
        binding.includeMini.root.setBackgroundColor(miniBg)
    }

    private fun hasStorage(): Boolean {
        return ContextCompat.checkSelfPermission(this, storagePermission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestPerms() {
        val list = mutableListOf(storagePermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        permLauncher.launch(list.toTypedArray())
    }

    private fun requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            maybeStartOverlay()
            Toast.makeText(this, R.string.overlay_on, Toast.LENGTH_SHORT).show()
        } else {
            overlayLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun maybeStartOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        if (PlayerState.song != null) sendPlayer(PlayerService.ACTION_RESUME)
    }

    private fun loadLibrary() {
        if (!hasStorage()) {
            binding.empty.visibility = View.VISIBLE
            binding.btnGrant.visibility = View.VISIBLE
            return
        }
        rawSongs = MusicScannerUtil.scan(this)
        showSorted()
        binding.btnGrant.visibility = View.GONE
    }

    private fun showSorted() {
        val sorted = MusicScannerUtil.sort(rawSongs, settings.sort)
        adapter.submit(sorted)
        binding.empty.visibility = if (sorted.isEmpty() && hasStorage()) View.VISIBLE else View.GONE
        if (!hasStorage()) binding.empty.visibility = View.VISIBLE
        binding.tvCount.text = getString(R.string.song_count, sorted.size)
    }

    private fun playAt(index: Int) {
        val list = adapter.songs()
        PlayerService.pendingQueue = list
        val i = Intent(this, PlayerService::class.java)
            .setAction(PlayerService.ACTION_PLAY_INDEX)
            .putExtra(PlayerService.EXTRA_INDEX, index)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        maybeStartOverlay()
    }

    private fun sendPlayer(action: String) {
        val i = Intent(this, PlayerService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
    }

    private fun refreshMini() {
        val song = PlayerState.song
        val mini = binding.includeMini.root
        if (song == null) {
            mini.visibility = View.GONE
            return
        }
        mini.visibility = View.VISIBLE
        binding.includeMini.tvMiniTitle.text = song.title
        binding.includeMini.tvMiniArtist.text = song.artist
        try {
            binding.includeMini.ivMiniArt.setImageURI(song.albumArtUri)
        } catch (_: Exception) {
        }
        val icon = if (PlayerState.playing) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val btn = binding.includeMini.btnPlay
        if (btn.tag != icon) {
            btn.tag = icon
            btn.animate().scaleX(0.7f).scaleY(0.7f).setDuration(90).withEndAction {
                btn.setImageResource(icon)
                btn.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
        }
        adapter.playingId = song.id
        if (!seekUser) {
            val dur = PlayerState.durationMs.coerceAtLeast(1)
            binding.includeMini.seekBar.progress =
                ((PlayerState.positionMs * 1000) / dur).toInt().coerceIn(0, 1000)
        }
        binding.includeMini.tvMiniTime.text =
            "${MusicScannerUtil.formatDuration(PlayerState.positionMs)} / ${MusicScannerUtil.formatDuration(PlayerState.durationMs)}"
    }
}
