package com.github.knightwood.downloader.ui.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class OperateLocker(val scope: CoroutineScope) {
    /** 任务操作串行锁，同一时间只允许一个操作执行 */
    private val taskOpMutex = Mutex()

    /** 上一次任务操作发起时间（单调毫秒），用于短时间去重 */
    private var lastTaskOpTime = 0L

    /** 短时间内的重复操作视为无效 */
    private val taskOpDebounceMs = 200L

    /**
     * 任务操作统一入口：串行执行 + 200ms 内去重
     *

     * @param op 实际要执行的操作
     */
    fun launchTaskOp(op: suspend () -> Unit) {
        scope.launch {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastTaskOpTime < taskOpDebounceMs) {
                return@launch
            }
            if (!taskOpMutex.tryLock()) {
                // 已有操作在执行，丢弃本次请求
                return@launch
            }
            try {
                lastTaskOpTime = android.os.SystemClock.elapsedRealtime()
                op()
            } finally {
                taskOpMutex.unlock()
            }
        }
    }

}
