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
package org.linphone.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCoreException;
import org.pniei.portal.R;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoChatMessage;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.notification.SpoNotificationsManager;
import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.CryptUtils;
import org.pniei.portal.utils.PrefsUtils;
import static org.pniei.portal.notification.SpoNotificationsManager.CHAT_ID;

import androidx.core.app.NotificationCompat;

public class NotificationBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent.getAction().equals(SpoNotificationsManager.INTENT_REPLY_NOTIF_ACTION)
                || intent.getAction().equals(SpoNotificationsManager.INTENT_MARK_AS_READ_ACTION)) {
            long idChat = intent.getLongExtra(CHAT_ID, 0);
            if (idChat == 0)
                return;

            DBUtils.setReadStatusMessages(idChat);
            DBUtils.saveDataBase();


            if (intent.getAction().equals(Compatibility.INTENT_REPLY_NOTIF_ACTION)) {
                /*final String reply = getMessageText(intent).toString();
                if (reply == null) {
                    Log.e("[Notification Broadcast Receiver] Couldn't get reply text");
                    onError(context, notifId);
                    return;
                }

                ChatMessage msg = room.createMessage(reply);
                msg.setUserData(notifId);
                msg.addListener(
                        LinphoneContext.instance().getNotificationManager().getMessageListener());
                msg.send();
                Log.i("[Notification Broadcast Receiver] Reply sent for notif id " + notifId);*/
            } else {
                SpoMessagesService.instance().removeMessageNotification();
            }
        } else if (intent.getAction().equals(Compatibility.INTENT_ANSWER_CALL_NOTIF_ACTION)
                || intent.getAction().equals(Compatibility.INTENT_HANGUP_CALL_NOTIF_ACTION)) {
            if (!LinphoneService.isReady()) {
                return;
            }

            if (intent.getAction().equals(Compatibility.INTENT_ANSWER_CALL_NOTIF_ACTION)) {
                try {
                    LinphoneCall[] calls = LinphoneManager.getLc().getCalls();
                    for (LinphoneCall call: calls) {
                        if (call.getState() == LinphoneCall.State.IncomingReceived) {
                            LinphoneManager.getInstance().routeAudioToReceiver();

                            if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
                                LinphoneAddress address = call.getRemoteAddress();
                                SpoContact contact = DBUtils.getContactForNumber(address.getUserName());
                                if (contact != null)
                                    CryptUtils.initCryptSound(Integer.parseInt(PrefsUtils.ins().getIdTT()), Integer.parseInt(contact.getIdUser()), true);
                                else
                                    CryptUtils.initCryptSound(0, 0, false);
                            } else {
                                CryptUtils.initCryptSound(0, 0, false);
                            }

                            LinphoneManager.getLc().acceptCall(call);
                            break;
                        }
                    }

                    context.startActivity(new Intent()
                            .setClass(context, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (LinphoneCoreException e) {
                    e.printStackTrace();
                }
            } else {
                LinphoneCall[] calls = LinphoneManager.getLc().getCalls();
                for (LinphoneCall call: calls) {
                    if (call.getState() == LinphoneCall.State.IncomingReceived) {
                        LinphoneManager.getLc().terminateCall(call);
                        break;
                    }
                }
            }
        }
    }

    /*private void onError(Context context, int notifId) {
        Notification replyError =
                Compatibility.createRepliedNotification(context, context.getString(R.string.error));
        LinphoneContext.instance().getNotificationManager().sendNotification(notifId, replyError);
    }

    private CharSequence getMessageText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence(Compatibility.KEY_TEXT_REPLY);
        }
        return null;
    }*/
}
