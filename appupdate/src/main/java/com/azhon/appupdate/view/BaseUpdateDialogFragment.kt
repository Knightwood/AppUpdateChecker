package com.azhon.appupdate.view

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.*
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.azhon.appupdate.R
import com.azhon.appupdate.config.Constant
import com.azhon.appupdate.listener.OnButtonClickListener
import com.azhon.appupdate.listener.OnDownloadListener
import com.azhon.appupdate.manager.DownloadManager
import com.azhon.appupdate.util.ApkUtil
import com.azhon.appupdate.util.ToastUtils
import java.io.File

/**
 * @author KnightWood
 *
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
 * @author KnightWood
 * @property vm BaseUpdateDialogViewModel
 * @property mView View
 * @property views SparseArray<View>
 * @property manager DownloadManager
 */
open class BaseUpdateDialogFragment : DialogFragment(), OnDownloadListener {
    lateinit var vm: BaseUpdateDialogViewModel
    lateinit var mView: View
    val views: SparseArray<View> = SparseArray()
    val manager: DownloadManager = DownloadManager.getInstance()

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
        if (manager.downloadState) {
            start()
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
                            stringId = R.string.app_update_start_downloading
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
                                stringId = R.string.app_update_download_error
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
            if (manager.config.apkSize.isNotEmpty()){
                visibility=View.VISIBLE
                text = String.format(
                    getString(R.string.update_size_tv), manager.config.apkSize
                )
            }else{
                visibility=View.INVISIBLE
            }
        }
        //update button
        btnUpdate().setOnClickListener {
            manager.config.onButtonClickListener?.onButtonClick(OnButtonClickListener.UPDATE)
            if (vm.stateLivedata.value == Action.readyInstall) {
                vm.installApk(this.requireContext())
            } else {
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

    override fun cancel() {}

    override fun error(e: Throwable) {
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
 * @author KnightWood
 * @property progress Int
 * @property visibility Int
 * @constructor
 */
data class ProgressState(
    val progress: Int = 0,
    val visibility: Int = View.VISIBLE
) : java.io.Serializable

/**
 * @author KnightWood
 * @property enable Boolean
 * @property visibility Int
 * @property stringId Int
 * @constructor
 */
data class ButtonState(
    val enable: Boolean = true,
    val visibility: Int = View.VISIBLE,
    val stringId: Int
) : java.io.Serializable

/**
 * @author KnightWood
 * @property apkFile File?
 * @property updateButtonLivedata MutableLiveData<ButtonState>
 * @property stateLivedata MutableLiveData<Int>
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
     * when download finish,
     * update button state changed,
     * state changed
     */
    fun downloadFinish(apk: File) {
        apkFile = apk
        stateLivedata.postValue(Action.readyInstall)
    }
}