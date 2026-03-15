package com.example.tvmediaplayer.playback

import com.example.tvmediaplayer.domain.model.SmbConfig

object PlaybackConfigStore {
    @Volatile
    private var activeConfig: SmbConfig = SmbConfig.Empty

    fun update(config: SmbConfig) {
        activeConfig = config
    }

    fun current(): SmbConfig = activeConfig
}
