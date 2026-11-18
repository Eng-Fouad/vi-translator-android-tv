package app.vitranslator.tv;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * The translations over what the TV shows (the game on an HDMI input): the PC app's TV page in a
 * transparent window on top of everything. The window can't be focused or touched, so the remote
 * keeps working the TV and the input under it.
 *
 * Runs while this TV is connected. It also: hides the window when "Show on" names another input;
 * notices when the PC app removed this TV (back to showing a code); and finds the PC again when it
 * got a new address on the home network.
 */
public class OverlayService extends Service {
    private static final String CHANNEL = "overlay";
    /** after the page couldn't be loaded (the PC off, the app closed) */
    private static final long RETRY_MS = 5000;
    /** how often the PC is asked how it is */
    private static final long CHECK_MS = 10_000;
    /** how often the input on screen is looked at */
    private static final long INPUT_MS = 400;

    /** For the screen: is the overlay up, and how the PC answered last. */
    static volatile boolean running;
    static volatile Pc.State state = Pc.State.UNREACHABLE;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private WebView web;
    private String url;
    private int unreachable;
    private boolean finding;
    private final Runnable retry = this::load;
    private final Runnable check = this::check;
    private final Runnable watchInput = this::watchInput;

    static void start(Context c) {
        c.startForegroundService(new Intent(c, OverlayService.class));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, OverlayService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.notify_channel), NotificationManager.IMPORTANCE_LOW));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // (in the foreground at once, as Android asks of a service started this way)
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notify_text))
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
        Prefs prefs = new Prefs(this);
        if (!prefs.connected() || !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        show(Pc.pageUrl(prefs.pc(), prefs.token()));
        main.removeCallbacks(check);
        main.post(check);
        main.removeCallbacks(watchInput);
        main.post(watchInput);
        return START_STICKY;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void show(String pageUrl) {
        if (web == null) {
            web = new WebView(this);
            web.setBackgroundColor(Color.TRANSPARENT);
            web.setFocusable(false);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setCacheMode(WebSettings.LOAD_NO_CACHE);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                    if (req.isForMainFrame()) failed();
                }

                @Override
                public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse res) {
                    if (req.isForMainFrame()) failed();
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                    return true; // the page never goes anywhere else
                }
            });
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            windows.addView(web, lp);
            running = true;
        }
        if (!pageUrl.equals(url)) {
            url = pageUrl;
            load();
        }
    }

    private void load() {
        main.removeCallbacks(retry);
        if (web != null && url != null) web.loadUrl(url);
    }

    /** The page isn't there: nothing on screen (not an error page), and try again a little later. */
    private void failed() {
        if (web == null) return;
        web.loadUrl("about:blank");
        main.removeCallbacks(retry);
        main.postDelayed(retry, RETRY_MS);
    }

    /** "Show on": the window only while the chosen input is on screen. */
    private void watchInput() {
        if (web == null) return;
        boolean show = Inputs.showing(getContentResolver(), new Prefs(this).showOn());
        int v = show ? View.VISIBLE : View.INVISIBLE;
        if (web.getVisibility() != v) web.setVisibility(v);
        main.postDelayed(watchInput, INPUT_MS);
    }

    /** How the PC is: removed this TV (disconnect), or gone a while (look for it at a new address). */
    private void check() {
        Prefs prefs = new Prefs(this);
        String pc = prefs.pc(), token = prefs.token();
        if (pc.isEmpty()) {
            stopSelf();
            return;
        }
        new Thread(() -> {
            Pc.State s = Pc.ask(pc, token, 2000, 4000);
            main.post(() -> {
                state = s;
                if (s == Pc.State.REMOVED) {
                    prefs.disconnect();
                    stopSelf();
                    return;
                }
                unreachable = s == Pc.State.UNREACHABLE ? unreachable + 1 : 0;
                if (unreachable >= 3 && !finding) findPc(pc, token);
                main.postDelayed(check, CHECK_MS);
            });
        }).start();
    }

    private void findPc(String pc, String token) {
        finding = true;
        int port = Integer.parseInt(pc.substring(pc.lastIndexOf(':') + 1));
        new Thread(() -> {
            String found = Pc.find(this, port, token);
            main.post(() -> {
                finding = false;
                if (found != null && !found.equals(pc)) {
                    Prefs prefs = new Prefs(this);
                    prefs.setPc(found);
                    unreachable = 0;
                    show(Pc.pageUrl(found, token));
                }
            });
        }).start();
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (web != null) {
            try {
                windows.removeView(web);
            } catch (IllegalArgumentException ignored) {
                // (not added)
            }
            web.destroy();
            web = null;
        }
        url = null;
        running = false;
        state = Pc.State.UNREACHABLE;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
