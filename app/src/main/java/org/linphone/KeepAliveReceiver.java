package org.linphone;

import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.mediastream.Log;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import org.pniei.portal.R;

public class KeepAliveReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (!LinphoneService.isReady()) {
			return;
		} else {
			boolean isDebugEnabled = LinphonePreferences.instance().isDebugEnabled();
			LinphoneCoreFactory.instance().enableLogCollection(isDebugEnabled);
			LinphoneCoreFactory.instance().setDebugMode(isDebugEnabled, context.getString(R.string.app_name));
			LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
			if (lc == null) return;

			String action = intent.getAction();
			if (action == null) {
				Log.i("[KeepAlive] Refresh registers");
				lc.refreshRegisters();
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					Log.e("Cannot sleep for 2s", e);
				} finally {
					//make sure the application will at least wakes up every 10 mn
					Intent newIntent = new Intent(context, KeepAliveReceiver.class);
					PendingIntent keepAlivePendingIntent;
					keepAlivePendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_MUTABLE);

					AlarmManager alarmManager = ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
					Compatibility.scheduleAlarm(alarmManager, AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 600000, keepAlivePendingIntent);
				}
			} else if (action.equalsIgnoreCase(Intent.ACTION_SCREEN_ON)) {
				Log.i("[KeepAlive] Screen is on, enable");
				lc.enableKeepAlive(true);
			} else if (action.equalsIgnoreCase(Intent.ACTION_SCREEN_OFF)) {
				Log.i("[KeepAlive] Screen is off, disable");
				//lc.enableKeepAlive(false);
			}
		}
	}
}
