package com.eggheadgames.siren

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class SirenHelper @VisibleForTesting constructor() {

    fun getPackageName(context: Context): String {
        return context.packageName
    }

    fun getDaysSinceLastCheck(context: Context?): Int {
        val lastCheckTimestamp = getLastVerificationDate(context)
        return if (lastCheckTimestamp > 0) {
            (TimeUnit.MILLISECONDS.toDays(Calendar.getInstance().timeInMillis) - TimeUnit.MILLISECONDS.toDays(
                lastCheckTimestamp
            )).toInt()
        } else {
            0
        }
    }

    fun getVersionCode(context: Context): Int {
        return try {
            context.packageManager.getPackageInfo(getPackageName(context), 0).versionCode
        } catch (e: NameNotFoundException) {
            e.printStackTrace()
            0
        }
    }

    fun isVersionSkippedByUser(context: Context?, minAppVersion: String): Boolean {
        val skippedVersion = PreferenceManager.getDefaultSharedPreferences(context).getString(
            Constants.PREFERENCES_SKIPPED_VERSION, ""
        )
        return skippedVersion == minAppVersion
    }

    fun setLastVerificationDate(context: Context?) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putLong(Constants.PREFERENCES_LAST_CHECK_DATE, Calendar.getInstance().timeInMillis)
            .apply()
    }

    fun getLastVerificationDate(context: Context?): Long {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getLong(Constants.PREFERENCES_LAST_CHECK_DATE, 0)
    }

    fun getAlertMessage(
        context: Context,
        minAppVersion: String?,
        locale: SirenSupportedLocales?
    ): String {
        return try {
            if (context.applicationInfo.labelRes == 0) {
                String.format(
                    getLocalizedString(context, R.string.update_alert_message, locale),
                    getLocalizedString(context, R.string.fallback_app_name, locale),
                    minAppVersion
                )
            } else {
                String.format(
                    getLocalizedString(context, R.string.update_alert_message, locale),
                    getLocalizedString(context, context.applicationInfo.labelRes, locale),
                    minAppVersion
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            context.getString(
                R.string.update_alert_message,
                context.getString(R.string.fallback_app_name),
                minAppVersion
            )
        }
    }

    fun getLocalizedString(
        context: Context?,
        stringResource: Int,
        locale: SirenSupportedLocales?
    ): String {
        if (context == null) {
            return ""
        }
        return if (locale == null) {
            context.getString(stringResource)
        } else {
            val standardResources = context.resources
            val assets = standardResources.assets
            val metrics = standardResources.displayMetrics
            val defaultConfiguration = standardResources.configuration
            val newConfiguration = Configuration(defaultConfiguration)
            newConfiguration.locale = Locale(locale.locale)
            val string = Resources(assets, metrics, newConfiguration).getString(stringResource)

            //need to turn back the default locale
            Resources(assets, metrics, defaultConfiguration)
            string
        }
    }

    fun openGooglePlay(activity: Activity?) {
        if (activity == null) {
            return
        }
        val appPackageName = getPackageName(activity)
        try {
            activity.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$appPackageName")
                )
            )
        } catch (e: ActivityNotFoundException) {
            activity.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                )
            )
        }
    }

    fun setVersionSkippedByUser(context: Context?, skippedVersion: String?) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(Constants.PREFERENCES_SKIPPED_VERSION, skippedVersion)
            .apply()
    }

    fun getVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(getPackageName(context), 0).versionName
        } catch (e: NameNotFoundException) {
            e.printStackTrace()
            ""
        }
    }

    fun isGreater(first: String, second: String): Boolean {
        return TextUtils.isDigitsOnly(first) && TextUtils.isDigitsOnly(second) && first.toInt() > second.toInt()
    }

    fun isEquals(first: String, second: String): Boolean {
        return TextUtils.isDigitsOnly(first) && TextUtils.isDigitsOnly(second) && first.toInt() == second.toInt()
    }

    fun isEmpty(appDescriptionUrl: String?): Boolean {
        return TextUtils.isEmpty(appDescriptionUrl)
    }

    fun logError(tag: String?, message: String?) {
        Log.d(tag, message!!)
    }

    companion object {
        val instance = SirenHelper()
    }
}
