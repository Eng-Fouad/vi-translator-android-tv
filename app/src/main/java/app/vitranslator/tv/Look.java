package app.vitranslator.tv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** The desktop app's Vice City look (its dark theme): night purple, a sunset gradient, neon teal. */
final class Look {
    private Look() {}

    static final int BG = 0xFF0F0B17;
    static final int BG_GLOW = 0xFF3A1236;
    static final int PANEL = 0xFF20182B;
    static final int TEXT = 0xFFF1EBF8;
    static final int MUTED = 0xFFA193B5;
    static final int PINK = 0xFFFF5AA5;
    static final int ORANGE = 0xFFFF9A4A;
    static final int NEON = 0xFF3EF0D8;
    static final int WARN = 0xFFFFB35C;

    private static Typeface regular, bold, black;

    /** Changa (the desktop app's font, Latin and Arabic) at a weight from its variable axis. */
    static Typeface font(Context c, int weight) {
        if (regular == null) {
            regular = load(c, 400);
            bold = load(c, 650);
            black = load(c, 800);
        }
        return weight >= 800 ? black : weight >= 600 ? bold : regular;
    }

    private static Typeface load(Context c, int weight) {
        try {
            return new Typeface.Builder(c.getAssets(), "changa.ttf").setFontVariationSettings("'wght' " + weight).build();
        } catch (RuntimeException e) {
            return weight >= 600 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        }
    }

    /** The screen: night sky fading to a sunset glow at the bottom. */
    static Drawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {BG, BG, 0xFF1C1030, BG_GLOW});
    }

    static TextView text(Context c, CharSequence s, float sp, int weight, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(font(c, weight));
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** Text painted with the sunset gradient (pink to orange), with a soft pink glow. */
    static void sunset(TextView t) {
        glowRoom(t);
        t.measure(0, 0);
        float w = Math.max(1, t.getPaint().measureText(t.getText().toString()));
        int left = t.getPaddingLeft();
        t.getPaint().setShader(new LinearGradient(left, 0, left + w, 0, PINK, ORANGE, Shader.TileMode.CLAMP));
        t.setShadowLayer(dp(t.getContext(), 14), 0, 0, 0x88FF5AA5);
    }

    /** Neon: the colour with a glow of itself. */
    static void neon(TextView t, int color) {
        t.setTextColor(color);
        t.setShadowLayer(dp(t.getContext(), 18), 0, 0, (color & 0x00FFFFFF) | 0xAA000000);
        glowRoom(t);
    }

    /** Room around glowing text, so the glow fades out instead of ending at the text's edges. */
    private static void glowRoom(TextView t) {
        int g = dp(t.getContext(), 18);
        t.setPadding(t.getPaddingLeft() + g, t.getPaddingTop() + g, t.getPaddingRight() + g, t.getPaddingBottom() + g);
    }

    /** A thin sunset line, like the desktop app's header. */
    static View line(Context c) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0x00FF5AA5, PINK, ORANGE, 0x00FF9A4A});
        v.setBackground(g);
        return v;
    }

    /** A pill button: dark with a pink edge, and the sunset gradient when the remote is on it. */
    static Button button(Context c, CharSequence label) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(font(c, 650));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}}, new int[] {Color.WHITE, TEXT}));
        // a full pill whatever the button's height (a radius over half the height is drawn as a half circle)
        int h = dp(c, 60), r = dp(c, 500);
        GradientDrawable on = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {PINK, ORANGE});
        on.setCornerRadius(r);
        GradientDrawable off = new GradientDrawable();
        off.setColor(PANEL);
        off.setCornerRadius(r);
        off.setStroke(dp(c, 2), 0x66FF5AA5);
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[] {android.R.attr.state_focused}, on);
        d.addState(new int[] {android.R.attr.state_pressed}, on);
        d.addState(new int[] {}, off);
        b.setBackground(d);
        b.setStateListAnimator(null);
        b.setPadding(dp(c, 32), 0, dp(c, 32), 0);
        b.setMinHeight(h);
        b.setMinimumHeight(h);
        b.setIncludeFontPadding(false);
        b.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start());
        return b;
    }

    /** A rounded dark card. */
    static Drawable card(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xCC17111F);
        g.setCornerRadius(dp(c, 18));
        g.setStroke(dp(c, 1), 0xFF2F2440);
        return g;
    }

    /** A small round dot (the connection light). */
    static Drawable dot(Context c, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        int s = dp(c, 12);
        g.setSize(s, s);
        return g;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
