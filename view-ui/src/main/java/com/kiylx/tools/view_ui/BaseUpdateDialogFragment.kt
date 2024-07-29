package com.kiylx.tools.view_ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.f_libs.appupdate.config.Constant
import com.f_libs.appupdate.listener.OnButtonClickListener
import com.f_libs.appupdate.listener.OnDownloadListener
import com.f_libs.appupdate.manager.DownloadManager
import com.f_libs.appupdate.util.ApkUtil
import com.f_libs.appupdate.util.ToastUtils
import java.io.File

/**
 * @author KnightWood
 */
class Action {
    companion object {
        //mode
        const val ready = 0x01
        const val downloading = 0x02
        const val readyInstall = 0x04

        const val canceled = 0x05
        const val error = 0x06
    }
}

/**
 * @property vm BaseUpdateDialogViewModel
 * @property mView View
 * @property views SparseArray<View>
 * @property manager DownloadManager
 * @author KnightWood
 */
open class BaseUpdateDialogFragment : DialogFragment(), OnDownloadListener {
    lateinit var vm: BaseUpdateDialogViewModel
    lateinit var mView: View
    val views: SparseArray<View> = SparseArray()
    val manager: DownloadManager = DownloadManager.getInstance()
    var cacheFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[BaseUpdateDialogViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        manager.config.registerDownloadListener(this)
        initView()
        observeLivedata()
        vm.stateLivedata.value = Action.ready
        //如果正在下载，更新一下界面状态
        if (manager.downloading) {
            start()
        }
        if (cacheFile != null) {
            vm.apkFile = cacheFile
            cacheFile = null
            vm.stateLivedata.value = Action.readyInstall
        }
    }

    private fun observeLivedata() {
        vm.run {
            stateLivedata.observe(viewLifecycleOwner) {
                when (it) {
                    Action.ready -> {
                        //更新按钮的初始文本
                        vm.updateButtonLivedata.value = ButtonState(
                            enable = manager.canDownload(),
                            stringId = R.string.update
                        )
                    }

                    Action.downloading -> {
                        vm.updateButtonLivedata.value = ButtonState(
                            enable = false,
                            stringId = com.f_libs.appupdate.R.string.app_update_start_downloading
                        )
                    }

                    Action.readyInstall -> {
                        updateButtonLivedata.postValue(
                            ButtonState(enable = true, stringId = R.string.install)
                        )
                    }

                    Action.error -> {
                        updateButtonLivedata.postValue(
                            ButtonState(
                                enable = true,
                                stringId = com.f_libs.appupdate.R.string.app_update_download_error
                            )
                        )
                    }

                    Action.canceled -> {
                        updateButtonLivedata.postValue(
                            ButtonState(enable = true, stringId = R.string.cancel)
                        )
                    }
                }
            }
            updateButtonLivedata.observe(viewLifecycleOwner) {
                btnUpdate().apply {
                    isEnabled = it.enable
                    setText(it.stringId)
                }
            }
            progress.observe(viewLifecycleOwner) {
                setProgress(it)
            }
        }
    }

    @CallSuper
    open fun initView() {
        //title
        tvDescription().text = manager.config.apkDescription.replace("\\n", "\n")
        //size
        tvSize().apply {
            if (manager.config.apkSize.isNotEmpty()) {
                visibility = View.VISIBLE
                text = String.format(
                    getString(R.string.update_size_tv), manager.config.apkSize
                )
            } else {
                visibility = View.INVISIBLE
            }
        }
        //update button
        btnUpdate().setOnClickListener {
            if (vm.stateLivedata.value == Action.readyInstall) {
                manager.config.onButtonClickListener?.onButtonClick(OnButtonClickListener.UPDATE)
                vm.installApk(this.requireContext())
            } else {
                manager.config.onButtonClickListener?.onButtonClick(OnButtonClickListener.DOWNLOAD)
                //执行下载
                manager.directDownload()
            }
        }
        //cancel button
        if (!manager.config.forcedUpgrade) {
            btnCancel().setOnClickListener {
                vm.stateLivedata.postValue(Action.canceled)
                manager.config.onButtonClickListener?.onButtonClick(OnButtonClickListener.CANCEL)
                if (manager.config.showBgdToast) {
                    ToastUtils.showShot(requireContext(), getString(R.string.has_cancel_download))
                }
                manager.cancel()
                dismiss()
            }
        } else {
            btnCancel().visibility = View.INVISIBLE
            isCancelable = false
        }
        //progressbar
        initProgressBar()
    }

    open fun initProgressBar() {
        (progressBar() as ProgressBar).run {
            max = 100
            progress = 0
        }
    }


    /**
     * 当存在缓存文件时，将不在下载，而是直接安装
     */
    fun setCacheFile(cacheFilePath: String?) {
        cacheFilePath?.let {
            cacheFile = File(it)
        }
    }


    open fun setProgress(progressValue: Int) {
        (progressBar() as ProgressBar).progress = progressValue
    }

    //===============download listener start===================//
    override fun start() {
        progressBar().visibility = View.VISIBLE
        vm.stateLivedata.postValue(Action.downloading)
    }

    override fun downloading(max: Int, progress: Int) {
        val curr = (progress / max.toDouble() * 100.0).toInt()
        vm.progress.value = curr
    }

    override fun done(apk: File) {
        vm.downloadFinish(apk)
        //service在下载完成后，会判断是否要自动跳转安装，所以ui界面中不需要处理跳转安装
        if (manager.config.jumpInstallPage) {
            dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun cancel() {
        ToastUtils.showShot(requireActivity().applicationContext, "下载取消")
    }

    override fun error(e: Throwable) {
        ToastUtils.showShot(requireActivity().applicationContext, "下载失败 ${e.message}")
        Log.e(TAG, "error:", e)
        vm.stateLivedata.postValue(Action.error)
    }
    //===============download listener end===================//

    override fun onDestroyView() {
        super.onDestroyView()
        manager.config.onDownloadListeners.remove(this)
    }

    //some view function
    inline fun <reified T : View> findView(id: Int): T =
        views.get(id)?.let {
            it as T
        } ?: let {
            val view: T = mView.findViewById<T>(id)
            views[id] = view
            view
        }

    fun tvTitle(): TextView = findView(R.id.app_update_tv_title)
    open fun tvSize(): TextView = findView(R.id.app_update_tv_size)
    open fun tvDescription(): TextView = findView(R.id.app_update_tv_description)
    open fun btnUpdate(): Button = findView(R.id.app_update_btn_update)
    open fun btnCancel(): View = findView(R.id.app_update_ib_close)
    open fun progressBar(): View = findView(R.id.app_update_progress_bar)

    companion object {
        const val TAG = "BaseUpdateDialog"
    }

}

/**
 * @constructor
 * @property progress Int
 * @property visibility Int
 * @author KnightWood
 */
data class ProgressState(
    val progress: Int = 0,
    val visibility: Int = View.VISIBLE
) : java.io.Serializable

/**
 * @constructor
 * @property enable Boolean
 * @property visibility Int
 * @property stringId Int
 * @author KnightWood
 */
data class ButtonState(
    val enable: Boolean = true,
    val visibility: Int = View.VISIBLE,
    val stringId: Int
) : java.io.Serializable

/**
 * @property apkFile File?
 * @property updateButtonLivedata MutableLiveData<ButtonState>
 * @property stateLivedata MutableLiveData<Int>
 * @author KnightWood
 */
class BaseUpdateDialogViewModel : ViewModel() {
    var apkFile: File? = null

    //update button
    val updateButtonLivedata: MutableLiveData<ButtonState> =
        MutableLiveData()

    //download & display state
    val stateLivedata: MutableLiveData<Int> =
        MutableLiveData()

    //下载进度
    val progress: MutableLiveData<Int> = MutableLiveData(0)

    fun installApk(context: Context) {
        apkFile?.let { it1 ->
            ApkUtil.installApk(context, Constant.AUTHORITIES!!, it1)
        }
    }

    /**
     * when download finish, update button state changed, state changed
     */
    fun downloadFinish(apk: File) {
        apkFile = apk
        stateLivedata.postValue(Action.readyInstall)
    }
}