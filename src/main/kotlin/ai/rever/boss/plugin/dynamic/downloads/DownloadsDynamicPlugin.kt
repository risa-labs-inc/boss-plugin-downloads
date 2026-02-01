package ai.rever.boss.plugin.dynamic.downloads

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * Dynamic plugin for Downloads panel.
 *
 * This plugin displays active and completed downloads in a sidebar panel.
 * It requires the DownloadDataProvider service from the host application.
 */
class DownloadsDynamicPlugin : DynamicPlugin {
    override val pluginId = "ai.rever.boss.plugin.dynamic.downloads"
    override val displayName = "Downloads (Dynamic)"
    override val version = "1.0.0"
    override val description = "Displays active and completed downloads in a sidebar panel"
    override val author = "Risa Labs"
    override val url = "https://github.com/risa-labs-inc/boss-plugin-downloads"

    override fun register(context: PluginContext) {
        val dataProvider = context.downloadDataProvider
            ?: throw IllegalStateException(
                "Downloads plugin requires DownloadDataProvider. " +
                "Ensure the host provides downloadDataProvider in PluginContext."
            )

        context.panelRegistry.registerPanel(DownloadsInfo) { ctx, panelInfo ->
            DownloadsComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                dataProvider = dataProvider
            )
        }
    }
}
