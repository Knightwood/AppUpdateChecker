package com.github.knightwood.downloader.ui.util

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class OperateLocker() {
    private val TAG = "OperateLocker"
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
   suspend fun launchTaskOp(op: suspend () -> Unit) {
        if (!taskOpMutex.tryLock()) {
            // 已有操作在执行，丢弃本次请求
            Log.d(TAG, "任务操作串行锁已锁定，丢弃本次请求")
            return
        }
        try {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastTaskOpTime < taskOpDebounceMs) {
                return
            }
            lastTaskOpTime = android.os.SystemClock.elapsedRealtime()
            op()
            Log.d(TAG, "任务操作完成")
        } finally {
            taskOpMutex.unlock()
        }
   }
    fun launchTaskOp(scope: CoroutineScope, op: suspend () -> Unit) {
        scope.launch {
            launchTaskOp(op)
        }
    }

}
