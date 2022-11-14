package com.eggheadgames.siren

import java.lang.Exception

interface ISirenListener {
    fun onShowUpdateDialog() // User presented with update dialog
    fun onLaunchGooglePlay() // User did click on button that launched Google Play
    fun onSkipVersion() // User did click on button that skips version update
    fun onCancel() // User did click on button that cancels update dialog
    fun onDetectNewVersionWithoutAlert(message: String?) // Siren performed version check and did not display alert
    fun onError(e: Exception?)
}
