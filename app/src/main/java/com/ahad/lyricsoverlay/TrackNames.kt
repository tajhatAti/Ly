package com.ahad.lyricsoverlay

object TrackNames {

    fun variants(title: String, artist: String): List<Pair<String, String>> {
        val out = LinkedHashSet<Pair<String, String>>()
        val t0 = clean(title)
        val a0 = cleanArtist(artist)
        if (t0.isNotBlank()) out += t0 to a0

        val split = Regex("""\s+[-–—]\s+""").split(t0, limit = 2)
        if (split.size == 2) {
            out += split[1] to cleanArtist(split[0])
            out += split[0] to cleanArtist(split[1])
        }
        if (a0.isNotBlank() && t0.isNotBlank()) {
            out += t0 to a0
        }
        return out.toList().ifEmpty { listOf(title.trim() to artist.trim()) }
    }

    fun clean(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("""\.(mp3|m4a|wav|flac|ogg|aac)$""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""^\s*\d{1,3}\s*[-.)]\s*"""), "")
        s = s.replace(Regex("""\s*[\(\[\{][^\)\]\}]*[\)\]\}]\s*"""), " ")
        s = s.replace(Regex("""\s+"""), " ").trim()
        return s
    }

    private fun cleanArtist(raw: String): String {
        val s = clean(raw)
        if (s.isBlank() || s.equals("unknown", true) || s.equals("unknown artist", true) || s == "<unknown>") {
            return ""
        }
        return s
    }
}
