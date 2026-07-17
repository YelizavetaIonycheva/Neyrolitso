package org.linphone.notifications;

import android.graphics.Bitmap;
import android.net.Uri;

public class NotifiableMessage {
    private final String mMessage;
    private final String mSender;
    private final long mTime;
    private Bitmap mSenderBitmap;
    private final Uri mFilePath;
    private final String mFileMime;

    public NotifiableMessage(
            String message, String sender, long time, Uri filePath, String fileMime) {
        mMessage = message;
        mSender = sender;
        mTime = time;
        mFilePath = filePath;
        mFileMime = fileMime;
    }

    public String getMessage() {
        return mMessage;
    }

    public String getSender() {
        return mSender;
    }

    public long getTime() {
        return mTime;
    }

    public Bitmap getSenderBitmap() {
        return mSenderBitmap;
    }

    public void setSenderBitmap(Bitmap bm) {
        mSenderBitmap = bm;
    }

    public Uri getFilePath() {
        return mFilePath;
    }

    public String getFileMime() {
        return mFileMime;
    }
}
