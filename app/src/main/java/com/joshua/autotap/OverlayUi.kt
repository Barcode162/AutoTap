package com.joshua.autotap

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs

/**
 * All the floating windows: the collapsible control (bubble ↔ panel) plus a set
 * of movable click dots. Drives [ClickAccessibilityService] for state changes
 * and reads dot positions back for the click loop.
 */
class OverlayUi(private val service: ClickAccessibilityService) {

    private val wm = service.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density
    private val dotSizePx = (44 * density).toInt()
    private val screenW = service.resources.displayMetrics.widthPixels
    private val screenH = service.resources.displayMetrics.heightPixels

    private lateinit var control: View
    private lateinit var bubble: TextView
    private lateinit var panel: View
    private lateinit var startStop: TextView
    private lateinit var holdPad: TextView
    private lateinit var modeToggle: TextView
    private lateinit var modeHold: TextView
    private lateinit var dotCountLabel: TextView

    private val controlLp = baseLp().apply {
        gravity = Gravity.TOP or Gravity.START
        x = 24
        y = 240
    }

    private data class Dot(val view: TextView, val lp: WindowManager.LayoutParams)
    private val dots = ArrayList<Dot>()

    private var expanded = false
    private var toggleArmed = false
    private var holdMode = false

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    fun add() {
        control = LayoutInflater.from(service).inflate(R.layout.overlay_control, null)
        bubble = control.findViewById(R.id.bubble)
        panel = control.findViewById(R.id.panel)
        startStop = control.findViewById(R.id.btnStartStop)
        holdPad = control.findViewById(R.id.btnHoldPad)
        modeToggle = control.findViewById(R.id.btnModeToggle)
        modeHold = control.findViewById(R.id.btnModeHold)
        dotCountLabel = control.findViewById(R.id.dotCount)

        val cpsValue = control.findViewById<TextView>(R.id.cpsValue)
        val cpsSeek = control.findViewById<SeekBar>(R.id.cpsSeek)
        cpsValue.text = "${service.cps} CPS"
        cpsSeek.progress = service.cps - 1
        cpsSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress + 1
                service.setCps(v)
                cpsValue.text = "$v CPS"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        attachDrag(bubble, controlLp, onTap = { expand() })
        attachDrag(control.findViewById(R.id.dragHandle), controlLp, onTap = null)

        control.findViewById<View>(R.id.btnCollapse).setOnClickListener { collapse() }
        control.findViewById<View>(R.id.btnClose).setOnClickListener { service.hideOverlay() }
        control.findViewById<View>(R.id.btnAddDot).setOnClickListener { addDot() }
        control.findViewById<View>(R.id.btnRemoveDot).setOnClickListener { removeDot() }

        modeToggle.setOnClickListener { setMode(false) }
        modeHold.setOnClickListener { setMode(true) }

        startStop.setOnClickListener {
            toggleArmed = !toggleArmed
            service.setArmed(toggleArmed)
            refreshStartStop()
        }
        holdPad.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    service.setHoldPressed(true)
                    holdPad.setBackgroundResource(R.drawable.bg_btn_primary)
                    holdPad.setTextColor(service.getColor(R.color.bg))
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    service.setHoldPressed(false)
                    holdPad.setBackgroundResource(R.drawable.bg_btn_secondary)
                    holdPad.setTextColor(service.getColor(R.color.onSurface))
                    true
                }
                else -> false
            }
        }

        wm.addView(control, controlLp)
        addDot() // start with one dot
        applyMode()
        showCollapsed()
    }

    fun remove() {
        for (d in dots) safeRemove(d.view)
        dots.clear()
        if (this::control.isInitialized) safeRemove(control)
    }

    // ---- expand / collapse ----

    private fun expand() {
        expanded = true
        bubble.visibility = View.GONE
        panel.visibility = View.VISIBLE
        service.setExpanded(true)
    }

    private fun collapse() {
        expanded = false
        showCollapsed()
        service.setExpanded(false)
    }

    private fun showCollapsed() {
        bubble.visibility = View.VISIBLE
        panel.visibility = View.GONE
    }

    // ---- mode ----

    private fun setMode(hold: Boolean) {
        holdMode = hold
        toggleArmed = false
        service.setMode(hold)
        applyMode()
        refreshStartStop()
    }

    private fun applyMode() {
        if (holdMode) {
            modeHold.setBackgroundResource(R.drawable.bg_btn_primary)
            modeHold.setTextColor(service.getColor(R.color.bg))
            modeToggle.setBackgroundResource(R.drawable.bg_btn_secondary)
            modeToggle.setTextColor(service.getColor(R.color.onSurface))
            startStop.visibility = View.GONE
            holdPad.visibility = View.VISIBLE
        } else {
            modeToggle.setBackgroundResource(R.drawable.bg_btn_primary)
            modeToggle.setTextColor(service.getColor(R.color.bg))
            modeHold.setBackgroundResource(R.drawable.bg_btn_secondary)
            modeHold.setTextColor(service.getColor(R.color.onSurface))
            startStop.visibility = View.VISIBLE
            holdPad.visibility = View.GONE
        }
    }

    private fun refreshStartStop() {
        if (toggleArmed) {
            startStop.text = "STOP"
            startStop.setBackgroundResource(R.drawable.bg_btn_danger)
            startStop.setTextColor(service.getColor(R.color.onSurface))
        } else {
            startStop.text = "START"
            startStop.setBackgroundResource(R.drawable.bg_btn_primary)
            startStop.setTextColor(service.getColor(R.color.bg))
        }
    }

    // ---- dots ----

    private fun addDot() {
        if (dots.size >= MAX_DOTS) return
        val view = LayoutInflater.from(service).inflate(R.layout.overlay_dot, null) as TextView
        view.text = (dots.size + 1).toString()
        val lp = baseLp().apply {
            gravity = Gravity.TOP or Gravity.START
            width = dotSizePx
            height = dotSizePx
            x = (screenW / 2 - dotSizePx / 2)
            y = (screenH / 2 - dotSizePx / 2) + dots.size * (dotSizePx + 12)
        }
        attachDrag(view, lp, onTap = null)
        wm.addView(view, lp)
        dots.add(Dot(view, lp))
        dotCountLabel.text = dots.size.toString()
    }

    private fun removeDot() {
        if (dots.isEmpty()) return
        val d = dots.removeAt(dots.lastIndex)
        safeRemove(d.view)
        dotCountLabel.text = dots.size.toString()
    }

    fun setDotsArmed(armed: Boolean) {
        for (d in dots) {
            d.lp.flags = if (armed) ARMED_FLAGS else BASE_FLAGS
            d.view.setBackgroundResource(if (armed) R.drawable.bg_dot_armed else R.drawable.bg_dot)
            try { wm.updateViewLayout(d.view, d.lp) } catch (_: Throwable) {}
        }
    }

    fun setBubbleArmed(armed: Boolean) {
        bubble.setBackgroundResource(if (armed) R.drawable.bg_bubble_armed else R.drawable.bg_bubble)
        bubble.setTextColor(service.getColor(if (armed) R.color.onSurface else R.color.bg))
    }

    fun dotCenters(): List<Pair<Int, Int>> =
        dots.map { it.lp.x + dotSizePx / 2 to it.lp.y + dotSizePx / 2 }

    // ---- helpers ----

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(
        view: View,
        lp: WindowManager.LayoutParams,
        onTap: (() -> Unit)?
    ) {
        var downX = 0f; var downY = 0f
        var origX = 0; var origY = 0
        var dragging = false
        view.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    origX = lp.x; origY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downX).toInt()
                    val dy = (ev.rawY - downY).toInt()
                    if (dragging || abs(dx) > DRAG_SLOP || abs(dy) > DRAG_SLOP) {
                        dragging = true
                        lp.x = (origX + dx).coerceIn(0, (screenW - view.width).coerceAtLeast(0))
                        lp.y = (origY + dy).coerceIn(0, (screenH - view.height).coerceAtLeast(0))
                        try { wm.updateViewLayout(view, lp) } catch (_: Throwable) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging) onTap?.invoke()
                    true
                }
                else -> false
            }
        }
    }

    private fun baseLp() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        BASE_FLAGS,
        PixelFormat.TRANSLUCENT
    )

    private fun safeRemove(v: View) {
        try { wm.removeView(v) } catch (_: Throwable) {}
    }

    private companion object {
        const val DRAG_SLOP = 16
        const val MAX_DOTS = 12
        const val BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        const val ARMED_FLAGS = BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }
}
