package org.linphone.compatibility;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;

@TargetApi(19)
public class ApiNineteenPlus {
	public static void scheduleAlarm(AlarmManager alarmManager, int type, long triggerAtMillis, PendingIntent operation) {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
			alarmManager.setExact(type, triggerAtMillis, operation);
		} else {
			alarmManager.set(type, triggerAtMillis, operation);
		}
	}
}
