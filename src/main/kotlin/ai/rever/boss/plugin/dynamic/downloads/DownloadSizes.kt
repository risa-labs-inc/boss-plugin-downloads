package ai.rever.boss.plugin.dynamic.downloads

import ai.rever.boss.plugin.api.DownloadItemData
import ai.rever.boss.plugin.api.DownloadStatusData
import java.nio.file.Files
import java.nio.file.Path

/** Run on the IO dispatcher: browser totals can be missing even after completion. */
internal fun withCompletedFileSize(download: DownloadItemData): DownloadItemData {
    if (download.status != DownloadStatusData.COMPLETED || download.destinationPath.isBlank()) {
        return download
    }
    val size = runCatching {
        val path = Path.of(download.destinationPath)
        if (Files.isRegularFile(path)) Files.size(path) else null
    }.getOrNull() ?: return download
    return download.copy(receivedBytes = size, totalBytes = size)
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
