package com.example.ulyssespact.model

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isTracked: Boolean = false,
    val dailyLimitMillis: Long = 0L,
    val usedTimeMillis: Long = 0L,
    val lockType: String = "STANDARD"
) {
    val isQuotaExceeded: Boolean
        get() = isTracked && usedTimeMillis >= dailyLimitMillis
}