package com.ahad.lyricsoverlay

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

data class TrackInfo(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val positionMs: Long,
    val playing: Boolean,
    val key: String
)

object NowPlaying {

    fun read(context: Context): TrackInfo? {
        val listener = ComponentName(context, MediaNotificationListener::class.java)
        val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions: List<MediaController> = try {
            mgr.getActiveSessions(listener)
        } catch (_: SecurityException) {
            emptyList()
        }
        val controller = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
            ?: return null
        val meta = controller.metadata ?: return null
        val title = meta.string(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = meta.string(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.string(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        if (title.isBlank()) return null
        val state = controller.playbackState
        return TrackInfo(
            title = title.trim(),
            artist = artist.trim(),
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = state?.position ?: 0L,
            playing = state?.state == PlaybackState.STATE_PLAYING,
            key = "${controller.packageName}|$artist|$title"
        )
    }
}
