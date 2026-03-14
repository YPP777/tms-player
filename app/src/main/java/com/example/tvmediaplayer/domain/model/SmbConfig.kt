package com.example.tvmediaplayer.domain.model

data class SmbConfig(
    val host: String,
    val share: String,
    val path: String,
    val username: String,
    val password: String,
    val guest: Boolean
) {
    fun normalizedPath(): String = path.trim().trim('/').replace("\\", "/")

    fun rootUrl(): String {
        val base = "smb://${host.trim()}/${share.trim()}"
        val sub = normalizedPath()
        return if (sub.isBlank()) base else "$base/$sub"
    }

    companion object {
        val Empty = SmbConfig(
            host = "",
            share = "",
            path = "",
            username = "",
            password = "",
            guest = true
        )
    }
}
