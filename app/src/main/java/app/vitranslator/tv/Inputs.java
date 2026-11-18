package app.vitranslator.tv;

import android.content.ContentResolver;
import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The TV's HDMI inputs, and which one is on screen now, for "Show on": the overlay only over one
 * input (the console), not over the TV's home screen, apps or other inputs.
 *
 * Android has no public way for an app to know the input on screen. TCL TVs keep it in two system
 * settings an app can read: the input's device id ("tcl_sys_tv_input_current_device_id") and
 * whether an input is on screen at all ("sys.livetv.act_state", 0 on the home screen or in an
 * app). On a TV without them, "Show on" isn't offered and the overlay shows everywhere.
 */
final class Inputs {
    private Inputs() {}

    static final class Input {
        /** the TV's device id of the input (as in the system setting) */
        final String deviceId;
        /** its name on this TV ("HDMI 3", or the name the user gave it, like "PS5") */
        final String label;

        Input(String deviceId, String label) {
            this.deviceId = deviceId;
            this.label = label;
        }
    }

    private static final Pattern HW = Pattern.compile("/HW(\\d+)$");

    /** The HDMI inputs, in the TV's order; empty when this TV can't say which one is on screen. */
    static List<Input> hdmi(Context ctx) {
        List<Input> out = new ArrayList<>();
        if (current(ctx.getContentResolver()) == null) return out;
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo info : tim.getTvInputList()) {
            // (the HDMI ports themselves, not the devices behind them that HDMI-CEC lists too)
            if (info.getType() != TvInputInfo.TYPE_HDMI || info.getParentId() != null) continue;
            Matcher m = HW.matcher(info.getId());
            if (!m.find()) continue;
            CharSequence label = info.loadLabel(ctx);
            out.add(new Input(m.group(1), label == null ? "HDMI" : label.toString()));
        }
        return out;
    }

    /** The input on screen now: its device id, "" when none (home screen, an app), null when unknown. */
    static String current(ContentResolver cr) {
        try {
            String act = Settings.Global.getString(cr, "sys.livetv.act_state");
            String device = Settings.Secure.getString(cr, "tcl_sys_tv_input_current_device_id");
            if (act == null || device == null) return null;
            return act.trim().equals("1") ? device.trim() : "";
        } catch (RuntimeException e) {
            return null; // (not readable on this TV)
        }
    }

    /** Should the overlay show now, for "Show on" = {@code showOn} ("" = everywhere)? */
    static boolean showing(ContentResolver cr, String showOn) {
        if (showOn.isEmpty()) return true;
        String now = current(cr);
        return now == null || now.equals(showOn);
    }
}
