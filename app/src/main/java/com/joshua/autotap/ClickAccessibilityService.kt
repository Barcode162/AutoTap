package com.joshua.autotap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * The clicker engine. As an AccessibilityService it stays alive while enabled
 * (no foreground-service plumbing needed) and is the only non-root way to inject
 * taps into other apps. It also hosts the floating overlay UI.
 *
 * It does NOT read screen content — config requests gestures only.
 */
class ClickAccessibilityService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var overlay: OverlayUi? = null

    // ---- user-controlled state ----
    var cps: Int = 10
        private set
    var holdMode: Boolean = false   // false = Toggle, true = Hold
        private set
    private var armed = false        // toggle mode on/off
    private var holdPressed = false  // hold pad currently pressed
    private var expanded = false

    private var clicking = false

    private val clickLoop = object : Runnable {
        override fun run() {
            if (!clicking) return
            val centers = overlay?.dotCenters().orEmpty()
            for (c in centers) click(c.first, c.second)
            main.postDelayed(this, intervalMs())
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (Settings.canDrawOverlays(this)) showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* unused */ }
    override fun onInterrupt() { /* unused */ }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        instance = null
        super.onDestroy()
    }

    // ---- overlay lifecycle ----

    fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        if (overlay == null) overlay = OverlayUi(this).also { it.add() }
    }

    fun hideOverlay() {
        teardown()
    }

    private fun teardown() {
        clicking = false
        main.removeCallbacks(clickLoop)
        overlay?.remove()
        overlay = null
        armed = false
        holdPressed = false
    }

    // ---- state setters (called from overlay) ----

    fun setCps(value: Int) {
        cps = value.coerceIn(MIN_CPS, MAX_CPS)
    }

    fun setMode(hold: Boolean) {
        holdMode = hold
        armed = false
        holdPressed = false
        updateClicking()
    }

    fun setArmed(value: Boolean) {
        armed = value
        updateClicking()
    }

    fun setHoldPressed(value: Boolean) {
        holdPressed = value
        updateClicking()
    }

    fun setExpanded(value: Boolean) {
        expanded = value
        // Collapsing removes the hold pad, so hold-clicking must stop. A toggle
        // that is armed keeps running — that is the "visibly on" exception.
        if (!expanded) holdPressed = false
        updateClicking()
    }

    fun isArmed(): Boolean = armed

    private fun updateClicking() {
        val shouldClick = (!holdMode && armed) || (holdMode && holdPressed)
        overlay?.setDotsArmed(shouldClick)
        overlay?.setBubbleArmed(!holdMode && armed)
        if (shouldClick && !clicking) {
            clicking = true
            main.removeCallbacks(clickLoop)
            main.post(clickLoop)
        } else if (!shouldClick) {
            clicking = false
            main.removeCallbacks(clickLoop)
        }
    }

    private fun intervalMs(): Long = (1000L / cps).coerceAtLeast(MIN_INTERVAL_MS)

    private fun click(x: Int, y: Int) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 1L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        try {
            dispatchGesture(gesture, null, null)
        } catch (_: Throwable) { /* ignore transient dispatch failures */ }
    }

    companion object {
        const val MIN_CPS = 1
        const val MAX_CPS = 50
        private const val MIN_INTERVAL_MS = 20L

        @Volatile
        var instance: ClickAccessibilityService? = null
            private set

        fun isEnabled(ctx: android.content.Context): Boolean {
            val flat = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val name = "${ctx.packageName}/${ClickAccessibilityService::class.java.name}"
            val short = "${ctx.packageName}/.${ClickAccessibilityService::class.java.simpleName}"
            return flat.split(':').any { it.equals(name, true) || it.equals(short, true) }
        }
    }
}
