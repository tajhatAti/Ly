package com.ahad.lyricsoverlay

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val albumId: Long,
    val path: String,
    val contentUri: Uri
) {
    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )

    companion object {
        fun mediaUri(id: Long): Uri =
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }
}
