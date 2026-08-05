package com.example.ulyssespact.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ulyssespact.data.repository.AppRepository
import com.example.ulyssespact.ui.theme.ChartPalettes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: AppRepository
): ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUIState())
    val uiState: StateFlow<DashboardUIState> = _uiState.asStateFlow()

    init {
        loadUsageData()
    }

    fun loadUsageData() {
        viewModelScope.launch {
            repository.getAppsStream().collect { appsWithQuota ->
                val activeApps = appsWithQuota
                    .filter { it.isTracked && it.usedTimeMillis > 0 }
                    .sortedByDescending { it.usedTimeMillis }
                    .take(ChartPalettes.Default.size)

                val totalUsedTime = activeApps.sumOf { it.usedTimeMillis }
                val totalMinutes = totalUsedTime / 60000

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeApps = activeApps,
                        totalUsedTimesMillis = totalUsedTime,
                        displayHours = totalMinutes / 60,
                        displayMins = totalMinutes % 60
                    )
                }
            }
        }
    }
}