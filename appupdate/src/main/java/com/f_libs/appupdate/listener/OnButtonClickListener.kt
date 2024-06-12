package com.f_libs.appupdate.listener


/**
 * ProjectName: AppUpdate
 * PackageName: com.azhon.appupdate.listener
 * FileName:    OnButtonClickListener
 * CreateDate:  2022/4/7 on 15:56
 * Desc:
 *
 * @author   azhon
 */

interface OnButtonClickListener {
    companion object {
        /**
         * click update button to install apk
         */
        const val UPDATE = 0

        /**
         * click cancel button
         */
        const val CANCEL = 1

        /**
         * download file
         */
        const val DOWNLOAD=2

    }

    fun onButtonClick(action: Int)
}