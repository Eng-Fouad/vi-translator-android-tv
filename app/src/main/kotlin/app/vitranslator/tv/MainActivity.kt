package app.vitranslator.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The only screen, minimal: not connected, it shows the code to type in VI Translator on the PC
 * (which connects this TV); connected, it shows the connection, "Show on" and Disconnect.
 */
class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs

    private lateinit var waiting: LinearLayout
    private lateinit var connected: LinearLayout
    private lateinit var permission: LinearLayout
    private lateinit var code: TextView
    private lateinit var thisTv: TextView
    private lateinit var status: TextView
    private lateinit var showOn: Button
    private lateinit var disconnect: Button
    private lateinit var allow: Button
    private lateinit var oldWebView: TextView
    private var inputs: List<Inputs.Input> = emptyList()
    private var resumed = false

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            if (resumed) main.postDelayed(this, STATUS_MS)
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        prefs = Prefs(this)

        val root = FrameLayout(this).apply { background = Look.background() }
        val col = column()
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        // the brand: logo and name in the sunset gradient, and the header's sunset line
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brand.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) }, LinearLayout.LayoutParams(dp(64), dp(64)))
        brand.addView(Look.text(this, "VI TRANSLATOR", 44f, 800, Look.TEXT).also { Look.sunset(it) })
        col.addView(brand, wrap())
        col.addView(Look.line(this), LinearLayout.LayoutParams(dp(520), dp(3)).apply {
            topMargin = dp(10)
            bottomMargin = dp(34)
        })

        // not connected: the code
        waiting = column()
        waiting.addView(Look.text(this, getString(R.string.enter_code), 22f, 400, Look.TEXT), wrap())
        code = Look.text(this, "", 96f, 800, Look.NEON).apply {
            Look.neon(this, Look.NEON)
            letterSpacing = 0.12f
            textDirection = View.TEXT_DIRECTION_LTR
        }
        waiting.addView(code, wrap())
        waiting.addView(Look.text(this, getString(R.string.where_on_pc), 16f, 400, Look.MUTED), wrap())
        thisTv = Look.text(this, "", 14f, 400, Look.MUTED).apply { setPadding(0, dp(18), 0, 0) }
        waiting.addView(thisTv, wrap())
        col.addView(waiting, wrap())

        // connected: the connection, Show on, Disconnect
        connected = column()
        status = Look.text(this, "", 26f, 650, Look.TEXT).apply { compoundDrawablePadding = dp(14) }
        connected.addView(status, wrap())
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            // (lined up by their boxes, not their text: by text, the row pushes them past its own bottom)
            isBaselineAligned = false
            // (room for the focused button, drawn a little bigger)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            clipToPadding = false
        }
        showOn = Look.button(this, "").apply { setOnClickListener { nextShowOn() } }
        disconnect = Look.button(this, getString(R.string.disconnect)).apply { setOnClickListener { disconnect() } }
        // (side gaps only: a top margin on a button in a centred row is counted twice and pushes it down)
        buttons.addView(showOn, wrap().apply { setMargins(dp(10), 0, dp(10), 0) })
        buttons.addView(disconnect, wrap().apply { setMargins(dp(10), 0, dp(10), 0) })
        connected.addView(buttons, wrap().apply { topMargin = dp(30) })
        col.addView(connected, wrap())

        // "Display over other apps" not allowed yet
        permission = column().apply {
            background = Look.card(this@MainActivity)
            setPadding(dp(28), dp(18), dp(28), dp(18))
        }
        permission.addView(Look.text(this, getString(R.string.permission_needed), 17f, 400, Look.WARN), wrap())
        allow = Look.button(this, getString(R.string.allow)).apply { setOnClickListener { askPermission() } }
        permission.addView(allow, wrap().apply { topMargin = dp(12) })
        val adb = getString(R.string.adb_hint, "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow")
        permission.addView(Look.text(this, adb, 13f, 400, Look.MUTED).apply { setPadding(0, dp(12), 0, 0) }, wrap())
        col.addView(permission, wrap().apply { topMargin = dp(30) })

        // a web engine too old to draw the PC's TV page (an old TV whose WebView was never updated)
        oldWebView = Look.text(this, getString(R.string.webview_old), 17f, 400, Look.WARN).apply {
            background = Look.card(this@MainActivity)
            setPadding(dp(28), dp(18), dp(28), dp(18))
            val v = Compat.webViewVersion(this@MainActivity)
            visibility = if (v in 1 until Compat.MIN_WEBVIEW) View.VISIBLE else View.GONE
        }
        col.addView(oldWebView, wrap().apply { topMargin = dp(30) })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        inputs = Inputs.hdmi(this)
        render()
        main.post(tick)
        // (the code listener starts a moment after: the code shows as soon as it's there)
        main.postDelayed({ refresh() }, 250)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        main.removeCallbacks(tick)
        // the PC is often on another input of this TV: keep waiting a few minutes, with the same code
        if (!prefs.connected) PairService.linger(this)
    }

    /** Which part shows, for whether this TV is connected; the code listener runs only while not. */
    private fun render() {
        val on = prefs.connected
        waiting.visibility = if (on) View.GONE else View.VISIBLE
        connected.visibility = if (on) View.VISIBLE else View.GONE
        if (on) {
            PairService.stop(this)
            showOn.visibility = if (inputs.isEmpty()) View.GONE else View.VISIBLE
            showOn.text = getString(R.string.show_on, showOnLabel())
            if (!disconnect.hasFocus() && !showOn.hasFocus()) (if (inputs.isEmpty()) disconnect else showOn).requestFocus()
        } else {
            thisTv.text = getString(R.string.this_tv, PairServer.deviceName(this))
            PairService.start(this)
            code.text = spaced(PairService.code)
        }
        refresh()
    }

    /** The connection light and text, the permission card, and the overlay kept running. */
    private fun refresh() {
        val allowed = Compat.canDrawOverlays(this)
        permission.visibility = if (allowed) View.GONE else View.VISIBLE
        if (!allowed && !allow.hasFocus() && !prefs.connected) allow.requestFocus()
        if (!prefs.connected) {
            // (the PC removed this TV meanwhile: back to a code)
            if (connected.visibility == View.VISIBLE) render()
            val c = spaced(PairService.code)
            if (!c.contentEquals(code.text)) code.text = c
            return
        }
        if (waiting.visibility == View.VISIBLE) {
            render()
            return
        }
        if (allowed && !OverlayService.running) OverlayService.start(this)
        val pc = prefs.pcName
        val (color, text) = when (OverlayService.state) {
            Pc.State.OK -> Look.NEON to getString(R.string.connected_to, pc)
            Pc.State.OVERLAYS_OFF -> Look.WARN to getString(R.string.overlays_off, pc)
            else -> Look.ORANGE to getString(R.string.looking_for, pc)
        }
        status.text = text
        status.setCompoundDrawablesRelativeWithIntrinsicBounds(Look.dot(this, color), null, null, null)
    }

    private fun disconnect() {
        val pc = prefs.pc
        val token = prefs.token
        Thread { Pc.forget(pc, token) }.start()
        prefs.disconnect()
        OverlayService.stop(this)
        render()
    }

    /** Show on: every input, then each HDMI input in turn. */
    private fun nextShowOn() {
        val i = inputs.indexOfFirst { it.deviceId == prefs.showOn }
        prefs.showOn = inputs.getOrNull(i + 1)?.deviceId ?: ""
        showOn.text = getString(R.string.show_on, showOnLabel())
    }

    private fun showOnLabel(): String = inputs.firstOrNull { it.deviceId == prefs.showOn }?.label ?: getString(R.string.all_inputs)

    private fun askPermission() {
        // (before Android 6.0 the permission comes with the install: this is never asked there)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (ignored: ActivityNotFoundException) {
                // (no such page on this TV: the adb command shown does it)
            }
        }
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun dp(v: Int) = Look.dp(this, v.toFloat())

    companion object {
        private const val STATUS_MS = 1500L

        private fun wrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        /** "482913" as "482 913" (easier to read from the sofa). */
        private fun spaced(c: String?) = if (c == null || c.length != 6) "" else "${c.substring(0, 3)} ${c.substring(3)}"
    }
}
