package com.example.tvmediaplayer.domain.repo

import com.example.tvmediaplayer.domain.model.SmbConfig
import com.example.tvmediaplayer.domain.model.SmbEntry

interface SmbRepository {
    suspend fun list(config: SmbConfig, path: String): List<SmbEntry>
}
