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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SpoMessagesService extends Service {
    private static final String TAG = "MessagesService";

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

        public F getFirst() { return first; }
        public S getSecond() { return second; }
        public void setFirst(F first) { this.first = first; }
        public void setSecond(S second) { this.second = second; }
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

    @Override
    public void onCreate() {
        Log.i(TAG, "CREATE");
        super.onCreate();
        mContext = this;
        mWaitingMessages = new LinkedList<>();
        mWaitingFiles = new LinkedList<>();
        mSpoNotificationsManager = SpoNotificationsManager.ins(this);
        SpoNotificationsManager.CreateChannel(this);
        mSpoNotificationsManager.startForeground(this);

        // Всегда используем базу Portal (шифрование удалено)
        DBUtils.setDataBasePath(mContext.getFilesDir().getAbsolutePath() + "/" + DBUtils.DB_FILE_P, null);
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case ACTION_SEND_MESSAGE:
                    sendMessage(intent.getLongExtra(MESSAGE_ID_KEY, -1));
                    break;
                case ACTION_RESEND_MESSAGE:
                    resendMessage((ArrayList<Long>) intent.getSerializableExtra(MESSAGE_ID_KEY));
                    break;
                case ACTION_SEND_FILE:
                    sendFile(intent.getLongExtra(FILE_ID_KEY, 0));
                    break;
                case ACTION_DOWNLOAD_FILE:
                    downloadFile(intent.getLongExtra(FILE_ID_KEY, 0));
                    break;
                case ACTION_ADD_CONTACT:
                    addContact((SpoContact) intent.getSerializableExtra(CONTACT_KEY));
                    break;
                case ACTION_CHANGE_CONTACT:
                    changeContact((SpoContact) intent.getSerializableExtra(CONTACT_KEY));
                    break;
                case ACTION_DELETE_CONTACT:
                    deleteContact((SpoContact) intent.getSerializableExtra(CONTACT_KEY));
                    break;
                case ACTION_SYNC_CONTACT:
                    syncContact();
                    break;
                case ACTION_START_SENDING:
                    startMessageSending();
                    break;
                case ACTION_STOP_SENDING_MESSAGE:
                    stopMessageSending(intent.getLongExtra(MESSAGE_ID_KEY, -1));
                    break;
                case ACTION_STOP_SENDING_FILE:
                    stopFileSending(intent.getLongExtra(FILE_ID_KEY, 0));
                    break;
                case ACTION_SET_DELAY_REQUEST:
                    setDelay(intent.getBooleanExtra(DELAY_KEY, false));
                    break;
                case ACTION_START:
                    Log.i(TAG, "onStartCommand ACTION_START");
                    idUser = intent.getStringExtra(ID_USER_KEY);
                    signatureUser = intent.getStringExtra(SIGNATURE_USER_KEY);
                    if (!isStarting) {
                        startRequestMessage();
                        startMessageSending();
                        isStarting = true;
                    }
                    break;
                case ACTION_STOP:
                    Log.i(TAG, "onStartCommand ACTION_STOP");
                    isStarting = false;
                    stopRequestMessage();
                    stopMessageSending();
                    mSpoNotificationsManager.stopForeground(this);
                    stopSelf();
                    return START_NOT_STICKY;
            }
        } else {
            Log.i(TAG, "onStartCommand intent == null");
            if (idUser != null && signatureUser != null) {
                if (isStarting) {
                    stopRequestMessage();
                    stopMessageSending();
                }
                startRequestMessage();
                startMessageSending();
            }
        }
        return START_STICKY;
    }

    // Управление задержкой запросов

    private void setDelay(boolean isFast) {
        new Thread(() -> {
            if (isFast) {
                TIME_DELAY_REQUEST_MESSAGE = 2;
            } else {
                TIME_DELAY_REQUEST_MESSAGE = 15;
            }
            if (processRequestMessage != null) {
                processRequestMessage.shutdownNow();
            }
            processRequestMessage = Executors.newSingleThreadScheduledExecutor();
            startRequestMessage();
        }).start();
    }

    // Работа с контактами

    private void syncContact() {
        Log.i(TAG, "syncContact");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                while (System.currentTimeMillis() - startTime < 10000) {
                    JSONObject result = NetworkRequestUtils.getListOfContacts(idUser, signatureUser);
                    if (result != null && result.has("listOfContacts")) {
                        ArrayList<SpoContact> addContacts = new ArrayList<>();
                        ArrayList<SpoContact> changedContacts = new ArrayList<>();
                        JSONArray contacts = result.getJSONArray("listOfContacts");
                        for (int i = 0; i < contacts.length(); i++) {
                            JSONObject contactObj = contacts.getJSONObject(i);
                            if (contactObj.has("id") && contactObj.has("name") && contactObj.has("phone")) {
                                String id = contactObj.getString("id");
                                String name = contactObj.getString("name");
                                String phone = contactObj.getString("phone");
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
                            if (lc != null) {
                                LinphoneFriend lf = lc.createFriend();
                                if (lf != null) {
                                    lf.addAddress(LinphoneCoreFactory.instance().createLinphoneAddress("sip:" + contact.getSipNumber() + "@" + PrefsUtils.ins().getIpAtsP()));
                                    lc.addFriend(lf);
                                }
                            }
                        }
                        LinphoneManager.getInstance().subscribeFriendList(true);
                        SpoListenerManager.callContactSync(true, null, addContacts, changedContacts);
                        return;
                    } else if (result != null && result.has("error")) {
                        SpoListenerManager.callContactSync(false, Utils.getError(result.getString("error")), null, null);
                        return;
                    }
                    Thread.sleep(1000);
                }
                SpoListenerManager.callContactSync(false, "Time out", null, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void addContact(SpoContact contact) {
        Log.i(TAG, "addContact");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                while (System.currentTimeMillis() - startTime < 10000) {
                    JSONObject result = NetworkRequestUtils.addContact(idUser, signatureUser, contact.getIdUser(), contact.getFullName(), contact.getSipNumber());
                    if (result != null) {
                        if (result.has("id") && result.has("error") && result.getInt("error") == 200) {
                            contact.setIdUser(result.getString("id"));
                            contact.setId(DBUtils.saveContact(contact));
                            checkChatRoom(contact);
                            LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                            if (lc != null) {
                                LinphoneFriend lf = lc.createFriend();
                                if (lf != null) {
                                    lf.addAddress(LinphoneCoreFactory.instance().createLinphoneAddress("sip:" + contact.getSipNumber() + "@" + PrefsUtils.ins().getIpAtsP()));
                                    lc.addFriend(lf);
                                }
                            }
                            LinphoneManager.getInstance().subscribeFriendList(true);
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
        }).start();
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
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                while (System.currentTimeMillis() - startTime < 10000) {
                    JSONObject result = NetworkRequestUtils.changeContact(idUser, signatureUser, contact.getIdUser(), contact.getFullName());
                    if (result != null) {
                        if (result.has("error") && result.getInt("error") == 200) {
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
                }
                SpoListenerManager.callContactChanged(false, "Time out");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void deleteContact(SpoContact contact) {
        Log.i(TAG, "deleteContact");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                while (System.currentTimeMillis() - startTime < 10000) {
                    JSONObject result = NetworkRequestUtils.dropContact(idUser, signatureUser, contact.getIdUser());
                    if (result != null) {
                        if (result.has("error") && result.getInt("error") == 200) {
                            LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                            if (lc != null) {
                                LinphoneFriend[] lfs = lc.getFriendList();
                                for (LinphoneFriend lf : lfs) {
                                    if (lf.getAddress() != null && lf.getAddress().getUserName() != null && lf.getAddress().getUserName().equals(contact.getSipNumber())) {
                                        lc.removeFriend(lf);
                                        break;
                                    }
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
                }
                SpoListenerManager.callContactDelete(false, "Time out");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Запрос сообщений

    private void startRequestMessage() {
        processRequestMessage = Executors.newSingleThreadScheduledExecutor();
        processRequestMessage.scheduleWithFixedDelay(new RequestMessageRunnable(), 0, TIME_DELAY_REQUEST_MESSAGE, TimeUnit.SECONDS);
    }

    private void stopRequestMessage() {
        if (processRequestMessage != null) {
            processRequestMessage.shutdownNow();
        }
    }

    // Отправка сообщений

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
        if (processSendingTextMessages != null) {
            processSendingTextMessages.shutdownNow();
        }
        if (processSendingFileMessages != null) {
            processSendingFileMessages.shutdownNow();
        }
    }

    private void stopMessageSending(long idMessage) {
        new Thread(() -> {
            for (Pair<Runnable, SpoChatMessage> pair : mWaitingMessages) {
                if (pair.getSecond().getId() == idMessage) {
                    if (pair.getSecond().getTypeContent() == SpoChatMessage.TEXT) {
                        ((SendingTextMessageRunnable) pair.getFirst()).stopSending();
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
            for (Pair<Runnable, SpoFile> pair : mWaitingFiles) {
                if (pair.getSecond().getId() == idFile) {
                    ((SendReceiveFileMessageRunnable) pair.getFirst()).stopSendReceive();
                    break;
                }
            }
            SpoFile file = DBUtils.getSpoFile(idFile);
            if (file != null) {
                if (file.getStatus() == SpoFile.STATUS_SEND_RECEIVE) {
                    if (file.getDir() == SpoFile.DIR_IN) {
                        file.setStatus(SpoFile.STATUS_READY_TO_DOWNLOAD);
                    } else {
                        file.setStatus(SpoFile.STATUS_ERROR);
                    }
                    DBUtils.updateFile(file);
                    SpoListenerManager.callFileStateChanged(file);
                } else if (file.getStatus() == SpoFile.STATUS_ERROR) {
                    DBUtils.deleteFile(file);
                    endSendingFile(file);
                }
                DBUtils.saveDataBase();
            }
        }).start();
    }

    private void successSendingMessage(SpoChatMessage message) {
        mWaitingMessages.removeIf(pair -> pair.getSecond() == message);
    }

    private void endSendingFile(SpoFile file) {
        mWaitingFiles.removeIf(pair -> pair.getSecond() == file);
        boolean isSendMessage = true;
        SpoChatMessage message = DBUtils.getChatMessageById(file.getIdMessage());
        List<SpoFile> files = Arrays.asList(DBUtils.getSpoFiles(message.getId()));
        if (!files.isEmpty()) {
            for (SpoFile f : files) {
                if (f.getStatus() == SpoFile.STATUS_SEND_RECEIVE || f.getStatus() == SpoFile.STATUS_ERROR) {
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

    private void resendMessage(ArrayList<Long> ids) {
        new Thread(() -> {
            for (long idMessage : ids) {
                SpoChatMessage message = DBUtils.getChatMessageById(idMessage);
                if (message.getStatus() == SpoChatMessage.SENDING) {
                    Runnable task = new SendingTextMessageRunnable(message);
                    Pair<Runnable, SpoChatMessage> msg = new Pair<>(task, message);
                    mWaitingMessages.add(msg);
                    try {
                        processSendingTextMessages.submit(task).get();
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }).start();
    }

    private void sendMessage(long id) {
        if (id != -1) {
            new Thread(() -> {
                SpoChatMessage message = DBUtils.getChatMessageById(id);
                if (message.getTypeContent() == SpoChatMessage.FILE) {
                    SpoFile[] files = DBUtils.getSpoFiles(message.getId());
                    for (SpoFile file : files) {
                        if (file.getStatus() == SpoFile.STATUS_SEND_RECEIVE) {
                            Runnable task = new SendReceiveFileMessageRunnable(file);
                            Pair<Runnable, SpoFile> msg = new Pair<>(task, file);
                            mWaitingFiles.add(msg);
                            processSendingFileMessages.submit(task);
                        }
                    }
                } else {
                    Runnable task = new SendingTextMessageRunnable(message);
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
            if (file.getStatus() == SpoFile.STATUS_SEND_RECEIVE || file.getStatus() == SpoFile.STATUS_ERROR) {
                if (file.getStatus() == SpoFile.STATUS_ERROR) {
                    file.setStatus(SpoFile.STATUS_SEND_RECEIVE);
                    DBUtils.updateFile(file);
                }
                Runnable task = new SendReceiveFileMessageRunnable(file);
                Pair<Runnable, SpoFile> msg = new Pair<>(task, file);
                mWaitingFiles.add(msg);
                processSendingFileMessages.submit(task);
            }
        }).start();
    }

    private void downloadFile(long idFile) {
        new Thread(() -> {
            SpoFile file = DBUtils.getSpoFile(idFile);
            if (file.getStatus() == SpoFile.STATUS_READY_TO_DOWNLOAD || file.getStatus() == SpoFile.STATUS_ERROR) {
                file.setStatus(SpoFile.STATUS_SEND_RECEIVE);
                DBUtils.updateFile(file);
                Runnable task = new SendReceiveFileMessageRunnable(file);
                Pair<Runnable, SpoFile> msg = new Pair<>(task, file);
                mWaitingFiles.add(msg);
                processSendingFileMessages.submit(task);
            }
        }).start();
    }

    private class RequestMessageRunnable implements Runnable {
        @Override
        public void run() {
            JSONArray jsonArrayMessage = NetworkRequestUtils.getIncomingMessages(idUser, signatureUser);
            if (jsonArrayMessage != null) {
                Log.i(TAG, "Request incoming messages. Incoming " + jsonArrayMessage.length() + " messages");
                SpoChatMessage lastMessage = null;
                for (int i = 0; i < jsonArrayMessage.length(); i++) {
                    try {
                        SpoChatMessage msg = messageHandling(jsonArrayMessage.getJSONObject(i));
                        if (msg != null) lastMessage = msg;
                    } catch (JSONException je) {
                        je.printStackTrace();
                    }
                }
                if (lastMessage != null) {
                    SpoListenerManager.callLastChatMessageReceived(lastMessage);
                    if (!SpoListenerManager.isAddChatListeners()) {
                        SpoChatRoom chatRoom = DBUtils.getChatRoomForIdUser(lastMessage.getIdUsers());
                        if (chatRoom != null) {
                            mSpoNotificationsManager.displayMessageNotification(chatRoom, lastMessage);
                        }
                    }
                }
            }
        }

        private SpoChatMessage messageHandling(JSONObject jsonMessage) throws JSONException {
            ArrayList<String> idUsers = new ArrayList<>();
            if (jsonMessage.has("id")) {
                idUsers.add(jsonMessage.getString("id"));
            } else if (jsonMessage.has("phone")) {
                idUsers.add("phone:" + jsonMessage.getString("phone"));
            } else {
                return null;
            }

            ArrayList<SpoFile.FileInterface> filesInfo = null;
            if (jsonMessage.has("file")) {
                filesInfo = new ArrayList<>();
                JSONArray jsonFiles = jsonMessage.getJSONArray("file");
                for (int i = 0; i < jsonFiles.length(); i++) {
                    JSONObject jsonFile = jsonFiles.getJSONObject(i);
                    String idFile = jsonFile.has("idFile") ? jsonFile.getString("idFile") : "";
                    String name = jsonFile.getString("fileName");
                    String link = jsonFile.getString("link");
                    filesInfo.add(new SpoFile.FileInterface(idFile, name, link));
                }
            }

            String text = jsonMessage.has("text") ? jsonMessage.getString("text") : null;
            SpoChatMessage message = SpoChatMessage.createIncomingMessage(idUsers, text, filesInfo);
            if (message == null) return null;

            SpoChatRoom chatRoom = DBUtils.getChatRoomForIdUser(message.getIdUsers());
            if (chatRoom == null) {
                chatRoom = new SpoChatRoom();
                chatRoom.setIdUsers(message.getIdUsers());
                String idUser = message.getIdUsers().get(0);
                if (idUser.startsWith("phone:")) {
                    chatRoom.setNameChat(idUser.substring(6));
                } else {
                    SpoContact contact = DBUtils.getContactForIdUser(idUser);
                    if (contact != null) {
                        chatRoom.setNameChat(contact.getFullName());
                    } else {
                        return null;
                    }
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

            if (message.getTypeContent() == SpoChatMessage.FILE && message.getSpoFiles() != null) {
                for (SpoFile file : message.getSpoFiles()) {
                    file.setIdMessage(message.getId());
                    file.setId(DBUtils.saveFile(file));
                }
            }

            SpoListenerManager.callChatMessageReceived(message);
            return message;
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
            Log.i(TAG, "SendReceiveFileMessageRunnable start Thread = " + Thread.currentThread().getId());
            isRun = new IsRunThread(true);

            if (mFile.getDir() == SpoFile.DIR_OUT) {
                boolean isSent = false;
                String idFile = "";
                try {
                    Uri uri = Uri.parse(mFile.getUri());
                    String fileName = Utils.getFileName(mContext, uri);
                    InputStream in = getContentResolver().openInputStream(uri);
                    JSONObject result = NetworkRequestUtils.uploadFile(mFile.getId(), idUser, signatureUser, fileName, in, isRun);
                    if (result != null && result.has("idFile")) {
                        idFile = result.getString("idFile");
                        isSent = true;
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
                try {
                    String urlDownload = mFile.getUrlDownload();
                    String fileName = mFile.getName();
                    SpoChatMessage message = DBUtils.getChatMessageById(mFile.getIdMessage());
                    File downloadFile = Utils.createReceivedFile(mContext, fileName);
                    OutputStream out = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        out = Files.newOutputStream(downloadFile.toPath());
                    }
                    if (NetworkRequestUtils.downloadFile(mFile.getId(), urlDownload, out, isRun, Integer.parseInt(message.getIdUsers().get(0)))) {
                        mFile.setUri(Uri.fromFile(downloadFile).toString());
                        isDownload = true;
                        if (out != null) {
                            out.flush();
                            out.close();
                        }
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
            Log.i(TAG, "SendReceiveFileMessageRunnable stop Thread = " + Thread.currentThread().getId());
        }

        public void stopSendReceive() {
            if (isRun != null) {
                isRun.setValue(false);
            }
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
                JSONObject result;
                if (mMessage.getTypeContent() == SpoChatMessage.FILE) {
                    ArrayList<SpoFile> files = new ArrayList<>(Arrays.asList(DBUtils.getSpoFiles(mMessage.getId())));
                    if (!files.isEmpty()) {
                        mMessage.setSpoFiles(files);
                        String[] idFiles = new String[files.size()];
                        for (int i = 0; i < files.size(); i++) {
                            idFiles[i] = files.get(i).getIdFile();
                        }
                        result = NetworkRequestUtils.sendTextMessage(idUser, signatureUser, mMessage.getMessage(), mMessage.getIdUsers(), idFiles);
                    } else {
                        result = NetworkRequestUtils.sendTextMessage(idUser, signatureUser, mMessage.getMessage(), mMessage.getIdUsers(), null);
                    }
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
        if (processRequestMessage != null) {
            processRequestMessage.shutdownNow();
        }
        if (processSendingTextMessages != null) {
            processSendingTextMessages.shutdownNow();
        }
        if (processSendingFileMessages != null) {
            processSendingFileMessages.shutdownNow();
        }
    }

    public static void start(Context context, String idUser, String signature) {
        Intent intent = new Intent(context, SpoMessagesService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(ID_USER_KEY, idUser);
        intent.putExtra(SIGNATURE_USER_KEY, signature);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, SpoMessagesService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static void sendMessage(Context context, long idMessage) {
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(context, SpoMessagesService.class);
            intent.setAction(ACTION_SEND_MESSAGE);
            intent.putExtra(MESSAGE_ID_KEY, idMessage);
            context.startService(intent);
        }, 300);
    }

    public static void resendMessage(Context context, ArrayList<Long> idMessages) {
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(context, SpoMessagesService.class);
            intent.setAction(ACTION_RESEND_MESSAGE);
            intent.putExtra(MESSAGE_ID_KEY, idMessages);
            context.startService(intent);
        }, 300);
    }

    public String[] checkUpdateApp(String currentVersionApp) {
        JSONObject result = NetworkRequestUtils.checkUpdate(idUser, signatureUser, currentVersionApp);
        if (result != null) {
            try {
                if (result.has("version") && result.has("link") && result.has("error") && result.getInt("error") == 200) {
                    return new String[]{result.getString("version"), result.getString("link")};
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void removeMessageNotification() {
        if (mSpoNotificationsManager != null) {
            mSpoNotificationsManager.removeMessageNotification();
        }
    }
}