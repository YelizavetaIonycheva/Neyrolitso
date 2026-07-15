package org.pniei.portal.database;

import java.util.ArrayList;
import java.util.List;

public class SpoChatRoom implements Comparable{
    @Override
    public int compareTo(Object o) {
        return Long.compare(((SpoChatRoom)o).mTimeLastMessage, this.mTimeLastMessage);
        //return (int)(this.mTimeLastMessage - ((SpoChatRoom)o).mTimeLastMessage);
        //return (int)(((SpoChatRoom)o).mTimeLastMessage - this.mTimeLastMessage);
    }

    public static final int ONE = 0;
    public static final int MANY = 1;

    private long mId;                 // ID чата
    private int mType;                // Тип чата (один на один или рассылка
    private List<String> mIdUsers;    // ID пользователей с кем идет общение (Если type == ONE, то ID дного пользователя, иначе ID всех кому рассылается сообщение
    private String mNameChat;         // Наименование чата
    private long mTimeLastMessage;    // Время последнего отправленного или принятого сообщения
    private String mIdUsersStr;

    private String statusNote;
    private int statusInt;

    public SpoChatRoom(long id, int type, List<String> idUsers, String nameChat, long timeLastMessage) {
        mId = id;
        mType = type;
        mIdUsers = idUsers;
        mNameChat = nameChat;
        mTimeLastMessage = timeLastMessage;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < idUsers.size()-1; i++) {
            sb.append(idUsers.get(i));
            sb.append(",");
        }
        sb.append(idUsers.get(idUsers.size()-1));
        mIdUsersStr = sb.toString();
        statusNote = "";
        statusInt = 2;
    }

    public SpoChatRoom(long id, int type, String idUsersStr, String nameChat, long timeLastMessage) {
        mId = id;
        mType = type;
        mNameChat = nameChat;
        mTimeLastMessage = timeLastMessage;
        mIdUsersStr = idUsersStr;

        mIdUsers = new ArrayList<>();
        if (mIdUsersStr.contains(",")) {
            String [] list = mIdUsersStr.split(",");
            for (String num : list) {
                mIdUsers.add(num);
            }
        } else {
            mIdUsers.add(mIdUsersStr);
        }
        statusNote = "";
        statusInt = 2;
    }

    public SpoChatRoom() {
        statusNote = "";
        statusInt = 2;
    }

    public long getId() {
        return mId;
    }

    public void setId(long id) {
        mId = id;
    }

    public void setType(int type) {
        this.mType = type;
    }

    public void setIdUsers(List<String> idUsers) {
        this.mIdUsers = idUsers;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < idUsers.size()-1; i++) {
            sb.append(idUsers.get(i));
            sb.append(",");
        }
        sb.append(idUsers.get(idUsers.size()-1));
        mIdUsersStr = sb.toString();
    }

    public String getNameChat() {
        return mNameChat;
    }

    public void setNameChat(String nameChat) {
        this.mNameChat = nameChat;
    }

    public int getType() {
        return mType;
    }

    public List<String> getIdUsers() {
        return mIdUsers;
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
