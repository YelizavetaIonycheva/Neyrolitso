package org.linphone.compatibility;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;

public class ApiNineteenPlus {
    @SuppressLint("ScheduleExactAlarm")
    public static void scheduleAlarm(AlarmManager alarmManager, int type, long triggerAtMillis, PendingIntent operation) {
        alarmManager.setExact(type, triggerAtMillis, operation);
    }
}
