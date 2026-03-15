package com.example.tvmediaplayer.playback

import com.example.tvmediaplayer.lyrics.LrcParser
import java.io.File
import org.jaudiotagger.audio.AudioFileIO
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleMediaValidationTest {

    private fun resolveSampleFile(name: String): File {
        val candidates = listOf(
            File("sample/$name"),
            File("../sample/$name"),
            File("../../sample/$name")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Sample file not found: $name")
    }

    @Test
    fun lrcSampleShouldBeParsable() {
        val lrc = resolveSampleFile("トラック01.lrc")
        val content = lrc.readText(Charsets.UTF_8)
        val timeline = LrcParser.parseTimeline(content)
        assertTrue("LRC should contain parsed lines", timeline.lines.isNotEmpty())
        assertTrue(
            "LRC should contain long line for wrapping validation",
            timeline.lines.any { it.text.length >= 20 }
        )
    }

    @Test
    fun mp3SampleShouldContainEmbeddedArtwork() {
        val mp3 = resolveSampleFile("トラック01.mp3")
        val audio = AudioFileIO.read(mp3)
        val artwork = audio.tag?.firstArtwork
        assertNotNull("Sample MP3 should contain embedded artwork", artwork)
        assertTrue("Embedded artwork bytes should not be empty", (artwork?.binaryData?.size ?: 0) > 0)
    }
}
