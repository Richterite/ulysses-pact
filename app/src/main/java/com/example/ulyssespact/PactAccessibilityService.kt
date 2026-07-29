package com.example.ulyssespact

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
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

class PactAccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var overlayView: LinearLayout? = null

    private val blockedApps = listOf(
        "com.android.chrome",
        "com.google.android.youtube"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.d("PactService", "Accessibility service Connected and WindowsManager Ready")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = e.packageName?.toString()
            val className = e.className.toString()

            if (packageName != null){
                Log.d("PactService", "Current Active App : $packageName")
                Log.d("PactService", "Class Name: $className")
            }

            if (packageName == this.packageName) {
                return
            }

            if (blockedApps.contains(packageName)) {
                showOverlay()
            } else {
                removeOverlay()
            }


            // Logic for quota app limit checking implemented here
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
                    val homeIntent = Intent(
                        Intent.ACTION_MAIN
                    ).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
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
    }
}