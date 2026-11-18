package app.vitranslator.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The only screen, minimal: not connected, it shows the code to type in VI Translator on the PC
 * (which connects this TV); connected, it shows the connection, "Show on" and Disconnect.
 */
public class MainActivity extends Activity {
    private static final long STATUS_MS = 1500;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Prefs prefs;

    private LinearLayout waiting, connected, permission;
    private TextView code, thisTv, status;
    private Button showOn, disconnect, allow;
    private List<Inputs.Input> inputs;
    private boolean resumed;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (resumed) main.postDelayed(this, STATUS_MS);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = new Prefs(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Look.background());

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(col, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // the brand: logo, name in the sunset gradient, "TV" in neon, the header's sunset line
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView name = Look.text(this, "VI TRANSLATOR", 44, 800, Look.TEXT);
        Look.sunset(name);
        brand.addView(name);
        TextView tv = Look.text(this, "TV", 44, 800, Look.NEON);
        Look.neon(tv, Look.NEON);
        // (the two glows' room overlaps: one word gap between them)
        LinearLayout.LayoutParams tvLp = wrap();
        tvLp.setMarginStart(-dp(26));
        brand.addView(tv, tvLp);
        col.addView(brand, wrap());
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(dp(520), dp(3));
        lineLp.topMargin = dp(10);
        lineLp.bottomMargin = dp(34);
        col.addView(Look.line(this), lineLp);

        // not connected: the code
        waiting = column();
        waiting.addView(Look.text(this, getString(R.string.enter_code), 22, 400, Look.TEXT), wrap());
        code = Look.text(this, "", 96, 800, Look.NEON);
        Look.neon(code, Look.NEON);
        code.setLetterSpacing(0.12f);
        code.setTextDirection(View.TEXT_DIRECTION_LTR);
        waiting.addView(code, wrap());
        waiting.addView(Look.text(this, getString(R.string.where_on_pc), 16, 400, Look.MUTED), wrap());
        thisTv = Look.text(this, "", 14, 400, Look.MUTED);
        thisTv.setPadding(0, dp(18), 0, 0);
        waiting.addView(thisTv, wrap());
        col.addView(waiting, wrap());

        // connected: the connection, Show on, Disconnect
        connected = column();
        status = Look.text(this, "", 26, 650, Look.TEXT);
        status.setCompoundDrawablePadding(dp(14));
        connected.addView(status, wrap());
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        // (lined up by their boxes, not their text: by text, the row pushes them past its own bottom)
        buttons.setBaselineAligned(false);
        // (room for the focused button, drawn a little bigger)
        buttons.setPadding(dp(12), dp(8), dp(12), dp(8));
        buttons.setClipToPadding(false);
        showOn = Look.button(this, "");
        showOn.setOnClickListener(v -> nextShowOn());
        disconnect = Look.button(this, getString(R.string.disconnect));
        disconnect.setOnClickListener(v -> disconnect());
        LinearLayout.LayoutParams bl = wrap();
        // (side gaps only: a top margin on a button in a centred row is counted twice and pushes it down)
        bl.setMargins(dp(10), 0, dp(10), 0);
        buttons.addView(showOn, bl);
        buttons.addView(disconnect, new LinearLayout.LayoutParams(bl));
        LinearLayout.LayoutParams rl = wrap();
        rl.topMargin = dp(30);
        connected.addView(buttons, rl);
        col.addView(connected, wrap());

        // "Display over other apps" not allowed yet
        permission = column();
        permission.setBackground(Look.card(this));
        permission.setPadding(dp(28), dp(18), dp(28), dp(18));
        permission.addView(Look.text(this, getString(R.string.permission_needed), 17, 400, Look.WARN), wrap());
        allow = Look.button(this, getString(R.string.allow));
        allow.setOnClickListener(v -> askPermission());
        LinearLayout.LayoutParams al = wrap();
        al.topMargin = dp(12);
        permission.addView(allow, al);
        TextView adb = Look.text(this, getString(R.string.adb_hint, "adb shell appops set " + getPackageName() + " SYSTEM_ALERT_WINDOW allow"), 13, 400, Look.MUTED);
        adb.setPadding(0, dp(12), 0, 0);
        permission.addView(adb, wrap());
        LinearLayout.LayoutParams pl = wrap();
        pl.topMargin = dp(30);
        col.addView(permission, pl);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        inputs = Inputs.hdmi(this);
        render();
        main.post(tick);
        // (the code listener starts a moment after: the code shows as soon as it's there)
        main.postDelayed(this::refresh, 250);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        main.removeCallbacks(tick);
        // the PC is often on another input of this TV: keep waiting a few minutes, with the same code
        if (!prefs.connected()) PairService.linger(this);
    }

    /** Which part shows, for whether this TV is connected; the code listener runs only while not. */
    private void render() {
        boolean on = prefs.connected();
        waiting.setVisibility(on ? View.GONE : View.VISIBLE);
        connected.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            PairService.stop(this);
            showOn.setVisibility(inputs.isEmpty() ? View.GONE : View.VISIBLE);
            showOn.setText(getString(R.string.show_on, showOnLabel()));
            if (!disconnect.hasFocus() && !showOn.hasFocus()) (inputs.isEmpty() ? disconnect : showOn).requestFocus();
        } else {
            thisTv.setText(getString(R.string.this_tv, PairServer.deviceName(this)));
            PairService.start(this);
            code.setText(spaced(PairService.code));
        }
        refresh();
    }

    /** The connection light and text, the permission card, and the overlay kept running. */
    private void refresh() {
        boolean allowed = Settings.canDrawOverlays(this);
        permission.setVisibility(allowed ? View.GONE : View.VISIBLE);
        if (!allowed && !allow.hasFocus() && !prefs.connected()) allow.requestFocus();
        if (!prefs.connected()) {
            // (the PC removed this TV meanwhile: back to a code)
            if (connected.getVisibility() == View.VISIBLE) render();
            String c = spaced(PairService.code);
            if (!c.contentEquals(code.getText())) code.setText(c);
            return;
        }
        if (waiting.getVisibility() == View.VISIBLE) {
            render();
            return;
        }
        if (allowed && !OverlayService.running) OverlayService.start(this);
        String pc = prefs.pcName();
        int color;
        String text;
        switch (OverlayService.state) {
            case OK:
                color = Look.NEON;
                text = getString(R.string.connected_to, pc);
                break;
            case OVERLAYS_OFF:
                color = Look.WARN;
                text = getString(R.string.overlays_off, pc);
                break;
            default:
                color = Look.ORANGE;
                text = getString(R.string.looking_for, pc);
        }
        status.setText(text);
        status.setCompoundDrawablesRelativeWithIntrinsicBounds(Look.dot(this, color), null, null, null);
    }

    private void disconnect() {
        String pc = prefs.pc(), token = prefs.token();
        new Thread(() -> Pc.forget(pc, token)).start();
        prefs.disconnect();
        OverlayService.stop(this);
        render();
    }

    /** Show on: every input, then each HDMI input in turn. */
    private void nextShowOn() {
        String now = prefs.showOn();
        int i = -1;
        for (int k = 0; k < inputs.size(); k++) if (inputs.get(k).deviceId.equals(now)) i = k;
        String next = i + 1 < inputs.size() ? inputs.get(i + 1).deviceId : "";
        prefs.setShowOn(next);
        showOn.setText(getString(R.string.show_on, showOnLabel()));
    }

    private String showOnLabel() {
        String id = prefs.showOn();
        for (Inputs.Input in : inputs) if (in.deviceId.equals(id)) return in.label;
        return getString(R.string.all_inputs);
    }

    private void askPermission() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (ActivityNotFoundException ignored) {
                // (no such page on this TV: the adb command shown does it)
            }
        }
    }

    /** "482913" as "482 913" (easier to read from the sofa). */
    private static String spaced(String c) {
        return c == null || c.length() != 6 ? "" : c.substring(0, 3) + " " + c.substring(3);
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        return l;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Look.dp(this, v);
    }
}
