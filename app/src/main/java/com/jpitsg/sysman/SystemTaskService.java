package com.jpitsg.sysman;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SystemTaskService extends Service {
    private static final String EXTRA_TASK_ID = "task_id";
    private static final String EXTRA_REASON = "reason";
    private static final String EXTRA_RESCHEDULE = "reschedule";
    private static final String CHANNEL_ID = "system_manager_task_service";
    private static final int NOTIFICATION_ID = 0x5301;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean stopping;
    private ExecutorService executor;
    private String activeTaskId = TaskIds.GPS_POST;
    private String activeReason = "unknown";

    static void startTask(Context context, String taskId, String reason, boolean reschedule) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, SystemTaskService.class);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        intent.putExtra(EXTRA_REASON, reason);
        intent.putExtra(EXTRA_RESCHEDULE, reschedule);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent);
        } else {
            app.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        ensureNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String taskId = intent == null ? TaskIds.GPS_POST : intent.getStringExtra(EXTRA_TASK_ID);
        String reason = intent == null ? "unknown" : intent.getStringExtra(EXTRA_REASON);
        boolean reschedule = intent != null && intent.getBooleanExtra(EXTRA_RESCHEDULE, false);
        if (taskId == null) {
            taskId = TaskIds.GPS_POST;
        }
        if (reason == null) {
            reason = "unknown";
        }

        if (reschedule && TaskIds.GPS_POST.equals(taskId)) {
            AlarmScheduler.scheduleGpsPost(this, "after-start");
        }

        if (!running.compareAndSet(false, true)) {
            LogStore.append(this, "service", "Task already running; ignored " + taskId + " reason=" + reason);
            return START_NOT_STICKY;
        }
        stopping = false;
        startForegroundForTask(taskId, reason);

        final String finalTaskId = taskId;
        final String finalReason = reason;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                runTask(finalTaskId, finalReason);
            }
        });
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running.set(false);
        stopping = true;
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }

    private void runTask(String taskId, String reason) {
        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                long holdMillis = Math.max(120_000L, Config.get(this).locationTimeoutSeconds() * 1000L + 45_000L);
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SystemManager:" + taskId);
                wakeLock.acquire(holdMillis);
            }
            SystemTask task = TaskRegistry.create(taskId);
            if (task == null) {
                LogStore.append(this, "service", "Unknown task: " + taskId);
                return;
            }
            LogStore.append(this, "service", "Starting task " + taskId + " reason=" + reason);
            TaskResult result = task.run(this, reason);
            LogStore.append(this, "service", "Task " + taskId + " " + (result.success ? "succeeded" : "failed") + ": " + result.message);
        } catch (Exception e) {
            LogStore.append(this, "service", "Task crashed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            stopping = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelf();
        }
    }

    private void startForegroundForTask(String taskId, String reason) {
        activeTaskId = taskId;
        activeReason = reason;
        ensureNotificationChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_system_manager)
                .setContentTitle("System Manager")
                .setContentText("Running " + taskId + " (" + reason + ")")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void ensureNotificationChannel() {
        ServiceNotifications.ensureChannel(
                this,
                CHANNEL_ID,
                "System task runner",
                "Short-lived service notification for System Manager background tasks.",
                NotificationManager.IMPORTANCE_MIN);
    }
}
