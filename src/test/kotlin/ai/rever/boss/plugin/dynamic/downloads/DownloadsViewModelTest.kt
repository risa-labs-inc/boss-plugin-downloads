package ai.rever.boss.plugin.dynamic.downloads

import ai.rever.boss.plugin.api.DownloadDataProvider
import ai.rever.boss.plugin.api.DownloadItemData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    @Test fun `failed operation stays visible and retry clears it`() = runTest {
        val provider = FakeProvider()
        val model = DownloadsViewModel(provider, StandardTestDispatcher(testScheduler))
        model.removeDownload("one")
        runCurrent()
        val failure = model.failures.value.getValue("one")
        assertEquals(DownloadAction.REMOVE, failure.action)
        assertFalse(failure.message.contains("private backend details"))
        provider.result = Result.success(Unit)
        model.retry(failure)
        runCurrent()
        assertTrue(model.failures.value.isEmpty())
        assertEquals(listOf("remove:one", "remove:one"), provider.calls)
        model.dispose()
    }

    @Test fun `repeated clicks do not overlap and other downloads remain usable`() = runTest {
        val provider = FakeProvider().apply { gate = CompletableDeferred() }
        val model = DownloadsViewModel(provider, StandardTestDispatcher(testScheduler))
        model.pauseDownload("one")
        model.removeDownload("one")
        model.pauseDownload("two")
        runCurrent()
        assertEquals(setOf("one", "two"), model.busy.value)
        assertEquals(listOf("pause:one", "pause:two"), provider.calls)
        provider.gate!!.complete(Unit)
        runCurrent()
        assertTrue(model.busy.value.isEmpty())
        assertEquals(setOf("one", "two"), model.failures.value.keys)
        model.dismissFailure("one")
        assertEquals(setOf("two"), model.failures.value.keys)
        model.dispose()
    }

    @Test fun `provider exceptions are recoverable without exposing their text`() = runTest {
        val provider = FakeProvider().apply { throws = true }
        val model = DownloadsViewModel(provider, StandardTestDispatcher(testScheduler))
        model.resumeDownload("one")
        runCurrent()
        assertEquals(DownloadAction.RESUME, model.failures.value.getValue("one").action)
        assertTrue(model.busy.value.isEmpty())
        model.dispose()
    }

    @Test fun `disposing pending action does not report failure`() = runTest {
        val provider = FakeProvider().apply { gate = CompletableDeferred() }
        val model = DownloadsViewModel(provider, StandardTestDispatcher(testScheduler))
        model.cancelDownload("one")
        runCurrent()
        model.dispose()
        runCurrent()
        assertTrue(model.failures.value.isEmpty())
        assertTrue(model.busy.value.isEmpty())
    }

    @Test fun `clear failure can be retried without removing another failure`() = runTest {
        val provider = FakeProvider()
        val model = DownloadsViewModel(provider, StandardTestDispatcher(testScheduler))
        model.cancelDownload("one")
        model.clearCompleted()
        runCurrent()
        assertEquals(2, model.failures.value.size)
        provider.result = Result.success(Unit)
        model.retry(model.failures.value.getValue(""))
        runCurrent()
        assertEquals(setOf("one"), model.failures.value.keys)
        model.dispose()
    }
}

private class FakeProvider : DownloadDataProvider {
    override val downloads = MutableStateFlow<List<DownloadItemData>>(emptyList())
    val calls = mutableListOf<String>()
    var result: Result<Unit> = Result.failure(IllegalStateException("private backend details"))
    var gate: CompletableDeferred<Unit>? = null
    var throws = false
    private suspend fun action(name: String): Result<Unit> {
        calls += name
        gate?.await()
        if (throws) error("private backend details")
        return result
    }
    override suspend fun pauseDownload(id: String) = action("pause:$id")
    override suspend fun resumeDownload(id: String) = action("resume:$id")
    override suspend fun cancelDownload(id: String) = action("cancel:$id")
    override suspend fun removeDownload(id: String) = action("remove:$id")
    override suspend fun clearCompleted() = action("clear")
    override fun revealInFolder(path: String) = Unit
    override fun openFile(path: String) = Unit
}
