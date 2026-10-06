package art.aduning.billddesk;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public final class CaptureService extends Service implements DisplayManager.DisplayListener {
    private DeskEngine engine;
    @Override public void onCreate() {
        super.onCreate(); engine = ((DeskApplication) getApplication()).engine();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("remote", "屏幕共享", NotificationManager.IMPORTANCE_LOW));
        getSystemService(DisplayManager.class).registerDisplayListener(this, new Handler(Looper.getMainLooper()));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        Intent consent = intent.getParcelableExtra("consent");
        if (consent == null) { stopSelf(); return START_NOT_STICKY; }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, CaptureService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, "remote").setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("BilldDesk 正在共享屏幕").setContentText("远程连接需你确认，点击可查看或停止")
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null, "停止共享", stop).build()).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(1, notification);
        DisplayMetrics metrics = metrics();
        engine.startScreen(consent, metrics.widthPixels, metrics.heightPixels);
        return START_NOT_STICKY;
    }
    private DisplayMetrics metrics() {
        DisplayMetrics m = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m); return m;
    }
    @Override public void onDisplayChanged(int displayId) {
        if (displayId == android.view.Display.DEFAULT_DISPLAY) {
            DisplayMetrics m = metrics(); engine.resizeScreen(m.widthPixels, m.heightPixels);
        }
    }
    @Override public void onDisplayAdded(int id) {}
    @Override public void onDisplayRemoved(int id) {}
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        getSystemService(DisplayManager.class).unregisterDisplayListener(this);
        engine.stopSharing(); super.onDestroy();
    }
}
