package org.aarchdroid.codehackide;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;


public class MyNot extends Service {

    private static final String TAG = "MyNot";

    @Override
    public void onCreate() {
        super.onCreate();

        Intent notificationIntent = new Intent(getApplicationContext(), org.aarchdroid.codehackide.MainActivityCodeHackIDE.class);

        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        int notificationId = 2;
        String channelId = "channel-02";
        String channelName = "CodeHACKIDE";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    channelId, channelName, importance);
            notificationManager.createNotificationChannel(mChannel);
        }

        Notification notification = new NotificationCompat.Builder(this)
                .setCategory(Notification.CATEGORY_PROMO)
                .setContentTitle("CodeHACK-IDE Running")
                .setContentText("Running in background")
                .setSubText("Close your project to stop")
                .setSmallIcon(R.drawable.codehack)
                .setChannelId(channelId)
                .setPriority(importance)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .build();

        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(2, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(2, notification);
            }
            Log.d(TAG, "startForeground succeeded");
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed: " + e.getMessage(), e);
            notificationManager.notify(2, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

}
