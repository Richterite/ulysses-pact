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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ulyssespact.model.AppInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    var currentAppInfo by remember { mutableStateOf(app) }

                    LaunchedEffect(key1 = refreshTrigger, key2 = app.packageName) {
                        currentAppInfo = BlockListManager.getAppQuotaInfo(ctx, app)
                    }

                    AppListItem(
                        app = app,
                        onSaveConfig = { isTracked, limitMinutes, lockType ->
                            coroutineScope.launch {
                                BlockListManager.saveAppConfig(
                                    ctx,
                                    app.packageName,
                                    isTracked,
                                    limitMinutes,
                                    lockType
                                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListItem(
    app: AppInfo,
    onSaveConfig: (Boolean, Int, String) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }

    var isTracked by remember { mutableStateOf(app.isTracked)}
    var limitMinutes by remember {
        mutableFloatStateOf((app.dailyLimitMillis / (60 * 1000L))
        .coerceAtLeast(15L)
        .toFloat())
    }
    var lockType by remember { mutableStateOf(app.lockType) }

    // key Type
    val lockOption = listOf("STANDARD", "COGNITIVE", "ULYSSES")
    val lockDisplayNames = mapOf(
        "STANDARD" to "Standard (10 seconds pause)",
        "COGNITIVE" to "Cognitive Obstacles (Typing Text)",
        "ULYSSES" to "Ulysses Pact (Friend Code)"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = app.appName, style = MaterialTheme.typography.titleMedium)
                    if (app.isTracked) {
                        Text(
                            text = "${app.usedTimeMillis / 60000}m/ ${app.dailyLimitMillis / 60000}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(text = "Not limited", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Switch(
                    checked = isTracked,
                    onCheckedChange = { checked ->
                        isTracked = checked
                        isExpanded = true // open menu automatically
                    }
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Slider
                    Text("Daily Time Allowance", style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = limitMinutes,
                            onValueChange = { limitMinutes = it},
                            valueRange = 3f..300f, // for debug purpose the limit lowered to 3 seconds
                            steps = 18,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${limitMinutes.toInt()}m",
                            modifier = Modifier.padding(start = 16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Dropdown lock type
                    Text("Lock type", style = MaterialTheme.typography.labelSmall)
                    var dropdownExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = lockDisplayNames[lockType] ?: "",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)},
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            lockOption.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(lockDisplayNames[selectionOption] ?: "") },
                                    onClick = {
                                        lockType = selectionOption
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            onSaveConfig(isTracked, limitMinutes.toInt(), lockType)
                            isExpanded = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save Settings")
                    }
                }
            }
        }
    }
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pact_prefs")
object BlockListManager {
    private fun isTrackedKey(pkg: String) = booleanPreferencesKey("tracked_$pkg")
    private fun limitKey(pkg: String) = longPreferencesKey("limit_$pkg")
    private fun usedTimeKey(pkg: String) = longPreferencesKey("used_$pkg")
    private fun lastDateKey(pkg: String) = stringPreferencesKey("date_$pkg")
    private fun lockTypeKey(pkg: String) = stringPreferencesKey("lock_$pkg")

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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
                prefs[lastDateKey(packageName)] = getTodayDateString()
                prefs[usedTimeKey(packageName)] = 0L
            }
        }
    }

    suspend fun getAppQuotaInfo(ctx: Context, baseAppInfo: AppInfo): AppInfo {
        val prefs = ctx.dataStore.data.first()
        val pkg = baseAppInfo.packageName

        val lastDate = prefs[lastDateKey(pkg)] ?: ""
        val today = getTodayDateString()

        val usedTimeMillis = if (lastDate == today) {
            prefs[usedTimeKey(pkg)] ?: 0L
        } else {
            0L
        }

        return baseAppInfo.copy(
            isTracked = prefs[isTrackedKey(pkg)] ?: false,
            dailyLimitMillis = prefs[limitKey(pkg)] ?: 0L,
            usedTimeMillis = usedTimeMillis,
            lockType = prefs[lockTypeKey(pkg)] ?: "STANDARD"
        )
    }


    suspend fun addUsedTime(ctx: Context, packageName: String, timeAddedMillis: Long) {
        ctx.dataStore.edit { prefs ->
            val lastDate = prefs[lastDateKey(packageName)] ?: ""
            val today = getTodayDateString()

            val currentUsed = if (lastDate == today) {
                prefs[usedTimeKey(packageName)] ?: 0L
            } else {
                0L
            }

            prefs[usedTimeKey(packageName)] = currentUsed + timeAddedMillis
            prefs[lastDateKey(packageName)] = today
        }
    }
}