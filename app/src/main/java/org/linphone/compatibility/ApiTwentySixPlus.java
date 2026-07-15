package org.linphone.compatibility;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.RingtoneManager;
import android.view.ViewTreeObserver;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import org.pniei.portal.R;

@TargetApi(26)
public class ApiTwentySixPlus {

	public static void CreateChannel(Context context) {
		NotificationManager notificationManager =
				(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		// Create service notification channel
		String id = context.getString(R.string.notification_service_channel_id);
		CharSequence name = context.getString(R.string.content_title_notification_service);
		String description = context.getString(R.string.content_title_notification_service);
		int importance = NotificationManager.IMPORTANCE_LOW;
		NotificationChannel mChannel = new NotificationChannel(id, name, importance);
		mChannel.setDescription(description);
		mChannel.enableVibration(false);
		mChannel.setShowBadge(false);
		notificationManager.createNotificationChannel(mChannel);

		// Create call notification channel
		id = context.getString(R.string.notification_call_channel_id);
		name = context.getString(R.string.content_title_notification_call);
		description = context.getString(R.string.content_title_notification_call);
		importance = NotificationManager.IMPORTANCE_HIGH;
		mChannel = new NotificationChannel(id, name, importance);
		mChannel.setDescription(description);
		mChannel.enableLights(true);
		mChannel.enableVibration(true);
		mChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null);
		mChannel.setLightColor(context.getColor(R.color.notification_color_led));
		notificationManager.createNotificationChannel(mChannel);
		
		notificationManager.deleteNotificationChannel(context.getString(R.string.notification_call_channel_id_old));


		id = context.getString(R.string.notification_other_channel_id);
		name = context.getString(R.string.content_title_notification);
		description = context.getString(R.string.content_title_notification);
		importance = NotificationManager.IMPORTANCE_MIN;
		mChannel = new NotificationChannel(id, name, importance);
		mChannel.setDescription(description);
		mChannel.enableLights(true);
		mChannel.setLightColor(context.getColor(R.color.notification_color_led));
		mChannel.setShowBadge(false);
		mChannel.setSound(null, null);
		notificationManager.createNotificationChannel(mChannel);

		id = context.getString(R.string.notification_spo_message_channel_id);
		name = context.getString(R.string.content_title_notification_spo_message);
		description = context.getString(R.string.content_title_notification_spo_message);
		importance = NotificationManager.IMPORTANCE_HIGH;
		mChannel = new NotificationChannel(id, name, importance);
		mChannel.setDescription(description);
		mChannel.enableVibration(true);
		mChannel.setLightColor(context.getColor(R.color.notification_color_led));
		mChannel.enableLights(true);
		mChannel.setShowBadge(true);
		mChannel.enableVibration(true);
		mChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
		notificationManager.createNotificationChannel(mChannel);
		
		notificationManager.deleteNotificationChannel(context.getString(R.string.notification_spo_message_channel_id_old));
	
	}

	public static Notification createIncomingCallNotification(
			Context context,
			int callId,
			Bitmap contactIcon,
			String contactName,
			String sipUri,
			PendingIntent intent) {
		RemoteViews notificationLayoutHeadsUp =
				new RemoteViews(
						context.getPackageName(), R.layout.call_incoming_notification_heads_up);
		notificationLayoutHeadsUp.setTextViewText(R.id.caller, contactName);
		notificationLayoutHeadsUp.setTextViewText(R.id.sip_uri, sipUri);
		notificationLayoutHeadsUp.setTextViewText(R.id.incoming_call_info, context.getString(R.string.incall_notif_incoming));
		/*if (contactIcon != null) {
			notificationLayoutHeadsUp.setImageViewBitmap(R.id.caller_picture, contactIcon);
		}*/

		return new NotificationCompat.Builder(context, context.getString(R.string.notification_call_channel_id))
				.setStyle(new NotificationCompat.DecoratedCustomViewStyle())
				.setSmallIcon(R.drawable.ic_call)
				.setContentIntent(intent)
				.setContentTitle(contactName)
				.setContentText(context.getString(R.string.incall_notif_incoming))
				.setCategory(Notification.CATEGORY_CALL)
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.setPriority(Notification.PRIORITY_HIGH)
				.setWhen(System.currentTimeMillis())
				.setAutoCancel(false)
				.setShowWhen(true)
				.setOngoing(true)
				.setColor(context.getColor(R.color.notification_led_color))
				.setFullScreenIntent(intent, true)
				.addAction(Compatibility.getCallDeclineAction(context, callId))
				.addAction(Compatibility.getCallAnswerAction(context, callId))
				//.setCustomHeadsUpContentView(notificationLayoutHeadsUp)
				.setCustomContentView(notificationLayoutHeadsUp)
				.build();
	}

	public static Notification createMessageNotification(Context context,
	                                                     int msgCount, String msgSender, String msg, Bitmap contactIcon,
	                                                     PendingIntent intent) {
		String title;
		if (msgCount == 1) {
			title = msgSender;
		} else {
			title = context.getString(R.string.unread_messages).replace("%i", String.valueOf(msgCount));
		}

		//NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		Notification notif = null;
		notif = new Notification.Builder(context, context.getString(R.string.notification_other_channel_id))
					.setContentTitle(title)
					.setContentText(msg)
					.setSmallIcon(R.drawable.ic_message)
					.setAutoCancel(true)
					.setContentIntent(intent)
					.setDefaults(Notification.DEFAULT_SOUND
							| Notification.DEFAULT_VIBRATE)
					.setLargeIcon(contactIcon)
					.setCategory(Notification.CATEGORY_MESSAGE)
					.setVisibility(Notification.VISIBILITY_PRIVATE)
					.setPriority(Notification.PRIORITY_HIGH)
					.setNumber(msgCount)
					.build();

		return notif;
	}

	public static Notification createInCallNotification(Context context,
	                                                    String title, String msg, int iconID, Bitmap contactIcon,
	                                                    String contactName, PendingIntent intent) {

		Notification notif = new Notification.Builder(context, context.getString(R.string.notification_other_channel_id))
				.setContentTitle(contactName)
				.setContentText(msg)
				.setSmallIcon(iconID)
				.setAutoCancel(false)
				.setContentIntent(intent)
				.setLargeIcon(contactIcon)
				.setCategory(Notification.CATEGORY_CALL)
				.setVisibility(Notification.VISIBILITY_PUBLIC)
				.setPriority(Notification.PRIORITY_MIN)
				.build();

		return notif;
	}

	public static Notification createNotification(Context context, String title, String message, int icon, int level, Bitmap largeIcon, PendingIntent intent, boolean isOngoingEvent,int priority) {
		Notification notif;

		if (largeIcon != null) {
			notif = new Notification.Builder(context, context.getString(R.string.notification_service_channel_id))
					.setContentTitle(title)
					.setContentText(message)
					.setSmallIcon(icon, level)
					.setLargeIcon(largeIcon)
					.setContentIntent(intent)
					.setCategory(Notification.CATEGORY_SERVICE)
					.setVisibility(Notification.VISIBILITY_SECRET)
					.setPriority(priority)
					.build();
		} else {
			notif = new Notification.Builder(context, context.getString(R.string.notification_service_channel_id))
					.setContentTitle(title)
					.setContentText(message)
					.setSmallIcon(icon, level)
					.setContentIntent(intent)
					.setCategory(Notification.CATEGORY_SERVICE)
					.setVisibility(Notification.VISIBILITY_SECRET)
					.setPriority(priority)
					.build();
		}

		return notif;
	}

	public static void removeGlobalLayoutListener(ViewTreeObserver viewTreeObserver, ViewTreeObserver.OnGlobalLayoutListener keyboardListener) {
		viewTreeObserver.removeOnGlobalLayoutListener(keyboardListener);
	}

	public static Notification createMissedCallNotification(Context context, String title, String text, PendingIntent intent) {
		Notification notif = new Notification.Builder(context, context.getString(R.string.notification_other_channel_id))
				.setContentTitle(title)
				.setContentText(text)
				.setSmallIcon(R.drawable.ic_call_missed)
				.setAutoCancel(true)
				.setContentIntent(intent)
				.setDefaults(Notification.DEFAULT_SOUND
						| Notification.DEFAULT_VIBRATE)
				.setCategory(Notification.CATEGORY_MESSAGE)
				.setVisibility(Notification.VISIBILITY_PRIVATE)
				.setPriority(Notification.PRIORITY_HIGH)
				.build();

		return notif;
	}

	public static Notification createSimpleNotification(Context context, String title, String text, PendingIntent intent) {
		Notification notif = new Notification.Builder(context, context.getString(R.string.notification_other_channel_id))
				.setContentTitle(title)
				.setContentText(text)
				.setSmallIcon(R.mipmap.ic_launcher)
				.setAutoCancel(true)
				.setContentIntent(intent)
				.setDefaults(Notification.DEFAULT_SOUND
						| Notification.DEFAULT_VIBRATE)
				.setCategory(Notification.CATEGORY_MESSAGE)
				.setVisibility(Notification.VISIBILITY_PRIVATE)
				.setPriority(Notification.PRIORITY_HIGH)
				.build();

		return notif;
	}

	public static void startService(Context context, Intent intent) {
		context.startForegroundService(intent);
	}
}
