package org.pniei.portal.database;

import java.util.ArrayList;
import java.util.List;

public class SpoChatRoom implements Comparable<SpoChatRoom> {

    public static final int ONE = 0;
    public static final int MANY = 1;

    private long mId;
    private int mType;
    private List<String> mIdUsers;
    private String mNameChat;
    private long mTimeLastMessage;
    private String mIdUsersStr;
    private String statusNote;
    private int statusInt;

    public SpoChatRoom(long id, int type, List<String> idUsers, String nameChat, long timeLastMessage) {
        mId = id;
        mType = type;
        mIdUsers = idUsers != null ? new ArrayList<>(idUsers) : new ArrayList<>();
        mNameChat = nameChat;
        mTimeLastMessage = timeLastMessage;
        mIdUsersStr = convertListToString(mIdUsers);
        statusNote = "";
        statusInt = 2;
    }

    public SpoChatRoom(long id, int type, String idUsersStr, String nameChat, long timeLastMessage) {
        mId = id;
        mType = type;
        mNameChat = nameChat;
        mTimeLastMessage = timeLastMessage;
        mIdUsersStr = idUsersStr != null ? idUsersStr : "";
        mIdUsers = convertStringToList(mIdUsersStr);
        statusNote = "";
        statusInt = 2;
    }

    public SpoChatRoom() {
        mIdUsers = new ArrayList<>();
        mIdUsersStr = "";
        statusNote = "";
        statusInt = 2;
    }

    @Override
    public int compareTo(SpoChatRoom other) {
        return Long.compare(other.mTimeLastMessage, this.mTimeLastMessage);
    }

    private String convertListToString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size() - 1; i++) {
            sb.append(list.get(i)).append(",");
        }
        sb.append(list.get(list.size() - 1));
        return sb.toString();
    }

    private List<String> convertStringToList(String str) {
        List<String> list = new ArrayList<>();
        if (str == null || str.isEmpty()) {
            return list;
        }
        if (str.contains(",")) {
            String[] parts = str.split(",");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    list.add(part);
                }
            }
        } else {
            list.add(str);
        }
        return list;
    }

    // Геттеры и сеттеры
    public long getId() {
        return mId;
    }

    public void setId(long id) {
        mId = id;
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        mType = type;
    }

    public List<String> getIdUsers() {
        return mIdUsers;
    }

    public void setIdUsers(List<String> idUsers) {
        this.mIdUsers = idUsers != null ? new ArrayList<>(idUsers) : new ArrayList<>();
        mIdUsersStr = convertListToString(this.mIdUsers);
    }

    public String getNameChat() {
        return mNameChat;
    }

    public void setNameChat(String nameChat) {
        mNameChat = nameChat;
    }

    public long getTimeLastMessage() {
        return mTimeLastMessage;
    }

    public void setTimeLastMessage(long timeLastMessage) {
        mTimeLastMessage = timeLastMessage;
    }

    public String getStatusNote() {
        return statusNote;
    }

    public void setStatusNote(String statusNote) {
        this.statusNote = statusNote;
    }

    public int getStatusInt() {
        return statusInt;
    }

    public void setStatusInt(int statusInt) {
        this.statusInt = statusInt;
    }
}