package org.pniei.portal.database;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DBUtils {
    private static final String TAG = "ManagerQueryDB";
    public static final String DB_FILE_TT = "/DB_TT.db";
    public static final String DB_FILE_P = "/DB_PORTAL.db";
    public static final String DB_HISTORY_FILE_TT = "/history_call_tt.db";
    public static final String DB_HISTORY_FILE_P = "/history_call_portal.db";

    private DBUtils() {
    }

    public static native void setDataBasePath(String path, byte[] key);

    public static native void closeDB();

    private synchronized static native void saveDB();

    public static void deleteDB(Context context) {
        String[] files = {
                DB_FILE_TT,
                DB_HISTORY_FILE_TT,
                DB_FILE_P,
                DB_HISTORY_FILE_P
        };

        for (String fileName : files) {
            deleteFileSafely(context, fileName);
        }
    }

    private static void deleteFileSafely(Context context, String fileName) {
        File file = new File(context.getFilesDir(), fileName);
        if (file.exists()) {
            if (file.delete()) {
                Log.d(TAG, "Deleted: " + fileName);
            } else {
                Log.w(TAG, "Failed to delete: " + fileName);
            }
        } else {
            Log.d(TAG, "File doesn't exist: " + fileName);
        }
    }

    public synchronized static void saveDataBase() {
        Log.d(TAG, "saveDataBase");
        saveDB();
    }

    /* SpoChatRoom */
    public native static SpoChatRoom getChatRoom(long id);

    public native static SpoChatRoom getChatRoomForIdUser(String idUser);

    public native static SpoChatRoom[] getChatList();

    public static SpoChatRoom getChatRoomForIdUser(List<String> idUsers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < idUsers.size() - 1; i++) {
            sb.append(idUsers.get(i));
            sb.append(",");
        }
        sb.append(idUsers.get(idUsers.size() - 1));

        return getChatRoomForIdUser(sb.toString());
    }

    public native static long saveChatRoom(SpoChatRoom cr);

    public native static void updateChatRoom(SpoChatRoom cr);

    public native static void deletechatroom(SpoChatRoom cr);

    public static void deleteChatRoom(SpoChatRoom cr) {
        deleteAllMessages(cr.getId());
        deletechatroom(cr);
    }

    /* SpoChatMessage */
    public native static int getUnreadMessagesCount(long idChatRoom);

    public native static void setReadStatusMessages(long idChatRoom);

    public native static SpoChatMessage getChatMessageById(long idMessage);

    public native static SpoChatMessage[] getSpoChatMessageRange(long idChatRoom, int startm, int endm, boolean isDescent);

    public native static SpoChatMessage[] getWaitingSpoChatMessages();

    public native static SpoChatMessage[] getWaitingSpoChatMessagesForCharRoom(long idChatRoom);

    public native static int getNumMessage(long idChatRoom);

    public native static long saveMessage(SpoChatMessage m);

    public native static void updateMessage(SpoChatMessage m);

    public native static void deleteMessage(SpoChatMessage m);

    public native static void deleteAllMessages(long idChatRoom);

    public static SpoChatMessage[] getSpoChatMessagesRange(long idChatRoom, int limit, boolean isDescent) {
        return getSpoChatMessagesRange(idChatRoom, 0, limit - 1, isDescent);
    }

    public static SpoChatMessage[] getSpoChatMessagesRange(long idChatRoom, int limit) {
        return getSpoChatMessagesRange(idChatRoom, 0, limit - 1, false);
    }

    public static SpoChatMessage[] getSpoChatMessagesRange(long idChatRoom, int startm, int endm, boolean isDescent) {
        SpoChatMessage[] messages = getSpoChatMessageRange(idChatRoom, startm, endm, isDescent);

        for (SpoChatMessage message : messages) {
            if (message.getTypeContent() == SpoChatMessage.FILE) {
                message.setSpoFiles(new ArrayList<>(Arrays.asList(getSpoFiles(message.getId()))));
            }
        }

        return messages;
    }

    /*  SpoContact */
    public native static SpoContact getContact(long id);

    public native static SpoContact getContactForNumber(String number);

    public native static SpoContact getContactForIdUser(String idUser);

    public native static SpoContact[] getContactList();

    public native static SpoContact[] getSearchContact(String s);

    public native static long saveContact(SpoContact c);

    public native static void updateContact(SpoContact c);

    public native static void deleteContact(SpoContact c);

    /* SpoFile */
    public native static SpoFile[] getSpoFiles(long idMessage);

    public native static SpoFile getSpoFile(long id);

    public native static long saveFile(SpoFile f);

    public native static void updateFile(SpoFile f);

    public native static void deleteFile(SpoFile f);

}
