package app.vitranslator.tv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** The VI Translator app on the PC this TV is connected to. */
final class Pc {
    private Pc() {}

    enum State {
        /** it answers, and shows its overlays */
        OK,
        /** it answers, but its Overlays switch is off */
        OVERLAYS_OFF,
        /** it doesn't know this TV any more (removed in the PC app) */
        REMOVED,
        /** no answer: the app closed, the PC off, sharing off, or a new address */
        UNREACHABLE
    }

    /** The TV page, with this TV's key. */
    static String pageUrl(String pc, String token) {
        return "http://" + pc + "/tv?t=" + enc(token);
    }

    /** Ask the PC app how it is (blocking: not on the main thread). */
    static State ask(String pc, String token, int connectMs, int readMs) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://" + pc + "/tv.json?h=&t=" + enc(token)).openConnection();
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setUseCaches(false);
            int code = c.getResponseCode();
            if (code == 403) return State.REMOVED;
            if (code != 200) return State.UNREACHABLE;
            String body = read(c.getInputStream(), 4 << 20);
            if (!body.contains("\"areas\"")) return State.UNREACHABLE;
            return body.contains("\"overlays\":false") ? State.OVERLAYS_OFF : State.OK;
        } catch (Exception e) {
            return State.UNREACHABLE;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Tell the PC this TV disconnected (its key is forgotten there). Best effort. */
    static void forget(String pc, String token) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://" + pc + "/tv/forget?t=" + enc(token)).openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(2000);
            c.getResponseCode();
        } catch (Exception ignored) {
            // (the PC is off: its key stays there until removed in the PC app)
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /**
     * The PC got a new address (from the router): look for it on this TV's /24 network, on the same
     * port, with this TV's key. Its new address, or null.
     */
    static String find(Context ctx, int port, String token) {
        Inet4Address mine = ownAddress(ctx);
        if (mine == null) return null;
        byte[] b = mine.getAddress();
        String prefix = (b[0] & 255) + "." + (b[1] & 255) + "." + (b[2] & 255) + ".";
        AtomicReference<String> found = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(48);
        for (int i = 1; i <= 254; i++) {
            if (i == (b[3] & 255)) continue;
            String a = prefix + i + ":" + port;
            pool.execute(() -> {
                if (found.get() != null) return;
                State s = ask(a, token, 400, 1500);
                if (s == State.OK || s == State.OVERLAYS_OFF) found.compareAndSet(null, a);
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();
        return found.get();
    }

    /** This TV's IPv4 address on its active network, or null. */
    static Inet4Address ownAddress(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        Network n = cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        if (lp == null) return null;
        for (LinkAddress la : lp.getLinkAddresses()) {
            InetAddress a = la.getAddress();
            if (a instanceof Inet4Address && !a.isLoopbackAddress()) return (Inet4Address) a;
        }
        return null;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }

    static String read(InputStream in, int max) throws java.io.IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = s.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > max) throw new java.io.IOException("too big");
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
