package ai.rever.boss.plugin.dynamic.downloads

import ai.rever.boss.plugin.api.DownloadDataProvider
import ai.rever.boss.plugin.api.DownloadItemData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DownloadsViewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `error stays reachable without download row and retry works in narrow panel`() {
        val provider = UiProvider()
        val model = DownloadsViewModel(provider)
        try {
            compose.setContent { Box(Modifier.size(280.dp, 320.dp)) { DownloadsView(model) } }
            compose.runOnIdle { model.removeDownload("gone") }
            compose.onNodeWithText("Could not remove this download. Try again.").assertIsDisplayed()
            compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
            compose.onNodeWithText("Could not remove this download. Try again.").assertDoesNotExist()
            compose.onNodeWithText("No downloads").assertIsDisplayed()
            compose.runOnIdle { assertEquals(2, provider.removals) }
        } finally { model.dispose() }
    }

    @Test fun `dismiss removes error without repeating failed operation`() {
        val provider = UiProvider()
        val model = DownloadsViewModel(provider)
        try {
            compose.setContent { DownloadsView(model) }
            compose.runOnIdle { model.removeDownload("gone") }
            compose.onNodeWithText("Dismiss").performClick()
            compose.onNodeWithText("Retry").assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, provider.removals) }
        } finally { model.dispose() }
    }
}

private class UiProvider : DownloadDataProvider {
    override val downloads = MutableStateFlow<List<DownloadItemData>>(emptyList())
    var removals = 0
    override suspend fun removeDownload(id: String): Result<Unit> {
        removals++
        return if (removals == 1) Result.failure(IllegalStateException("refused")) else Result.success(Unit)
    }
    override suspend fun pauseDownload(id: String) = Result.success(Unit)
    override suspend fun resumeDownload(id: String) = Result.success(Unit)
    override suspend fun cancelDownload(id: String) = Result.success(Unit)
    override suspend fun clearCompleted() = Result.success(Unit)
    override fun revealInFolder(path: String) = Unit
    override fun openFile(path: String) = Unit
}
