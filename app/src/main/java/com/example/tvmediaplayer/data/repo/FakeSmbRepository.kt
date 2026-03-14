package com.example.tvmediaplayer.data.repo

import com.example.tvmediaplayer.domain.model.SmbConfig
import com.example.tvmediaplayer.domain.model.SmbEntry
import com.example.tvmediaplayer.domain.repo.SmbRepository
import kotlinx.coroutines.delay

class FakeSmbRepository : SmbRepository {
    override suspend fun list(config: SmbConfig, path: String): List<SmbEntry> {
        delay(180)
        if (config.host.isBlank() || config.share.isBlank()) {
            throw IllegalArgumentException("SMB host/share is required")
        }

        val nowPath = path.trim('/').ifBlank { config.normalizedPath() }
        val prefix = if (nowPath.isBlank()) "" else "$nowPath/"
        return listOf(
            SmbEntry(name = "..", fullPath = nowPath, isDirectory = true),
            SmbEntry(name = "ACG", fullPath = "${prefix}ACG", isDirectory = true),
            SmbEntry(name = "Classics", fullPath = "${prefix}Classics", isDirectory = true),
            SmbEntry(
                name = "01 - intro.flac",
                fullPath = "${prefix}01 - intro.flac",
                isDirectory = false,
                streamUri = "${config.rootUrl()}/01%20-%20intro.flac"
            ),
            SmbEntry(
                name = "02 - theme.mp3",
                fullPath = "${prefix}02 - theme.mp3",
                isDirectory = false,
                streamUri = "${config.rootUrl()}/02%20-%20theme.mp3"
            )
        )
    }
}
