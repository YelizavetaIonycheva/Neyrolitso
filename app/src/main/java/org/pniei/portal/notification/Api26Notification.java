package org.pniei.portal.notification;

import static org.pniei.portal.notification.SpoNotificationsManager.CHAT_ID;
import static org.pniei.portal.notification.SpoNotificationsManager.CHAT_NOTIFICATIONS_GROUP;
import static org.pniei.portal.notification.SpoNotificationsManager.INTENT_MARK_AS_READ_ACTION;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import org.linphone.notifications.NotificationBroadcastReceiver;
import org.pniei.portal.R;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoChatMessage;
import org.pniei.portal.database.SpoChatRoom;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.utils.Utils;

import java.io.IOException;

@androidx.annotation.RequiresApi(26)
public class Api26Notification {

    public static Notification createMessageNotification(Context context, SpoChatRoom chatRoom, SpoChatMessage message, PendingIntent intent) {
        String userName, uriPhoto;
        SpoContact contact = DBUtils.getContactForIdUser(chatRoom.getIdUsers().get(0));

        if (contact != null) {
            userName = contact.getFullName();
            uriPhoto = contact.getFullName();
        } else {
            userName = chatRoom.getNameChat();
            uriPhoto = null;
        }

        Notification.MessagingStyle style = getMessagingStyle(message, userName);

        Icon userIcon = null;

        if (uriPhoto != null && !uriPhoto.isEmpty()) {
            Bitmap bm = null;
            try {
                bm = MediaStore.Images.Media.getBitmap(context.getContentResolver(), Uri.parse(uriPhoto));
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (bm != null) {
                userIcon = Icon.createWithBitmap(Utils.getCroppedBitmap(bm, 256, 256, 256));
            }
        }

        if (userIcon == null) {
            userIcon = Icon.createWithResource(context, R.drawable.ic_avatar_one);
        }

        int numUnreadMessage = DBUtils.getUnreadMessagesCount(chatRoom.getId());
        return new Notification.Builder(
                context, context.getString(R.string.notification_spo_message_channel_id))
                .setSmallIcon(R.drawable.ic_message)
                .setAutoCancel(true)
                .setContentIntent(intent)
                .setLargeIcon(userIcon)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setGroupSummary(true)
                .setGroup(CHAT_NOTIFICATIONS_GROUP + chatRoom.getId())
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setNumber(numUnreadMessage)
                .setColor(context.getColor(R.color.notification_led_color))
                .setStyle(style)
                .setAutoCancel(true)
                //.addAction(Compatibility.getReplyMessageAction(context, notif))
                .addAction(getMarkMessageAsReadAction(context, chatRoom))
                .build();
    }

    @NonNull
    private static Notification.MessagingStyle getMessagingStyle(SpoChatMessage message, String userName) {
        Notification.MessagingStyle style = new Notification.MessagingStyle(userName);
        Notification.MessagingStyle.Message msg;
        if (!message.getMessage().isEmpty()) {
            msg = new Notification.MessagingStyle.Message(message.getMessage(), message.getDate(), userName);
        } else {
            if (!message.getSpoFiles().isEmpty()) {
                msg = new Notification.MessagingStyle.Message("Получен файл", message.getDate(), userName);
            } else {
                msg = new Notification.MessagingStyle.Message("", message.getDate(), userName);
            }
        }
        style.addMessage(msg);
        return style;
    }


    private static Notification.Action getMarkMessageAsReadAction(Context context, SpoChatRoom chatRoom) {
        Intent markAsReadIntent = new Intent(context, NotificationBroadcastReceiver.class);
        markAsReadIntent.setAction(INTENT_MARK_AS_READ_ACTION);
        markAsReadIntent.putExtra(CHAT_ID, chatRoom.getId());

        PendingIntent markAsReadPendingIntent = PendingIntent.getBroadcast(context, 0, markAsReadIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_call),
                context.getString(R.string.notification_mark_as_read_label),
                markAsReadPendingIntent)
                .build();
    }

}
