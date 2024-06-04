package com.azhon.appupdate.view

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import com.azhon.appupdate.R
import com.azhon.appupdate.util.DensityUtil

open class DefaultUpdateDialogFragment : BaseUpdateDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setStyle(STYLE_NORMAL, R.style.M3AppTheme)
        mView = inflater.inflate(R.layout.app_update_dialog_update, container, false)
        //全屏
        dialog?.window?.let { window ->
            //这步是必须的
            window.setBackgroundDrawableResource(R.color.transparent)
            //必要，设置 padding，这一步也是必须的，内容不能填充全部宽度和高度
            window.decorView.setPadding(0, 0, 0, 0)
            // 关键是这句，其实跟xml里的配置长得几乎一样
            val wlp: WindowManager.LayoutParams = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                wlp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            wlp.gravity = Gravity.CENTER
            wlp.width = DensityUtil.dip2px(this.requireContext(),300f).toInt()
            wlp.height = WindowManager.LayoutParams.WRAP_CONTENT
            window.attributes = wlp
        }
        return mView
    }

    override fun initView() {
        super.initView()
        progressBar().visibility =View.INVISIBLE
        tvTitle().setText("发现新版${manager.config.apkVersionName}可以下载啦！")
    }

    override fun setProgress(progressValue: Int) {
        (progressBar() as NumberProgressBar).progress = progressValue
    }

    override fun initProgressBar() {
        (progressBar() as NumberProgressBar).max = 100
    }


    companion object {
        const val TAG = "DefaultUpdateDialogFragment"

        fun open(host: FragmentActivity) {
            host.run {
                val dialog = DefaultUpdateDialogFragment()
                val ft = supportFragmentManager.beginTransaction()
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                dialog.show(ft, TAG)
            }
        }
    }

}