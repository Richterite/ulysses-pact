package com.example.ulyssespact.ui.screens.dashboard

import com.example.ulyssespact.model.AppInfo

data class DashboardUIState (
    val isLoading: Boolean = false,
    val activeApps: List<AppInfo> = emptyList(),
    val totalUsedTimesMillis: Long = 0L,
    val displayHours: Long = 0L,
    val displayMins: Long = 0L
)