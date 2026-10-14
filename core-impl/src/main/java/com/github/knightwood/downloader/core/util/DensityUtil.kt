package com.github.knightwood.downloader.core.util

import android.content.Context

object DensityUtil {
    fun dip2px(context: Context, dpValue: Float): Float {
        val scale = context.resources.displayMetrics.density
        return dpValue * scale + 0.5f
    }
}
