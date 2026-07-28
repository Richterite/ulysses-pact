package com.example.ulyssespact

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class PactAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("PactService", "Accessibility service Connected")
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = e.packageName?.toString()
            val className = e.className.toString()

            if (packageName != null){
                Log.d("PactService", "Current Active App : $packageName")
                Log.d("PactService", "Class Name: $className")
            }


            // Logic for quota app limit checking implemented here
        }
    }

    override fun onInterrupt() {
        Log.d("PactService", "Accessibility Service disconnected")
    }
}