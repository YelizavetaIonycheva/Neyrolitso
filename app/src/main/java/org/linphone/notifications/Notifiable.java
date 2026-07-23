package org.linphone.notifications;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class Notifiable {
    private final int mNotificationId;
    private List<NotifiableMessage> mMessages;
    private boolean mIsGroup;
    private String mGroupTitle;
    private String mLocalIdentity;
    private String mMyself;
    private int mIconId;
    private int mTextId;

    public Notifiable(int id) {
        mNotificationId = id;
        mMessages = new ArrayList<>();
        mIsGroup = false;
        mIconId = 0;
        mTextId = 0;
    }

    public int getNotificationId() {
        return mNotificationId;
    }

    public void resetMessages() {
        mMessages = new ArrayList<>();
    }

    public void addMessage(NotifiableMessage notifMessage) {
        mMessages.add(notifMessage);
    }

    public List<NotifiableMessage> getMessages() {
        return mMessages;
    }

    public boolean isGroup() {
        return mIsGroup;
    }

    public void setIsGroup(boolean isGroup) {
        mIsGroup = isGroup;
    }

    public String getGroupTitle() {
        return mGroupTitle;
    }

    public void setGroupTitle(String title) {
        mGroupTitle = title;
    }

    public String getMyself() {
        return mMyself;
    }

    public void setMyself(String myself) {
        mMyself = myself;
    }

    public String getLocalIdentity() {
        return mLocalIdentity;
    }

    public void setLocalIdentity(String localIdentity) {
        mLocalIdentity = localIdentity;
    }

    public int getIconResourceId() {
        return mIconId;
    }

    public void setIconResourceId(int id) {
        mIconId = id;
    }

    public int getTextResourceId() {
        return mTextId;
    }

    public void setTextResourceId(int id) {
        mTextId = id;
    }

    @NonNull
    public String toString() {
        return "Id: "
                + mNotificationId
                + ", local identity: "
                + mLocalIdentity
                + ", myself: "
                + mMyself
                + ", isGrouped: "
                + mIsGroup;
    }
}
