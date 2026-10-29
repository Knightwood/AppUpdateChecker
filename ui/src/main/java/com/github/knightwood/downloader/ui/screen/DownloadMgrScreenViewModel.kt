package com.github.knightwood.downloader.ui.screen

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.model.FileEntity
import com.github.knightwood.downloader.core.android.model.TaskParams
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import com.github.knightwood.downloader.core.util.HttpConnectionUtils
import com.github.knightwood.downloader.core.util.HttpConstant
import com.github.knightwood.downloader.core.util.UrlResponseMeta
import com.github.knightwood.downloader.ui.room.DownloadTaskDatabase
import com.github.knightwood.downloader.ui.room.entity.DownloadTaskEntity
import com.github.knightwood.downloader.ui.room.model.AndroidPathType
import com.github.knightwood.downloader.ui.room.model.DownloadStatusType
import com.github.knightwood.downloader.ui.repository.DownloadTaskRepository
import com.github.knightwood.downloader.ui.storage.DownloadTarget
import com.github.knightwood.downloader.ui.storage.DownloadTargetResolver
import com.github.knightwood.downloader.ui.util.calculateProgress
import com.github.knightwood.downloader.ui.util.sanitizeFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 下载任务管理器
 *
 * 数据流：
 * ```
 * DownloadServiceLinker ──(stateFlow)──> DownloadTaskRepository ──(写库)──> Room
 * Room ──(observeAll)──> 任务列表 StateFlow ──> 界面
 * ```
 * 界面只从数据库读取任务，因此进程重启后仍能看到历史记录；
 * 运行中的任务由仓库把状态/进度持续写回数据库。
 */
@Stable
class DownloadMgrScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DownloadTaskDatabase.getInstance(application).downloadTaskDao()
    private val repository = DownloadTaskRepository(dao, viewModelScope)

    /** 任务列表，按创建时间倒序 */
    val tasks: StateFlow<List<DownloadTaskItem>> = dao.observeAll()
        .map { records -> records.map { it.toUiItem() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 新建任务弹窗的表单状态 */
    private val _newTask = MutableStateFlow(NewTaskUiState())
    val newTask: StateFlow<NewTaskUiState> = _newTask.asStateFlow()

    /** 是否展示新建任务弹窗（界面内嵌弹窗使用） */
    private val _showNewTaskDialog = MutableStateFlow(false)
    val showNewTaskDialog: StateFlow<Boolean> = _showNewTaskDialog.asStateFlow()

    /** 一次性提示消息 */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var probeJob: Job? = null

    //<editor-fold desc="服务连接">

    /**
     * 下载服务连接成功后由宿主界面调用
     */
    fun onLinkerAttached(linker: DownloadServiceLinker) {
        repository.attach(linker)
    }

    /**
     * 下载服务断开后由宿主界面调用
     */
    fun onLinkerDetached() {
        repository.detach()
    }

    override fun onCleared() {
        super.onCleared()
        repository.detach()
    }

    //</editor-fold>

    //<editor-fold desc="任务操作">

    fun pause(taskId: Int) = repository.pause(taskId)

    fun resume(taskId: Int) = repository.resume(taskId)

    fun cancel(taskId: Int) {
        viewModelScope.launch {
            runCatching { repository.cancel(taskId) }
                .onFailure { _messages.trySend("停止任务失败: ${it.message}") }
        }
    }

    fun delete(taskId: Int) {
        viewModelScope.launch {
            runCatching { repository.delete(taskId) }
                .onFailure { _messages.trySend("删除任务失败: ${it.message}") }
        }
    }

    fun retry(taskId: Int) {
        viewModelScope.launch {
            repository.retry(taskId)
                .onSuccess { _messages.trySend("已重新加入下载队列") }
                .onFailure { _messages.trySend(it.message ?: "重新下载失败") }
        }
    }

    fun clearFinished() {
        viewModelScope.launch {
            runCatching { repository.clearFinished() }
                .onSuccess { _messages.trySend("已清空已完成的任务记录") }
                .onFailure { _messages.trySend("清空失败: ${it.message}") }
        }
    }

    //</editor-fold>

    //<editor-fold desc="新建任务表单">

    fun openNewTaskDialog() {
        _newTask.value = NewTaskUiState()
        _showNewTaskDialog.value = true
    }

    fun dismissNewTaskDialog() {
        _showNewTaskDialog.value = false
        probeJob?.cancel()
        _newTask.value = NewTaskUiState()
    }

    /**
     * 清空表单（供 DialogFragment 入口关闭时调用）
     */
    fun resetNewTaskForm() {
        probeJob?.cancel()
        _newTask.value = NewTaskUiState()
    }

    fun onUrlChanged(url: String) {
        probeJob?.cancel()
        _newTask.update { it.copy(url = url, probe = ProbeState.Idle, error = null) }
    }

    fun onFileNameChanged(name: String) {
        _newTask.update { it.copy(fileName = name, error = null) }
    }

    fun onTargetChanged(target: DownloadTarget) {
        _newTask.update {
            it.copy(
                target = target,
                chunkEnabled = it.multiThreadSupported && DownloadTarget.supportsParallel(target),
            )
        }
    }

    /**
     * SAF 目录选择完成
     *
     * 同时申请该目录的持久化权限，避免重启后无法再次写入
     */
    fun onSafTreeSelected(treeUri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        onTargetChanged(DownloadTarget.SafDirectory(treeUri))
    }

    fun onStoragePermissionResult(granted: Boolean) {
        _messages.trySend(if (granted) "已获得存储权限" else "未获得存储权限，无法写入系统下载目录")
    }

    fun onChunkEnabledChanged(enabled: Boolean) {
        _newTask.update { it.copy(chunkEnabled = enabled) }
    }

    fun onChunkCountChanged(count: Int) {
        _newTask.update { it.copy(chunkCount = count.coerceIn(1, MAX_CHUNK_COUNT)) }
    }

    /**
     * 探测下载地址：获取文件长度、文件名以及是否支持分块下载
     */
    fun probe() {
        val url = _newTask.value.url.trim()
        if (url.isBlank()) {
            _newTask.update { it.copy(error = "请先输入下载地址") }
            return
        }
        probeJob?.cancel()
        _newTask.update { it.copy(probe = ProbeState.Loading, error = null) }
        probeJob = viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) {
                runCatching { HttpConnectionUtils.probe(url) }.getOrNull()
            }
            _newTask.update { state ->
                when {
                    // 探测期间地址已被修改，丢弃本次结果
                    state.url.trim() != url -> state
                    meta == null -> state.copy(probe = ProbeState.Failed("无法获取文件信息，请检查下载地址或网络"))
                    else -> state.copy(
                        probe = ProbeState.Success(meta),
                        fileName = if (state.fileName.isBlank()) sanitizeFileName(meta.fileName) else state.fileName,
                        chunkEnabled = meta.acceptRanges && DownloadTarget.supportsParallel(state.target),
                    )
                }
            }
        }
    }

    /**
     * 提交新的下载任务
     */
    fun submitNewTask() {
        val state = _newTask.value
        if (!state.canSubmit) return
        _newTask.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val app = getApplication<Application>()
            val url = state.url.trim()
            val fileName = sanitizeFileName(state.fileName)
            val mimeType = guessMimeType(fileName)

            if (!hasWritePermission(app, state.target)) {
                _newTask.update { it.copy(submitting = false, error = "写入系统下载目录需要存储权限") }
                return@launch
            }

            // 未主动探测时补一次，用于填充文件长度与分块能力
            val meta = state.meta ?: withContext(Dispatchers.IO) {
                runCatching { HttpConnectionUtils.probe(url) }.getOrNull()
            }

            val androidPath = runCatching {
                DownloadTargetResolver.resolve(app, state.target, fileName, mimeType)
            }.getOrElse { e ->
                _newTask.update { it.copy(submitting = false, error = e.message ?: "创建下载文件失败") }
                return@launch
            }

            val useChunk = state.chunkEnabled &&
                meta != null &&
                meta.acceptRanges &&
                DownloadTarget.supportsParallel(state.target) &&
                meta.length >= HttpConstant.MIN_CHUNK_SIZE

            val params = AndroidDownloadParams().apply {
                fileEntity = FileEntity(
                    url = url,
                    mimeType = mimeType,
                    name = fileName,
                    size = meta?.length,
                )
                taskParams = TaskParams().apply {
                    path = androidPath
                    isSupportChunkDownload = useChunk
                    maxChunkCount = if (useChunk) state.chunkCount else 0
                    minChunkSize = HttpConstant.MIN_CHUNK_SIZE
                }
            }

            repository.submit(params)
                .onSuccess {
                    _newTask.value = NewTaskUiState()
                    _showNewTaskDialog.value = false
                    _messages.trySend("已加入下载队列")
                }
                .onFailure { e ->
                    _newTask.update { it.copy(submitting = false, error = e.message ?: "提交任务失败") }
                }
        }
    }

    private fun hasWritePermission(context: Context, target: DownloadTarget): Boolean {
        if (target != DownloadTarget.PublicDownloads) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun guessMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    //</editor-fold>

    private fun DownloadTaskEntity.toUiItem(): DownloadTaskItem = DownloadTaskItem(
        taskId = taskId,
        name = name,
        url = url,
        state = status,
        totalSize = totalSize,
        downloadedSize = downloadedSize,
        speed = if (status == DownloadStatusType.DOWNLOADING) snapSpeed else Double.NaN,
        chunkCount = chunkCount,
        chunked = isSupportChunkDownload,
        errorMessage = errorMessage,
        targetText = pathText(),
    )

    private fun DownloadTaskEntity.pathText(): String = when (pathType) {
        AndroidPathType.NONE -> "应用缓存目录"
        AndroidPathType.JVM -> pathValue ?: "本地文件"
        AndroidPathType.MEDIA_STORE -> "系统下载目录"
        AndroidPathType.DOCUMENT_FILE -> "自定义文件夹"
    }

    private companion object {
        const val MAX_CHUNK_COUNT = 8
    }
}

/**
 * 任务列表中的一项
 */
data class DownloadTaskItem(
    val taskId: Int,
    val name: String,
    val url: String,
    val state: DownloadStatusType,
    val totalSize: Long,
    val downloadedSize: Long,
    val speed: Double,
    val chunkCount: Int,
    val chunked: Boolean,
    val errorMessage: String?,
    val targetText: String,
) {
    val progress: Float
        get() = if (state == DownloadStatusType.FINISHED) {
            1f
        } else {
            calculateProgress(downloadedSize, totalSize)
        }
}

/**
 * 地址探测状态
 */
sealed interface ProbeState {

    data object Idle : ProbeState

    data object Loading : ProbeState

    data class Success(val meta: UrlResponseMeta) : ProbeState

    data class Failed(val message: String) : ProbeState
}

/**
 * 新建任务表单状态
 */
data class NewTaskUiState(
    val url: String = "",
    val fileName: String = "",
    val target: DownloadTarget = DownloadTarget.AppCache,
    val probe: ProbeState = ProbeState.Idle,
    val chunkEnabled: Boolean = false,
    val chunkCount: Int = HttpConstant.DEFAULT_CHUNK_COUNT,
    val submitting: Boolean = false,
    val error: String? = null,
) {
    /** 探测到的文件元信息 */
    val meta: UrlResponseMeta?
        get() = (probe as? ProbeState.Success)?.meta

    /** 服务器是否支持分块下载 */
    val multiThreadSupported: Boolean
        get() = meta?.acceptRanges == true

    /** 当前选择的保存位置 + 文件大小是否满足分块下载条件 */
    val chunkAvailable: Boolean
        get() = multiThreadSupported &&
            DownloadTarget.supportsParallel(target) &&
            (meta?.length ?: 0L) >= HttpConstant.MIN_CHUNK_SIZE

    /** 实际生效的分块下载开关 */
    val useChunk: Boolean
        get() = chunkEnabled && chunkAvailable

    val canSubmit: Boolean
        get() = url.isNotBlank() &&
            fileName.isNotBlank() &&
            !submitting &&
            probe !is ProbeState.Loading
}
