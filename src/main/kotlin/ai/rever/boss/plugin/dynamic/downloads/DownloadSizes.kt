package ai.rever.boss.plugin.dynamic.downloads

import ai.rever.boss.plugin.api.DownloadItemData
import ai.rever.boss.plugin.api.DownloadStatusData
import java.nio.file.Files
import java.nio.file.Path

/**
 * Run on the IO dispatcher: browser totals can be missing even after completion.
 * The result describes the file currently at the destination, which may have been replaced.
 */
internal fun withCompletedFileSize(download: DownloadItemData): DownloadItemData {
    if (download.status != DownloadStatusData.COMPLETED || download.destinationPath.isBlank()) {
        return download
    }
    val size = readCompletedFileSize(download) ?: return download
    return download.copy(receivedBytes = size, totalBytes = size)
}

private fun readCompletedFileSize(download: DownloadItemData): Long? = runCatching {
    val path = Path.of(download.destinationPath)
    if (Files.isRegularFile(path)) Files.size(path) else null
}.getOrNull()

/**
 * Used by the ViewModel's sequential IO flow. Resolve each completed download once,
 * including unavailable files, and discard entries removed from the download list.
 * On-demand MCP listings still read the current file metadata directly.
 */
internal class CompletedDownloadSizeCache(
    private val readSize: (DownloadItemData) -> Long? = ::readCompletedFileSize,
) {
    private data class Key(
        val id: String,
        val path: String,
        val startTime: Long,
        val endTime: Long?,
    )

    private val sizes = mutableMapOf<Key, Long?>()

    fun enrich(downloads: List<DownloadItemData>): List<DownloadItemData> {
        val liveKeys = mutableSetOf<Key>()
        val enriched = downloads.map { download ->
            if (download.status != DownloadStatusData.COMPLETED || download.destinationPath.isBlank()) {
                download
            } else {
                val key = Key(download.id, download.destinationPath, download.startTime, download.endTime)
                liveKeys.add(key)
                // containsKey also caches a failed lookup, preventing retries on every tick.
                if (!sizes.containsKey(key)) sizes[key] = readSize(download)
                val size = sizes[key]
                if (size == null) download else download.copy(receivedBytes = size, totalBytes = size)
            }
        }
        sizes.keys.retainAll(liveKeys)
        return enriched
    }
}

internal fun buildSizeText(download: DownloadItemData): String {
    if (download.status == DownloadStatusData.COMPLETED) {
        val size = download.receivedBytes.takeIf { it > 0 }
            ?: download.totalBytes?.takeIf { it >= 0 }
        return size?.let(::formatBytes) ?: "Unknown size"
    }
    val received = formatBytes(download.receivedBytes.coerceAtLeast(0))
    val total = download.totalBytes?.takeIf { it > 0 }?.let(::formatBytes)
    return if (total != null) "$received / $total" else "$received • Size unknown"
}
