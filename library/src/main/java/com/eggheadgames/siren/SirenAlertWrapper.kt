package com.eggheadgames.siren

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.lang.ref.WeakReference

class SirenAlertWrapper(
    activity: Activity,
    private val mSirenListener: ISirenListener?,
    private val mSirenAlertType: SirenAlertType,
    private val mMinAppVersion: String,
    private val mLocale: SirenSupportedLocales,
    private val mSirenHelper: SirenHelper
) {
    private val mActivityRef: WeakReference<Activity>

    init {
        mActivityRef = WeakReference(activity)
    }

    fun show() {
        val activity = mActivityRef.get()
        if (activity == null) {
            mSirenListener?.onError(NullPointerException("activity reference is null"))
        } else if (!activity.isDestroyed || !activity.isFinishing) {
            val alertDialog = initDialog(activity)
            setupDialog(alertDialog)
            mSirenListener?.onShowUpdateDialog()
        }
    }

    @SuppressLint("InflateParams")
    private fun initDialog(activity: Activity): AlertDialog {
        val alertBuilder = AlertDialog.Builder(activity)
        alertBuilder.setTitle(
            mSirenHelper.getLocalizedString(
                mActivityRef.get(),
                R.string.update_available,
                mLocale
            )
        )
        alertBuilder.setCancelable(false)
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.siren_dialog, null)
        alertBuilder.setView(dialogView)
        val alertDialog = alertBuilder.create()
        alertDialog.show()
        return alertDialog
    }

    private fun setupDialog(dialog: AlertDialog) {
        val message = dialog.findViewById<View>(R.id.tvSirenAlertMessage) as TextView
        val update = dialog.findViewById<View>(R.id.btnSirenUpdate) as Button
        val nextTime = dialog.findViewById<View>(R.id.btnSirenNextTime) as Button
        val skip = dialog.findViewById<View>(R.id.btnSirenSkip) as Button
        update.text = mSirenHelper.getLocalizedString(mActivityRef.get(), R.string.update, mLocale)
        nextTime.text =
            mSirenHelper.getLocalizedString(mActivityRef.get(), R.string.next_time, mLocale)
        skip.text =
            mSirenHelper.getLocalizedString(mActivityRef.get(), R.string.skip_this_version, mLocale)
        message.text = mSirenHelper.getAlertMessage(mActivityRef.get()!!, mMinAppVersion, mLocale)
        if (mSirenAlertType === SirenAlertType.FORCE || mSirenAlertType === SirenAlertType.OPTION || mSirenAlertType === SirenAlertType.SKIP) {
            update.setOnClickListener {
                mSirenListener?.onLaunchGooglePlay()
                dialog.dismiss()
                mSirenHelper.openGooglePlay(mActivityRef.get())
            }
        }
        if (mSirenAlertType === SirenAlertType.OPTION
            || mSirenAlertType === SirenAlertType.SKIP
        ) {
            nextTime.visibility = View.VISIBLE
            nextTime.setOnClickListener {
                mSirenListener?.onCancel()
                dialog.dismiss()
            }
        }
        if (mSirenAlertType === SirenAlertType.SKIP) {
            skip.visibility = View.VISIBLE
            skip.setOnClickListener {
                mSirenListener?.onSkipVersion()
                mSirenHelper.setVersionSkippedByUser(mActivityRef.get(), mMinAppVersion)
                dialog.dismiss()
            }
        }
    }
}