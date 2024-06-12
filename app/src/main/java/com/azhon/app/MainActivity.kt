package com.azhon.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.azhon.app.compose.ComposeExampleActivity
import com.azhon.app.example.PixelUpdateDialogFragment
import com.azhon.app.example.Win8UpdateDialogFragment
import com.f_libs.appupdate.listener.OnButtonClickListener
import com.f_libs.appupdate.listener.OnDownloadListenerAdapter
import com.f_libs.appupdate.manager.DownloadManager
import com.f_libs.appupdate.util.ApkUtil
import com.f_libs.appupdate.util.ToastUtils
import com.kiylx.tools.view_ui.UpdateDialogType
import com.kiylx.tools.view_ui.showDownloadDialog

class CustomType {
    companion object {
        const val Inner = 0 //内置类型
        const val Win8 = 3
        const val Pixel = 4
    }
}

/**
 * @property url String
 * @property apkName String
 * @property manager DownloadManager?
 * @property tvPercent TextView
 * @property progressBar ProgressBar
 * @property viewStyle Int
 * @property customType Int
 * @property listenerAdapter OnDownloadListenerAdapter
 * @author KnightWood
 */
class MainActivity : AppCompatActivity(), View.OnClickListener, OnButtonClickListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val url = "http://s.duapps.com/apks/own/ESFileExplorer-cn.apk"
    private val apkName = "appupdate.apk"
    private lateinit var manager: DownloadManager
    private lateinit var tvPercent: TextView
    private lateinit var progressBar: ProgressBar
    private var viewStyle = com.kiylx.tools.view_ui.UpdateDialogType.Colorful
    private var customType = CustomType.Pixel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = getString(R.string.app_title)
        progressBar = findViewById(R.id.number_progress_bar)
        tvPercent = findViewById<Button>(R.id.tv_percent)
        findViewById<Button>(R.id.btn_1).setOnClickListener(this)
        findViewById<Button>(R.id.btn_2).setOnClickListener(this)
        findViewById<Button>(R.id.btn_3).setOnClickListener(this)
        findViewById<Button>(R.id.btn_4).setOnClickListener(this)
        findViewById<Button>(R.id.btn_5).setOnClickListener(this)
        findViewById<Button>(R.id.btn_6).setOnClickListener(this)

        findViewById<RadioGroup>(R.id.radio_group).setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.colorful -> {
                    viewStyle = com.kiylx.tools.view_ui.UpdateDialogType.Colorful
                }

                R.id.simpledialog -> {
                    viewStyle = com.kiylx.tools.view_ui.UpdateDialogType.SimpleDialog
                }
            }
        }

        findViewById<RadioGroup>(R.id.radio_group2).setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.pixel -> {
                    customType = CustomType.Pixel
                }

                R.id.win -> {
                    customType = CustomType.Win8
                }
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_1 -> {
                startUpdate1()
            }

            R.id.btn_2 -> {
                startUpdate2()
            }

            R.id.btn_3 -> {
                startUpdate3()
            }

            R.id.btn_4 -> {
                manager?.cancel()
            }

            R.id.btn_5 -> {
                //delete downloaded old Apk
//        val result = ApkUtil.deleteOldApk(this, "${externalCacheDir?.path}/${Constant.cacheDirName}/$apkName")
                //删除所有下载的文件
                ApkUtil.deleteDefaultCacheDir(application)
                ToastUtils.showShot(this.applicationContext, "已删除")
            }

            R.id.btn_6 -> {
                startActivity(Intent(this, ComposeExampleActivity::class.java))
            }
        }
    }

    /**
     * 使用自定义视图展示下载
     * 需要做的事情极其简单，你不需要关注下载的流程，监听之类的，只需要继承并重写布局，然后显示这个自定义的DialogFragment
     * 1,跟往常一样生成一个[DownloadManager]
     * 2,写一个类继承BaseUpdateDialogFragment，然后显示这个自定义的DialogFragment
     */
    private fun startUpdate1() {
        manager = DownloadManager.config(application) {
            apkUrl = url
            apkName = this@MainActivity.apkName
            apkVersionCode = 2
            apkVersionName = "v4.2.1"
            apkSize = "7.7MB"
            apkDescription = getString(R.string.dialog_msg)
            enableLog(true)
            jumpInstallPage = false
            showNotification = true
            showBgdToast = false
            forcedUpgrade = false
            onButtonClickListener = this@MainActivity
        }
        manager.registerDownloadListener(listenerAdapter)
        if (customType == CustomType.Pixel) {
            PixelUpdateDialogFragment.open(this)
        } else {
            Win8UpdateDialogFragment.open(this)
        }
    }

    /**
     * 不使用内置视图，通过[registerDownloadListener]自己实现界面
     */
    private fun startUpdate2() {
        resetPb()
        val manager = DownloadManager.config(application) {
            apkUrl = url
            apkName = this@MainActivity.apkName
//            smallIcon = R.mipmap.ic_launcher
            apkVersionCode = 2
            apkVersionName = "v4.2.1"
            apkSize = "7.7MB"
            apkDescription = getString(R.string.dialog_msg)
            showNotification = true
            showBgdToast = false
            forcedUpgrade = false
            enableLog(true)
            jumpInstallPage = true

        }
        manager.registerDownloadListener(listenerAdapter)//监听下载进度
        manager.checkThenDownload()//立即开始下载
    }

    /**
     * 也可以使用builder方式创建
     */
    private fun startUpdate2_1() {
        val downloadManager = DownloadManager.Builder(this)
            .apkUrl(url)
            .apkVersionCode(2)
            .apkVersionName("v4.2.1")
            .apkSize("7.7MB")
            .apkName(this@MainActivity.apkName)
//            .smallIcon(R.mipmap.ic_launcher)
            .dialogButtonColor(Color.RED)
            .apkDescription(getString(R.string.dialog_msg))
            .build()
        downloadManager.showDownloadDialog(this, updateDialogType = UpdateDialogType.None)
    }

    /**
     * 使用内置的视图展示下载
     */
    private fun startUpdate3() {
        manager = DownloadManager.config(application) {
            apkUrl = url
            apkName = this@MainActivity.apkName
//            smallIcon = R.mipmap.ic_launcher
            apkVersionCode = 2
            apkVersionName = "v4.2.1"
            apkSize = "7.7MB"
            apkDescription = getString(R.string.dialog_msg)
            apkMD5="DC501F04BBAA458C9DC33008EFED5E7F"

            enableLog(true)
//            httpManager()
            jumpInstallPage = false
            showNewerToast = true
            configDialog {
//              dialogImage=R.drawable.ic_dialog
//              dialogButtonColor=Color.parseColor("#E743DA")
//              dialogProgressBarColor=Color.parseColor("#E743DA")
                dialogButtonTextColor = Color.WHITE
            }
            showNotification = true
            showBgdToast = false
            forcedUpgrade = false
            onButtonClickListener = this@MainActivity
        }
        manager.registerButtonListener(object : OnButtonClickListener {
            override fun onButtonClick(action: Int) {
                Log.d(TAG, "onButtonClick: $action")
                if (action == OnButtonClickListener.CANCEL && !manager.downloading) {
                    manager.directDownload(true)//client download
                }
            }
        })
        manager.registerDownloadListener(listenerAdapter)
        showDownloadDialog(manager, updateDialogType = viewStyle)
    }

    private fun resetPb() {
        progressBar.progress = 0
        tvPercent.text = "0%"
    }

    private val listenerAdapter: OnDownloadListenerAdapter = object : OnDownloadListenerAdapter() {

        override fun downloading(max: Int, progress: Int) {
            val curr = (progress / max.toDouble() * 100.0).toInt()
            progressBar.max = 100
            progressBar.progress = curr
            tvPercent.text = "$curr%"
        }
    }


    override fun onButtonClick(action: Int) {
        Log.e(TAG, "onButtonClick: $action")
    }
}