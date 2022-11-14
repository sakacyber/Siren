package com.eggheadgames.siren

/**
 * Determines the frequency in which the the version check is performed
 */
enum class SirenVersionCheckType(  // Version check performed once a week
    val value: Int
) {
    IMMEDIATELY(0),  // Version check performed every time the app is launched
    DAILY(1),  // Version check performed once a day
    WEEKLY(7);
}
