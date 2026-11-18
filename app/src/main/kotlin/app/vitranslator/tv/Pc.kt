package app.vitranslator.tv

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** The VI Translator app on the PC this TV is connected to. */
object Pc {
    enum class State {
        /** it answers, and shows its overlays */
        OK,

        /** it answers, but its Overlays switch is off */
        OVERLAYS_OFF,

        /** it doesn't know this TV any more (removed in the PC app) */
        REMOVED,

        /** no answer: the app closed, the PC off, sharing off, or a new address */
        UNREACHABLE,
    }

    /** The TV page, with this TV's key. */
    fun pageUrl(pc: String, token: String) = "http://$pc/tv?t=${enc(token)}"

    /** Ask the PC app how it is (blocking: not on the main thread). */
    fun ask(pc: String, token: String, connectMs: Int, readMs: Int): State {
        var c: HttpURLConnection? = null
        return try {
            c = URL("http://$pc/tv.json?h=&t=${enc(token)}").openConnection() as HttpURLConnection
            c.connectTimeout = connectMs
            c.readTimeout = readMs
            c.useCaches = false
            when (c.responseCode) {
                403 -> State.REMOVED
                200 -> {
                    val body = read(c.inputStream, 4 shl 20)
                    when {
                        !body.contains("\"areas\"") -> State.UNREACHABLE
                        body.contains("\"overlays\":false") -> State.OVERLAYS_OFF
                        else -> State.OK
                    }
                }
                else -> State.UNREACHABLE
            }
        } catch (e: Exception) {
            State.UNREACHABLE
        } finally {
            c?.disconnect()
        }
    }

    /** Tell the PC this TV disconnected (its key is forgotten there). Best effort. */
    fun forget(pc: String, token: String) {
        var c: HttpURLConnection? = null
        try {
            c = URL("http://$pc/tv/forget?t=${enc(token)}").openConnection() as HttpURLConnection
            c.connectTimeout = 1500
            c.readTimeout = 2000
            c.responseCode
        } catch (ignored: Exception) {
            // (the PC is off: its key stays there until removed in the PC app)
        } finally {
            c?.disconnect()
        }
    }

    /**
     * The PC got a new address (from the router): look for it on this TV's /24 network, on the same
     * port, with this TV's key. Its new address, or null.
     */
    fun find(ctx: Context, port: Int, token: String): String? {
        val mine = ownAddress(ctx) ?: return null
        val b = mine.address
        val prefix = "${b[0].toInt() and 255}.${b[1].toInt() and 255}.${b[2].toInt() and 255}."
        val me = b[3].toInt() and 255
        val found = AtomicReference<String?>()
        val pool = Executors.newFixedThreadPool(48)
        for (i in 1..254) {
            if (i == me) continue
            val a = "$prefix$i:$port"
            pool.execute {
                if (found.get() != null) return@execute
                val s = ask(a, token, 400, 1500)
                if (s == State.OK || s == State.OVERLAYS_OFF) found.compareAndSet(null, a)
            }
        }
        pool.shutdown()
        try {
            pool.awaitTermination(30, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        pool.shutdownNow()
        return found.get()
    }

    /** This TV's IPv4 address on its active network, or null. */
    fun ownAddress(ctx: Context): Inet4Address? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
            val lp = cm.activeNetwork?.let { cm.getLinkProperties(it) } ?: return null
            return lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress } as Inet4Address?
        }
        // (before Android 6.0: the first network interface that is up with an IPv4 address)
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress } as Inet4Address?
        } catch (e: Exception) {
            null
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun read(input: InputStream, max: Int): String = input.use { s ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16384)
        while (true) {
            val n = s.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw IOException("too big")
        }
        out.toString("UTF-8")
    }
}
