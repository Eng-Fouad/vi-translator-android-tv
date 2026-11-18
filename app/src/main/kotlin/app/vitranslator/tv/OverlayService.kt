package app.vitranslator.tv

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * The translations over what the TV shows (the game on an HDMI input): the PC app's TV page in a
 * transparent window on top of everything. The window can't be focused or touched, so the remote
 * keeps working the TV and the input under it.
 *
 * Runs while this TV is connected. It also: hides the window when "Show on" names another input;
 * notices when the PC app removed this TV (back to showing a code); and finds the PC again when it
 * got a new address on the home network.
 */
class OverlayService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private var web: WebView? = null
    private var url: String? = null
    private var unreachable = 0
    private var finding = false
    private val retry = Runnable { load() }
    private val check = Runnable { check() }
    private val watchInput = Runnable { watchInput() }

    override fun onCreate() {
        super.onCreate()
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    // (its notification in the app's language)
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // (in the foreground at once, as Android asks of a service started this way)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), Compat.immutable)
        Compat.foreground(this, 1, CHANNEL, getString(R.string.notify_channel), getString(R.string.app_name), getString(R.string.notify_text), open)
        val prefs = Prefs(this)
        if (!prefs.connected || !Compat.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        show(Pc.pageUrl(prefs.pc, prefs.token))
        main.removeCallbacks(check)
        main.post(check)
        main.removeCallbacks(watchInput)
        main.post(watchInput)
        return START_STICKY
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun show(pageUrl: String) {
        if (web == null) {
            val w = WebView(this)
            w.setBackgroundColor(Color.TRANSPARENT)
            w.isFocusable = false
            w.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            w.webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, req: WebResourceRequest, error: WebResourceError) {
                    if (req.isForMainFrame) failed()
                }

                // (the same, before Android 6.0: called for the page itself only)
                @Deprecated("Android before 6.0")
                override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                    failed()
                }

                override fun onReceivedHttpError(view: WebView, req: WebResourceRequest, res: WebResourceResponse) {
                    if (req.isForMainFrame) failed()
                }

                // the page never goes anywhere else
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest) = true
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Compat.overlayWindowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            )
            windows.addView(w, lp)
            web = w
            running = true
        }
        if (pageUrl != url) {
            url = pageUrl
            load()
        }
    }

    private fun load() {
        main.removeCallbacks(retry)
        val u = url ?: return
        val prefs = Prefs(this)
        val (pc, token) = prefs.pc to prefs.token
        // only once the PC answers this TV as connected: an error page (its text) never shows over the game
        Thread {
            val s = Pc.ask(pc, token, 2000, 4000)
            main.post {
                if (u != url) return@post
                if (s == Pc.State.OK || s == Pc.State.OVERLAYS_OFF) web?.loadUrl(u) else failed()
            }
        }.start()
    }

    /** The page isn't there: nothing on screen (not an error page), and try again a little later. */
    private fun failed() {
        val w = web ?: return
        w.loadUrl("about:blank")
        main.removeCallbacks(retry)
        main.postDelayed(retry, RETRY_MS)
    }

    /** "Show on": the window only while the chosen input is on screen. */
    private fun watchInput() {
        val w = web ?: return
        val v = if (Inputs.showing(contentResolver, Prefs(this).showOn)) View.VISIBLE else View.INVISIBLE
        if (w.visibility != v) w.visibility = v
        main.postDelayed(watchInput, INPUT_MS)
    }

    /** How the PC is: removed this TV (disconnect), or gone a while (look for it at a new address). */
    private fun check() {
        val prefs = Prefs(this)
        val pc = prefs.pc
        val token = prefs.token
        if (pc.isEmpty()) {
            stopSelf()
            return
        }
        Thread {
            val s = Pc.ask(pc, token, 2000, 4000)
            main.post {
                state = s
                if (s == Pc.State.REMOVED) {
                    prefs.disconnect()
                    stopSelf()
                    return@post
                }
                unreachable = if (s == Pc.State.UNREACHABLE) unreachable + 1 else 0
                if (unreachable >= 3 && !finding) findPc(pc, token)
                main.postDelayed(check, CHECK_MS)
            }
        }.start()
    }

    private fun findPc(pc: String, token: String) {
        finding = true
        val port = pc.substringAfterLast(':').toInt()
        Thread {
            val found = Pc.find(this, port, token)
            main.post {
                finding = false
                if (found != null && found != pc) {
                    Prefs(this).setPc(found)
                    unreachable = 0
                    show(Pc.pageUrl(found, token))
                }
            }
        }.start()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        web?.let { w ->
            try {
                windows.removeView(w)
            } catch (ignored: IllegalArgumentException) {
                // (not added)
            }
            w.destroy()
        }
        web = null
        url = null
        running = false
        state = Pc.State.UNREACHABLE
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "overlay"

        /** after the page couldn't be loaded (the PC off, the app closed) */
        private const val RETRY_MS = 5000L

        /** how often the PC is asked how it is */
        private const val CHECK_MS = 10_000L

        /** how often the input on screen is looked at */
        private const val INPUT_MS = 400L

        /** For the screen: is the overlay up, and how the PC answered last. */
        @Volatile
        var running = false

        @Volatile
        var state = Pc.State.UNREACHABLE

        fun start(c: Context) {
            Compat.startForegroundService(c, Intent(c, OverlayService::class.java))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, OverlayService::class.java))
        }
    }
}
