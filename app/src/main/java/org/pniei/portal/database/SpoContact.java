package org.pniei.portal.database;

import java.io.Serializable;

public class SpoContact implements Serializable {
    private long mId;
    private String mIdUser;
    private String mFullName;
    private String mSipNumber;
    private String mUriPhoto;

    private String statusNote;
    private int statusInt;

    public SpoContact() {
        mUriPhoto = null;
        statusNote = "";
        statusInt = 2;
    }

    public SpoContact(long id, String idUser, String fullName, String sipNumber, String uriPhoto) {
        mId = id;
        mIdUser = idUser;
        mFullName = fullName;
        mSipNumber = sipNumber;
        if (mSipNumber == null) {
            mSipNumber = fullName;
        }
        mUriPhoto = uriPhoto;
        statusNote = "";
        statusInt = 2;
    }

    public void setId(long id) {
        mId = id;
    }

    public long getId() {
        return mId;
    }

    public String getIdUser() {
        return mIdUser;
    }

    public void setIdUser(String idUser) {
        mIdUser = idUser;
    }

    public String getFullName() {
        return mFullName;
    }

    public void setFullName(String fullName) {
        mFullName = fullName;
    }

    public String getSipNumber() {
        return mSipNumber;
    }

    public void setSipNumber(String sipNumber) {
        mSipNumber = sipNumber;
    }

    public String getUriPhoto() {
        return mUriPhoto;
    }

    public void setUriPhoto(String uriPhoto) {
        mUriPhoto = uriPhoto;
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
