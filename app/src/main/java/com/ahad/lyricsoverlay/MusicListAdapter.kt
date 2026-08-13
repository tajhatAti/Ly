package com.ahad.lyricsoverlay

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MusicListAdapter(
    private val onClick: (Song, Int) -> Unit
) : RecyclerView.Adapter<MusicListAdapter.Holder>() {

    private val items = ArrayList<Song>()
    var grid: Boolean = false
        private set
    var cardStyle: String = AppSettings.CARD_ROUNDED
        private set
    var accent: Int = 0xFFB388FF.toInt()
    var typeface: Typeface = Typeface.DEFAULT
    var titleColor: Int = Color.WHITE
    var mutedColor: Int = 0xFF9AA0B2.toInt()

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

    fun songs(): List<Song> = items.toList()

    fun applyChrome(grid: Boolean, cardStyle: String, accent: Int, typeface: Typeface) {
        val layoutChanged = this.grid != grid || this.cardStyle != cardStyle
        this.grid = grid
        this.cardStyle = cardStyle
        this.accent = accent
        this.typeface = typeface
        if (layoutChanged) notifyDataSetChanged() else notifyItemRangeChanged(0, items.size)
    }

    override fun getItemViewType(position: Int): Int {
        val g = if (grid) 10 else 0
        val s = when (cardStyle) {
            AppSettings.CARD_FLAT -> 1
            AppSettings.CARD_COMPACT -> 2
            else -> 0
        }
        return g + s
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = when (viewType) {
            11 -> R.layout.item_song_grid_flat
            12 -> R.layout.item_song_grid_compact
            10 -> R.layout.item_song_grid_card
            1 -> R.layout.item_song_list_flat
            2 -> R.layout.item_song_list_compact
            else -> R.layout.item_song_list_card
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view.findViewById<TextView>(R.id.tvTitle)
        private val artist = view.findViewById<TextView>(R.id.tvArtist)
        private val duration = view.findViewById<TextView>(R.id.tvDuration)
        private val art = view.findViewById<ImageView>(R.id.ivArt)

        fun bind(song: Song) {
            title.text = song.title
            artist.text = song.artist
            duration?.text = MusicScannerUtil.formatDuration(song.durationMs)
            title.typeface = typeface
            artist.typeface = typeface
            duration?.typeface = typeface
            val active = song.id == playingId
            title.setTextColor(if (active) accent else titleColor)
            artist.setTextColor(mutedColor)
            duration?.setTextColor(mutedColor)
            itemView.alpha = if (active) 1f else 0.94f
            if (active && itemView.background is GradientDrawable) {
                (itemView.background.mutate() as GradientDrawable).setStroke(3, accent)
            }
            art.setImageDrawable(ColorDrawable(0x332A2438))
            try {
                art.setImageURI(song.albumArtUri)
            } catch (_: Exception) {
                art.setImageResource(android.R.drawable.ic_media_play)
            }
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(items[pos], pos)
            }
        }
    }
}
