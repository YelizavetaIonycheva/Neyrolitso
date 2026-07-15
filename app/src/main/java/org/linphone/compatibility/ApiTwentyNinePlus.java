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
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.linphone.notifications.NotificationBroadcastReceiver;

import androidx.core.app.NotificationCompat;
import org.pniei.portal.R;

@TargetApi(29)
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

        PendingIntent hangupPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hangupPendingIntent = PendingIntent.getBroadcast(context, callId, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            hangupPendingIntent = PendingIntent.getBroadcast(context, callId, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        return new NotificationCompat.Action.Builder(
                        R.drawable.ic_call_end,
                        context.getString(R.string.notification_call_hangup_label),
                        hangupPendingIntent)
                .build();
    }
}
