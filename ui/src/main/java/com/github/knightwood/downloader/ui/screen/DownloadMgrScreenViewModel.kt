package com.github.knightwood.downloader.ui.screen

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.model.FileEntity
import com.github.knightwood.downloader.core.android.model.TaskParams
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import com.github.knightwood.downloader.core.model.DownloadProgressRecord
import com.github.knightwood.downloader.core.model.DownloadStatus
import com.github.knightwood.downloader.core.util.DownloadFormat
import com.github.knightwood.downloader.core.util.HttpConnectionUtils
import com.github.knightwood.downloader.core.util.HttpConstant
import com.github.knightwood.downloader.core.util.UrlResponseMeta
import com.github.knightwood.downloader.ui.room.DownloadTaskDatabase
import com.github.knightwood.downloader.ui.room.entity.DownloadTaskEntity
import com.github.knightwood.downloader.ui.room.entity.SafPermissionEntity
import com.github.knightwood.downloader.ui.room.model.AndroidPathType
import com.github.knightwood.downloader.ui.room.model.DownloadStatusType
import com.github.knightwood.downloader.ui.repository.DownloadTaskRepository
import com.github.knightwood.downloader.ui.repository.TaskRuntimeState
import com.github.knightwood.downloader.ui.storage.DownloadTarget
import com.github.knightwood.downloader.ui.storage.DownloadTargetResolver
import com.github.knightwood.downloader.ui.util.OperateLocker
import com.github.knightwood.downloader.ui.util.SqlUtils
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * 下载任务管理器
 *
 * 数据流：
 * ```
 * DownloadServiceLinker ──(eventFlow + progressFlow)──> DownloadTaskRepository
 *                                          │
 *        ┌───────────────┴───────────────┐
 *   状态变化写库（Room）                 进度发布到内存（runtimeStates）
 *        │                                 │
 *        └──────────> tasks: StateFlow ──┘
 *                          │
 *                        界面
 * ```
 * 历史记录从数据库加载，运行中任务的进度直接从 [DownloadTaskRepository.runtimeStates]
 * （即 `DownloadTask` 的 `eventFlow`/`progressFlow`）读取，不经过数据库。
 */
@Stable
class DownloadMgrScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = javaClass.simpleName
    private val dao = DownloadTaskDatabase.getInstance(application).downloadTaskDao()
    private val safPermissionDao = DownloadTaskDatabase.getInstance(application).safPermissionDao()
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

    /** SAF 已授权的文件夹列表（来自数据库，供选择弹窗加载） */
    val safPermissions: StateFlow<List<SafPermissionEntity>> = safPermissionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 是否展示 SAF 文件夹选择弹窗 */
    private val _showSafSelector = MutableStateFlow(false)
    val showSafSelector: StateFlow<Boolean> = _showSafSelector.asStateFlow()

    /** 一次性事件：通知宿主界面拉起系统文件夹选择器 */
    private val _openSystemPicker = Channel<Unit>(Channel.BUFFERED)
    val openSystemPicker = _openSystemPicker.receiveAsFlow()

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

    val locker by lazy { OperateLocker(viewModelScope) }
    fun pause(taskId: Int) = locker.launchTaskOp() {
        runCatching { repository.pause(taskId) }
            .onFailure { _messages.trySend("暂停任务失败: ${it.message}") }
    }

    fun resume(taskId: Int) = locker.launchTaskOp() {
        runCatching { repository.resume(taskId) }
            .onFailure { _messages.trySend("恢复任务失败: ${it.message}") }
    }

    fun cancel(taskId: Int) = locker.launchTaskOp() {
        runCatching { repository.cancel(taskId) }
            .onFailure { _messages.trySend("停止任务失败: ${it.message}") }
    }

    fun delete(taskId: Int) = locker.launchTaskOp() {
        runCatching { repository.delete(taskId) }
            .onFailure { _messages.trySend("删除任务失败: ${it.message}") }
    }

    fun retry(taskId: Int) = locker.launchTaskOp() {
        repository.retry(taskId)
            .onSuccess { _messages.trySend("已重新加入下载队列") }
            .onFailure { _messages.trySend(it.message ?: "重新下载失败") }
    }

    fun clearFinished() = locker.launchTaskOp() {
        runCatching { repository.clearFinished() }
            .onSuccess { _messages.trySend("已清空已完成的任务记录") }
            .onFailure { _messages.trySend("清空失败: ${it.message}") }

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
     * 点击“自定义文件夹”
     *
     * - 表中没有授权记录：直接拉起系统选择器让用户选取并记录
     * - 已有授权记录：弹出 [SAFSelectorDialog] 供用户选择/管理
     */
    fun onPickCustomFolder() {
        viewModelScope.launch {
            if (safPermissionDao.count() > 0) {
                _showSafSelector.value = true
            } else {
                _openSystemPicker.trySend(Unit)
            }
        }
    }

    /** 弹窗内点击“新增文件夹”，拉起系统选择器（选择结果返回后会自动记录） */
    fun onAddNewSafDirectory() {
        viewModelScope.launch { _openSystemPicker.send(Unit) }
    }

    fun dismissSafSelector() {
        _showSafSelector.value = false
    }

    /**
     * SAF 目录选择完成
     *
     * 申请该目录的持久化读写权限，并把目录信息与授权状态记录到数据库，
     * 避免重启后无法再次写入。
     */
    fun onSafTreeSelected(treeUri: Uri) {
        val granted = runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = safPermissionDao.findByTreeUri(treeUri.toString())
            val entity = SafPermissionEntity(
                id = existing?.id ?: 0L,
                treeUri = treeUri.toString(),
                displayName = queryTreeDisplayName(treeUri),
                hasWritePermission = granted,
                createTime = existing?.createTime ?: now,
                updateTime = now,
            )
            safPermissionDao.upsert(entity)
            _showSafSelector.value = false
            onTargetChanged(DownloadTarget.SafDirectory(treeUri))
            if (!granted) _messages.trySend("已记录目录，但未取得持久化权限，重启后可能需要重新授权")
        }
    }

    /**
     * 从 [SAFSelectorDialog] 中选择一条已有的授权记录
     */
    fun onSafDirectoryChosen(permission: SafPermissionEntity) {
        viewModelScope.launch {
            safPermissionDao.touch(permission.id, System.currentTimeMillis())
            _showSafSelector.value = false
            onTargetChanged(DownloadTarget.SafDirectory(Uri.parse(permission.treeUri)))
        }
    }

    /**
     * 删除一条授权记录，同时释放系统侧的持久化权限
     */
    fun onDeleteSafPermission(permission: SafPermissionEntity) {
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    Uri.parse(permission.treeUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            safPermissionDao.deleteById(permission.id)
            // 若当前表单选中的正是被删除的目录，回退到默认位置
            if ((_newTask.value.target as? DownloadTarget.SafDirectory)?.treeUri?.toString() == permission.treeUri) {
                onTargetChanged(DownloadTarget.AppCache)
            }
        }
    }

    /**
     * 查询 tree uri 对应的目录展示名称，失败时回退为 lastPathSegment
     */
    private suspend fun queryTreeDisplayName(treeUri: Uri): String = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        runCatching {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            app.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) {
                    cursor.getString(index)
                } else null
            }
        }.getOrNull()
            ?: treeUri.lastPathSegment
            ?: treeUri.toString()
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
                    else -> {
                        val fileName = if (state.fileName.isBlank()) DownloadFormat.sanitizeFileName(meta.fileName) else state.fileName
                        // 与数据库中已有记录重名时，在文件名后追加序号避免冲突
                        val uniqueFileName = makeUniqueFileName(fileName)

                        state.copy(
                            probe = ProbeState.Success(meta),
                            fileName = uniqueFileName,
                            chunkEnabled = meta.acceptRanges && DownloadTarget.supportsParallel(state.target),
                        ).normalized()
                    }
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
            var fileName = DownloadFormat.sanitizeFileName(state.fileName)
            fileName = makeUniqueFileName(fileName)
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

    /**
     * 生成与数据库已有记录不重复的文件名
     *
     * 未重名时原样返回；重名时在文件名主体与扩展名之间追加递增序号，
     * 例如 `app.apk` -> `app(1).apk`、`app(2).apk`，扩展名保持不变。
     */
    private suspend fun makeUniqueFileName(fileName: String): String {
        val existing = dao.getAllNamesLike(SqlUtils.escapeLike(fileName)).toHashSet()
        if (fileName !in existing) return fileName
        val dot = fileName.lastIndexOf('.')
        val hasExtension = dot > 0
        val base = if (hasExtension) fileName.substring(0, dot) else fileName
        val extension = if (hasExtension) fileName.substring(dot) else ""
        var index = 1
        var candidate: String
        do {
            candidate = "$base($index)$extension"
            index++
        } while (candidate in existing)
        return candidate
    }

    //</editor-fold>

    /**
     * 把数据库记录与运行时状态合并成列表项
     *
     * 进度优先取内存中的 [TaskRuntimeState]（逐帧刷新），
     * 没有运行时状态（进程重启后的历史记录）时回退到数据库中的秒级快照。
     */
    private fun DownloadTaskEntity.toUiItem(runtime: TaskRuntimeState?): DownloadTaskItem {
        return if (runtime != null && runtime.progress != null) {
            toUiItemWithRuntime(runtime.status, runtime.progress)
        } else {
            toUiItemWithRecord()
        }
    }

    /**
     * 将任务运行中的实时状态转换成列表项状态
     *
     * @receiver 数据库记录
     * @param status 任务状态
     * @param progressRecord 任务进度
     */
    private fun DownloadTaskEntity.toUiItemWithRuntime(
        status: DownloadStatus,
        progressRecord: DownloadProgressRecord,
    ): DownloadTaskItem {
        val downloadStatusType = DownloadStatusType.of(status)
        val total = progressRecord.length
        val downloaded = when {
            // 已完成的任务没有运行时进度，按文件长度展示
            downloadStatusType == DownloadStatusType.FINISHED -> total
            else -> progressRecord.downloaded
        }
        Log.d(TAG, "toUiItemWithRuntime: $taskId $name $downloadStatusType $total $downloaded")
        return DownloadTaskItem(
            taskId = taskId,
            name = name,
            url = url,
            state = downloadStatusType,
            totalSize = total,
            downloadedSize = downloaded,
            speed = if (downloadStatusType == DownloadStatusType.DOWNLOADING) progressRecord.snapSpeed else Double.NaN,
            chunkCount = this@toUiItemWithRuntime.chunkCount,
            chunked = isSupportChunkDownload,
            errorMessage = (status as? DownloadStatus.ERROR)?.e?.message ?: errorMessage,
            targetText = pathText(),
        )
    }

    private fun DownloadTaskEntity.toUiItemWithRecord(): DownloadTaskItem {
        Log.d(TAG, "toUiItemWithRuntime: $taskId $name $status $totalSize $downloadedSize")
        return DownloadTaskItem(
            taskId = taskId,
            name = name,
            url = url,
            /* 正在执行下载时将进程杀死, 重新打开app后读取的记录每个都将是downloading状态, 但此时不会因为他们是downloading状态就自动执行下载
             要么自动执行下载,要么重置为暂停状态,由于
             DownloadTaskRepository#syncWithService会将原本处理正在下载状态的任务状态更改为暂停,这里就不在重复处理*/
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
            DownloadFormat.calculateProgress(downloadedSize, totalSize).toFloat()
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
