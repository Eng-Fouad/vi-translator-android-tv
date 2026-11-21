package app.vitranslator.tv

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from the app's GitHub releases: a newer release is offered on the main screen; "Update"
 * downloads its APK and hands it to Android's installer, which asks to confirm (an app installed from
 * outside a store can't update itself silently). Android installs it only when it's signed with the
 * same key as the installed app, so a replaced file can't get in.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/vi-translator/vi-translator-android-tv/releases/latest"

    /** At most this often (the GitHub API limits callers without an account). */
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    data class Release(val version: String, val apk: String)

    enum class State { IDLE, DOWNLOADING, INSTALLING, FAILED }

    /** A newer release, once found. */
    @Volatile
    var available: Release? = null

    @Volatile
    var state = State.IDLE

    @Volatile
    private var checking = false

    /** "1.2.3" -> (1, 2, 3); null for anything else. */
    fun parse(v: String): Triple<Int, Int, Int>? {
        val p = v.trim().removePrefix("v").split(".")
        if (p.size != 3) return null
        val n = p.map { it.toIntOrNull() ?: return null }
        return Triple(n[0], n[1], n[2])
    }

    fun newer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        return compareValuesBy(a, b, { it.first }, { it.second }, { it.third }) > 0
    }

    /** The release in GitHub's answer, if it's newer than [current] and has this app's APK. */
    fun fromLatest(json: String, current: String): Release? {
        val o = JSONObject(json)
        val version = o.optString("tag_name").removePrefix("v")
        if (!newer(version, current)) return null
        val assets = o.optJSONArray("assets") ?: return null
        val name = "vi-translator-android-tv-$version.apk"
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name") == name) return Release(version, a.optString("browser_download_url"))
        }
        return null
    }

    fun current(ctx: Context): String =
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

    /** Look for a newer release in the background (at most every few hours); [found] runs on it. */
    fun check(ctx: Context, found: () -> Unit) {
        // the Google Play build: Play updates it
        if (!BuildConfig.SELF_UPDATE) return
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val last = prefs.getLong("checked", 0)
        if (available != null || checking || System.currentTimeMillis() - last < CHECK_EVERY_MS) return
        checking = true
        Thread {
            try {
                val c = URL(LATEST).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 10000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "vi-translator-tv")
                if (c.responseCode == 200) {
                    val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    prefs.edit().putLong("checked", System.currentTimeMillis()).apply()
                    available = fromLatest(body, current(app))
                    if (available != null) found()
                }
                c.disconnect()
            } catch (e: Exception) {
                // (offline, or GitHub busy: next time)
            } finally {
                checking = false
            }
        }.start()
    }

    /** Download [r] and hand it to Android's installer; [changed] runs (on a background thread) as it goes. */
    fun install(ctx: Context, r: Release, changed: () -> Unit) {
        if (state == State.DOWNLOADING || state == State.INSTALLING) return
        val app = ctx.applicationContext
        state = State.DOWNLOADING
        changed()
        Thread {
            try {
                val file = File(app.cacheDir, "update.apk")
                download(r.apk, file)
                state = State.INSTALLING
                changed()
                hand(app, file)
            } catch (e: Exception) {
                state = State.FAILED
                changed()
            }
        }.start()
    }

    private fun download(url: String, to: File) {
        var u = URL(url)
        // (GitHub sends the file from another host: follow the redirects, https only)
        repeat(5) {
            val c = u.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "vi-translator-tv")
            when (c.responseCode) {
                in 300..399 -> {
                    val next = URL(u, c.getHeaderField("Location") ?: error("redirect without a place"))
                    if (next.protocol != "https") error("not https")
                    c.disconnect()
                    u = next
                }
                200 -> {
                    c.inputStream.use { input -> to.outputStream().use { input.copyTo(it) } }
                    c.disconnect()
                    return
                }
                else -> error("HTTP ${c.responseCode}")
            }
        }
        error("too many redirects")
    }

    /** The APK to Android's installer: it asks to confirm, then replaces this app. */
    private fun hand(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            apk.inputStream().use { input ->
                s.openWrite("app.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    s.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val done = PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateReceiver::class.java), flags)
            s.commit(done.intentSender)
        }
    }
}

/** The installer's answer: show its confirmation, or note that it didn't go (the app is replaced when it does). */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.state = Updater.State.IDLE
            else -> Updater.state = Updater.State.FAILED
        }
    }
}
