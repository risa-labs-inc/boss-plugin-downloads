package ai.rever.boss.plugin.dynamic.downloads

import ai.rever.boss.plugin.api.DownloadDataProvider
import ai.rever.boss.plugin.api.DownloadItemData
import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.McpToolResult

/**
 * MCP tools contributed by the Downloads plugin: list downloads and
 * pause/resume/cancel/open them. Registered in [DownloadsDynamicPlugin.register];
 * removed automatically on disable/unload.
 */
internal class DownloadsMcpToolProvider(
    override val providerId: String,
    private val downloads: DownloadDataProvider,
) : McpToolProvider {

    override fun tools(): List<McpToolDefinition> = listOf(
        McpToolDefinition(
            name = "downloads_list",
            description = "List downloads with status and progress.",
            handler = McpToolHandler {
                val items = downloads.downloads.value
                if (items.isEmpty()) McpToolResult("No downloads.")
                else McpToolResult(items.joinToString("\n") { format(it) })
            },
        ),
        McpToolDefinition(
            name = "download_cancel",
            description = "Cancel a download by id.",
            inputSchema = idSchema("Download id (from downloads_list)."),
            readOnly = false,
            handler = McpToolHandler { args -> op(args) { downloads.cancelDownload(it) } },
        ),
        McpToolDefinition(
            name = "download_pause",
            description = "Pause a download by id.",
            inputSchema = idSchema("Download id."),
            readOnly = false,
            handler = McpToolHandler { args -> op(args) { downloads.pauseDownload(it) } },
        ),
        McpToolDefinition(
            name = "download_resume",
            description = "Resume a paused download by id.",
            inputSchema = idSchema("Download id."),
            readOnly = false,
            handler = McpToolHandler { args -> op(args) { downloads.resumeDownload(it) } },
        ),
        McpToolDefinition(
            name = "download_open",
            description = "Open a completed download's file.",
            inputSchema = idSchema("Download id."),
            readOnly = false,
            handler = McpToolHandler { args ->
                val id = args.string("id")
                    ?: return@McpToolHandler McpToolResult("Missing required argument: id", isError = true)
                val item = downloads.downloads.value.firstOrNull { it.id == id }
                    ?: return@McpToolHandler McpToolResult("No download with id $id", isError = true)
                downloads.openFile(item.destinationPath)
                McpToolResult("Opening ${item.fileName}.")
            },
        ),
        McpToolDefinition(
            name = "downloads_clear_completed",
            description = "Remove all completed downloads from the list.",
            readOnly = false,
            handler = McpToolHandler {
                downloads.clearCompleted().fold(
                    onSuccess = { McpToolResult("Cleared completed downloads.") },
                    onFailure = { McpToolResult("Failed: ${it.message}", isError = true) },
                )
            },
        ),
    )

    private suspend fun op(
        args: ai.rever.boss.plugin.api.McpToolArgs,
        action: suspend (String) -> Result<Unit>,
    ): McpToolResult {
        val id = args.string("id")
            ?: return McpToolResult("Missing required argument: id", isError = true)
        return action(id).fold(
            onSuccess = { McpToolResult("OK") },
            onFailure = { McpToolResult("Failed: ${it.message}", isError = true) },
        )
    }

    private fun format(d: DownloadItemData): String {
        val pct = d.totalBytes?.takeIf { it > 0 }?.let { " ${(d.receivedBytes * 100 / it)}%" } ?: ""
        val err = d.errorReason?.let { " error=$it" } ?: ""
        return "${d.id}  ${d.status}$pct  ${d.fileName}$err"
    }

    private fun idSchema(desc: String): String =
        """{"type":"object","properties":{"id":{"type":"string","description":"$desc"}},"required":["id"]}"""
}
