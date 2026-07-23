package org.linphone.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.compatibility.Compatibility;
<<<<<<< HEAD
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCoreException;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.services.SpoMessagesService;
=======
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCoreException;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.notification.SpoNotificationsManager;
import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.PrefsUtils;
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db

import static org.pniei.portal.notification.SpoNotificationsManager.CHAT_ID;

import java.util.Objects;

public class NotificationBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
<<<<<<< HEAD
        if (Objects.equals(intent.getAction(), org.pniei.portal.notification.SpoNotificationsManager.INTENT_REPLY_NOTIF_ACTION)
                || Objects.equals(intent.getAction(), org.pniei.portal.notification.SpoNotificationsManager.INTENT_MARK_AS_READ_ACTION)) {
=======
        if (Objects.equals(intent.getAction(), SpoNotificationsManager.INTENT_REPLY_NOTIF_ACTION)
                || Objects.equals(intent.getAction(), SpoNotificationsManager.INTENT_MARK_AS_READ_ACTION)) {
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
            long idChat = intent.getLongExtra(CHAT_ID, 0);
            if (idChat == 0)
                return;

<<<<<<< HEAD
            org.pniei.portal.database.DBUtils.setReadStatusMessages(idChat);
            org.pniei.portal.database.DBUtils.saveDataBase();

            if (intent.getAction().equals(Compatibility.INTENT_REPLY_NOTIF_ACTION)) {
                // reply action - not used
=======
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
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
            } else {
                SpoMessagesService.instance().removeMessageNotification();
            }
        } else if (Objects.equals(intent.getAction(), Compatibility.INTENT_ANSWER_CALL_NOTIF_ACTION)
                || Objects.equals(intent.getAction(), Compatibility.INTENT_HANGUP_CALL_NOTIF_ACTION)) {
            if (!LinphoneService.isReady()) {
                return;
            }

            if (intent.getAction().equals(Compatibility.INTENT_ANSWER_CALL_NOTIF_ACTION)) {
                try {
                    LinphoneCall[] calls = LinphoneManager.getLc().getCalls();
                    for (LinphoneCall call : calls) {
                        if (call.getState() == LinphoneCall.State.IncomingReceived) {
                            LinphoneManager.getInstance().routeAudioToReceiver();
<<<<<<< HEAD
=======

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

>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
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
                for (LinphoneCall call : calls) {
                    if (call.getState() == LinphoneCall.State.IncomingReceived) {
                        LinphoneManager.getLc().terminateCall(call);
                        break;
                    }
                }
            }
        }
    }
<<<<<<< HEAD
}
=======

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
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
