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
}
