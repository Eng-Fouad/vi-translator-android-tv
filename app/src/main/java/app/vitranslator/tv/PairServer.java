package app.vitranslator.tv;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * While this TV waits to be connected: answers the VI Translator app on the PC, which finds the TV
 * (GET /vit-tv) and sends the code the user typed there, with its address and a key for this TV
 * (POST /vit-tv/pair). Only requests carrying the X-VIT header are answered: a web page can't send
 * it to the TV from a browser.
 */
final class PairServer {
    static final int PORT = 8792;
    /** wrong codes before the code changes */
    private static final int TRIES = 5;

    interface Listener {
        /** The code to show changed (a new one after too many wrong tries). Any thread. */
        void onCode(String code);

        /** Connected: the PC's address, the key for this TV and the PC's name. Any thread. */
        void onConnected(String pc, String token, String pcName);
    }

    private final Context ctx;
    private final Listener listener;
    private final SecureRandom random = new SecureRandom();
    private volatile ServerSocket socket;
    private volatile String code;
    private int wrong;

    PairServer(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    String code() {
        return code;
    }

    synchronized void start() {
        if (socket != null) return;
        newCode();
        Thread t = new Thread(this::run, "pair-server");
        t.setDaemon(true);
        try {
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(PORT));
            socket = s;
        } catch (IOException e) {
            return; // (the port is taken: the PC won't find this TV; shown as "waiting" anyway)
        }
        t.start();
    }

    synchronized void stop() {
        ServerSocket s = socket;
        socket = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // closing
            }
        }
    }

    private void newCode() {
        code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        wrong = 0;
        listener.onCode(code);
    }

    private void run() {
        ServerSocket s;
        while ((s = socket) != null) {
            try (Socket c = s.accept()) {
                c.setSoTimeout(3000);
                handle(c);
            } catch (IOException e) {
                // closed, or a client that went away
            }
        }
    }

    private void handle(Socket c) throws IOException {
        InputStream in = new BufferedInputStream(c.getInputStream());
        String request = line(in);
        if (request == null) return;
        boolean vit = false;
        int length = 0;
        String h;
        while ((h = line(in)) != null && !h.isEmpty()) {
            int colon = h.indexOf(':');
            if (colon < 0) continue;
            String name = h.substring(0, colon).trim().toLowerCase(Locale.ROOT), value = h.substring(colon + 1).trim();
            if (name.equals("x-vit")) vit = true;
            if (name.equals("content-length")) {
                try {
                    length = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    length = -1;
                }
            }
        }
        OutputStream out = c.getOutputStream();
        String[] parts = request.split(" ");
        if (!vit || parts.length < 2) {
            respond(out, 403, "{}");
            return;
        }
        String method = parts[0], path = parts[1];
        if (method.equals("GET") && path.equals("/vit-tv")) {
            JSONObject o = new JSONObject();
            try {
                o.put("app", "vi-translator-tv").put("id", new Prefs(ctx).id()).put("name", deviceName());
            } catch (org.json.JSONException ignored) {
                // (plain strings)
            }
            respond(out, 200, o.toString());
        } else if (method.equals("POST") && path.equals("/vit-tv/pair") && length > 0 && length <= 4096) {
            pair(out, new String(body(in, length), StandardCharsets.UTF_8));
        } else {
            respond(out, 404, "{}");
        }
    }

    private void pair(OutputStream out, String body) throws IOException {
        String pc, token, pcName, given;
        try {
            JSONObject o = new JSONObject(body);
            given = o.optString("code");
            pc = Prefs.address(o.optString("pc"));
            token = o.optString("token");
            pcName = o.optString("pc_name").trim();
        } catch (org.json.JSONException e) {
            respond(out, 400, "{}");
            return;
        }
        synchronized (this) {
            if (!given.equals(code)) {
                if (++wrong >= TRIES) {
                    newCode();
                    respond(out, 429, "{}");
                } else {
                    respond(out, 403, "{}");
                }
                return;
            }
        }
        if (pc == null || !token.matches("[0-9a-f]{32,128}")) {
            respond(out, 400, "{}");
            return;
        }
        if (pcName.length() > 60) pcName = pcName.substring(0, 60);
        JSONObject o = new JSONObject();
        try {
            o.put("id", new Prefs(ctx).id()).put("name", deviceName());
        } catch (org.json.JSONException ignored) {
            // (plain strings)
        }
        respond(out, 200, o.toString());
        listener.onConnected(pc, token, pcName.isEmpty() ? "PC" : pcName);
    }

    String deviceName() {
        return deviceName(ctx);
    }

    /** The TV's name as set in its settings ("Living room TV"), or its model. */
    static String deviceName(Context ctx) {
        String n = null;
        try {
            n = Settings.Global.getString(ctx.getContentResolver(), Settings.Global.DEVICE_NAME);
        } catch (RuntimeException ignored) {
            // (not readable)
        }
        return n == null || n.trim().isEmpty() ? Build.MODEL : n.trim();
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 4096) throw new IOException("line too long");
        }
        if (c < 0 && b.size() == 0) return null;
        return b.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static byte[] body(InputStream in, int length) throws IOException {
        byte[] b = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(b, off, length - off);
            if (n < 0) throw new IOException("short body");
            off += n;
        }
        return b;
    }

    private static void respond(OutputStream out, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String reason = status == 200 ? "OK" : status == 403 ? "Forbidden" : status == 429 ? "Too Many Requests" : status == 400 ? "Bad Request" : "Not Found";
        String head = "HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: application/json\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
