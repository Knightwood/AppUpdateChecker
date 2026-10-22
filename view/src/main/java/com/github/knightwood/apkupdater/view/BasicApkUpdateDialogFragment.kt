package com.github.knightwood.apkupdater.view

import android.content.Context
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentDialog
import androidx.lifecycle.coroutineScope
import com.github.knightwood.downloader.core.android.Downloader
import com.github.knightwood.downloader.core.android.model.AndroidDownloadParams
import com.github.knightwood.downloader.core.android.service.DownloadServiceLinker
import com.kiylx.tools.view_ui.NumberProgressBar
import com.kiylx.tools.view_ui.R
import kotlinx.coroutines.launch

open class BasicApkUpdateDialogFragment constructor(
    val param: AndroidDownloadParams,
    context: Context,
    themeResId: Int,
) : ComponentDialog(context, themeResId) {
    lateinit var binding: IBasicApkUpdateViewBinding
    lateinit var serviceLinker: DownloadServiceLinker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = provideBinding()
        setContentView(binding.root)
        linkDownloadService()
        initView()
    }

    private fun linkDownloadService() {
        lifecycle.coroutineScope.launch {
            serviceLinker = Downloader.getDownloadServiceLinker(
                context,
                this@BasicApkUpdateDialogFragment,
                true
            )
        }
    }

    fun initView() {
        with(param.fileEntity) {
            binding.tvTitle.text = name
            sizeText?.let {
                binding.tvSize.visibility = View.VISIBLE
                binding.tvSize.text = it
            }
            binding.tvDescription.text = description
            (binding.progressBar as NumberProgressBar).apply {
                this.progress = 0
                this.max = 100
            }
            binding.btnUpdate.setOnClickListener {
                serviceLinker.submitTask(id, param)
            }
        }
    }

    open fun provideBinding(): IBasicApkUpdateViewBinding {
        return BasicApkUpdateViewBinding().bind(
            LayoutInflater.from(context).inflate(R.layout.app_update_dialog_update, null)
        )
    }
}

interface IBasicApkUpdateViewBinding {
    val root: View
    val tvTitle: TextView
    val tvSize: TextView
    val tvDescription: TextView
    val btnUpdate: Button
    val btnCancel: View
    val progressBar: View
}

class BasicApkUpdateViewBinding : IBasicApkUpdateViewBinding {
    override val tvTitle: TextView get() = findView(R.id.app_update_tv_title)
    override val tvSize: TextView get() = findView(R.id.app_update_tv_size)
    override val tvDescription: TextView get() = findView(R.id.app_update_tv_description)
    override val btnUpdate: Button get() = findView(R.id.app_update_btn_update)
    override val btnCancel: View get() = findView(R.id.app_update_ib_close)
    override val progressBar: View get() = findView(R.id.app_update_progress_bar)
    override lateinit var root: View
    val views: SparseArray<View> = SparseArray()

    //some view function
    inline fun <reified T : View> findView(id: Int): T =
        views.get(id)?.let {
            it as T
        } ?: let {
            val view: T = root.findViewById<T>(id)
            views[id] = view
            view
        }

    fun bind(viewRoot: View): BasicApkUpdateViewBinding {
        this.root = viewRoot
        return this
    }
}
