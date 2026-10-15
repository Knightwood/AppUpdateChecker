package com.github.knightwood.downloader.core

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.github.knightwood.downloader.core.model.TaskDesc
import com.github.knightwood.downloader.core.service.INotifier
import com.github.knightwood.downloader.core.service.BasicNotifierImpl
import com.github.knightwood.downloader.core.util.ApkUtil
import okio.Path
import com.github.knightwood.downloader.core.service.DownloadService
import com.github.knightwood.downloader.core.service.DownloadServiceLinker
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ```
 * //设置全局配置
 * Downloader {
 *     disableNotification = false
 *     shutdownAfterDownloaded = true
 *     mNotifier = object:INotifier{
 *          //提供自定义通知栏消息
 *     }
 *     postHandle = DownloadedHandle { context, path, id, conf ->
 *         //下载完成后自动处理文件, 比如安装程序
 *         ApkUtil.installApk(context, Downloader.authorities(context), path.toFile())
 *     }
 * }
 *
 * //文件下载
 * val linker = Downloader.getDownloadServiceLinker()
 * //提交即开始下载
 * val task = linker.submitTask()
 * linker.cancelTask() //取消某下载任务
 * linker.stopService() //关停服务
 *
 * ```
 */
object Downloader {
    /**
     * 提供下载状态、进度状态栏通知功能
     */
    var mNotifier: INotifier = BasicNotifierImpl()

    /**
     * true: 禁止使用通知栏通知消息提示下载状态和进度
     * 如果启动服务时使用了前台服务, 即使此变量为false,也会显示一条通知消息
     */
    var disableNotification = false

    /**
     * 下载完成并使用[postHandle]处理后自动结束[DownloadService]
     */
    var shutdownAfterDownloaded = true

    /**
     * 下载结束后[DownloadService]会自动调用此接口方法以对下载文件做处理
     */
    var postHandle: DownloadedHandle = DownloadedHandle { context, path, id, conf ->
        ApkUtil.installApk(context, authorities(context), path.toFile())
    }

    operator fun invoke(action: Downloader.() -> Unit) {
        action(this)
    }

    /**
     * 获取用于分享安装包进行安装的fileProvider authorities
     */
    fun authorities(context: Context): String {
        return "${context.packageName}.fileProvider"
    }

    suspend fun getDownloadServiceLinker(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        foreground: Boolean = false,
    ): DownloadServiceLinker {
        return suspendCancellableCoroutine { scope ->
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    (service as? DownloadServiceLinker)?.let { serviceConn ->
                        scope.resume(serviceConn)
                    } ?: scope.resumeWithException(IllegalStateException("Service not connected"))
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    scope.cancel()
                }
            }
            DownloadService.start(context, foreground)
            DownloadService.bind(context, conn)
            lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
                override fun onStateChanged(
                    source: LifecycleOwner,
                    event: Lifecycle.Event,
                ) {
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        context.unbindService(conn)
                        lifecycleOwner.lifecycle.removeObserver(this)
                    }
                }
            })
        }
    }
}

fun interface DownloadedHandle {
    fun process(context: Context, path: Path, taskId: Int, conf: TaskDesc)
}
