package app.vitranslator.tv

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/** The desktop app's Vice City look (its dark theme): night purple, a sunset gradient, neon teal. */
object Look {
    const val BG = 0xFF0F0B17.toInt()
    const val BG_GLOW = 0xFF3A1236.toInt()
    const val PANEL = 0xFF20182B.toInt()
    const val TEXT = 0xFFF1EBF8.toInt()
    const val MUTED = 0xFFA193B5.toInt()
    const val PINK = 0xFFFF5AA5.toInt()
    const val ORANGE = 0xFFFF9A4A.toInt()
    const val NEON = 0xFF3EF0D8.toInt()
    const val WARN = 0xFFFFB35C.toInt()

    private var fonts: Triple<Typeface, Typeface, Typeface>? = null

    /** Changa (the desktop app's font, Latin and Arabic) at a weight from its variable axis. */
    fun font(c: Context, weight: Int): Typeface {
        val (regular, bold, black) = fonts ?: Triple(load(c, 400), load(c, 650), load(c, 800)).also { fonts = it }
        return when {
            weight >= 800 -> black
            weight >= 600 -> bold
            else -> regular
        }
    }

    private fun load(c: Context, weight: Int): Typeface = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Typeface.Builder(c.assets, "changa.ttf").setFontVariationSettings("'wght' $weight").build()
        } else {
            // (before Android 8.0, one weight of the font, made bold by Android for the bold ones)
            Typeface.createFromAsset(c.assets, "changa.ttf").let { if (weight >= 600) Typeface.create(it, Typeface.BOLD) else it }
        }
    } catch (e: RuntimeException) {
        if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** The screen: night sky fading to a sunset glow at the bottom. */
    fun background(): Drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG, BG, 0xFF1C1030.toInt(), BG_GLOW))

    fun text(c: Context, s: CharSequence, sp: Float, weight: Int, color: Int) = TextView(c).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = font(c, weight)
        setTextColor(color)
        gravity = Gravity.CENTER
    }

    /** Text painted with the sunset gradient (pink to orange), with a soft pink glow. */
    fun sunset(t: TextView) {
        glowRoom(t)
        t.measure(0, 0)
        val w = max(1f, t.paint.measureText(t.text.toString()))
        val left = t.paddingLeft.toFloat()
        t.paint.shader = LinearGradient(left, 0f, left + w, 0f, PINK, ORANGE, Shader.TileMode.CLAMP)
        t.setShadowLayer(dp(t.context, 14f).toFloat(), 0f, 0f, 0x88FF5AA5.toInt())
    }

    /** Neon: the colour with a glow of itself. */
    fun neon(t: TextView, color: Int) {
        t.setTextColor(color)
        t.setShadowLayer(dp(t.context, 18f).toFloat(), 0f, 0f, (color and 0x00FFFFFF) or 0xAA000000.toInt())
        glowRoom(t)
    }

    /** Room around glowing text, so the glow fades out instead of ending at the text's edges. */
    private fun glowRoom(t: TextView) {
        val g = dp(t.context, 18f)
        t.setPadding(t.paddingLeft + g, t.paddingTop + g, t.paddingRight + g, t.paddingBottom + g)
    }

    /** A thin sunset line, like the desktop app's header. */
    fun line(c: Context) = View(c).apply {
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0x00FF5AA5, PINK, ORANGE, 0x00FF9A4A))
    }

    /** A pill button: dark with a pink edge, and the sunset gradient when the remote is on it. */
    fun button(c: Context, label: CharSequence) = Button(c).apply {
        text = label
        isAllCaps = false
        typeface = font(c, 650)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(Color.WHITE, TEXT)))
        // a full pill whatever the button's height (a radius over half the height is drawn as a half circle)
        val h = dp(c, 60f)
        val r = dp(c, 500f).toFloat()
        val on = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(PINK, ORANGE)).apply { cornerRadius = r }
        val off = GradientDrawable().apply {
            setColor(PANEL)
            cornerRadius = r
            setStroke(dp(c, 2f), 0x66FF5AA5)
        }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), on)
            addState(intArrayOf(android.R.attr.state_pressed), on)
            addState(intArrayOf(), off)
        }
        stateListAnimator = null
        setPadding(dp(c, 32f), 0, dp(c, 32f), 0)
        minHeight = h
        minimumHeight = h
        includeFontPadding = false
        setOnFocusChangeListener { v, has ->
            val s = if (has) 1.06f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
    }

    /** A rounded dark card. */
    fun card(c: Context): Drawable = GradientDrawable().apply {
        setColor(0xCC17111F.toInt())
        cornerRadius = dp(c, 18f).toFloat()
        setStroke(dp(c, 1f), 0xFF2F2440.toInt())
    }

    /** A small round dot (the connection light). */
    fun dot(c: Context, color: Int): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        val s = dp(c, 12f)
        setSize(s, s)
    }

    fun dp(c: Context, v: Float): Int = (v * c.resources.displayMetrics.density).roundToInt()
}
