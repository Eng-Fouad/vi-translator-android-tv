package app.vitranslator.tv

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app's language: the TV's own, or one the user picked in the app (the desktop app's 33
 * interface languages, by their own names). Applied to the screen and the services' notifications.
 */
object Lang {
    /** A language: the tag Android looks up resources with, and its name in itself. */
    data class Choice(val tag: String, val name: String)

    /** In the desktop app's order. (Chinese with its region, so Android before 7.0 finds it too.) */
    val ALL = listOf(
        Choice("en", "English"),
        Choice("ar", "العربية"),
        Choice("hi", "हिन्दी"),
        Choice("bn", "বাংলা"),
        Choice("ur", "اردو"),
        Choice("id", "Bahasa Indonesia"),
        Choice("tr", "Türkçe"),
        Choice("vi", "Tiếng Việt"),
        Choice("fa", "فارسی"),
        Choice("th", "ไทย"),
        Choice("ta", "தமிழ்"),
        Choice("te", "తెలుగు"),
        Choice("mr", "मराठी"),
        Choice("uk", "Українська"),
        Choice("nl", "Nederlands"),
        Choice("fil", "Filipino"),
        Choice("ms", "Bahasa Melayu"),
        Choice("sw", "Kiswahili"),
        Choice("ro", "Română"),
        Choice("el", "Ελληνικά"),
        Choice("cs", "Čeština"),
        Choice("fr", "Français"),
        Choice("it", "Italiano"),
        Choice("de", "Deutsch"),
        Choice("ru", "Русский"),
        Choice("ja", "日本語"),
        Choice("pl", "Polski"),
        Choice("ko", "한국어"),
        Choice("zh-Hans-CN", "简体中文"),
        Choice("zh-Hant-TW", "繁體中文"),
        Choice("es-MX", "Español (México)"),
        Choice("es-ES", "Español (España)"),
        Choice("pt-BR", "Português (Brasil)"),
    )

    /** The context in the chosen language (the TV's own when none was chosen). */
    fun wrap(base: Context): Context {
        val tag = Prefs(base).language
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(locale)
        conf.setLayoutDirection(locale)
        return base.createConfigurationContext(conf)
    }

    /** The chosen language's name, or null when the TV's own is used. */
    fun chosenName(c: Context): String? = Prefs(c).language.let { t -> ALL.firstOrNull { it.tag == t }?.name }
}
