package com.ahad.lyricsoverlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahad.lyricsoverlay.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MusicListAdapter
    private var songs: List<Song> = emptyList()
    private var seekUser = false

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
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = MusicListAdapter { _, index -> playAt(index) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnGrant.setOnClickListener { requestPerms() }
        binding.btnOverlay.setOnClickListener { requestOverlay() }

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
                val i = Intent(this@MainActivity, PlayerService::class.java)
                    .setAction(PlayerService.ACTION_SEEK)
                    .putExtra(PlayerService.EXTRA_POS, pos.toLong())
                startService(i)
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS, storagePermission))
            }
        }

        if (hasStorage()) loadLibrary() else {
            binding.empty.visibility = View.VISIBLE
            binding.btnGrant.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        PlayerState.addListener(onPlayer)
        refreshMini()
        if (hasStorage() && songs.isEmpty()) loadLibrary()
    }

    override fun onPause() {
        PlayerState.removeListener(onPlayer)
        super.onPause()
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
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun maybeStartOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        val i = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SYNC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
    }

    private fun loadLibrary() {
        if (!hasStorage()) {
            binding.empty.visibility = View.VISIBLE
            binding.btnGrant.visibility = View.VISIBLE
            return
        }
        songs = MusicScannerUtil.scan(this)
        adapter.submit(songs)
        binding.empty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        binding.btnGrant.visibility = View.GONE
        binding.tvCount.text = getString(R.string.song_count, songs.size)
    }

    private fun playAt(index: Int) {
        PlayerService.pendingQueue = songs
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
