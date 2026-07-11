package com.wake.app.capture

object Exclusions {
    private val blockedPrefixes = listOf(
        "com.wake.app",
        "com.google.android.apps.nbu.paisa",
        "com.phonepe",
        "net.one97.paytm",
        "in.org.npci",
        "com.google.android.gms",
        "com.android.vending",
        "com.android.systemui",
        "com.google.android.googlequicksearchbox"
    )

    private val blockedKeywords = listOf("bank", "upi", "wallet", "authenticator", "password")

    fun isExcluded(pkg: String?): Boolean {
        if (pkg == null) return true
        if (blockedPrefixes.any { pkg.startsWith(it) }) return true
        return blockedKeywords.any { pkg.contains(it, ignoreCase = true) }
    }
}
