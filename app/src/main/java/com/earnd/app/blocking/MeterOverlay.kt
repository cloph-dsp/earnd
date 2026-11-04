package com.earnd.app.blocking

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.earnd.app.R
import com.earnd.app.domain.OverlayMath

/** Debug-visible state of the overlay. */
object MeterOverlayState {
    @Volatile var visible = false
}

/**
 * Small draggable counter shown over a counted leisure app. A plain View in an accessibility overlay window that
 * only covers its own pixels (not focusable, not touch-modal). Tap collapses it to a dot, long-press shows the app
 * name for a moment, drag moves it and it snaps to the nearest side edge. Position is remembered per package.
 */
class MeterOverlay(private val context: Context, private val wm: WindowManager) {
    private val dp = context.resources.displayMetrics.density
    private val prefs = context.getSharedPreferences("meter_overlay", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var pkg = ""
    private var label = ""
    private var text = ""
    private var low = false
    private var collapsed = false
    private var showLabelUntil = 0L

    val shown: Boolean get() = view != null

    /** Shows or updates the counter. [remainingMs] is real time left at the current rate. */
    fun show(pkg: String, label: String, remainingMs: Long, ratePct: Int) {
        if (view == null || this.pkg != pkg) { hide(); create(pkg) }
        this.label = label
        text = OverlayMath.text(remainingMs, ratePct)
        low = OverlayMath.isLow(remainingMs)
        render()
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        params = null
        MeterOverlayState.visible = false
    }

    private fun px(v: Int) = (v * dp).toInt()

    private fun create(forPkg: String) {
        pkg = forPkg
        collapsed = prefs.getBoolean("collapsed", false)
        val tv = TextView(context).apply {
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.ibm_plex_mono_medium) }.getOrNull() ?: Typeface.MONOSPACE
            textSize = 10f
            setTextColor(AMBER)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setOnTouchListener(::onTouch)
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        val left = prefs.getBoolean("left_$forPkg", false)
        p.gravity = Gravity.TOP or if (left) Gravity.START else Gravity.END
        p.x = px(4)
        p.y = prefs.getInt("y_$forPkg", px(120))
        view = tv
        params = p
        wm.addView(tv, p)
        MeterOverlayState.visible = true
    }

    private fun render() {
        val tv = view ?: return
        val showName = System.currentTimeMillis() < showLabelUntil
        val color = if (low) RED else AMBER
        tv.setTextColor(color)
        tv.background = GradientDrawable().apply {
            if (collapsed && !showName) setColor(color) else { setColor(RAISED); setStroke(px(1).coerceAtLeast(1), BORDER) }
            cornerRadius = if (collapsed && !showName) px(6).toFloat() else px(4).toFloat()
        }
        if (collapsed && !showName) {
            tv.text = ""
            tv.minWidth = px(12); tv.minHeight = px(12)
            tv.setPadding(0, 0, 0, 0)
            tv.layoutParams?.let { it.width = px(12); it.height = px(12) }
        } else {
            tv.text = if (showName) "$label $text" else text
            tv.minWidth = 0; tv.minHeight = px(22)
            tv.setPadding(px(8), 0, px(8), 0)
            tv.layoutParams?.let { it.width = WindowManager.LayoutParams.WRAP_CONTENT; it.height = WindowManager.LayoutParams.WRAP_CONTENT }
        }
        val p = params ?: return
        if (collapsed && !showName) { p.width = px(12); p.height = px(12) }
        else { p.width = WindowManager.LayoutParams.WRAP_CONTENT; p.height = WindowManager.LayoutParams.WRAP_CONTENT }
        runCatching { wm.updateViewLayout(tv, p) }
    }

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var longPressed = false

    private fun onTouch(v: View, e: MotionEvent): Boolean {
        val p = params ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY
                val loc = IntArray(2); v.getLocationOnScreen(loc)
                startX = loc[0]; startY = loc[1]
                dragging = false; longPressed = false
                handler.postDelayed({
                    if (!dragging) {
                        longPressed = true
                        showLabelUntil = System.currentTimeMillis() + 2500
                        render()
                        handler.postDelayed({ render() }, 2600)
                    }
                }, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX; val dy = e.rawY - downY
                if (!dragging && (dx * dx + dy * dy) > slop * slop) {
                    dragging = true
                    p.gravity = Gravity.TOP or Gravity.START
                }
                if (dragging) {
                    p.x = (startX + dx).toInt()
                    p.y = (startY + dy).toInt()
                    runCatching { wm.updateViewLayout(v, p) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) snap(v, p)
                else if (!longPressed && e.actionMasked == MotionEvent.ACTION_UP) {
                    collapsed = !collapsed
                    prefs.edit().putBoolean("collapsed", collapsed).apply()
                    render()
                }
            }
        }
        return true
    }

    private fun snap(v: View, p: WindowManager.LayoutParams) {
        val dm = context.resources.displayMetrics
        val left = OverlayMath.snapLeft(p.x + v.width / 2, dm.widthPixels)
        val y = OverlayMath.clampY(p.y, v.height, dm.heightPixels)
        p.gravity = Gravity.TOP or if (left) Gravity.START else Gravity.END
        p.x = px(4)
        p.y = y
        runCatching { wm.updateViewLayout(v, p) }
        prefs.edit().putBoolean("left_$pkg", left).putInt("y_$pkg", y).apply()
    }

    private companion object {
        val AMBER = Color.parseColor("#FFB000")
        val RED = Color.parseColor("#FF4A3D")
        val DARK = Color.parseColor("#121110")
        val RAISED = Color.parseColor("#1D1B18")
        val BORDER = Color.parseColor("#3A342A")
    }
}
