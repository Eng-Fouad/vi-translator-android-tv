package app.vitranslator.tv

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Locale

/**
 * While this TV waits to be connected: answers the VI Translator app on the PC, which finds the TV
 * (GET /vit-tv) and sends the code the user typed there, with its address and a key for this TV
 * (POST /vit-tv/pair). Only requests carrying the X-VIT header are answered: a web page can't send
 * it to the TV from a browser.
 */
class PairServer(context: Context, private val listener: Listener) {
    interface Listener {
        /** The code to show changed (a new one after too many wrong tries). Any thread. */
        fun onCode(code: String)

        /** Connected: the PC's address, the key for this TV and the PC's name. Any thread. */
        fun onConnected(pc: String, token: String, pcName: String)
    }

    private val ctx = context.applicationContext
    private val random = SecureRandom()

    @Volatile
    private var socket: ServerSocket? = null

    @Volatile
    var code: String = ""
        private set
    private var wrong = 0

    @Synchronized
    fun start() {
        if (socket != null) return
        newCode()
        socket = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
            }
        } catch (e: IOException) {
            return // (the port is taken: the PC won't find this TV; shown as "waiting" anyway)
        }
        Thread(::run, "pair-server").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        val s = socket
        socket = null
        try {
            s?.close()
        } catch (ignored: IOException) {
            // closing
        }
    }

    private fun newCode() {
        code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000))
        wrong = 0
        listener.onCode(code)
    }

    private fun run() {
        while (true) {
            val s = socket ?: return
            try {
                s.accept().use { c ->
                    c.soTimeout = 3000
                    handle(c)
                }
            } catch (e: IOException) {
                // closed, or a client that went away
            }
        }
    }

    private fun handle(c: Socket) {
        val input = BufferedInputStream(c.getInputStream())
        val request = line(input) ?: return
        var vit = false
        var length = 0
        while (true) {
            val h = line(input)
            if (h.isNullOrEmpty()) break
            val colon = h.indexOf(':')
            if (colon < 0) continue
            val name = h.substring(0, colon).trim().lowercase(Locale.ROOT)
            val value = h.substring(colon + 1).trim()
            if (name == "x-vit") vit = true
            if (name == "content-length") length = value.toIntOrNull() ?: -1
        }
        val out = c.getOutputStream()
        val parts = request.split(" ")
        if (!vit || parts.size < 2) {
            respond(out, 403, "{}")
            return
        }
        val (method, path) = parts
        when {
            method == "GET" && path == "/vit-tv" -> {
                val o = JSONObject().put("app", "vi-translator-tv").put("id", Prefs(ctx).id).put("name", deviceName(ctx))
                respond(out, 200, o.toString())
            }
            method == "POST" && path == "/vit-tv/pair" && length in 1..4096 -> pair(out, String(body(input, length), Charsets.UTF_8))
            else -> respond(out, 404, "{}")
        }
    }

    private fun pair(out: OutputStream, body: String) {
        val given: String
        val pc: String?
        val token: String
        var pcName: String
        try {
            val o = JSONObject(body)
            given = o.optString("code")
            pc = Prefs.address(o.optString("pc"))
            token = o.optString("token")
            pcName = o.optString("pc_name").trim()
        } catch (e: JSONException) {
            respond(out, 400, "{}")
            return
        }
        synchronized(this) {
            if (given != code) {
                if (++wrong >= TRIES) {
                    newCode()
                    respond(out, 429, "{}")
                } else {
                    respond(out, 403, "{}")
                }
                return
            }
        }
        if (pc == null || !TOKEN.matches(token)) {
            respond(out, 400, "{}")
            return
        }
        pcName = pcName.take(60)
        respond(out, 200, JSONObject().put("id", Prefs(ctx).id).put("name", deviceName(ctx)).toString())
        listener.onConnected(pc, token, pcName.ifEmpty { "PC" })
    }

    companion object {
        const val PORT = 8792

        /** wrong codes before the code changes */
        private const val TRIES = 5
        private val TOKEN = Regex("[0-9a-f]{32,128}")

        /** The TV's name as set in its settings ("Living room TV"), or its model. */
        fun deviceName(ctx: Context): String {
            val n = try {
                Settings.Global.getString(ctx.contentResolver, "device_name") // (Settings.Global.DEVICE_NAME, from Android 7.1)
            } catch (e: RuntimeException) {
                null // (not readable)
            }
            return n?.trim()?.takeIf { it.isNotEmpty() } ?: Build.MODEL
        }

        private fun line(input: InputStream): String? {
            val b = ByteArrayOutputStream()
            var c: Int
            while (true) {
                c = input.read()
                if (c < 0 || c == '\n'.code) break
                if (c != '\r'.code) b.write(c)
                if (b.size() > 4096) throw IOException("line too long")
            }
            if (c < 0 && b.size() == 0) return null
            return b.toString("ISO-8859-1")
        }

        private fun body(input: InputStream, length: Int): ByteArray {
            val b = ByteArray(length)
            var off = 0
            while (off < length) {
                val n = input.read(b, off, length - off)
                if (n < 0) throw IOException("short body")
                off += n
            }
            return b
        }

        private fun respond(out: OutputStream, status: Int, json: String) {
            val body = json.toByteArray(Charsets.UTF_8)
            val reason = when (status) {
                200 -> "OK"
                403 -> "Forbidden"
                429 -> "Too Many Requests"
                400 -> "Bad Request"
                else -> "Not Found"
            }
            val head = "HTTP/1.1 $status $reason\r\nContent-Type: application/json\r\nContent-Length: ${body.size}" +
                "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
            out.write(head.toByteArray(Charsets.ISO_8859_1))
            out.write(body)
            out.flush()
        }
    }
}
