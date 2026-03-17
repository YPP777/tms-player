package com.github.gbandszxc.tvmediaplayer.lyrics

import android.util.Log
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.playback.SmbPathResolver
import java.io.File
import java.nio.charset.Charset
import java.util.Properties
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

class SmbLyricsRepository {

    companion object {
        private const val TAG = "SmbLyrics"
    }

    suspend fun load(config: SmbConfig, entry: SmbEntry): LrcTimeline? = withContext(Dispatchers.IO) {
        if (entry.isDirectory || entry.streamUri.isNullOrBlank()) return@withContext null
        Log.d(TAG, "load: uri=${entry.streamUri}, configHost=${config.host}")

        val context = buildContext(config)
        val external = runCatching { loadExternalLrc(config, entry, context) }.getOrNull()
        if (external != null && external.lines.isNotEmpty()) {
            Log.d(TAG, "load: external lrc ok, lines=${external.lines.size}")
            return@withContext external
        }

        Log.d(TAG, "load: no external lrc, trying embedded")
        val embedded = runCatching { loadEmbeddedLyrics(context, entry) }.getOrElse { e ->
            Log.w(TAG, "load: embedded failed", e); null
        }
        if (embedded.isNullOrBlank()) { Log.d(TAG, "load: no embedded lyrics"); return@withContext null }

        val maybeTimeline = LrcParser.parseTimeline(embedded)
        if (maybeTimeline.lines.isNotEmpty()) maybeTimeline else LrcTimeline(
            lines = listOf(LyricLine(0, embedded.trim())),
            offsetMs = 0
        )
    }

    private fun loadExternalLrc(config: SmbConfig, entry: SmbEntry, context: CIFSContext): LrcTimeline? {
        val candidates = linkedSetOf<String>()
        val stream = entry.streamUri.orEmpty()
        if (stream.startsWith("smb://", ignoreCase = true)) {
            val lrcByUri = stream.substringBeforeLast('.', stream) + ".lrc"
            candidates.add(lrcByUri)
        }
        val resolvedPath = SmbPathResolver.buildExternalLrcPath(config, entry)
        if (resolvedPath.isNotBlank()) candidates.add(resolvedPath)
        Log.d(TAG, "loadExternalLrc: candidates=$candidates")

        for (lrcPath in candidates) {
            runCatching {
                val lrcFile = SmbFile(lrcPath, context)
                val exists = lrcFile.exists()
                Log.d(TAG, "loadExternalLrc: $lrcPath exists=$exists")
                if (!exists || lrcFile.isDirectory) return@runCatching
                val bytes = SmbFileInputStream(lrcFile).use { it.readBytes() }
                val content = decodeText(bytes)
                val timeline = LrcParser.parseTimeline(content)
                Log.d(TAG, "loadExternalLrc: parsed ${timeline.lines.size} lines from $lrcPath")
                if (timeline.lines.isNotEmpty()) return timeline
            }.onFailure { e -> Log.w(TAG, "loadExternalLrc: failed for $lrcPath", e) }
        }
        return null
    }

    private fun loadEmbeddedLyrics(context: CIFSContext, entry: SmbEntry): String? {
        val smbFile = SmbFile(requireNotNull(entry.streamUri), context)
        // 必须用真实扩展名，jaudiotagger 用扩展名选解析器，.tmp 会导致 CannotReadException
        val ext = entry.streamUri!!.substringAfterLast('.', "").lowercase()
            .let { if (it.isBlank() || it.length > 8) "mp3" else it }
        val tempFile = File.createTempFile("lyrics-", ".$ext")
        return try {
            SmbFileInputStream(smbFile).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tag ?: return null
            tag.getFirst(FieldKey.LYRICS).takeIf { it.isNotBlank() }
        } finally {
            tempFile.delete()
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) {
                return bytes.toString(Charsets.UTF_16LE)
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return bytes.toString(Charsets.UTF_16BE)
            }
        }
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) {
            bytes.toString(Charset.forName("GB18030"))
        } else {
            utf8
        }
    }

    private fun buildContext(config: SmbConfig): CIFSContext {
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.responseTimeout", "10000")
            setProperty("jcifs.smb.client.connTimeout", "10000")
            setProperty("jcifs.smb.client.soTimeout", "10000")
            if (config.smb1Enabled) {
                setProperty("jcifs.smb.client.minVersion", "SMB1")
            } else {
                setProperty("jcifs.smb.client.minVersion", "SMB202")
            }
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
        }
        val base = BaseContext(PropertyConfiguration(properties))
        return if (config.guest) {
            base.withCredentials(NtlmPasswordAuthenticator("", ""))
        } else {
            base.withCredentials(NtlmPasswordAuthenticator("", config.username.trim(), config.password))
        }
    }
}
