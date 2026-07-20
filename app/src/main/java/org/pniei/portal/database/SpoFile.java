package org.pniei.portal.database;

public class SpoFile {
    public static final int DIR_OUT = 0;
    public static final int DIR_IN = 1;
    public static final int STATUS_SEND_RECEIVE = 0;
    public static final int STATUS_READY_TO_DOWNLOAD = 1;
    public static final int STATUS_OK = 2;
    public static final int STATUS_ERROR = 3;

    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_VOICE = 1;
    public static final int TYPE_OTHER = 2;

    private long mId;
    private int mDir;
    private int mType;
    private long mIdMessage;
    private String mIdFile;
    private int mStatus;
    private String mUri;
    private String mUrlDownload;
    private String mName;

    public static class FileInterface {
        private String idServer;
        private String name;
        private String link;

        public FileInterface(String idServer, String name, String link) {
            this.idServer = idServer;
            this.name = name;
            this.link = link;
        }

        public String getIdServer() {
            return idServer;
        }

        public void setIdServer(String idServer) {
            this.idServer = idServer;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLink() {
            return link;
        }

        public void setLink(String link) {
            this.link = link;
        }
    }

    public SpoFile(long id, int dir, int type, long idMessage, String idFile, int status, String uri, String urlDownload, String name) {
        mId = id;
        mDir = dir;
        mType = type;
        mIdMessage = idMessage;
        mIdFile = idFile;
        mStatus = status;
        mUri = uri;
        mUrlDownload = urlDownload;
        mName = name;
    }

    public SpoFile() {
    }

    public void setId(long id) {
        mId = id;
    }

    public long getId() {
        return mId;
    }

    public int getDir() {
        return mDir;
    }

    public void setDir(int dir) {
        mDir = dir;
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        mType = type;
    }

    public long getIdMessage() {
        return mIdMessage;
    }

    public void setIdMessage(long idMessage) {
        mIdMessage = idMessage;
    }

    public String getIdFile() {
        return mIdFile;
    }

    public void setIdFile(String idFile) {
        mIdFile = idFile;
    }

    public int getStatus() {
        return mStatus;
    }

    public void setStatus(int status) {
        mStatus = status;
    }

    public String getUri() {
        return mUri;
    }

    public void setUri(String uri) {
        mUri = uri;
    }

    public String getUrlDownload() {
        return mUrlDownload;
    }

    public void setUrlDownload(String urlDownload) {
        mUrlDownload = urlDownload;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }
}
