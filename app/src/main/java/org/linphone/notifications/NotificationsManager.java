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

import static android.content.Context.NOTIFICATION_SERVICE;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.IOException;
import java.util.HashMap;
import org.linphone.CallActivity;
import org.linphone.CallIncomingActivity;
import org.linphone.CallOutgoingActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.pniei.portal.R;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoContact;

public class NotificationsManager {
    private static final int SERVICE_NOTIF_ID = 7;
    private static final int MISSED_CALLS_NOTIF_ID = 2;

    private final Context mContext;
    private final NotificationManager mNM;
    private final HashMap<String, Notifiable> mChatNotifMap;
    private final HashMap<String, Notifiable> mCallNotifMap;
    private int mLastNotificationId;
    private final Notification mServiceNotification;
    private int mCurrentForegroundServiceNotification;

    public NotificationsManager(Context context) {
        mContext = context;
        mChatNotifMap = new HashMap<>();
        mCallNotifMap = new HashMap<>();
        mCurrentForegroundServiceNotification = 0;

        mNM = (NotificationManager) mContext.getSystemService(NOTIFICATION_SERVICE);

        /*if (mContext.getResources().getBoolean(R.bool.keep_missed_call_notification_upon_restart)) {
            StatusBarNotification[] notifs = Compatibility.getActiveNotifications(mNM);
            if (notifs != null && notifs.length > 1) {
                for (StatusBarNotification notif : notifs) {
                    if (notif.getId() != MISSED_CALLS_NOTIF_ID) {
                        dismissNotification(notif.getId());
                    }
                }
            }
        } else {*/
            mNM.cancelAll();
        //}

        mLastNotificationId = 10; // Do not conflict with hardcoded notifications ids !

        Bitmap bm = null;
        try {
            bm = BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.ic_launcher);
        } catch (Exception e) {
            //Log.e(e);
        }

        Intent notifIntent = new Intent(mContext, MainActivity.class);
        notifIntent.putExtra("Notification", true);
        addFlagsToIntent(notifIntent);

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(mContext, SERVICE_NOTIF_ID, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(mContext, SERVICE_NOTIF_ID, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        mServiceNotification = Compatibility.createNotification(
                        mContext,
                        mContext.getString(R.string.service_name),
                        "",
                        R.drawable.ic_notify,
                        R.mipmap.ic_launcher,
                        bm,
                        pendingIntent,
                        Notification.PRIORITY_MIN,
                        true);
    }

    public void destroy() {
        if (mCurrentForegroundServiceNotification > 0) {
            mNM.cancel(mCurrentForegroundServiceNotification);
        }

        for (Notifiable notifiable : mCallNotifMap.values()) {
            mNM.cancel(notifiable.getNotificationId());
        }
    }

    private void addFlagsToIntent(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    }

    private void startForeground(Notification notification, int id) {
        if (LinphoneService.isReady()) {
            LinphoneService.instance().startForeground(id, notification);
            mCurrentForegroundServiceNotification = id;
        }
    }

    public void stopForeground() {
        if (LinphoneService.isReady()) {
            LinphoneService.instance().stopForeground(true);
            mCurrentForegroundServiceNotification = 0;
        }
    }

    public void sendNotification(int id, Notification notif) {
        mNM.notify(id, notif);
    }

    public void dismissNotification(int notifId) {
        mNM.cancel(notifId);
    }

    private boolean isServiceNotificationDisplayed() {
        return LinphonePreferences.instance().getServiceNotificationVisibility();
    }

    public void displayCallNotification(LinphoneCall call, LinphoneCall.State state) {
        if (call == null) return;

        Class callNotifIntentClass = CallActivity.class;
        if (state == LinphoneCall.State.IncomingReceived) {
            /*if (!PrefsUtils.ins().isAppBackground())
                return;*/
            callNotifIntentClass = CallIncomingActivity.class;
        } else if (state == LinphoneCall.State.OutgoingInit
                || state == LinphoneCall.State.OutgoingProgress
                || state == LinphoneCall.State.OutgoingRinging
                || state == LinphoneCall.State.OutgoingEarlyMedia) {
            callNotifIntentClass = CallOutgoingActivity.class;
        }
        Intent callNotifIntent = new Intent(mContext, callNotifIntentClass);
        callNotifIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(mContext, 0, callNotifIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(mContext, 0, callNotifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        LinphoneAddress address = call.getRemoteAddress();
        String addressAsString = address.asStringUriOnly();
        Notifiable notif = mCallNotifMap.get(addressAsString);
        if (notif == null) {
            notif = new Notifiable(mLastNotificationId);
            mLastNotificationId += 1;
            mCallNotifMap.put(addressAsString, notif);
        }

        int notificationTextId;
        int iconId;

        if(call.getState() == LinphoneCall.State.CallReleased || call.getState() == LinphoneCall.State.CallEnd) {
            if (mCurrentForegroundServiceNotification == notif.getNotificationId()) {
                stopForeground();
            }
            mNM.cancel(notif.getNotificationId());
            mCallNotifMap.remove(addressAsString);
            return;
        } else if (call.getState() == LinphoneCall.State.Paused || call.getState() == LinphoneCall.State.PausedByRemote || call.getState() == LinphoneCall.State.Pausing) {
            iconId = R.drawable.ic_call;
            notificationTextId = R.string.incall_notif_paused;
        } else if (call.getState() == LinphoneCall.State.IncomingReceived) {
            iconId = R.drawable.ic_call;
            notificationTextId = R.string.incall_notif_incoming;
        } else if (call.getState() == LinphoneCall.State.OutgoingEarlyMedia ||
                call.getState() == LinphoneCall.State.OutgoingInit ||
                call.getState() == LinphoneCall.State.OutgoingProgress ||
                call.getState() == LinphoneCall.State.OutgoingRinging) {
            iconId = R.drawable.ic_call;
            notificationTextId = R.string.incall_notif_outgoing;
        } else {
            iconId = R.drawable.ic_call;
            notificationTextId = R.string.incall_notif_active;
        }

        if (notif.getIconResourceId() == iconId
                && notif.getTextResourceId() == notificationTextId) {
            return;
        } else if (notif.getTextResourceId() == R.string.incall_notif_incoming) {
            dismissNotification(notif.getNotificationId());
        }

        notif.setIconResourceId(iconId);
        notif.setTextResourceId(notificationTextId);

        Bitmap bm = null;
        SpoContact contact = DBUtils.getContactForNumber(address.getUserName());
        String name;
        if (contact != null) {
            name = contact.getFullName();
            if (contact.getUriPhoto() != null) {
                try {
                    bm = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), Uri.parse(contact.getUriPhoto()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            name = address.getUserName();
        }

        boolean isIncoming = callNotifIntentClass == CallIncomingActivity.class;

        Notification notification;
        if (isIncoming) {
            notification =
                    Compatibility.createIncomingCallNotification(
                            mContext,
                            notif.getNotificationId(),
                            bm,
                            name,
                            addressAsString,
                            pendingIntent);
        } else {
            notification =
                    Compatibility.createInCallNotification(
                            mContext,
                            "",
                            mContext.getString(notificationTextId),
                            iconId,
                            bm,
                            name,
                            pendingIntent);
        }

        // Don't use incoming call notification as foreground service notif !
        if (!isServiceNotificationDisplayed() && !isIncoming) {
            if (LinphoneManager.getLc().getCallsNb() == 0) {
                stopForeground();
            } else {
                if (mCurrentForegroundServiceNotification == 0) {
                    if (Compatibility.isAppUserRestricted(mContext)) {
                        sendNotification(notif.getNotificationId(), notification);
                    } else {
                        startForeground(notification, notif.getNotificationId());
                    }
                } else {
                    sendNotification(notif.getNotificationId(), notification);
                }
            }
        } else {
            sendNotification(notif.getNotificationId(), notification);
        }
    }

}
