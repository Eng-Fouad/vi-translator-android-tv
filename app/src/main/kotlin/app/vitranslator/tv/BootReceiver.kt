package app.vitranslator.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The TV turned on: the overlay comes back if this TV is connected to a PC. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs(c).connected && Compat.canDrawOverlays(c)) OverlayService.start(c)
    }
}
