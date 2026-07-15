package org.pniei.portal.database;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import androidx.core.util.Pair;

import org.pniei.portal.R;
import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.FileUtils;
import org.pniei.portal.utils.Utils;

public class SpoChatMessage {
    public static final int OUT = 0;
    public static final int IN = 1;
    public static final int READ = 1;
    public static final int UNREAD = 0;
    public static final int TEXT = 0;
    public static final int FILE = 1;
   // public static final int IMAGE = 2;
   // public static final int VOICE = 3;
    public static final int SENDING = 0;
    public static final int SENT = 1;

    private long mId;
    private int mDir;
    private int mIsRead;
    private int mTypeContent;
    private int mStatus;
    private List<String> mIdUsers;
    private String mMessage;
    private long mIdSpoChatRoom;
    private long mDate;
    private ArrayList<SpoFile> mSpoFiles;
    private String mIdUsersStr;

    private SpoChatMessage() { }

    public SpoChatMessage(long id, int dir, int isRead, int typeContent, int status, ArrayList<String> idUsers, String message, long idSpoChatRoom, long date) {
        mId = id;
        mDir = dir;
        mIsRead = isRead;
        mTypeContent = typeContent;
        mStatus = status;
        mIdUsers = idUsers;
        mMessage = message;
        mIdSpoChatRoom = idSpoChatRoom;
        mDate = date;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < idUsers.size()-1; i++) {
            sb.append(idUsers.get(i));
            sb.append(",");
        }
        sb.append(idUsers.get(idUsers.size()-1));
        mIdUsersStr = sb.toString();
    }

    public SpoChatMessage(long id, int dir, int isRead, int typeContent, int status, String idUsersStr, String message, long idSpoChatRoom, long date) {
        mId = id;
        mDir = dir;
        mIsRead = isRead;
        mTypeContent = typeContent;
        mStatus = status;
        mMessage = message;
        mIdSpoChatRoom = idSpoChatRoom;
        mDate = date;
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
    }

    public SpoChatMessage(int dir, int isRead, int typeContent, int status, ArrayList<String> idUsers, String message, long idSpoChatRoom, long date) {
        mDir = dir;
        mIsRead = isRead;
        mTypeContent = typeContent;
        mStatus = status;
        mIdUsers = idUsers;
        mMessage = message;
        mIdSpoChatRoom = idSpoChatRoom;
        mDate = date;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < idUsers.size()-1; i++) {
            sb.append(idUsers.get(i));
            sb.append(",");
        }
        sb.append(idUsers.get(idUsers.size()-1));
        mIdUsersStr = sb.toString();
    }

    public static SpoChatMessage createIncomingMessage(ArrayList<String> idUsers, String text, ArrayList<SpoFile.FileInterface> filesInfo) {
        if (text == null && filesInfo == null)
            return null;

        SpoChatMessage message = new SpoChatMessage();
        message.mId = 0;
        message.mDir = IN;
        message.mIsRead = UNREAD;
        message.mStatus = SENT;
        message.mIdUsers = idUsers;
        message.mMessage = text;
        Uri fileUriResieve;
        Uri fileUriSended;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < idUsers.size()-1; i++) {
            sb.append(idUsers.get(i));
            sb.append(",");
        }
        sb.append(idUsers.get(idUsers.size()-1));
        message.mIdUsersStr = sb.toString();

        if (filesInfo != null && filesInfo.size() > 0) {
            message.mTypeContent = FILE;
            ArrayList<SpoFile> files = new ArrayList<>();
            for (SpoFile.FileInterface fileInfo : filesInfo) {
                SpoFile file = new SpoFile();
                file.setName(fileInfo.getName());
                file.setType(Utils.getTypeFile(fileInfo.getName()));

                Context mContext = SpoMessagesService.instance().getContext();
                if (android.os.Build.VERSION.SDK_INT < 29) {
                    fileUriSended = Uri.parse( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + mContext.getString(R.string.app_name) + "/Sended/" + file.getName());
                    fileUriResieve = Uri.parse( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + mContext.getString(R.string.app_name) + "/Recieved/" + file.getName());
                } else {
                    fileUriSended = Uri.parse(mContext.getExternalMediaDirs()[0].getAbsolutePath() + "/Sended/" + file.getName());
                    fileUriResieve = Uri.parse(mContext.getExternalMediaDirs()[0].getAbsolutePath() + "/Recieved/" + file.getName());
                }
                File fileRecieve = new File(String.valueOf(fileUriResieve));
                File fileSended = new File(String.valueOf(fileUriSended));
                if ((!fileSended.isFile())&&(!fileRecieve.isFile())) {
                        file.setStatus(SpoFile.STATUS_READY_TO_DOWNLOAD);
                } else {
                    if (fileRecieve.isFile()) {
                        file.setStatus(SpoFile.STATUS_OK);
                        file.setUri(String.valueOf("file://" + fileRecieve));
                    }
                    if (fileSended.isFile()){
                        file.setStatus(SpoFile.STATUS_OK);
                        file.setUri(String.valueOf("file://" + fileSended));
                    }
                }
                //file.setStatus(SpoFile.STATUS_READY_TO_DOWNLOAD);
                file.setUrlDownload(fileInfo.getLink());
                file.setIdMessage(0);
                file.setDir(SpoFile.DIR_IN);
                file.setIdFile(fileInfo.getIdServer());
                files.add(file);
            }
            message.setSpoFiles(files);
        } else {
            message.mTypeContent = TEXT;
        }

        message.mDate = new Date().getTime();
        return message;
    }
    public static SpoChatMessage createOutgoingResendMessage(long idMessage, SpoChatRoom chatRoom) {
        SpoChatMessage resendMessageInfo = DBUtils.getChatMessageById(idMessage);
        SpoChatMessage message = new SpoChatMessage();
        message.mId = 0;
        message.mDir = OUT;
        message.mStatus = SENDING;
        message.mIsRead = READ;
        message.mIdUsers = chatRoom.getIdUsers();
        message.mIdSpoChatRoom = chatRoom.getId();
        message.mMessage = resendMessageInfo.mMessage;
        message.mTypeContent = resendMessageInfo.mTypeContent;
        message.mDate = new Date().getTime();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < message.mIdUsers.size()-1; i++) {
            sb.append(message.mIdUsers.get(i));
            sb.append(",");
        }
        sb.append(message.mIdUsers.get(message.mIdUsers.size()-1));
        message.mIdUsersStr = sb.toString();

        message.mSpoFiles = new ArrayList<>(Arrays.asList(DBUtils.getSpoFiles(idMessage)));

        return message;
    }
    public static SpoChatMessage createOutgoingMessage(ArrayList<Uri> uriFiles, String text, SpoChatRoom chatRoom) {
        SpoChatMessage message = new SpoChatMessage();
        message.mId = 0;
        message.mDir = OUT;
        message.mStatus = SENDING;
        message.mIsRead = READ;
        message.mIdUsers = chatRoom.getIdUsers();
        message.mIdSpoChatRoom = chatRoom.getId();
        message.mMessage = text;
        message.mTypeContent = TEXT;
        message.mDate = new Date().getTime();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < message.mIdUsers.size()-1; i++) {
            sb.append(message.mIdUsers.get(i));
            sb.append(",");
        }
        sb.append(message.mIdUsers.get(message.mIdUsers.size()-1));
        message.mIdUsersStr = sb.toString();

        if (uriFiles != null && uriFiles.size() > 0) {
            message.mTypeContent = FILE;
            ArrayList<SpoFile> files = new ArrayList<>();

            for (Uri uri : uriFiles) {
                SpoFile file = new SpoFile();
                //file.setIdMessage(message.getId());
                file.setDir(SpoFile.DIR_OUT);
                file.setStatus(SpoFile.STATUS_SEND_RECEIVE);
                file.setUri(uri.toString());
                files.add(file);
            }
            message.mSpoFiles = files;
        }

        return message;
    }

    public static SpoChatMessage createOutgoingVoiceMessage(String fileName, SpoChatRoom chatRoom) {
        SpoChatMessage message = new SpoChatMessage();
        message.mId = 0;
        message.mDir = OUT;
        message.mStatus = SENDING;
        message.mIsRead = READ;
        message.mIdUsers = chatRoom.getIdUsers();
        message.mIdSpoChatRoom = chatRoom.getId();
        message.mMessage = "";
        message.mTypeContent = FILE;
        message.mDate = new Date().getTime();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < message.mIdUsers.size()-1; i++) {
            sb.append(message.mIdUsers.get(i));
            sb.append(",");
        }
        sb.append(message.mIdUsers.get(message.mIdUsers.size()-1));
        message.mIdUsersStr = sb.toString();

        ArrayList<SpoFile> files = new ArrayList<>();

        SpoFile file = new SpoFile();
        file.setDir(SpoFile.DIR_OUT);
        file.setStatus(SpoFile.STATUS_SEND_RECEIVE);
        Uri uriVoice = Uri.fromFile(new File(fileName));
        file.setUri(uriVoice.toString());
        file.setName(fileName);
        file.setType(SpoFile.TYPE_VOICE);
        files.add(file);
        message.mSpoFiles = files;

        return message;
    }

    public int getDir() {
        return mDir;
    }

    public long getId() {
        return mId;
    }

    public void setId(long id) {mId = id;}

    public void setDir(int mDir) {
        this.mDir = mDir;
    }

    public int getIsRead() {
        return mIsRead;
    }

    public void setIsRead(int mIsRead) {
        this.mIsRead = mIsRead;
    }

    public int getTypeContent() {
        return mTypeContent;
    }

    public void setTypeContent(int mTypeContent) {
        this.mTypeContent = mTypeContent;
    }

    public int getStatus() {
        return mStatus;
    }

    public void setStatus(int mStatus) {
        this.mStatus = mStatus;
    }

    public List<String> getIdUsers() {
        return mIdUsers;
    }

    public void setIdUsers(List<String> idUsers) {
        mIdUsers = idUsers;
    }

    public String getMessage() {
        return mMessage;
    }

    public void setMessage(String mMessage) {
        this.mMessage = mMessage;
    }

    public long getIdChatRoom() {
        return mIdSpoChatRoom;
    }

    public void setIdSpoChatRoom(long mIdSpoChatRoom) {
        this.mIdSpoChatRoom = mIdSpoChatRoom;
    }

    public long getDate() {
        return mDate;
    }

    public ArrayList<SpoFile> getSpoFiles() {
        return mSpoFiles;
    }

    public void setSpoFiles(ArrayList<SpoFile> spoFiles) {
        mSpoFiles = spoFiles;
    }
}
