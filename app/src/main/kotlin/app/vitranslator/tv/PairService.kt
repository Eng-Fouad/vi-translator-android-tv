package app.vitranslator.tv

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Waits to be connected (the code listener, PairServer), also after the user leaves the app: the
 * PC is often on another input of this same TV, so the user notes the code, switches to the PC
 * and connects from there. It keeps the same code, and stops 10 minutes after the app was left, or
 * once connected.
 */
class PairService : Service(), PairServer.Listener {
    private val main = Handler(Looper.getMainLooper())
    private var server: PairServer? = null
    private val end = Runnable { stopSelf() }

    // (its notification in the app's language)
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), Compat.immutable)
        Compat.foreground(this, 2, CHANNEL, getString(R.string.pair_channel), getString(R.string.app_name), getString(R.string.pair_notify), open)
        if (Prefs(this).connected) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (server == null) server = PairServer(this, this).also { it.start() }
        main.removeCallbacks(end)
        if (intent?.action == ACTION_LINGER) main.postDelayed(end, LINGER_MS)
        return START_NOT_STICKY
    }

    override fun onCode(code: String) {
        Companion.code = code
    }

    override fun onConnected(pc: String, token: String, pcName: String) {
        main.post {
            Prefs(this).connect(pc, token, pcName)
            OverlayService.state = Pc.State.UNREACHABLE
            if (Compat.canDrawOverlays(this)) OverlayService.start(this)
            stopSelf()
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        server?.stop()
        server = null
        code = ""
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "pairing"
        private const val ACTION_LINGER = "app.vitranslator.tv.LINGER"

        /** how long it keeps waiting after the app was left */
        const val LINGER_MS = 10 * 60_000L

        /** The code to show ("" until the listener is up). */
        @Volatile
        var code: String = ""

        /** The app is on screen: wait, with no end. */
        fun start(c: Context) {
            Compat.startForegroundService(c, Intent(c, PairService::class.java))
        }

        /** The app was left: keep waiting a few minutes more. */
        fun linger(c: Context) {
            Compat.startForegroundService(c, Intent(c, PairService::class.java).setAction(ACTION_LINGER))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, PairService::class.java))
        }
    }
}
