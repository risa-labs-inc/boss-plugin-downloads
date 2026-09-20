package ai.rever.boss.plugin.dynamic.downloads

import ai.rever.boss.plugin.api.DownloadDataProvider
import ai.rever.boss.plugin.api.DownloadItemData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

internal enum class DownloadAction(val label: String) {
    PAUSE("pause"), RESUME("resume"), CANCEL("cancel"), REMOVE("remove"), CLEAR("clear completed downloads")
}

internal data class DownloadActionFailure(
    val id: String,
    val action: DownloadAction,
    val fileName: String
) {
    val message: String get() = if (action == DownloadAction.CLEAR) {
        "Could not clear completed downloads. Try again."
    } else {
        "Could not ${action.label} $fileName. Try again."
    }
}

/** Keeps rejected actions visible and prevents overlapping commands for one download. */
class DownloadsViewModel internal constructor(
    private val dataProvider: DownloadDataProvider,
    context: CoroutineContext
) {
    constructor(dataProvider: DownloadDataProvider) : this(dataProvider, Dispatchers.Main)

    private val scope = CoroutineScope(context + SupervisorJob())
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    internal val busy = _busy.asStateFlow()
    private val _failures = MutableStateFlow<Map<String, DownloadActionFailure>>(emptyMap())
    internal val failures = _failures.asStateFlow()
    val downloads: StateFlow<List<DownloadItemData>> = dataProvider.downloads

    fun pauseDownload(id: String) = perform(id, DownloadAction.PAUSE)
    fun resumeDownload(id: String) = perform(id, DownloadAction.RESUME)
    fun cancelDownload(id: String) = perform(id, DownloadAction.CANCEL)
    fun removeDownload(id: String) = perform(id, DownloadAction.REMOVE)
    fun clearCompleted() = perform("", DownloadAction.CLEAR)

    internal fun retry(failure: DownloadActionFailure) = perform(failure.id, failure.action)
    internal fun dismissFailure(id: String) { _failures.value -= id }

    private fun perform(id: String, action: DownloadAction) {
        if (id in _busy.value) return
        val name = downloads.value.firstOrNull { it.id == id }?.fileName
            ?: _failures.value[id]?.fileName ?: "this download"
        _busy.value += id
        _failures.value -= id
        scope.launch {
            try {
                val result = when (action) {
                    DownloadAction.PAUSE -> dataProvider.pauseDownload(id)
                    DownloadAction.RESUME -> dataProvider.resumeDownload(id)
                    DownloadAction.CANCEL -> dataProvider.cancelDownload(id)
                    DownloadAction.REMOVE -> dataProvider.removeDownload(id)
                    DownloadAction.CLEAR -> dataProvider.clearCompleted()
                }
                val error = result.exceptionOrNull()
                if (error is CancellationException) throw error
                if (error != null) _failures.value += (id to DownloadActionFailure(id, action, name))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _failures.value += (id to DownloadActionFailure(id, action, name))
            } finally {
                _busy.value -= id
            }
        }
    }

    fun revealInFolder(path: String) = dataProvider.revealInFolder(path)
    fun openFile(path: String) = dataProvider.openFile(path)
    fun dispose() = scope.cancel()
}
