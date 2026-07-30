package com.example.ulyssespact

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityGranted by remember {
        mutableStateOf(isAccessibilityEnabled(ctx))
    }
    var isOverlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(ctx))
    }



    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityGranted = isAccessibilityEnabled(ctx)
                isOverlayGranted = Settings.canDrawOverlays(ctx)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Pact",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (!isAccessibilityGranted) {
            PermissionCard(
                title = "Accessibility Permission",
                description = "Pact needs this permission to detect when you open a blocked app.",
                buttonText = "Open Accessibility Settings",
                onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    )
                }
            )
        } else if (!isOverlayGranted){
            PermissionCard(
                title = "Permissions for Overlay App",
                description = "Pact needs this permission to display a blocker screen when your time runs out.",
                buttonText = "Grant Overlay Permission",
                onClick = {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${ctx.packageName}".toUri()
                        )
                    )
                }
            )
        } else {
            var refreshTrigger by remember {
                mutableIntStateOf(0)
            }

            val coroutineScope = rememberCoroutineScope()

            val installedApps = remember { getInstalledApps(ctx) }

            Text(
                text = "Pick App to Block",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(installedApps) { app ->
                    var currentlyBlocked by remember {
                        mutableStateOf(false)
                    }

                    LaunchedEffect(key1 = refreshTrigger, key2 = app.packageName) {
                        currentlyBlocked = BlockListManager.isCurrentlyBlocked(ctx, app.packageName)
                    }

                    AppListItem(
                        app = app,
                        isBlocked = currentlyBlocked,
                        onSetTimer = { durationMinutes ->
                            coroutineScope.launch {
                                BlockListManager.setBlockTimer(ctx, app.packageName, durationMinutes)
                                refreshTrigger++
                            }
                        }
                    )
                }
            }
        }
    }

}

@Composable
fun PermissionCard(title: String, description: String, buttonText: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = description, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClick
            ) {
                Text(buttonText)
            }
        }
    }
}


fun isAccessibilityEnabled(context: Context): Boolean {
    val expectedComponentName = "${context.packageName}/${PactAccessibilityService::class.java.name}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return  false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)

    while (splitter.hasNext()) {
        val componentName = splitter.next()
        if (componentName.equals(expectedComponentName, ignoreCase = true)) {
            return true
        }
    }
    return false
}


data class AppInfo(val appName: String, val packageName: String)

@SuppressLint("QueryPermissionsNeeded")
fun getInstalledApps(ctx: Context): List<AppInfo> {
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

@Composable
fun AppListItem(app: AppInfo, isBlocked: Boolean, onSetTimer: (Int) -> Unit) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if(!isBlocked) showDialog = true
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(text = app.appName, style = MaterialTheme.typography.titleMedium)
            Text(text = app.packageName, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = isBlocked,
            onCheckedChange = {
                if (!isBlocked) showDialog = true
            },
            enabled = !isBlocked
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Pact: Block ${app.appName}") },
            text = { Text("How long do you want to lock this app?") },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSetTimer(15); showDialog = false }
                    ) { Text("15 Minutes") }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSetTimer(60); showDialog = false }
                    ) { Text("1 Hour") }

                    TextButton(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = { showDialog = false }
                    ) { Text("Cancel") }
                }
            }
        )
    }
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pact_prefs")
object BlockListManager {
    private val KEY_BLOCKED_APPS = stringSetPreferencesKey("blocked_apps_set")

    suspend fun setBlockTimer(ctx: Context, packageName: String, durationMinutes: Int) {
        val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        val timeKey = longPreferencesKey("endtime_$packageName")

        // Add endtime based on its package name
        ctx.dataStore.edit { preferences ->
            val currentSet = preferences[KEY_BLOCKED_APPS]?.toMutableSet() ?: mutableSetOf()
            currentSet.add(packageName)

            preferences[KEY_BLOCKED_APPS] = currentSet
            preferences[timeKey] = endTime
        }
    }

    suspend fun isCurrentlyBlocked(ctx: Context, packageName: String): Boolean {
        val preferences = ctx.dataStore.data.first()
        // Check if exist in Blocked Set
        if (preferences[KEY_BLOCKED_APPS]?.contains(packageName) != true) {
            return false
        }

        val timeKey = longPreferencesKey("endtime_$packageName")
        val endTime = preferences[timeKey] ?: 0L
        val isStillBlocked = System.currentTimeMillis() < endTime

        if (!isStillBlocked && endTime > 0L) {
            unblockApp(ctx, packageName)
        }

        return  isStillBlocked
    }

    suspend fun unblockApp(ctx: Context, packageName: String) {
        val timeKey = longPreferencesKey("endtime_$packageName")

        ctx.dataStore.edit { preferences ->
            val currentSet = preferences[KEY_BLOCKED_APPS]?.toMutableSet() ?: mutableSetOf()
            currentSet.remove(packageName)
            preferences[KEY_BLOCKED_APPS] = currentSet
            preferences.remove(timeKey)
        }
    }
}