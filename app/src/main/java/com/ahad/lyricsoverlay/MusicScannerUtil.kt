package com.ahad.lyricsoverlay

import android.content.Context
import android.provider.MediaStore

object MusicScannerUtil {

    private val audioExt = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac")

    fun scan(context: Context): List<Song> {
        val songs = ArrayList<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val cursor = try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sort
            )
        } catch (_: SecurityException) {
            null
        } ?: return emptyList()

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            val musicCol = c.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC)
            val nameCol = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                if (musicCol >= 0 && c.getInt(musicCol) == 0) continue
                val path = if (dataCol >= 0) c.getString(dataCol).orEmpty() else ""
                val name = if (nameCol >= 0) c.getString(nameCol).orEmpty() else path
                val ext = (name.ifBlank { path }).substringAfterLast('.', "").lowercase()
                if (ext.isNotEmpty() && ext !in audioExt) continue
                val id = c.getLong(idCol)
                val artist = c.getString(artistCol).orEmpty()
                    .ifBlank { "Unknown artist" }
                    .let { if (it == "<unknown>") "Unknown artist" else it }
                songs += Song(
                    id = id,
                    title = c.getString(titleCol).orEmpty().ifBlank { name.ifBlank { "Unknown" } },
                    artist = artist,
                    durationMs = c.getLong(durCol).coerceAtLeast(0L),
                    albumId = c.getLong(albumCol),
                    path = path,
                    contentUri = Song.mediaUri(id)
                )
            }
        }
        return songs
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val total = ms / 1000
        val m = total / 60
        val s = total % 60
        return "%d:%02d".format(m, s)
    }
}
