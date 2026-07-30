package com.example.ulyssespact

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    val installedApps = remember { getInstalledApps(ctx) }

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
            Text(
                text = "Pick App to Block",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(installedApps) {
                    app -> AppListItem(app = app)
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
fun AppListItem(app: AppInfo) {
    var isChecked by remember {
        mutableStateOf(false)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            checked = isChecked,
            onCheckedChange = { isChecked = it }
        )
    }
}