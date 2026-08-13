package com.ahad.lyricsoverlay

data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {

    private val lineRegex = Regex("""((?:\[\d{1,2}:\d{2}(?:[.,]\d{1,3})?])+)\s*(.*)""")
    private val timeRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.,](\d{1,3}))?]""")

    fun parse(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val match = lineRegex.matchEntire(line) ?: continue
            val body = match.groupValues[2].trim()
            if (body.isEmpty()) continue
            for (tm in timeRegex.findAll(match.groupValues[1])) {
                val min = tm.groupValues[1].toLong()
                val sec = tm.groupValues[2].toLong()
                val frac = tm.groupValues[3]
                val ms = when {
                    frac.isEmpty() -> 0L
                    frac.length == 1 -> frac.toLong() * 100L
                    frac.length == 2 -> frac.toLong() * 10L
                    else -> frac.take(3).toLong()
                }
                out += LyricLine(min * 60_000 + sec * 1000 + ms, body)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    fun lineAt(lines: List<LyricLine>, positionMs: Long): LyricLine? {
        if (lines.isEmpty()) return null
        var lo = 0
        var hi = lines.lastIndex
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return if (best >= 0) lines[best] else lines.first()
    }
}
