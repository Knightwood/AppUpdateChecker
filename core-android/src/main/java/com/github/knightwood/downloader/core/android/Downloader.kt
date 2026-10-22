package com.github.knightwood.downloader.core.android

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.github.knightwood.downloader.core.android.service.INotifier
import com.github.knightwood.downloader.core.android.service.ApkUpdateNotifierImpl
import okio.Path
import com.github.knightwood.downloader.core.android.service.DownloadService
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.util.Constant
import com.github.knightwood.downloader.core.android.util.Constant.CACHE_DIR_NAME
import com.github.knightwood.downloader.core.executor.IDownloadExecutor
import com.github.knightwood.downloader.core.filestorage.FileStorage

/**
 * 此类用于记录下载服务的全局配置
 *
 * 任务配置[AndroidDownloadParams]优先级高于[Downloader]全局配置
 *
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
 *
 * 流程设计:
 * 1. 生成下载配置信息, 提交到服务, 得到服务返回的任务实例
 * 2. 将任务实例id作为参数启动ui弹窗, 弹窗通过服务获取下载信息、下载任务实体, 弹窗显示下载信息、进度
 *
 * 应用下载、更新，实则是构建了一个后处理指定为安装应用的普通任务
 */
object Downloader {
    /**
     * 提供下载状态、进度状态栏通知功能
     */
    var mNotifier: INotifier = ApkUpdateNotifierImpl()

    /**
     * 同时下载最大数量
     */
    var maxParallel: Int = 8

    /**
     * 全局配置
     * true: 使用通知栏通知消息提示下载状态和进度
     * 如果启动服务时使用了前台服务, 即使此变量为false,也会显示一条通知消息
     *
     * 此变量为true时如果[AndroidDownloadParams]中关闭了下载通知, 下载过程中将不显示通知栏通知
     */
    var enableNotification = true

    /**
     * 下载完成并使用[postHandler]处理后自动结束[DownloadService]
     */
    var shutdownAfterDownloaded = true

    /**
     * 下载结束后[DownloadService]会自动调用此接口方法以对下载文件做处理
     *
     * 如果[AndroidDownloadParams]中指定了后处理接口实现, 将不再使用此处的接口实现进行处理
     */
    var postHandler: IDownloadedHandler? = null

    operator fun invoke(action: Downloader.() -> Unit) {
        action(this)
    }

    /**
     * 提供自定义下载执行器
     */
    var downloadExecutor: IDownloadExecutor? = null


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

fun interface IDownloadedHandler {
    fun process(context: Context, path: Path, taskId: Int, conf: AndroidDownloadParams)
}
