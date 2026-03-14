package com.example.tvmediaplayer.lyrics

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

object LrcParser {
    private val timeTag = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?]")

    fun parse(content: String): List<LyricLine> {
        return content.lineSequence().flatMap { line ->
            val text = line.replace(timeTag, "").trim()
            timeTag.findAll(line).map { match ->
                val minute = match.groupValues[1].toLong()
                val second = match.groupValues[2].toLong()
                val milliText = match.groupValues[3]
                val milli = if (milliText.isBlank()) 0 else milliText.padEnd(3, '0').take(3).toLong()
                LyricLine(
                    timestampMs = minute * 60_000 + second * 1_000 + milli,
                    text = text
                )
            }
        }.sortedBy { it.timestampMs }.toList()
    }
}
