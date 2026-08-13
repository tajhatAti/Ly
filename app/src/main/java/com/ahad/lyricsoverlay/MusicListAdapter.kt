package com.ahad.lyricsoverlay

import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ahad.lyricsoverlay.databinding.ItemSongBinding

class MusicListAdapter(
    private val onClick: (Song, Int) -> Unit
) : RecyclerView.Adapter<MusicListAdapter.Holder>() {

    private val items = ArrayList<Song>()
    var playingId: Long = -1L
        set(value) {
            val old = field
            field = value
            val a = items.indexOfFirst { it.id == old }
            val b = items.indexOfFirst { it.id == value }
            if (a >= 0) notifyItemChanged(a)
            if (b >= 0) notifyItemChanged(b)
        }

    fun submit(list: List<Song>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.tvTitle.text = song.title
            binding.tvArtist.text = song.artist
            binding.tvDuration.text = MusicScannerUtil.formatDuration(song.durationMs)
            binding.ivArt.setImageDrawable(ColorDrawable(0xFF2A2438.toInt()))
            try {
                binding.ivArt.setImageURI(song.albumArtUri)
                if (binding.ivArt.drawable == null) {
                    binding.ivArt.setImageResource(android.R.drawable.ic_media_play)
                }
            } catch (_: Exception) {
                binding.ivArt.setImageResource(android.R.drawable.ic_media_play)
            }
            val active = song.id == playingId
            binding.root.alpha = if (active) 1f else 0.92f
            binding.tvTitle.setTextColor(if (active) 0xFFB388FF.toInt() else 0xFFFFFFFF.toInt())
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(items[pos], pos)
            }
        }
    }
}
