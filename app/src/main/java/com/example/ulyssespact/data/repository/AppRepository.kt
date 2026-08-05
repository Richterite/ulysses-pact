package com.example.ulyssespact.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.ulyssespact.dataStore
import com.example.ulyssespact.model.AppInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppRepository(private val ctx: Context) {
    private fun isTrackedKey(pkg: String) = booleanPreferencesKey("tracked_$pkg")
    private fun limitKey(pkg: String) = longPreferencesKey("limit_$pkg")
    private fun usedTimeKey(pkg: String) = longPreferencesKey("used_$pkg")
    private fun lastDateKey(pkg: String) = stringPreferencesKey("date_$pkg")
    private fun lockTypeKey(pkg: String) = stringPreferencesKey("lock_$pkg")

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun getAppsStream(): Flow<List<AppInfo>> {
        val baseInstalledApps = this.getInstalledApps(ctx)
        return ctx.dataStore.data.map { prefs ->
            val today = this.getTodayDateString()

            baseInstalledApps.map { baseApp ->
                val pkg = baseApp.packageName
                val lastDate = prefs[lastDateKey(pkg)] ?: ""

                val usedTimeMillis = if (lastDate == today) {
                    prefs[usedTimeKey(pkg)] ?: 0L
                } else {
                    0L
                }

                baseApp.copy(
                    isTracked = prefs[isTrackedKey(pkg)] ?: false,
                    dailyLimitMillis = prefs[limitKey(pkg)] ?: 0L,
                    usedTimeMillis = usedTimeMillis,
                    lockType = prefs[lockTypeKey(pkg)] ?: "STANDARD"
                )
            }
        }
    }

    suspend fun  saveAppConfig(
        ctx: Context,
        packageName: String,
        isTracked: Boolean,
        limitMinutes: Int,
        lockType: String
    ) {
        ctx.dataStore.edit { prefs ->
            prefs[isTrackedKey(packageName)] = isTracked
            prefs[limitKey(packageName)] = limitMinutes * 60 * 1000L
            prefs[lockTypeKey(packageName)] = lockType

            // Write down current date if this is first time
            if (!prefs.contains(lastDateKey(packageName))) {
                prefs[lastDateKey(packageName)] = this.getTodayDateString()
                prefs[usedTimeKey(packageName)] = 0L
            }
        }
    }

    suspend fun addUsedTime(ctx: Context, packageName: String, timeAddedMillis: Long) {
        ctx.dataStore.edit { prefs ->
            val lastDate = prefs[lastDateKey(packageName)] ?: ""
            val today = this.getTodayDateString()

            val currentUsed = if (lastDate == today) {
                prefs[usedTimeKey(packageName)] ?: 0L
            } else {
                0L
            }

            prefs[usedTimeKey(packageName)] = currentUsed + timeAddedMillis
            prefs[lastDateKey(packageName)] = today
        }
    }


    @SuppressLint("QueryPermissionsNeeded")
    private fun getInstalledApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager

        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
//        pm.getInstalledApplications(0)
        }

        return resolveInfos.mapNotNull { resolveInfo ->
            val appName = resolveInfo.loadLabel(pm).toString()
            val packageName =resolveInfo.activityInfo.packageName

            if (packageName == ctx.packageName) {
                null
            } else {
                AppInfo(appName, packageName)
            }
        }.distinctBy { it.packageName }
            .sortedBy { it.appName }
    }


}