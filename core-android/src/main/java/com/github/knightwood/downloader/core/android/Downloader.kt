package com.github.knightwood.downloader.core.android

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.github.knightwood.downloader.core.android.service.INotifier
import com.github.knightwood.downloader.core.android.service.DownloadService
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.model.AndroidPath
import com.github.knightwood.downloader.core.android.service.DefaultDownloadNotifierImpl
import com.github.knightwood.downloader.core.android.util.Constant
import com.github.knightwood.downloader.core.executor.IDownloadExecutor
import com.github.knightwood.downloader.core.filestorage.FileStorage
import com.github.knightwood.downloader.core.filestorage.FileStorageFactory

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
     *
     * [AndroidDownloadParams]中的设定将覆盖此处全局设定
     */
    var mNotifier: INotifier = DefaultDownloadNotifierImpl()

    /**
     * 同时下载最大数量
     */
    var maxParallel: Int = 8

    /**
     * 全局配置
     * true: 使用通知栏通知消息提示下载状态和进度
     * 如果启动服务时使用了前台服务, 即使此变量为false,也会显示一条通知消息
     *
     * [AndroidDownloadParams]中的设定将覆盖此处全局设定
     */
    var enableNotification = true

    /**
     * 下载完成并使用[postHandler]处理后自动结束[DownloadService]
     */
    var shutdownAfterDownloaded = true

    /**
     * 下载结束后[DownloadService]会自动调用此接口方法以对下载文件做处理
     *
     * [AndroidDownloadParams]中的设定将覆盖此处全局设定
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
     * 若使用java file api，路径可以代码中直接生成，使用父路径和文件名拼接
     * 若使用media store 需要在下载之前使用content resolver insert一条记录，得到uri
     * 若使用 saf框架，则需要用户指定存储文件夹，使用api创建文件拿到uri；或者手动创建一个文件，拿到uri
     * 第一种情况我们通过路径得到文件夹位置，文件路径等信息，
     * media store完全无法得到文件夹位置，文件路径
     * saf框架若不允许授权文件夹，每次下载都手动创建文件，也没法得到文件夹位置和文件路径
     * 所以无法给[DownloadTaskConf]配置文件夹位置和文件名再构造出存储路径、根据文件位置查找父路径下文件缓存等功能，
     * 需要在下载任务配置类中定义path:AndroidPath变量，用于存储文件位置，
     * 根据不同api类型构造fileStorage，若没有赋值，则使用此方法获取一个存储到应用外置存储缓存的filestorage
     */
    fun defaultFileStorage(
        context: Context,
        filename: String,
        parallel: Boolean = false,
    ): FileStorage {
        val dirPath = Constant.downloadCacheDirPath(context)
        return FileStorageFactory.create(parallel, { dirPath.resolve(filename).toFile() })
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

fun interface IDownloadedHandler {
    fun process(context: Context, path: AndroidPath?, taskId: Int, params: AndroidDownloadParams)
}
