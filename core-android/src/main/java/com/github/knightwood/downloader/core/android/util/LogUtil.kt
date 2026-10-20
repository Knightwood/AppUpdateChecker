package com.github.knightwood.downloader.core.android.util

import android.util.Log
import com.github.knightwood.downloader.core.util.HttpConstant


object LogUtil {
    var b = true

    fun enable(enable: Boolean) {
        b = enable
    }

    fun e(tag: String, msg: String) {
        if (b) Log.e(HttpConstant.DEFAULT_TAG_PREFIX + tag, msg)
    }

    fun e(tag: String, err:Throwable) {
        if (b) Log.e(HttpConstant.DEFAULT_TAG_PREFIX + tag, err.message,err)
    }

    fun d(tag: String, msg: String) {
        if (b) Log.d(HttpConstant.DEFAULT_TAG_PREFIX + tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (b) Log.i(HttpConstant.DEFAULT_TAG_PREFIX + tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (b) Log.w(HttpConstant.DEFAULT_TAG_PREFIX + tag, msg)
    }
}
