package ai.rever.boss.plugin.dynamic.downloads

import ai.rever.boss.plugin.api.DownloadDataProvider
import ai.rever.boss.plugin.api.ActiveTabsProvider
import ai.rever.boss.plugin.api.LocalWindowIdProvider
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.ui.BossTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle

/**
 * Component for the Downloads panel.
 */
class DownloadsComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    dataProvider: DownloadDataProvider,
    private val activeTabsProvider: ActiveTabsProvider? = null
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = DownloadsViewModel(dataProvider)

    init {
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() {
                viewModel.dispose()
            }
        })
    }

    @Composable
    override fun Content() {
        val tabs = activeTabsProvider?.activeTabs?.collectAsState()?.value.orEmpty()
        val windowId = LocalWindowIdProvider.current?.getWindowId()
        val showTitle = windowId != null && tabs.any {
            it.windowId == windowId &&
                it.typeId == "panel-host" &&
                it.tabId == "panel-tab:${panelInfo.id.panelId}"
        }
        BossTheme {
            DownloadsView(viewModel = viewModel, showTitle = showTitle)
        }
    }
}
