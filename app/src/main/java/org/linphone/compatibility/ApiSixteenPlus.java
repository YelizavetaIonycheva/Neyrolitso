package org.linphone.compatibility;

import org.pniei.portal.R;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

@TargetApi(16)
public class ApiSixteenPlus {

	@SuppressWarnings("deprecation")
	public static Notification createMessageNotification(Context context,
			int msgCount, String msgSender, String msg, Bitmap contactIcon,
			PendingIntent intent) {
		String title;
		if (msgCount == 1) {
			title = msgSender;
		} else {
			title = context.getString(R.string.unread_messages).replace("%i", String.valueOf(msgCount));
		}

		Notification notif = new Notification.Builder(context)
			.setContentTitle(title)
			.setContentText(msg)
			.setSmallIcon(R.drawable.ic_message)
			.setAutoCancel(true)
			.setContentIntent(intent)
			.setDefaults(
					Notification.DEFAULT_LIGHTS
							| Notification.DEFAULT_SOUND
							| Notification.DEFAULT_VIBRATE)
			.setWhen(System.currentTimeMillis())
			.setLargeIcon(contactIcon)
			.setNumber(msgCount)
			.build();

		return notif;
	}

	public static Notification createInCallNotification(Context context,
			String title, String msg, int iconID, Bitmap contactIcon,
			String contactName, PendingIntent intent) {

		Notification notif = new Notification.Builder(context).setContentTitle(contactName)
						.setContentText(msg).setSmallIcon(iconID)
						.setAutoCancel(false)
						.setContentIntent(intent)
						.setWhen(System.currentTimeMillis())
						.setLargeIcon(contactIcon).build();
		notif.flags |= Notification.FLAG_ONGOING_EVENT;

		return notif;
	}

	public static Notification createNotification(Context context, String title, String message, int icon, int level, Bitmap largeIcon, PendingIntent intent, boolean isOngoingEvent,int priority) {
		Notification notif;

		if (largeIcon != null) {
			notif = new Notification.Builder(context)
	        .setContentTitle(title)
	        .setContentText(message)
	        .setSmallIcon(icon, level)
	        .setLargeIcon(largeIcon)
	        .setContentIntent(intent)
	        .setWhen(System.currentTimeMillis())
	        .setPriority(priority)
	        .build();
		} else {
			notif = new Notification.Builder(context)
	        .setContentTitle(title)
	        .setContentText(message)
	        .setSmallIcon(icon, level)
	        .setContentIntent(intent)
	        .setWhen(System.currentTimeMillis())
	        .setPriority(priority)
	        .build();
		}
		if (isOngoingEvent) {
			notif.flags |= Notification.FLAG_ONGOING_EVENT;
		}

		return notif;
	}

	public static void removeGlobalLayoutListener(ViewTreeObserver viewTreeObserver, OnGlobalLayoutListener keyboardListener) {
		viewTreeObserver.removeOnGlobalLayoutListener(keyboardListener);
	}

	public static Notification createMissedCallNotification(Context context, String title, String text, PendingIntent intent) {
		Notification notif = new Notification.Builder(context)
		.setContentTitle(title)
		.setContentText(text)
		.setSmallIcon(R.drawable.ic_call_missed)
		.setAutoCancel(true)
		.setContentIntent(intent)
		.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
		.setWhen(System.currentTimeMillis())
		.build();

		return notif;
	}

	public static Notification createSimpleNotification(Context context, String title, String text, PendingIntent intent) {
		Notification notif = new Notification.Builder(context)
		.setContentTitle(title)
		.setContentText(text)
		.setSmallIcon(R.mipmap.ic_launcher)
		.setAutoCancel(true)
		.setContentIntent(intent)
		.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
		.setWhen(System.currentTimeMillis())
		.build();

		return notif;
	}

	public static void startService(Context context, Intent intent) {
		context.startService(intent);
	}
}
