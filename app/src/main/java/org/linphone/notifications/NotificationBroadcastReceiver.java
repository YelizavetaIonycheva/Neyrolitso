package org.linphone.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCoreException;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.services.SpoMessagesService;

import static org.pniei.portal.notification.SpoNotificationsManager.CHAT_ID;

import java.util.Objects;

public class NotificationBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), org.pniei.portal.notification.SpoNotificationsManager.INTENT_REPLY_NOTIF_ACTION)
                || Objects.equals(intent.getAction(), org.pniei.portal.notification.SpoNotificationsManager.INTENT_MARK_AS_READ_ACTION)) {
            long idChat = intent.getLongExtra(CHAT_ID, 0);
            if (idChat == 0)
                return;

            org.pniei.portal.database.DBUtils.setReadStatusMessages(idChat);
            org.pniei.portal.database.DBUtils.saveDataBase();

            if (intent.getAction().equals(Compatibility.INTENT_REPLY_NOTIF_ACTION)) {
                // reply action - not used
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
}