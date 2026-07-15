/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.compatibility;

import static org.linphone.compatibility.Compatibility.INTENT_ANSWER_CALL_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.INTENT_HANGUP_CALL_NOTIF_ACTION;
import static org.linphone.compatibility.Compatibility.INTENT_NOTIF_ID;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

import org.linphone.notifications.NotificationBroadcastReceiver;

import androidx.core.app.NotificationCompat;
import org.pniei.portal.R;

@TargetApi(24)
class ApiTwentyFourPlus {

    public static Notification createRepliedNotification(Context context, String reply) {
        return new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_message)
                .setContentText(
                        context.getString(R.string.notification_replied_label).replace("%s", reply))
                .build();
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
        notificationLayoutHeadsUp.setTextViewText(
                R.id.incoming_call_info, context.getString(R.string.incall_notif_incoming));
        if (contactIcon != null) {
            notificationLayoutHeadsUp.setImageViewBitmap(R.id.caller_picture, contactIcon);
        }

        return new NotificationCompat.Builder(context, "")
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle(contactName)
                .setContentText(context.getString(R.string.incall_notif_incoming))
                .setContentIntent(intent)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setShowWhen(true)
                .setOngoing(true)
                .setColor(context.getColor(R.color.notification_led_color))
                .addAction(Compatibility.getCallDeclineAction(context, callId))
                .addAction(Compatibility.getCallAnswerAction(context, callId))
                .setCustomHeadsUpContentView(notificationLayoutHeadsUp)
                .setFullScreenIntent(intent, true)
                .build();
    }

    public static NotificationCompat.Action getCallAnswerAction(Context context, int callId) {
        Intent answerIntent = new Intent(context, NotificationBroadcastReceiver.class);
        answerIntent.setAction(INTENT_ANSWER_CALL_NOTIF_ACTION);
        answerIntent.putExtra(INTENT_NOTIF_ID, callId);

        PendingIntent answerPendingIntent = PendingIntent.getBroadcast(context, callId, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                        R.drawable.ic_call,
                        context.getString(R.string.notification_call_answer_label),
                        answerPendingIntent)
                .build();
    }

    public static NotificationCompat.Action getCallDeclineAction(Context context, int callId) {
        Intent hangupIntent = new Intent(context, NotificationBroadcastReceiver.class);
        hangupIntent.setAction(INTENT_HANGUP_CALL_NOTIF_ACTION);
        hangupIntent.putExtra(INTENT_NOTIF_ID, callId);

        PendingIntent hangupPendingIntent =
                PendingIntent.getBroadcast(
                        context, callId, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                        R.drawable.ic_call_end,
                        context.getString(R.string.notification_call_hangup_label),
                        hangupPendingIntent)
                .build();
    }

    public static void closeContentProviderClient(ContentProviderClient client) {
        client.close();
    }
}
