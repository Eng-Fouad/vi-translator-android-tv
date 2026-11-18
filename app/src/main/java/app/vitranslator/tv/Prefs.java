package app.vitranslator.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * This TV's settings: the PC it's connected to (its address and the key it gave this TV), and which
 * input the overlay shows on.
 */
final class Prefs {
    private final SharedPreferences p;

    Prefs(Context c) {
        p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** This app's own id: the PC knows this TV by it (connected again, it replaces the old key). */
    String id() {
        String id = p.getString("id", "");
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            p.edit().putString("id", id).apply();
        }
        return id;
    }

    boolean connected() {
        return !pc().isEmpty() && !token().isEmpty();
    }

    /** The PC app's home-network address, "192.168.1.20:8791" ("" when not connected). */
    String pc() {
        return p.getString("pc", "");
    }

    String token() {
        return p.getString("token", "");
    }

    String pcName() {
        return p.getString("pc_name", "");
    }

    void connect(String pc, String token, String pcName) {
        p.edit().putString("pc", pc).putString("token", token).putString("pc_name", pcName).apply();
    }

    /** The PC moved to another address (the router gave it a new one). */
    void setPc(String pc) {
        p.edit().putString("pc", pc).apply();
    }

    void disconnect() {
        p.edit().remove("pc").remove("token").remove("pc_name").apply();
    }

    /** The input the overlay shows on (its TV input device id), or "" for every input and app. */
    String showOn() {
        return p.getString("show_on", "");
    }

    void setShowOn(String deviceId) {
        p.edit().putString("show_on", deviceId).apply();
    }

    /** "ip:port", an IPv4 address on a home network and a port; null otherwise. */
    static String address(String s) {
        if (s == null) return null;
        int colon = s.lastIndexOf(':');
        if (colon < 0) return null;
        String host = s.substring(0, colon);
        int port;
        try {
            port = Integer.parseInt(s.substring(colon + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (port < 1 || port > 65535) return null;
        if (!host.matches("(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}")) return null;
        String[] o = host.split("\\.");
        int a = Integer.parseInt(o[0]), b = Integer.parseInt(o[1]);
        boolean home = a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168) || (a == 169 && b == 254);
        return home ? host + ":" + port : null;
    }
}
