package com.cinehub.player

import java.net.URL

/**
 * Pure URL builder for indexed provider references published by SaimoPlayer.
 * It deliberately builds candidates only; playback success is determined by Media3.
 */
internal object ProviderUrlResolver {
    /** Extracts the signed HLS URL embedded by the authorized Dadinho provider page. */
    fun extractDadinhoStreamUrl(html: String): String? {
        val pattern = Regex("""STREAM_URLS\s*=\s*\[\s*"([^"]+)""")
        val candidate = pattern.find(html)?.groupValues?.getOrNull(1)?.replace("\\/", "/") ?: return null
        return candidate.takeIf(PlaybackUrlPolicy::isSafeHttpUrl)
    }

    fun resolve(base: String, rawReference: String): String? {
        val normalizedBase = if (base.endsWith("/")) base else "$base/"
        val reference = rawReference.trim()
        if (reference.isBlank() || reference.contains("://") ||
            reference.split('/').any { it == ".." }) return null

        val baseUrl = runCatching { URL(normalizedBase) }.getOrNull() ?: return null
        if (baseUrl.protocol !in setOf("http", "https") ||
            baseUrl.host.endsWith(".invalid", ignoreCase = true)) return null

        // Match SaimoTV VodFormato: refs with an extension are used as-is; otherwise append .mp4.
        val suffix = if (reference.contains('.')) reference else "$reference.mp4"
        val candidate = normalizedBase + suffix
        val parsed = runCatching { URL(candidate) }.getOrNull() ?: return null
        if (parsed.protocol !in setOf("http", "https") ||
            parsed.host.endsWith(".invalid", ignoreCase = true)) return null
        return candidate
    }
}
