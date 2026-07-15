package org.linphone.compatibility;

import org.linphone.mediastream.Version;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Html;
import android.text.Spanned;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

public class Compatibility {
	public static final String CHAT_NOTIFICATIONS_GROUP = "CHAT_NOTIF_GROUP";
	public static final String KEY_TEXT_REPLY = "key_text_reply";
	public static final String INTENT_NOTIF_ID = "NOTIFICATION_ID";
	public static final String INTENT_REPLY_NOTIF_ACTION = "REPLY_ACTION";
	public static final String INTENT_HANGUP_CALL_NOTIF_ACTION = "HANGUP_CALL_ACTION";
	public static final String INTENT_ANSWER_CALL_NOTIF_ACTION = "ANSWER_CALL_ACTION";
	public static final String INTENT_LOCAL_IDENTITY = "LOCAL_IDENTITY";
	public static final String INTENT_MARK_AS_READ_ACTION = "MARK_AS_READ_ACTION";


	public static void CreateChannel(Context context) {
		if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
			ApiTwentySixPlus.CreateChannel(context);
		}
	}
/*	public static Notification createSimpleNotification(Context context, String title, String text, PendingIntent intent) {
		if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
			return ApiTwentySixPlus.createSimpleNotification(context, title, text, intent);
		} else if (Version.sdkAboveOrEqual(Version.API21_LOLLIPOP_50)) {
			return ApiTwentyOnePlus.createSimpleNotification(context, title, text, intent);
		} else if (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41)) {
			return ApiSixteenPlus.createSimpleNotification(context, title, text, intent);
		} else {
			return ApiElevenPlus.createSimpleNotification(context, title, text, intent);
		}
	}*/
	public static Notification createMissedCallNotification(Context context, String title, String text, PendingIntent intent) {
		if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
			return ApiTwentySixPlus.createMissedCallNotification(context, title, text, intent);
		} else if (Version.sdkAboveOrEqual(Version.API21_LOLLIPOP_50)) {
			return ApiTwentyOnePlus.createMissedCallNotification(context, title, text, intent);
		} else if (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41)) {
			return ApiSixteenPlus.createMissedCallNotification(context, title, text, intent);
		} else  {
			return ApiElevenPlus.createMissedCallNotification(context, title, text, intent);
		}
	}

	public static Notification createMessageNotification(Context context, int msgCount, String to, String msgSender, String msg, Bitmap contactIcon, PendingIntent intent) {
		if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
			return ApiTwentySixPlus.createMessageNotification(context, msgCount, msgSender, msg, contactIcon, intent);
		} else if (Version.sdkAboveOrEqual(Version.API21_LOLLIPOP_50)) {
			return ApiTwentyOnePlus.createMessageNotification(context, msgCount, msgSender, msg, contactIcon, intent);
		} else if (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41)) {
			return ApiSixteenPlus.createMessageNotification(context, msgCount, msgSender, msg, contactIcon, intent);
		} else {
			return ApiElevenPlus.createMessageNotification(context, msgCount, msgSender, msg, contactIcon, intent);
		}
	}

	public static Notification createInCallNotification(Context context, String title, String msg, int iconID, Bitmap contactIcon, String contactName, PendingIntent intent) {
		if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
			return ApiTwentySixPlus.createInCallNotification(context, title, msg, iconID, contactIcon, contactName, intent);
		} else if (Version.sdkAboveOrEqual(Version.API21_LOLLIPOP_50)) {
			return ApiTwentyOnePlus.createInCallNotification(context, title, msg, iconID, contactIcon, contactName, intent);
		} else if (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41)) {
			return ApiSixteenPlus.createInCallNotification(context, title, msg, iconID, contactIcon, contactName, intent);
		} else {
			return ApiElevenPlus.createInCallNotification(context, title, msg, iconID, contactIcon, contactName, intent);
		}
	}

	public static Notification createNotification(Context context, String title, String message, int icon, int iconLevel, Bitmap largeIcon, PendingIntent intent, int priority, boolean isOngoingEvent) {
		if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
			return ApiTwentySixPlus.createNotification(context, title, message, icon, iconLevel, largeIcon, intent, isOngoingEvent,priority);
		} else if (Version.sdkAboveOrEqual(Version.API21_LOLLIPOP_50)) {
			return ApiTwentyOnePlus.createNotification(context, title, message, icon, iconLevel, largeIcon, intent, isOngoingEvent,priority);
		} else if (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41)) {
			return ApiSixteenPlus.createNotification(context, title, message, icon, iconLevel, largeIcon, intent, isOngoingEvent,priority);
		} else {
			return ApiElevenPlus.createNotification(context, title, message, icon, iconLevel, largeIcon, intent, isOngoingEvent);
		}
	}

	public static CompatibilityScaleGestureDetector getScaleGestureDetector(Context context, CompatibilityScaleGestureListener listener) {
		if (Version.sdkAboveOrEqual(Version.API08_FROYO_22)) {
			CompatibilityScaleGestureDetector csgd = new CompatibilityScaleGestureDetector(context);
			csgd.setOnScaleListener(listener);
			return csgd;
		}
		return null;
	}

	public static Notification createIncomingCallNotification(
			Context context,
			int callId,
			Bitmap contactIcon,
			String contactName,
			String sipUri,
			PendingIntent intent) {
		if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
			return ApiTwentySixPlus.createIncomingCallNotification(
					context, callId, contactIcon, contactName, sipUri, intent);
		} else if (Version.sdkAboveOrEqual(Version.API24_NOUGAT_70)) {
			return ApiTwentyFourPlus.createIncomingCallNotification(
					context, callId, contactIcon, contactName, sipUri, intent);
		}
		return ApiTwentyOnePlus.createIncomingCallNotification(
				context, contactIcon, contactName, sipUri, intent);
	}

	public static NotificationCompat.Action getCallAnswerAction(Context context, int callId) {
		if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
			return ApiTwentyNinePlus.getCallAnswerAction(context, callId);
		} else if (Version.sdkAboveOrEqual(Version.API24_NOUGAT_70)) {
			return ApiTwentyFourPlus.getCallAnswerAction(context, callId);
		}
		return null;
	}

	public static NotificationCompat.Action getCallDeclineAction(Context context, int callId) {
		if (Version.sdkAboveOrEqual(Version.API29_ANDROID_10)) {
			return ApiTwentyNinePlus.getCallDeclineAction(context, callId);
		} else if (Version.sdkAboveOrEqual(Version.API24_NOUGAT_70)) {
			return ApiTwentyFourPlus.getCallDeclineAction(context, callId);
		}
		return null;
	}


	public static boolean canDrawOverlays(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			return Settings.canDrawOverlays(context);
		}
		return true;
	}

	public static boolean isAppUserRestricted(Context context) {
		if (Version.sdkAboveOrEqual(Version.API28_PIE_90)) {
			return ApiTwentyEightPlus.isAppUserRestricted(context);
		}
		return false;
	}

	public static void setTextAppearance(TextView textview, Context context, int style) {
		if (Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60)) {
			ApiTwentyThreePlus.setTextAppearance(textview, style);
		} else {
			ApiElevenPlus.setTextAppearance(textview, context, style);
		}
	}

	public static void scheduleAlarm(AlarmManager alarmManager, int type, long triggerAtMillis, PendingIntent operation) {
		if (Version.sdkAboveOrEqual(Version.API19_KITKAT_44)) {
			ApiNineteenPlus.scheduleAlarm(alarmManager, type, triggerAtMillis, operation);
		} else {
			ApiElevenPlus.scheduleAlarm(alarmManager, type, triggerAtMillis, operation);
		}
	}

	public static void startService(Context context, Intent intent) {
		if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
			ApiTwentySixPlus.startService(context, intent);
		} else {
			ApiSixteenPlus.startService(context, intent);
		}
	}
}
