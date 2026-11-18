package app.vitranslator.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** The TV turned on: the overlay comes back if this TV is connected to a PC. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (new Prefs(c).connected() && Settings.canDrawOverlays(c)) OverlayService.start(c);
    }
}
