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
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.util.HttpConnectionUtils
import com.github.knightwood.downloader.core.util.HttpConstant
import com.github.knightwood.downloader.core.util.UrlResponseMeta
import com.github.knightwood.downloader.ui.room.DownloadTaskDatabase
import com.github.knightwood.downloader.ui.room.entity.DownloadTaskEntity
import com.github.knightwood.downloader.ui.room.model.AndroidPathType
import com.github.knightwood.downloader.ui.room.model.DownloadStatusType
import com.github.knightwood.downloader.ui.repository.DownloadTaskRepository
import com.github.knightwood.downloader.ui.repository.TaskRuntimeState
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
 * DownloadServiceLinker ──(stateFlow + currentDownloadingProgress)──> DownloadTaskRepository
 *                                          │
 *        ┌───────────────┴───────────────┐
 *   状态变化写库（Room）                 进度发布到内存（runtimeStates）
 *        │                                 │
 *        └──────────> tasks: StateFlow ──┘
 *                          │
 *                        界面
 * ```
 * 历史记录从数据库加载，运行中任务的进度直接从 [DownloadTaskRepository.runtimeStates]
 * （即 `DownloadTask` 的 `stateFlow`/`currentDownloadingProgress`）读取，不经过数据库。
 */
@Stable
class DownloadMgrScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = DownloadTaskDatabase.getInstance(application).downloadTaskDao()
    private val repository = DownloadTaskRepository(dao, viewModelScope)

    /** 任务列表：数据库记录 + 服务中任务的实时状态/进度 */
    val tasks: StateFlow<List<DownloadTaskItem>> = combine(
        dao.observeAll(),
        repository.runtimeStates,
    ) { records, runtimeStates ->
        records.map { record -> record.toUiItem(runtimeStates[record.taskId]) }
    }
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
        _newTask.update { state ->
            state.copy(
                chunkCount = count.coerceIn(
                    NewTaskUiState.MIN_CHUNK_COUNT,
                    state.maxChunkCount,
                ),
            )
        }
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
                    ).normalized()
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
                    // 每块不小于最小分块大小, 且限制在 1 ~ 128 之间
                    chunkCount = if (useChunk) {
                        state.chunkCount.coerceIn(
                            NewTaskUiState.MIN_CHUNK_COUNT,
                            NewTaskUiState.maxChunkCountOf(meta.length, state.minChunkSize),
                        )
                    } else {
                        0
                    }
                    minChunkSize = state.minChunkSize
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

    /**
     * 把数据库记录与运行时状态合并成列表项
     *
     * 进度优先取内存中的 [TaskRuntimeState]（逐帧刷新），
     * 没有运行时状态（进程重启后的历史记录）时回退到数据库中的秒级快照。
     */
    private fun DownloadTaskEntity.toUiItem(runtime: TaskRuntimeState?): DownloadTaskItem {
        return if (runtime != null) toUiItemWithRuntime(runtime) else toUiItemWithRecord()
    }

    /**
     * @receiver 数据库记录
     * @param runtime 任务运行中的实时状态
     */
    private fun DownloadTaskEntity.toUiItemWithRuntime(runtime: TaskRuntimeState): DownloadTaskItem {
        val downloadStatusType = DownloadStatusType.of(runtime.status)
        //实时下载进度
        val downloadProgressRecord = runtime.progress
        val total = downloadProgressRecord?.length ?: size ?: totalSize
        val downloaded = when {
            downloadProgressRecord != null -> downloadProgressRecord.progress
            // 已完成的任务没有运行时进度，按文件长度展示
            downloadStatusType == DownloadStatusType.FINISHED -> total
            else -> this.downloadedSize
        }
        return DownloadTaskItem(
            taskId = taskId,
            name = name,
            url = url,
            state = downloadStatusType,
            totalSize = total,
            downloadedSize = downloaded,
            speed = if (downloadStatusType == DownloadStatusType.DOWNLOADING) {
                downloadProgressRecord?.snapSpeed ?: Double.NaN
            } else {
                Double.NaN
            },
            chunkCount = this@toUiItemWithRuntime.chunkCount,
            chunked = isSupportChunkDownload,
            errorMessage = (runtime?.status as? DownloadStatus.ERROR)?.e?.message ?: errorMessage,
            targetText = pathText(),
        )
    }

    private fun DownloadTaskEntity.toUiItemWithRecord(): DownloadTaskItem {
        return DownloadTaskItem(
            taskId = taskId,
            name = name,
            url = url,
            state = status,
            totalSize = this.totalSize,
            downloadedSize = this.downloadedSize,
            speed = Double.NaN,
            chunkCount = this@toUiItemWithRecord.chunkCount,
            chunked = isSupportChunkDownload,
            errorMessage = errorMessage,
            targetText = pathText(),
        )
    }

    private fun DownloadTaskEntity.pathText(): String = when (pathType) {
        AndroidPathType.NONE -> "应用缓存目录"
        AndroidPathType.JVM -> pathValue ?: "本地文件"
        AndroidPathType.MEDIA_STORE -> "系统下载目录"
        AndroidPathType.DOCUMENT_FILE -> "自定义文件夹"
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
    /* 最小分块长度，单位字节，后续会从datastore中加载赋值，所以不能删除此变量并在代码中固定使用 HttpConstant.MIN_CHUNK_SIZE */
    val minChunkSize: Long = HttpConstant.MIN_CHUNK_SIZE,
    /* 当前分块数量 */
    val chunkCount: Int = 1,
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
                (meta?.length ?: 0L) >= minChunkSize

    /** 实际生效的分块下载开关 */
    val useChunk: Boolean
        get() = chunkEnabled && chunkAvailable

    /**
     * 分块数量上限
     *
     * 由文件长度除以最小分块大小得到（保证每块不小于 minChunkSize），
     * 并限制在 [MIN_CHUNK_COUNT] ~ [MAX_CHUNK_COUNT] 之间。
     */
    val maxChunkCount: Int
        get() = maxChunkCountOf(meta?.length ?: 1L, minChunkSize)

    /**
     * 把分块数量收敛到当前文件允许的区间
     *
     * 未探测到文件信息时不处理，避免把用户的选择提前压缩成下限。
     */
    fun normalized(): NewTaskUiState {
        if (meta == null) return this
        val count = chunkCount.coerceIn(MIN_CHUNK_COUNT, maxChunkCount)
        return if (count == chunkCount) this else copy(chunkCount = count)
    }

    val canSubmit: Boolean
        get() = url.isNotBlank() &&
                fileName.isNotBlank() &&
                !submitting &&
                probe !is ProbeState.Loading

    companion object {

        /** 分块数量下限 */
        const val MIN_CHUNK_COUNT = 2

        /** 分块数量上限 */
        const val MAX_CHUNK_COUNT = 128

        /**
         * 根据文件长度计算分块数量上限
         *
         * @param length 文件总字节数，未知时传 0
         * @param minChunkSize 最小分块大小，单位字节
         */
        fun maxChunkCountOf(length: Long, minChunkSize: Long): Int {
            if (length <= 0L) return MIN_CHUNK_COUNT
            val actualChunkCount = HttpConnectionUtils.solveChunkCount(length, minChunkSize)
            return actualChunkCount.coerceIn(MIN_CHUNK_COUNT, MAX_CHUNK_COUNT)
        }
    }
}
