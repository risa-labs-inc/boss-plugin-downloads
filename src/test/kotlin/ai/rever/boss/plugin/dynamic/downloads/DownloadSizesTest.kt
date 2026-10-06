package ai.rever.boss.plugin.dynamic.downloads

import ai.rever.boss.plugin.api.DownloadItemData
import ai.rever.boss.plugin.api.DownloadStatusData
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadSizesTest {
    private fun download(
        status: DownloadStatusData = DownloadStatusData.COMPLETED,
        received: Long = 2500,
        total: Long? = 0,
        path: String = "",
    ) = DownloadItemData(
        id = "test", fileName = "test.bin", destinationPath = path, url = "",
        status = status, receivedBytes = received, totalBytes = total, speed = 0.0,
        canPause = false, canResume = false, errorReason = null, startTime = 0, endTime = null,
    )

    @Test
    fun completedDownloadUsesTransferredBytesWhenTotalIsInvalid() {
        for (total in listOf(null, 0L, -1L, 1500L)) {
            assertEquals(formatBytes(2500), buildStatusText(download(total = total)))
        }
        assertEquals(formatBytes(5000), buildStatusText(download(received = 0, total = 5000)))
        assertEquals("Unknown size", buildStatusText(download(received = 0, total = -1)))
    }

    @Test
    fun completedDownloadUsesActualFileSizeIncludingEmptyFiles() {
        val file = Files.createTempFile("download-size", ".bin")
        try {
            Files.write(file, ByteArray(4321))
            val item = withCompletedFileSize(download(path = file.toString()))
            assertEquals(4321L, item.receivedBytes)
            assertEquals(4321L, item.totalBytes)
            Files.write(file, ByteArray(0))
            assertEquals("0 B", buildStatusText(withCompletedFileSize(download(path = file.toString()))))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun missingFileAndInvalidPathKeepReportedCounts() {
        val directory = Files.createTempDirectory("download-size")
        try {
            for (path in listOf(directory.resolve("missing").toString(), directory.toString(), "\u0000")) {
                val item = download(path = path)
                assertEquals(item, withCompletedFileSize(item))
            }
        } finally {
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun activeFileSizeNeverOverridesLiveCounts() {
        val file = Files.createTempFile("download-size", ".part")
        try {
            val item = download(status = DownloadStatusData.DOWNLOADING, path = file.toString())
            assertEquals(item, withCompletedFileSize(item))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun pausedDownloadsRetainSizeAndUnknownTotalsAreExplicit() {
        assertEquals(
            "Paused • ${formatBytes(2500)} / ${formatBytes(5000)}",
            buildStatusText(download(status = DownloadStatusData.PAUSED, total = 5000)),
        )
        for (total in listOf(null, 0L, -1L)) {
            assertEquals(
                "${formatBytes(2500)} • Size unknown • 0 B/s",
                buildStatusText(download(status = DownloadStatusData.DOWNLOADING, total = total)),
            )
        }
    }

    @Test
    fun decimalUnitsMatchTheirLabels() {
        assertEquals("999 B", formatBytes(999))
        assertEquals(String.format("%.1f KB", 1.0), formatBytes(1000))
        assertEquals(String.format("%.1f MB", 1.0), formatBytes(1_000_000))
        assertEquals(String.format("%.1f GB", 1.0), formatBytes(1_000_000_000))
        assertEquals(String.format("%.1f TB", 1.0), formatBytes(1_000_000_000_000))
        assertEquals("Unknown size", formatBytes(-1))
    }

    @Test
    fun progressTicksReadCompletedFilesOnlyOnceAndKeepLiveMetadata() {
        var reads = 0
        val cache = CompletedDownloadSizeCache { reads++; 4321L }
        val completed = download(path = "/downloads/completed.bin")
        val active = download(status = DownloadStatusData.DOWNLOADING, path = "/downloads/active.bin")
        repeat(20) { tick ->
            val currentCompleted = completed.copy(fileName = "renamed-$tick.bin")
            val currentActive = active.copy(receivedBytes = tick.toLong())
            val result = cache.enrich(listOf(currentCompleted, currentActive))
            assertEquals(currentCompleted.copy(receivedBytes = 4321, totalBytes = 4321), result[0])
            assertEquals(currentActive, result[1])
        }
        assertEquals(1, reads)
    }

    @Test
    fun unavailableFilesAreCachedWithoutFreezingReportedCounts() {
        var reads = 0
        val cache = CompletedDownloadSizeCache { reads++; null }
        val completed = download(path = "/downloads/missing.bin")
        assertEquals(listOf(completed), cache.enrich(listOf(completed)))
        val updated = completed.copy(receivedBytes = 6000, totalBytes = 6000)
        assertEquals(listOf(updated), cache.enrich(listOf(updated)))
        assertEquals(1, reads)
    }

    @Test
    fun changedDownloadIdentityOrDestinationInvalidatesSize() {
        val original = download(path = "/downloads/original.bin")
        for (changed in listOf(
            original.copy(id = "new-id"),
            original.copy(destinationPath = "/downloads/new.bin"),
            original.copy(startTime = 1),
            original.copy(endTime = 1),
        )) {
            var reads = 0
            val cache = CompletedDownloadSizeCache { ++reads * 1000L }
            assertEquals(1000L, cache.enrich(listOf(original)).single().receivedBytes)
            assertEquals(2000L, cache.enrich(listOf(changed)).single().receivedBytes)
            assertEquals(2000L, cache.enrich(listOf(changed)).single().receivedBytes)
            assertEquals(2, reads)
        }
    }

    @Test
    fun removedOrRestartedDownloadsAreReadAgainOnCompletion() {
        val completed = download(path = "/downloads/completed.bin")
        for (intermediate in listOf(emptyList(), listOf(completed.copy(status = DownloadStatusData.DOWNLOADING)))) {
            var reads = 0
            val cache = CompletedDownloadSizeCache { ++reads * 1000L }
            assertEquals(1000L, cache.enrich(listOf(completed)).single().receivedBytes)
            assertEquals(intermediate, cache.enrich(intermediate))
            assertEquals(2000L, cache.enrich(listOf(completed)).single().receivedBytes)
            assertEquals(2, reads)
        }
    }

    @Test
    fun emptyFilesAreCachedAndBlankDestinationsSkipLookups() {
        var reads = 0
        val cache = CompletedDownloadSizeCache { reads++; 0L }
        val completed = download(path = "/downloads/empty.bin")
        repeat(2) {
            assertEquals("0 B", buildStatusText(cache.enrich(listOf(completed)).single()))
        }
        assertEquals(1, reads)
        val noPath = download()
        assertEquals(listOf(noPath), cache.enrich(listOf(noPath)))
        assertEquals(1, reads)
    }
}
