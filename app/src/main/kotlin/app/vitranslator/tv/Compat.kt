package app.vitranslator.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.webkit.WebSettings

/**
 * What differs between Android versions, so the app runs on every Android TV: from Android 5.0
 * (the first Android TV, API 21) to today's Google TV.
 */
object Compat {
    /** Drawing over other apps: allowed by the user from Android 6.0; granted at install before. */
    fun canDrawOverlays(c: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(c)

    /** The window type for drawing over other apps (Android 8.0 replaced the old one). */
    @Suppress("DEPRECATION")
    val overlayWindowType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    /**
     * The overlay window's flags. Until Android 12 it lets touches through (FLAG_NOT_TOUCHABLE). From
     * Android 12, Android caps the opacity of such a window at 0.8 (touches through an overlay more
     * opaque than that are blocked), so the game showed through everything drawn: there the window
     * takes touches instead. A TV has none; the remote's keys still go to the input under it
     * (FLAG_NOT_FOCUSABLE).
     */
    val overlayWindowFlags: Int
        get() = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            (if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0)

    /** A service that shows a notification while it runs (started in the foreground from Android 8.0). */
    fun startForegroundService(c: Context, i: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i) else c.startService(i)
    }

    /**
     * The oldest web engine (Chrome / Android System WebView major version) that draws the PC app's
     * TV page: its drawing code uses modern JavaScript and CSS (flex gap: Chrome 84).
     */
    const val MIN_WEBVIEW = 84

    /** This TV's WebView major version, from its user agent ("… Chrome/95.0…"); 0 when unknown. */
    fun webViewVersion(c: Context): Int = try {
        Regex("""Chrome/(\d+)""").find(WebSettings.getDefaultUserAgent(c))?.groupValues?.get(1)?.toInt() ?: 0
    } catch (e: Exception) {
        0
    }

    /** PendingIntent flags: immutable where Android knows the flag (6.0 and later). */
    val immutable: Int get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    /** The service's ongoing notification (channels from Android 8.0). */
    fun foreground(s: Service, id: Int, channel: String, channelName: String, title: String, text: String, open: PendingIntent) {
        val nm = s.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channel, channelName, NotificationManager.IMPORTANCE_LOW))
            Notification.Builder(s, channel)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(s)
        }
        val n = b.setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            s.startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            s.startForeground(id, n)
        }
    }
}
