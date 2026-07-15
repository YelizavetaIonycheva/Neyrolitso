package org.pniei.portal.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneFriend;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoChatMessage;
import org.pniei.portal.database.SpoChatRoom;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.database.SpoFile;
import org.pniei.portal.listener.SpoListenerManager;
import org.pniei.portal.notification.SpoNotificationsManager;
import org.pniei.portal.utils.IsRunThread;
import org.pniei.portal.utils.NetworkRequestUtils;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SpoMessagesService extends Service {
    private static final String TAG = "SpoMessagesService";
    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_SEND_MESSAGE = "snd_msg";

    public static final String ACTION_RESEND_MESSAGE = "resnd_msg";

    public static final String ACTION_SEND_FILE = "snd_file";
    public static final String ACTION_DOWNLOAD_FILE = "dwn_file";
    public static final String ACTION_ADD_CONTACT = "add_c";
    public static final String ACTION_CHANGE_CONTACT = "change_c";
    public static final String ACTION_DELETE_CONTACT = "delete_c";
    public static final String ACTION_SYNC_CONTACT = "sync_c";
    public static final String ACTION_START_SENDING = "start_snd";
    public static final String ACTION_STOP_SENDING_MESSAGE = "stop_snd";
    public static final String ACTION_STOP_SENDING_FILE = "stop_snd_file";
    public static final String ACTION_SET_DELAY_REQUEST = "set_delay";

    public static final String ID_USER_KEY = "id_user_key";
    public static final String SIGNATURE_USER_KEY = "signature_user_key";
    public static final String MESSAGE_ID_KEY = "message_key";
    public static final String FILE_ID_KEY = "file_key";
    public static final String CONTACT_KEY = "contact_key";
    public static final String DELAY_KEY = "delay_key";

    private boolean isStarting = false;
    private int TIME_DELAY_REQUEST_MESSAGE = 15;
    private ScheduledExecutorService processRequestMessage = null;
    private ScheduledExecutorService processCryptDB = null;
    private ExecutorService processSendingTextMessages = null;
    private ExecutorService processSendingFileMessages = null;
    private LinkedList<Pair<Runnable, SpoChatMessage>> mWaitingMessages = null;
    private LinkedList<Pair<Runnable, SpoFile>> mWaitingFiles = null;
    private Context mContext;
    private static SpoMessagesService instance;
    private String idUser = null;
    private String signatureUser = null;
    private SpoNotificationsManager mSpoNotificationsManager;

    private static class Pair<F, S> {
        private F first;
        private S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        public F getFirst() {
            return first;
        }

        public S getSecond() {
            return second;
        }

        public void setFirst(F first) {
            this.first = first;
        }

        public void setSecond(S second) {
            this.second = second;
        }
    }

    public Context getContext() {
        return mContext;
    }

    public static SpoMessagesService instance() {
        return instance;
    }

    public static boolean isReady() {
        return instance != null;
    }

    public void onCreate() {
        Log.i(TAG, "CREATE");
        super.onCreate();
        mContext = this;
        mWaitingMessages = new LinkedList<>();
        mWaitingFiles = new LinkedList<>();
        mSpoNotificationsManager = SpoNotificationsManager.ins(this);
        SpoNotificationsManager.CreateChannel(this);
        mSpoNotificationsManager.startForeground(this);
        if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
            DBUtils.setDataBasePath(mContext.getFilesDir().getAbsolutePath() + "/" + DBUtils.DB_FILE_TT, PrefsUtils.ins().getHashPass());
        } else {
            DBUtils.setDataBasePath(mContext.getFilesDir().getAbsolutePath() + "/" + DBUtils.DB_FILE_P, PrefsUtils.ins().getHashPass());
        }
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action == null) return START_STICKY;

            switch (action) {
                case ACTION_SEND_MESSAGE: {
                    sendMessage(intent.getLongExtra(MESSAGE_ID_KEY, -1));
                    break;
                }
                case ACTION_RESEND_MESSAGE: {
                    resendMessage((ArrayList<Long>) intent.getSerializableExtra(MESSAGE_ID_KEY));
                    break;
                }
                case ACTION_SEND_FILE: {
                    sendFile(intent.getLongExtra(FILE_ID_KEY, 0));
                    break;
                }
                case ACTION_DOWNLOAD_FILE: {
                    downloadFile(intent.getLongExtra(FILE_ID_KEY, 0));
                    break;
                }
                case ACTION_ADD_CONTACT: {
                    addContact((SpoContact) intent.getSerializableExtra(CONTACT_KEY));
                    break;
                }
                case ACTION_CHANGE_CONTACT: {
                    changeContact((SpoContact) intent.getSerializableExtra(CONTACT_KEY));
                    break;
                }
                case ACTION_DELETE_CONTACT: {
                    deleteContact((SpoContact) intent.getSerializableExtra(CONTACT_KEY));
                    break;
                }
                case ACTION_SYNC_CONTACT: {
                    syncContact();
                    break;
                }
                case ACTION_START_SENDING: {
                    startMessageSending();
                    break;
                }
                case ACTION_STOP_SENDING_MESSAGE: {
                    stopMessageSending(intent.getLongExtra(MESSAGE_ID_KEY, -1));
                    break;
                }
                case ACTION_STOP_SENDING_FILE: {
                    stopFileSending(intent.getLongExtra(FILE_ID_KEY, 0));
                    break;
                }
                case ACTION_SET_DELAY_REQUEST: {
                    setDelay(intent.getBooleanExtra(DELAY_KEY, false));
                    break;
                }
                case ACTION_START: {
                    Log.i(TAG, "onStartCommand ACTION_START");
                    idUser = intent.getStringExtra(ID_USER_KEY);
                    signatureUser = intent.getStringExtra(SIGNATURE_USER_KEY);
                    if (!isStarting) {
                        startRequestMessage();
                        startCryptDBProcess();
                        startMessageSending();
                        isStarting = true;
                    }
                    break;
                }
                case ACTION_STOP: {
                    Log.i(TAG, "onStartCommand ACTION_STOP");
                    isStarting = false;
                    stopRequestMessage();
                    stopCryptDBProcess();
                    stopMessageSending();
                    mSpoNotificationsManager.stopForeground(this);
                    stopSelf();
                    return START_NOT_STICKY;
                }
            }
        } else {
            Log.i(TAG, "onStartCommand intent == null");
            if (idUser != null && signatureUser != null) {
                if (isStarting) {
                    stopRequestMessage();
                    stopMessageSending();
                    stopCryptDBProcess();
                }
                startRequestMessage();
                startMessageSending();
                startCryptDBProcess();
            }
        }

        return START_STICKY;
    }

    private void setDelay(boolean isFast) {
        new Thread(() -> {
            if (isFast) {
                TIME_DELAY_REQUEST_MESSAGE = 2;
            } else {
                TIME_DELAY_REQUEST_MESSAGE = 15;
            }
            if (processRequestMessage != null) {
                processRequestMessage.shutdown();
            }
            processRequestMessage = Executors.newSingleThreadScheduledExecutor();
            startRequestMessage();
        }).start();
    }

    private void syncContact() {
        Log.i(TAG, "syncContact");

        Thread thread = new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                while (System.currentTimeMillis() - startTime < 10000) {
                    JSONObject result = NetworkRequestUtils.getListOfContacts(idUser, signatureUser);

                    if (result != null) {
                        if (result.has("listOfContacts")) {
                            ArrayList<SpoContact> addContacts = new ArrayList<>();
                            ArrayList<SpoContact> changedContacts = new ArrayList<>();
                            JSONArray contacts = result.getJSONArray("listOfContacts");

                            for (int i = 0; i < contacts.length(); i++) {
                                if (contacts.getJSONObject(i).has("id")
                                        && contacts.getJSONObject(i).has("name")
                                        && contacts.getJSONObject(i).has("phone")) {
                                    String id = contacts.getJSONObject(i).getString("id");
                                    String name = contacts.getJSONObject(i).getString("name");
                                    String phone = contacts.getJSONObject(i).getString("phone");

                                    SpoContact contact = DBUtils.getContactForIdUser(id);

                                    if (contact == null) {
                                        contact = new SpoContact();
                                        contact.setIdUser(id);
                                        contact.setFullName(name);
                                        contact.setSipNumber(phone);
                                        addContacts.add(contact);
                                        contact.setId(DBUtils.saveContact(contact));
                                    } else {
                                        if (!contact.getFullName().equals(name) || !contact.getSipNumber().equals(phone)) {
                                            contact.setFullName(name);
                                            contact.setSipNumber(phone);
                                            changedContacts.add(contact);
                                            DBUtils.updateContact(contact);
                                        }
                                    }
                                }
                            }
                            DBUtils.saveDataBase();

                            LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                            for (SpoContact contact : addContacts) {
                                assert lc != null;
                                LinphoneFriend lf = lc.createFriend();
                                if (lf != null) {
                                    if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
                                        lf.addAddress(LinphoneCoreFactory.instance().createLinphoneAddress("sip:" + contact.getSipNumber() + "@" + PrefsUtils.ins().getIpAtsTT()));
                                    } else {
                                        lf.addAddress(LinphoneCoreFactory.instance().createLinphoneAddress("sip:" + contact.getSipNumber() + "@" + PrefsUtils.ins().getIpAtsP()));
                                    }
                                    lc.addFriend(lf);
                                }
                            }
                            LinphoneManager.getInstance().subscribeFriendList(true);

                            SpoListenerManager.callContactSync(true, null, addContacts, changedContacts);
                            return;
                        } else if (result.has("error")) {
                            SpoListenerManager.callContactSync(false, Utils.getError(result.getString("error")), null, null);
                            return;
                        }
                    }
                    Thread.sleep(1000);
                }

                SpoListenerManager.callContactSync(false, "Time out", null, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }

    private void addContact(SpoContact contact) {
        Log.i(TAG, "addContact");

        Thread thread = new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                while (System.currentTimeMillis() - startTime < 10000) {
                    JSONObject result = NetworkRequestUtils.addContact(idUser, signatureUser, contact.getIdUser(), contact.getFullName(), contact.getSipNumber());

                    if (result != null) {
                        if (result.has("id") && result.has("error") && result.getInt("error") == 200) {
                            contact.setIdUser(result.getString("id"));
                            contact.setId(DBUtils.saveContact(contact));
                            checkChatRoom(contact);
                            ///////////
                            LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                            assert lc != null;
                            LinphoneFriend lf = lc.createFriend();
                            if (lf != null) {
                                if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
                                    lf.addAddress(LinphoneCoreFactory.instance().createLinphoneAddress("sip:" + contact.getSipNumber() + "@" + PrefsUtils.ins().getIpAtsTT()));
                                } else {
                                    lf.addAddress(LinphoneCoreFactory.instance().createLinphoneAddress("sip:" + contact.getSipNumber() + "@" + PrefsUtils.ins().getIpAtsP()));
                                }
                                lc.addFriend(lf);
                            }
                            LinphoneManager.getInstance().subscribeFriendList(true);
                            ///////////
                            SpoListenerManager.callContactAdd(true, null);
                        } else {
                            SpoListenerManager.callContactAdd(false, Utils.getError(result.getString("error")));
                        }
                        return;
                    }
                    Thread.sleep(1000);
                }

                SpoListenerManager.callContactAdd(false, "Time out");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }

    private void checkChatRoom(SpoContact contact) {
        String idUser = "phone:" + contact.getSipNumber();
        SpoChatRoom chatRoom = DBUtils.getChatRoomForIdUser(idUser);
        if (chatRoom != null) {
            SpoChatMessage[] messages = DBUtils.getSpoChatMessagesRange(chatRoom.getId(), 0);
            List<String> idUsers = new ArrayList<>();
            idUsers.add(contact.getIdUser());
            for (SpoChatMessage message : messages) {
                message.setIdUsers(idUsers);
                DBUtils.updateMessage(message);
            }
            chatRoom.setIdUsers(idUsers);
            chatRoom.setNameChat(contact.getFullName());
            DBUtils.updateChatRoom(chatRoom);
        }
    }

    private void changeContact(SpoContact contact) {
        Log.i(TAG, "changeContact");

        Thread thread = new Thread(() -> {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 10000) {
                try {
                    JSONObject result = NetworkRequestUtils.changeContact(idUser, signatureUser, contact.getIdUser(), contact.getFullName());
                    if (result != null) {
                        if (result.has("error") && result.getInt("error") == 200) {
                            Log.i(TAG, "Контакт изменен, id = " + contact.getIdUser());
                            DBUtils.updateContact(contact);
                            SpoChatRoom chatRoom = DBUtils.getChatRoomForIdUser(contact.getIdUser());
                            if (chatRoom != null) {
                                chatRoom.setNameChat(contact.getFullName());
                                DBUtils.updateChatRoom(chatRoom);
                            }
                            SpoListenerManager.callContactChanged(true, null);
                        } else {
                            SpoListenerManager.callContactChanged(false, Utils.getError(result.getString("error")));
                        }
                        return;
                    }
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            SpoListenerManager.callContactChanged(false, "Time out");
        });
        thread.start();
    }

    private void deleteContact(SpoContact contact) {
        Log.i(TAG, "deleteContact");

        Thread thread = new Thread(() -> {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 10000) {
                try {
                    JSONObject result = NetworkRequestUtils.dropContact(idUser, signatureUser, contact.getIdUser());
                    if (result != null) {
                        if (result.has("error") && result.getInt("error") == 200) {
                            Log.i(TAG, "Контакт удален, id = " + contact.getIdUser());

                            LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                            assert lc != null;
                            LinphoneFriend[] lfs = lc.getFriendList();
                            for (LinphoneFriend lf : lfs) {
                                if (lf.getAddress() != null && lf.getAddress().getUserName() != null && lf.getAddress().getUserName().equals(contact.getSipNumber())) {
                                    lc.removeFriend(lf);
                                    break;
                                }
                            }
                            DBUtils.deleteContact(contact);
                            SpoListenerManager.callContactDelete(true, null);
                        } else {
                            SpoListenerManager.callContactDelete(false, Utils.getError(result.getString("error")));
                        }
                        return;
                    }
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            SpoListenerManager.callContactDelete(false, "Time out");
        });
        thread.start();
    }

    private void startRequestMessage() {
        processRequestMessage = Executors.newSingleThreadScheduledExecutor();
        processRequestMessage.scheduleWithFixedDelay(new RequestMessageRunnable(), 0, TIME_DELAY_REQUEST_MESSAGE, TimeUnit.SECONDS);
    }

    private void stopRequestMessage() {
        if (processRequestMessage != null)
            processRequestMessage.shutdownNow();
    }

    private void startCryptDBProcess() {
        processCryptDB = Executors.newSingleThreadScheduledExecutor();
        int TIME_DELAY_CRYPT_DB = 60;
        processCryptDB.scheduleWithFixedDelay(new CryptDBRunnable(), 60, TIME_DELAY_CRYPT_DB, TimeUnit.SECONDS);
    }

    private void stopCryptDBProcess() {
        if (processCryptDB != null)
            processCryptDB.shutdownNow();
    }

    private void startMessageSending() {
        processSendingTextMessages = Executors.newCachedThreadPool();
        processSendingFileMessages = Executors.newSingleThreadExecutor();
        List<SpoChatMessage> waitingMessages = Arrays.asList(DBUtils.getWaitingSpoChatMessages());
        if (!waitingMessages.isEmpty()) {
            for (SpoChatMessage message : waitingMessages) {
                if (message.getTypeContent() == SpoChatMessage.TEXT) {
                    Runnable task = new SendingTextMessageRunnable(message);
                    Pair<Runnable, SpoChatMessage> msg = new Pair<>(task, message);
                    mWaitingMessages.add(msg);
                    processSendingTextMessages.submit(task);
                } else {
                    SpoFile[] files = DBUtils.getSpoFiles(message.getId());
                    for (SpoFile file : files) {
                        if (file.getStatus() == SpoFile.STATUS_SEND_RECEIVE) {
                            Runnable task = new SendReceiveFileMessageRunnable(file);
                            Pair<Runnable, SpoFile> msg = new Pair<>(task, file);
                            mWaitingFiles.add(msg);
                            processSendingFileMessages.submit(task);
                        }
                    }
                }
            }
        }
    }

    private void stopMessageSending() {
        if (processSendingTextMessages != null)
            processSendingTextMessages.shutdownNow();
        if (processSendingFileMessages != null)
            processSendingFileMessages.shutdownNow();
    }

    private void stopMessageSending(long idMessage) {
        new Thread(() -> {
            for (Pair<Runnable, SpoChatMessage> message : mWaitingMessages) {
                if (message.getSecond().getId() == idMessage) {
                    if (message.getSecond().getTypeContent() == SpoChatMessage.TEXT) {
                        ((SendingTextMessageRunnable) message.getFirst()).stopSending();
                    } else {
                        for (SpoFile file : DBUtils.getSpoFiles(idMessage)) {
                            stopFileSending(file.getId());
                        }
                    }
                    break;
                }
            }
        }).start();
    }

    private void stopFileSending(long idFile) {
        new Thread(() -> {
            for (Pair<Runnable, SpoFile> waitingFile : mWaitingFiles) {
                if (waitingFile.getSecond().getId() == idFile) {
                    ((SendReceiveFileMessageRunnable) waitingFile.getFirst()).stopSendReceive();
                    break;
                }
            }
            SpoFile mFile = DBUtils.getSpoFile(idFile);
            if (mFile != null) {
                if (mFile.getStatus() == SpoFile.STATUS_SEND_RECEIVE) {
                    if (mFile.getDir() == SpoFile.DIR_IN) {
                        mFile.setStatus(SpoFile.STATUS_READY_TO_DOWNLOAD);
                    } else {
                        mFile.setStatus(SpoFile.STATUS_ERROR);
                    }
                    DBUtils.updateFile(mFile);
                    SpoListenerManager.callFileStateChanged(mFile);
                } else if (mFile.getStatus() == SpoFile.STATUS_ERROR) {
                    DBUtils.deleteFile(mFile);
                    endSendingFile(mFile);
                }
                DBUtils.saveDataBase();
            }
        }).start();
    }

    private void successSendingMessage(SpoChatMessage message) {
        for (Pair<Runnable, SpoChatMessage> waitMessage : mWaitingMessages) {
            if (waitMessage.getSecond() == message) {
                mWaitingMessages.remove(waitMessage);
                break;
            }
        }
    }

    private void endSendingFile(SpoFile file) {
        for (Pair<Runnable, SpoFile> waitFile : mWaitingFiles) {
            if (waitFile.getSecond() == file) {
                mWaitingFiles.remove(waitFile);
                break;
            }
        }
        boolean isSendMessage = true;
        SpoChatMessage message = DBUtils.getChatMessageById(file.getIdMessage());
        List<SpoFile> files = Arrays.asList(DBUtils.getSpoFiles(message.getId()));

        if (!files.isEmpty()) {
            for (SpoFile _file : files) {
                if (_file.getStatus() == SpoFile.STATUS_SEND_RECEIVE || _file.getStatus() == SpoFile.STATUS_ERROR) {
                    isSendMessage = false;
                    break;
                }
            }
        } else if (message.getMessage() == null || message.getMessage().isEmpty()) {
            isSendMessage = false;
            DBUtils.deleteMessage(message);
        }

        if (isSendMessage) {
            Runnable task = new SendingTextMessageRunnable(message);
            Pair<Runnable, SpoChatMessage> msg = new Pair<>(task, message);
            mWaitingMessages.add(msg);
            processSendingTextMessages.submit(task);
        }
    }

    private void resendMessage(ArrayList<Long> id) {
        new Thread(() -> {
            for (long idMessage : id) {
                SpoChatMessage message = DBUtils.getChatMessageById(idMessage);
                message.setSpoFiles(new ArrayList<>(Arrays.asList(DBUtils.getSpoFiles(idMessage))));
                Runnable task;

                if (message.getStatus() == SpoChatMessage.SENDING) {
                    task = new SendingTextMessageRunnable(message);
                    Pair<Runnable, SpoChatMessage> msg = new Pair<>(task, message);
                    mWaitingMessages.add(msg);
                    try {
                        processSendingTextMessages.submit(task).get();
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private void sendMessage(final long id) {
        if (id != -1) {
            new Thread(() -> {
                SpoChatMessage message = DBUtils.getChatMessageById(id);
                Runnable task;

                if (message.getTypeContent() == SpoChatMessage.FILE) {
                    SpoFile[] files = DBUtils.getSpoFiles(message.getId());
                    for (SpoFile file : files) {
                        if (file.getStatus() == SpoFile.STATUS_SEND_RECEIVE) {
                            task = new SendReceiveFileMessageRunnable(file);
                            Pair<Runnable, SpoFile> msg = new Pair<>(task, file);
                            mWaitingFiles.add(msg);
                            processSendingFileMessages.submit(task);
                        }
                    }
                } else {
                    task = new SendingTextMessageRunnable(message);
                    Pair<Runnable, SpoChatMessage> msg = new Pair<>(task, message);
                    mWaitingMessages.add(msg);
                    processSendingTextMessages.submit(task);
                }
            }).start();
        }
    }

    private void sendFile(long id) {
        new Thread(() -> {
            SpoFile file = DBUtils.getSpoFile(id);
            Runnable task;

            if (file.getStatus() == SpoFile.STATUS_SEND_RECEIVE || file.getStatus() == SpoFile.STATUS_ERROR) {
                if (file.getStatus() == SpoFile.STATUS_ERROR) {
                    file.setStatus(SpoFile.STATUS_SEND_RECEIVE);
                    DBUtils.updateFile(file);
                }

                task = new SendReceiveFileMessageRunnable(file);
                Pair<Runnable, SpoFile> msg = new Pair<>(task, file);
                mWaitingFiles.add(msg);
                processSendingFileMessages.submit(task);
            }
        }).start();
    }

    private void downloadFile(long idFile) {
        new Thread(() -> {
            SpoFile file = DBUtils.getSpoFile(idFile);
            Runnable task;

            if (file.getStatus() == SpoFile.STATUS_READY_TO_DOWNLOAD || file.getStatus() == SpoFile.STATUS_ERROR) {
                file.setStatus(SpoFile.STATUS_SEND_RECEIVE);
                DBUtils.updateFile(file);

                task = new SendReceiveFileMessageRunnable(file);
                Pair<Runnable, SpoFile> msg = new Pair<>(task, file);
                mWaitingFiles.add(msg);
                processSendingFileMessages.submit(task);
            }
        }).start();
    }

    private class RequestMessageRunnable implements Runnable {
        private final String KEY_DATE = "dispatchTime";

        @Override
        public void run() {
            SpoChatMessage message, lastMessage = null;
            JSONArray jsonArrayMessage = NetworkRequestUtils.getIncomingMessages(idUser, signatureUser);

            if (jsonArrayMessage != null) {
                Log.i(TAG, "Request incoming messages. Incoming " + jsonArrayMessage.length() + " messages");
                if (jsonArrayMessage.length() != 0) {
                    for (int i = 0; i < jsonArrayMessage.length(); i++) {
                        try {
                            message = messageHanding(jsonArrayMessage.getJSONObject(i));

                            if (message != null)
                                lastMessage = message;
                        } catch (JSONException je) {
                            je.printStackTrace();
                        }
                    }

                    if (lastMessage != null) {
                        SpoListenerManager.callLastChatMessageReceived(lastMessage);
                        if (!SpoListenerManager.isAddChatListeners()) {
                            SpoChatRoom chatRoom = DBUtils.getChatRoomForIdUser(lastMessage.getIdUsers());
                            mSpoNotificationsManager.displayMessageNotification(chatRoom, lastMessage);
                        }
                    }
                }
            }
        }

        private SpoChatMessage messageHanding(JSONObject jsonMessage) throws JSONException {
            ArrayList<String> idUsers = new ArrayList<>();
            ArrayList<SpoFile.FileInterface> filesInfo = null;
            String text = null;

            String KEY_USER_ID = "id";
            String KEY_PHONE = "phone";
            if (jsonMessage.has(KEY_USER_ID)) {
                idUsers.add(jsonMessage.getString(KEY_USER_ID));
            } else if (jsonMessage.has(KEY_PHONE)) {
                if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT)
                    return null;
                idUsers.add("phone:" + jsonMessage.getString(KEY_PHONE));
            } else {
                return null;
            }

            String KEY_FILE = "file";
            if (jsonMessage.has(KEY_FILE)) {
                filesInfo = new ArrayList<>();
                JSONArray jsonFiles = jsonMessage.getJSONArray(KEY_FILE);
                for (int i = 0; i < jsonFiles.length(); i++) {
                    JSONObject jsonFile = jsonFiles.getJSONObject(i);
                    SpoFile.FileInterface fileInfo;
                    String KEY_FILE_NAME = "fileName";
                    String KEY_FILE_LINK = "link";
                    String KEY_FILE_ID_SERVER = "idFile";
                    if (jsonFile.has(KEY_FILE_ID_SERVER)) {
                        fileInfo = new SpoFile.FileInterface(jsonFile.getString(KEY_FILE_ID_SERVER), jsonFile.getString(KEY_FILE_NAME), jsonFile.getString(KEY_FILE_LINK));
                    } else {
                        fileInfo = new SpoFile.FileInterface("", jsonFile.getString(KEY_FILE_NAME), jsonFile.getString(KEY_FILE_LINK));
                    }
                    filesInfo.add(fileInfo);
                }
            }

            String KEY_TEXT = "text";
            if (jsonMessage.has(KEY_TEXT)) {
                text = jsonMessage.getString(KEY_TEXT);
                // Шифрование полностью удалено - текст передается как есть
            }

            SpoChatMessage message = SpoChatMessage.createIncomingMessage(idUsers, text, filesInfo);
            if (message == null)
                return null;
            SpoChatRoom chatRoom = DBUtils.getChatRoomForIdUser(message.getIdUsers());

            if (chatRoom == null) {
                chatRoom = new SpoChatRoom();
                chatRoom.setIdUsers(message.getIdUsers());
                String idUser = message.getIdUsers().get(0);
                if (idUser.contains("phone:")) {
                    chatRoom.setNameChat(idUser.substring(6));
                } else {
                    SpoContact contact = DBUtils.getContactForIdUser(message.getIdUsers().get(0));
                    if (contact != null)
                        chatRoom.setNameChat(contact.getFullName());
                    else
                        return null;
                }
                chatRoom.setType(SpoChatRoom.ONE);
                chatRoom.setTimeLastMessage(message.getDate());
                chatRoom.setId(DBUtils.saveChatRoom(chatRoom));
            } else {
                chatRoom.setTimeLastMessage(message.getDate());
                DBUtils.updateChatRoom(chatRoom);
            }
            message.setIdSpoChatRoom(chatRoom.getId());
            message.setId(DBUtils.saveMessage(message));

            if (message.getTypeContent() == SpoChatMessage.FILE) {
                ArrayList<SpoFile> files = message.getSpoFiles();
                if (files != null && !files.isEmpty()) {
                    for (SpoFile file : files) {
                        file.setIdMessage(message.getId());
                        file.setId(DBUtils.saveFile(file));
                    }
                }
            }

            SpoListenerManager.callChatMessageReceived(message);
            return message;
        }
    }

    private static class CryptDBRunnable implements Runnable {
        @Override
        public void run() {
            DBUtils.saveDataBase();
        }
    }

    private class SendReceiveFileMessageRunnable implements Runnable {
        private final SpoFile mFile;
        private IsRunThread isRun = null;

        public SendReceiveFileMessageRunnable(SpoFile file) {
            mFile = file;
        }

        @Override
        public void run() {
            Log.i(TAG, "SendingFileMessageRunnable start Thread = " + Thread.currentThread().getId());
            isRun = new IsRunThread(true);

            if (mFile.getDir() == SpoFile.DIR_OUT) {
                boolean isSent = false;
                String idFile = "";
                try {
                    Uri uri = Uri.parse(mFile.getUri());
                    String fileName = Utils.getFileName(mContext, uri);
                    InputStream in = getContentResolver().openInputStream(uri);
                    JSONObject result = NetworkRequestUtils.uploadFile(mFile.getId(), idUser, signatureUser, fileName, in, isRun);

                    if (result != null) {
                        if (result.has("idFile")) {
                            idFile = result.getString("idFile");
                            isSent = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (isSent) {
                    Log.i(TAG, "Send File OK");
                    mFile.setIdFile(idFile);
                    mFile.setStatus(SpoFile.STATUS_OK);
                } else {
                    Log.i(TAG, "Send File ERROR");
                    mFile.setStatus(SpoFile.STATUS_ERROR);
                }
                DBUtils.updateFile(mFile);
                SpoListenerManager.callFileStateChanged(mFile);
                endSendingFile(mFile);
            } else {
                boolean isDownload = false;
                String urlDownload = mFile.getUrlDownload();
                String fileName = mFile.getName();
                SpoChatMessage message = DBUtils.getChatMessageById(mFile.getIdMessage());

                try {
                    File downloadFile = Utils.createReceivedFile(mContext, fileName);
                    OutputStream out = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        out = Files.newOutputStream(downloadFile.toPath());
                    }

                    if (NetworkRequestUtils.downloadFile(mFile.getId(), urlDownload, out, isRun)) {
                        mFile.setUri(Uri.fromFile(downloadFile).toString());
                        isDownload = true;
                        assert out != null;
                        out.flush();
                        out.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (isDownload) {
                    Log.i(TAG, "Download File OK");
                    mFile.setStatus(SpoFile.STATUS_OK);
                } else {
                    Log.i(TAG, "Download File ERROR");
                    mFile.setStatus(SpoFile.STATUS_ERROR);
                }
                DBUtils.updateFile(mFile);
                SpoListenerManager.callFileStateChanged(mFile);
            }
            Log.i(TAG, "SendingFileMessageRunnable stop Thread = " + Thread.currentThread().getId());
        }

        public void stopSendReceive() {
            isRun.setValue(false);
        }
    }

    private class SendingTextMessageRunnable implements Runnable {
        private final SpoChatMessage mMessage;
        private boolean isRun;

        public SendingTextMessageRunnable(SpoChatMessage message) {
            mMessage = message;
        }

        @Override
        public void run() {
            Log.i(TAG, "SendingMessageRunnable start Thread = " + Thread.currentThread().getId());
            boolean isSent = false;

            isRun = true;
            while (isRun) {
                switch (mMessage.getTypeContent()) {
                    case SpoChatMessage.FILE: {
                        JSONObject result;
                        ArrayList<SpoFile> files = new ArrayList<>(Arrays.asList(DBUtils.getSpoFiles(mMessage.getId())));
                        if (!files.isEmpty()) {
                            mMessage.setSpoFiles(files);
                            String[] idFiles = new String[mMessage.getSpoFiles().size()];
                            int index = 0;
                            for (SpoFile file : mMessage.getSpoFiles()) {
                                idFiles[index] = file.getIdFile();
                                index++;
                            }
                            result = NetworkRequestUtils.sendTextMessage(idUser, signatureUser, mMessage.getMessage(), mMessage.getIdUsers(), idFiles);
                        } else {
                            result = NetworkRequestUtils.sendTextMessage(idUser, signatureUser, mMessage.getMessage(), mMessage.getIdUsers(), null);
                        }

                        try {
                            if (result != null && result.has("error") && result.getInt("error") == 200) {
                                isSent = true;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    case SpoChatMessage.TEXT: {
                        JSONObject result = NetworkRequestUtils.sendTextMessage(idUser, signatureUser, mMessage.getMessage(), mMessage.getIdUsers(), null);
                        try {
                            if (result != null && result.has("error") && result.getInt("error") == 200) {
                                isSent = true;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unexpected value: " + mMessage.getTypeContent());
                }

                if (isSent) {
                    Log.i(TAG, "Send Message OK");
                    mMessage.setStatus(SpoChatMessage.SENT);
                    DBUtils.updateMessage(mMessage);
                    SpoListenerManager.callChatMessageStateChanged(mMessage);
                    break;
                } else {
                    if (Thread.interrupted() || !isRun) {
                        break;
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            successSendingMessage(mMessage);

            Log.i(TAG, "SendingMessageRunnable stop Thread = " + Thread.currentThread().getId());
        }

        public void stopSending() {
            isRun = false;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (processRequestMessage != null)
            processRequestMessage.shutdownNow();
        if (processSendingTextMessages != null)
            processSendingTextMessages.shutdownNow();
        if (processSendingFileMessages != null)
            processSendingFileMessages.shutdownNow();
    }

    public static void start(Context context, String idUser, String signature) {
        Intent intent = new Intent(context, SpoMessagesService.class);
        intent.setAction(SpoMessagesService.ACTION_START);
        intent.putExtra(SpoMessagesService.ID_USER_KEY, idUser);
        intent.putExtra(SpoMessagesService.SIGNATURE_USER_KEY, signature);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, SpoMessagesService.class);
        intent.setAction(SpoMessagesService.ACTION_STOP);
        context.startService(intent);
    }

    public static void sendMessage(Context context, long idMessage) {
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(context, SpoMessagesService.class);
            intent.setAction(SpoMessagesService.ACTION_SEND_MESSAGE);
            intent.putExtra(SpoMessagesService.MESSAGE_ID_KEY, idMessage);
            context.startService(intent);
        }, 300);
    }

    public static void resendMessage(Context context, ArrayList<Long> idMessages) {
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(context, SpoMessagesService.class);
            intent.setAction(SpoMessagesService.ACTION_RESEND_MESSAGE);
            intent.putExtra(SpoMessagesService.MESSAGE_ID_KEY, idMessages);
            context.startService(intent);
        }, 300);
    }

    public String[] checkUpdateApp(String currentVersionApp) {
        JSONObject result = NetworkRequestUtils.checkUpdate(idUser, signatureUser, currentVersionApp);

        if (result != null) {
            if (result.has("version")
                    && result.has("name")
                    && result.has("link")
                    && result.has("divergence")
                    && result.has("error")) {
                try {
                    if (result.getInt("error") != 200)
                        return null;

                    String[] verAndLink = new String[2];

                    verAndLink[0] = result.getString("version");
                    verAndLink[1] = result.getString("link");

                    return verAndLink;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                return null;
            }
        }

        return null;
    }

    public void removeMessageNotification() {
        mSpoNotificationsManager.removeMessageNotification();
    }
}