package org.linphone.compatibility;

import static org.linphone.compatibility.Compatibility.INTENT_ANSWER_CALL_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.INTENT_HANGUP_CALL_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.INTENT_NOTIF_ID;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.linphone.notifications.NotificationBroadcastReceiver;

import androidx.core.app.NotificationCompat;

import org.pniei.portal.R;

@androidx.annotation.RequiresApi(29)
public class ApiTwentyNinePlus {

    public static NotificationCompat.Action getCallAnswerAction(Context context, int callId) {
        Intent answerIntent = new Intent(context, NotificationBroadcastReceiver.class);
        answerIntent.setAction(INTENT_ANSWER_CALL_NOTIF_ACTION);
        answerIntent.putExtra(INTENT_NOTIF_ID, callId);

        PendingIntent answerPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            answerPendingIntent = PendingIntent.getBroadcast(context, callId, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            answerPendingIntent = PendingIntent.getBroadcast(context, callId, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        return new NotificationCompat.Action.Builder(R.drawable.ic_call, context.getString(R.string.notification_call_answer_label), answerPendingIntent).build();
    }

    public static NotificationCompat.Action getCallDeclineAction(Context context, int callId) {
        Intent hangupIntent = new Intent(context, NotificationBroadcastReceiver.class);
        hangupIntent.setAction(INTENT_HANGUP_CALL_NOTIF_ACTION);
        hangupIntent.putExtra(INTENT_NOTIF_ID, callId);

        PendingIntent hangupPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hangupPendingIntent = PendingIntent.getBroadcast(context, callId, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            hangupPendingIntent = PendingIntent.getBroadcast(context, callId, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        return new NotificationCompat.Action.Builder(R.drawable.ic_call_end, context.getString(R.string.notification_call_hangup_label), hangupPendingIntent).build();
    }
}
