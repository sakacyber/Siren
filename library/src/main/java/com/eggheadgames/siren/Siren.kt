package com.eggheadgames.siren

import android.app.Activity
import android.content.Context
import android.os.AsyncTask
import androidx.annotation.VisibleForTesting
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONException
import org.json.JSONObject

/**
 * JSON format should be the following
 * {
 * "com.example.app": {
 * "minVersionName": "1.0.0.0"
 * }
 * }
 *
 * OR
 *
 * {
 * "com.example.app": {
 * "minVersionCode": 7,
 * }
 * }
 */
open class Siren @VisibleForTesting constructor() {
    @JvmField
    @VisibleForTesting
    var mApplicationContext: Context? = null
    private var mSirenListener: ISirenListener? = null
    private var mActivityRef: WeakReference<Activity>? = null

    /**
     * Determines alert type during version code verification
     */
    private var versionCodeUpdateAlertType = SirenAlertType.OPTION

    /**
     * Determines the type of alert that should be shown for major version updates: A.b.c
     */
    private var majorUpdateAlertType = SirenAlertType.OPTION

    /**
     * Determines the type of alert that should be shown for minor version updates: a.B.c
     */
    private var minorUpdateAlertType = SirenAlertType.OPTION

    /**
     * Determines the type of alert that should be shown for minor patch updates: a.b.C
     */
    private var patchUpdateAlertType = SirenAlertType.OPTION

    /**
     * Determines the type of alert that should be shown for revision updates: a.b.c.D
     */
    private var revisionUpdateAlertType = SirenAlertType.OPTION

    /**
     * Overrides the default localization of a user's device when presenting the update message and button titles in the alert.
     */
    private var forceLanguageLocalization: SirenSupportedLocales? = null

    fun checkVersion(
        activity: Activity,
        versionCheckType: SirenVersionCheckType,
        appDescriptionUrl: String?
    ) {
        mActivityRef = WeakReference(activity)
        if (sirenHelper.isEmpty(appDescriptionUrl)) {
            sirenHelper.logError(
                javaClass.simpleName,
                "Please make sure you set correct path to app version description document"
            )
            return
        }
        if (versionCheckType == SirenVersionCheckType.IMMEDIATELY) {
            performVersionCheck(appDescriptionUrl)
        } else if (versionCheckType.value <= sirenHelper.getDaysSinceLastCheck(mApplicationContext)
            || sirenHelper.getLastVerificationDate(mApplicationContext) == 0L
        ) {
            performVersionCheck(appDescriptionUrl)
        }
    }

    fun setMajorUpdateAlertType(majorUpdateAlertType: SirenAlertType) {
        this.majorUpdateAlertType = majorUpdateAlertType
    }

    fun setMinorUpdateAlertType(minorUpdateAlertType: SirenAlertType) {
        this.minorUpdateAlertType = minorUpdateAlertType
    }

    fun setPatchUpdateAlertType(patchUpdateAlertType: SirenAlertType) {
        this.patchUpdateAlertType = patchUpdateAlertType
    }

    fun setRevisionUpdateAlertType(revisionUpdateAlertType: SirenAlertType) {
        this.revisionUpdateAlertType = revisionUpdateAlertType
    }

    fun setSirenListener(sirenListener: ISirenListener?) {
        mSirenListener = sirenListener
    }

    fun setVersionCodeUpdateAlertType(versionCodeUpdateAlertType: SirenAlertType) {
        this.versionCodeUpdateAlertType = versionCodeUpdateAlertType
    }

    fun setLanguageLocalization(localization: SirenSupportedLocales?) {
        forceLanguageLocalization = localization
    }

    @VisibleForTesting
    fun performVersionCheck(appDescriptionUrl: String?) {
        LoadJsonTask().execute(appDescriptionUrl)
    }

    @VisibleForTesting
    fun handleVerificationResults(json: String?) {
        try {
            val rootJson = JSONObject(json)
            if (rootJson.isNull(sirenHelper.getPackageName(mApplicationContext!!))) {
                throw JSONException("field not found")
            } else {
                val appJson =
                    rootJson.getJSONObject(sirenHelper.getPackageName(mApplicationContext!!))
                //version name have higher priority then version code
                if (checkVersionName(appJson)) {
                    return
                }
                checkVersionCode(appJson)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            if (mSirenListener != null) {
                mSirenListener!!.onError(e)
            }
        }
    }

    @VisibleForTesting
    fun getAlertWrapper(alertType: SirenAlertType, appVersion: String): SirenAlertWrapper {
        val activity = mActivityRef!!.get()
        return SirenAlertWrapper(
            activity!!,
            mSirenListener,
            alertType,
            appVersion,
            forceLanguageLocalization!!,
            sirenHelper
        )
    }

    val sirenHelper: SirenHelper
        get() = SirenHelper.instance

    @Throws(JSONException::class)
    private fun checkVersionName(appJson: JSONObject): Boolean {
        if (appJson.isNull(Constants.JSON_MIN_VERSION_NAME)) {
            return false
        }
        sirenHelper.setLastVerificationDate(mApplicationContext)

        // If no config found, assume version check is enabled
        val versionCheckEnabled =
            if (appJson.has(Constants.JSON_ENABLE_VERSION_CHECK)) appJson.getBoolean(
                Constants.JSON_ENABLE_VERSION_CHECK
            ) else true
        if (!versionCheckEnabled) {
            return false
        }

        // If no config found, assume force update = false
        val forceUpdateEnabled =
            if (appJson.has(Constants.JSON_FORCE_ALERT_TYPE)) appJson.getBoolean(
                Constants.JSON_FORCE_ALERT_TYPE
            ) else false
        val minVersionName = appJson.getString(Constants.JSON_MIN_VERSION_NAME)
        val currentVersionName = sirenHelper.getVersionName(mApplicationContext!!)
        if (sirenHelper.isEmpty(minVersionName) || sirenHelper.isEmpty(currentVersionName) || sirenHelper.isVersionSkippedByUser(
                mApplicationContext,
                minVersionName
            )
        ) {
            return false
        }
        var alertType: SirenAlertType? = null
        val minVersionNumbers = minVersionName.split("\\.").toTypedArray()
        val currentVersionNumbers = currentVersionName.split("\\.").toTypedArray()
        if (minVersionNumbers.size == currentVersionNumbers.size) {
            var versionUpdateDetected = false
            for (index in 0 until Math.min(minVersionNumbers.size, currentVersionNumbers.size)) {
                val compareResult =
                    checkVersionDigit(minVersionNumbers, currentVersionNumbers, index)
                if (compareResult == 1) {
                    versionUpdateDetected = true
                    alertType = if (forceUpdateEnabled) {
                        SirenAlertType.FORCE
                    } else {
                        when (index) {
                            0 -> majorUpdateAlertType
                            1 -> minorUpdateAlertType
                            2 -> patchUpdateAlertType
                            3 -> revisionUpdateAlertType
                            else -> SirenAlertType.OPTION
                        }
                    }
                    break
                } else if (compareResult == -1) {
                    return false
                }
            }
            if (versionUpdateDetected) {
                showAlert(minVersionName, alertType!!)
                return true
            }
        }
        return false
    }

    private fun checkVersionDigit(
        minVersionNumbers: Array<String>,
        currentVersionNumbers: Array<String>,
        digitIndex: Int
    ): Int {
        if (minVersionNumbers.size > digitIndex) {
            if (sirenHelper.isGreater(
                    minVersionNumbers[digitIndex],
                    currentVersionNumbers[digitIndex]
                )
            ) {
                return 1
            } else if (sirenHelper.isEquals(
                    minVersionNumbers[digitIndex],
                    currentVersionNumbers[digitIndex]
                )
            ) {
                return 0
            }
        }
        return -1
    }

    @Throws(JSONException::class)
    private fun checkVersionCode(appJson: JSONObject): Boolean {
        if (!appJson.isNull(Constants.JSON_MIN_VERSION_CODE)) {
            val minAppVersionCode = appJson.getInt(Constants.JSON_MIN_VERSION_CODE)
            // If no config found, assume version check is enabled
            val versionCheckEnabled =
                if (appJson.has(Constants.JSON_ENABLE_VERSION_CHECK)) appJson.getBoolean(
                    Constants.JSON_ENABLE_VERSION_CHECK
                ) else true
            if (!versionCheckEnabled) {
                return false
            }

            // If no config found, assume force update = false
            val forceUpdateEnabled =
                if (appJson.has(Constants.JSON_FORCE_ALERT_TYPE)) appJson.getBoolean(
                    Constants.JSON_FORCE_ALERT_TYPE
                ) else false

            //save last successful verification date
            sirenHelper.setLastVerificationDate(mApplicationContext)
            if (sirenHelper.getVersionCode(mApplicationContext!!) < minAppVersionCode
                && !sirenHelper.isVersionSkippedByUser(
                    mApplicationContext,
                    minAppVersionCode.toString()
                )
            ) {
                showAlert(
                    minAppVersionCode.toString(),
                    if (forceUpdateEnabled) SirenAlertType.FORCE else versionCodeUpdateAlertType
                )
                return true
            }
        }
        return false
    }

    private fun showAlert(appVersion: String, alertType: SirenAlertType) {
        if (alertType == SirenAlertType.NONE) {
            if (mSirenListener != null) {
                mSirenListener!!.onDetectNewVersionWithoutAlert(
                    sirenHelper.getAlertMessage(
                        mApplicationContext!!,
                        appVersion,
                        forceLanguageLocalization
                    )
                )
            }
        } else {
            getAlertWrapper(alertType, appVersion).show()
        }
    }

    open class LoadJsonTask : AsyncTask<String?, Void?, String?>() {

        override fun doInBackground(vararg params: String?): String? {
            var connection: HttpURLConnection? = null
            try {
                val tlsSocketFactory = TLSSocketFactory()
                val url = URL(params[0])
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.useCaches = false
                connection.allowUserInteraction = false
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                if ("https".equals(url.protocol, ignoreCase = true)) {
                    (connection as HttpsURLConnection?)!!.sslSocketFactory = tlsSocketFactory
                }
                connection.connect()
                val status = connection.responseCode
                when (status) {
                    200, 201 -> {
                        val br = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                        val sb = StringBuilder()
                        var line: String?
                        while (br.readLine().also { line = it } != null) {
                            if (isCancelled) {
                                br.close()
                                connection.disconnect()
                                return null
                            }
                            sb.append(line).append('\n')
                        }
                        br.close()
                        return sb.toString()
                    }
                    else -> {}
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
                if (sirenInstance.mSirenListener != null) {
                    sirenInstance.mSirenListener!!.onError(ex)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            } finally {
                if (connection != null) {
                    try {
                        connection.disconnect()
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        if (sirenInstance.mSirenListener != null) {
                            sirenInstance.mSirenListener!!.onError(ex)
                        }
                    }
                }
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            if (sirenInstance.sirenHelper.isEmpty(result)) {
                if (sirenInstance.mSirenListener != null) {
                    sirenInstance.mSirenListener!!.onError(NullPointerException())
                }
            } else {
                sirenInstance.handleVerificationResults(result)
            }
        }
    }

    companion object {
        @VisibleForTesting
        protected val sirenInstance = Siren()

        /**
         * @param context - you should use an Application mApplicationContext here in order to not cause memory leaks
         */
        fun getInstance(context: Context?): Siren {
            sirenInstance.mApplicationContext = context
            return sirenInstance
        }
    }
}