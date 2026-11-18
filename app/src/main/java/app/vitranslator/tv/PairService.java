package app.vitranslator.tv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

/**
 * Waits to be connected (the code listener, PairServer), also after the user leaves the app: the
 * PC is often on another input of this same TV, so the user notes the code, switches to the PC
 * and connects from there. It keeps the same code, and stops 10 minutes after the app was left, or
 * once connected.
 */
public class PairService extends Service implements PairServer.Listener {
    private static final String CHANNEL = "pairing";
    private static final String ACTION_LINGER = "app.vitranslator.tv.LINGER";
    /** how long it keeps waiting after the app was left */
    static final long LINGER_MS = 10 * 60_000;

    /** The code to show ("" until the listener is up). */
    static volatile String code = "";

    private final Handler main = new Handler(Looper.getMainLooper());
    private PairServer server;
    private final Runnable end = this::stopSelf;

    /** The app is on screen: wait, with no end. */
    static void start(Context c) {
        c.startForegroundService(new Intent(c, PairService.class));
    }

    /** The app was left: keep waiting a few minutes more. */
    static void linger(Context c) {
        c.startForegroundService(new Intent(c, PairService.class).setAction(ACTION_LINGER));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, PairService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.pair_channel), NotificationManager.IMPORTANCE_LOW));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.pair_notify))
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(2, n);
        }
        if (new Prefs(this).connected()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (server == null) {
            server = new PairServer(this, this);
            server.start();
        }
        main.removeCallbacks(end);
        if (intent != null && ACTION_LINGER.equals(intent.getAction())) main.postDelayed(end, LINGER_MS);
        return START_NOT_STICKY;
    }

    @Override
    public void onCode(String c) {
        code = c;
    }

    @Override
    public void onConnected(String pc, String token, String pcName) {
        main.post(() -> {
            new Prefs(this).connect(pc, token, pcName);
            OverlayService.state = Pc.State.UNREACHABLE;
            if (Settings.canDrawOverlays(this)) OverlayService.start(this);
            stopSelf();
        });
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (server != null) server.stop();
        server = null;
        code = "";
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
