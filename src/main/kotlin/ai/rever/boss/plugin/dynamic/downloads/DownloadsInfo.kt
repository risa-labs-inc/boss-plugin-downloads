package ai.rever.boss.plugin.dynamic.downloads

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download

/**
 * Downloads panel info for dynamic plugin.
 *
 * Displays active and completed downloads in a compact sidebar format.
 */
object DownloadsInfo : PanelInfo {
    override val id = PanelId("downloads", 2)
    override val displayName = "Downloads"
    override val icon = Icons.Outlined.Download
    override val defaultSlotPosition = left.top.bottom
}
