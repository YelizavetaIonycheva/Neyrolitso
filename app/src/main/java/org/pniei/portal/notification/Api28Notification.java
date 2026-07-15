package org.pniei.portal.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import org.linphone.notifications.NotificationBroadcastReceiver;
import org.pniei.portal.R;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoChatMessage;
import org.pniei.portal.database.SpoChatRoom;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.utils.Utils;
import java.io.IOException;
import static org.pniei.portal.notification.SpoNotificationsManager.CHAT_NOTIFICATIONS_GROUP;
import static org.pniei.portal.notification.SpoNotificationsManager.INTENT_MARK_AS_READ_ACTION;
import static org.pniei.portal.notification.SpoNotificationsManager.CHAT_ID;

@TargetApi(28)
public class Api28Notification {

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

        Person person = new Person.Builder().setName(userName).build();
        Notification.MessagingStyle style = new Notification.MessagingStyle(person);

        Person.Builder builder = new Person.Builder().setName(userName);
        Icon userIcon = null;

        if (uriPhoto != null && uriPhoto.length() > 0) {
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

        builder.setIcon(userIcon);
        Person user = builder.build();
        Notification.MessagingStyle.Message msg;

        if (message.getMessage().length() > 0) {
            msg = new Notification.MessagingStyle.Message(message.getMessage(), message.getDate(), user);
        } else {
            if (message.getSpoFiles().size() > 0) {
                msg = new Notification.MessagingStyle.Message("Получен файл", message.getDate(), user);
            } else {
                msg = new Notification.MessagingStyle.Message("", message.getDate(), user);
            }
        }
        style.addMessage(msg);
        int numUnreadMessage = DBUtils.getUnreadMessagesCount(chatRoom.getId());

        return new Notification.Builder(context, context.getString(R.string.notification_spo_message_channel_id))
                .setStyle(style)
                .setSmallIcon(R.drawable.ic_message)
                .setContentIntent(intent)
                .setLargeIcon(userIcon)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setGroupSummary(true)
                .setGroup(CHAT_NOTIFICATIONS_GROUP + chatRoom.getId())
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setShowWhen(true)
                .setColor(context.getColor(R.color.notification_led_color))
                .setNumber(numUnreadMessage)
                //.addAction(Compatibility.getReplyMessageAction(context, notif))
                .addAction(getMarkMessageAsReadAction(context, chatRoom))
                .build();
    }

    private static Notification.Action getMarkMessageAsReadAction(Context context, SpoChatRoom chatRoom) {
        Intent markAsReadIntent = new Intent(context, NotificationBroadcastReceiver.class);
        markAsReadIntent.setAction(INTENT_MARK_AS_READ_ACTION);
        markAsReadIntent.putExtra(CHAT_ID, chatRoom.getId());

        PendingIntent markAsReadPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            markAsReadPendingIntent = PendingIntent.getBroadcast(context, 0, markAsReadIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            markAsReadPendingIntent = PendingIntent.getBroadcast(context, 0, markAsReadIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        return new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_message),
                context.getString(R.string.notification_mark_as_read_label),
                markAsReadPendingIntent)
                .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .build();
    }
}
