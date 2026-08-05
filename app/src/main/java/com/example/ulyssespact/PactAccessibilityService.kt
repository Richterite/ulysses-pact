package com.example.ulyssespact

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PactAccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var overlayView: LinearLayout? = null


    private var currentPackageName: String? = null
    private var startTimeMillis: Long = 0L

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    private var launcherPackages: List<String> = emptyList()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // if the screen turned off, stop counting and save time
                saveUsageTimeAndStop()
            } else if (intent?.action == Intent.ACTION_SCREEN_ON) {
                // if the screen is turned on, start counting again for the app
                startTimeMillis = System.currentTimeMillis()
            }
        }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfos = packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_DEFAULT_ONLY
        )

        launcherPackages = resolveInfos.map { it.activityInfo.packageName }
        // Add screen on/off detector
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun saveUsageTimeAndStop() {
        val packageName = currentPackageName

        if (packageName != null && startTimeMillis > 0L) {
            val timeSpent = System.currentTimeMillis() - startTimeMillis



            // only save the time when exceeds 1 seconds not below
            if (timeSpent > 1000L) {
                serviceScope.launch {
                    BlockListManager.addUsedTime(
                        this@PactAccessibilityService,
                        packageName,
                        timeSpent
                    )
                }
            }
        }
        // reset state
        currentPackageName = null
        startTimeMillis = 0L
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val newPackageName = e.packageName?.toString() ?: return

            val activeWindowPackage = rootInActiveWindow?.packageName?.toString()
            if (activeWindowPackage != null && activeWindowPackage != newPackageName ){
                return
            }

            if (newPackageName != currentPackageName) {
                // save previous timestamp
                saveUsageTimeAndStop()

                // Start timer to count new app
                currentPackageName = newPackageName

                if (newPackageName == this.packageName ||
                    launcherPackages.contains(newPackageName)) {
                    return
                }

                startTimeMillis = System.currentTimeMillis()

                // Check new app quota
                serviceScope.launch {
                    val baseInfo = AppInfo(appName = "", packageName = newPackageName)
                    val quotaInfo = BlockListManager.getAppQuotaInfo(this@PactAccessibilityService, baseInfo)

                    // Switch to main thread to Construct/Destroy overlay
                    withContext(Dispatchers.Main) {
                        if (quotaInfo.isQuotaExceeded) {
                            showOverlay()
                        } else {
                            removeOverlay()
                        }
                    }
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun showOverlay() {
        // Prevent reinitialization for overlayView (singleton)
        if (overlayView != null) return

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor("#E53935".toColorInt())

            val textView = TextView(this@PactAccessibilityService).apply {
                text = "Time's out\nGet Back to your task please"
                textSize = 24f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0,0,0,48)
            }

            val homeButton = Button(this@PactAccessibilityService).apply {
                text = "Close and back to Home"
                setOnClickListener {
                    // send back to home first
                    val homeIntent = Intent(
                        Intent.ACTION_MAIN
                    ).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)

                    // then trigger removeOverlay
                    serviceScope.launch {
                        kotlinx.coroutines.delay(300L)
                        withContext(Dispatchers.Main) {
                            removeOverlay()
                        }
                    }
                }
            }

            addView(textView)
            addView(homeButton)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    override fun onInterrupt() {
        removeOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.d("PactService", e.toString())
        }
    }
}