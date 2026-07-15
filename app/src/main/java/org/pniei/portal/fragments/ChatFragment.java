package org.pniei.portal.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.PresenceModel;
import org.pniei.portal.BuildConfig;
import org.pniei.portal.R;
import org.pniei.portal.activities.SecondaryActivity;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoChatMessage;
import org.pniei.portal.database.SpoChatRoom;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.database.SpoFile;
import org.pniei.portal.databinding.ChatBubbleInBinding;
import org.pniei.portal.databinding.ChatBubbleInFirstBinding;
import org.pniei.portal.databinding.ChatBubbleOutBinding;
import org.pniei.portal.databinding.ChatBubbleOutFirstBinding;
import org.pniei.portal.databinding.ChatDateDividerInBinding;
import org.pniei.portal.databinding.ChatDateDividerOutBinding;
import org.pniei.portal.databinding.ChatFragmentBinding;
import org.pniei.portal.databinding.SelectFileCellBinding;
import org.pniei.portal.databinding.SendFileCellBinding;
import org.pniei.portal.listener.SpoChatMessageListener;
import org.pniei.portal.listener.SpoListenerManager;
import org.pniei.portal.notification.SpoNotificationsManager;
import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.FileUtils;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.utils.VoiceMassagePlayer;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static org.pniei.portal.database.SpoFile.STATUS_ERROR;
import static org.pniei.portal.database.SpoFile.STATUS_OK;
import static org.pniei.portal.database.SpoFile.STATUS_READY_TO_DOWNLOAD;
import static org.pniei.portal.database.SpoFile.STATUS_SEND_RECEIVE;

public class ChatFragment extends Fragment implements View.OnClickListener, SpoChatMessageListener, MediaRecorder.OnInfoListener {
    public interface OnLoadMoreListener {
        void onLoadOldMessages();

        void onLoadUnreadMessages();
    }

    private static final String TAG = "ChatFragment";
    private static final String ARG_CHAT_ROOM_ID = "chat_room_id";
    private static final int MESSAGE_IN = 0;
    private static final int MESSAGE_IN_FIRST = 1;
    private static final int MESSAGE_IN_FIRST_DATE = 2;
    private static final int MESSAGE_IN_FIRST_DATE_UNREAD = 3;
    private static final int MESSAGE_IN_FIRST_UNREAD = 4;
    private static final int MESSAGE_OUT = 5;
    private static final int MESSAGE_OUT_FIRST = 6;
    private static final int MESSAGE_OUT_FIRST_DATE = 7;
    private static final String LIST_STATE_KEY = "list_state_key";
    private static final int LIMIT_MES_LOAD = 80;
    private final int MAX_SIZE_FILE = 0x6400000; // 100 MB
    private final int MAX_NUM_FILE = 10;

    private ActivityResultLauncher<Intent> selectExternalFileResult;
    private ActivityResultLauncher<Intent> takePhotoResult;
    private ActivityResultLauncher<String> permissionCameraResult;
    private SpoChatRoom mChatRoom = null;
    private ChatFragmentBinding mBinding;
    private Handler mHandler;
    private ChatMessageAdapter mAdapter;
    private SelectFileAdapter mSelectedFileAdapter;
    private Parcelable mListState;
    private static Context mContext;
    private ViewGroup.LayoutParams messageInputParams;
    private ViewGroup.LayoutParams messageInputContainerParams;
    boolean isSizeSetOver6Lines = false;
    private boolean isNotFoundContact = false;
    private boolean hasUnreadMessage = false;
    private int numUnreadMessage;                                   // Число непрочитанных сообщений ChatRoom
    private int indexMessageBeginLoaded, indexMessageEndLoaded;     // Индексы сообщений (с ранней датой и новой датой соответственно) ChatRoom загруженные в адаптер
    private int numOfChatRoomMessages;
    private ArrayList<Uri> selectedUriFiles;
    private boolean isRecording = false, isRecordEnable = true, isRecordLock = false;
    private boolean recordIsDone = false;
    private Thread timerThread;
    private float dX, dY, startX, startY;
    private String recordFileName;
    private float minX, minY;
    private MediaRecorder mMediaRecorder = null;
    private MediaPlayer player = null;
    private String currentPhotoPath = null;
    private SpoNotificationsManager mSpoNotificationsManager;
    private LinphoneCoreListenerBase mListener = null;

    private boolean isSelectMessage;
    private ArrayList<Boolean> checked;
    int countSetChecked;

    public static ChatFragment newInstance(Context context, long idChatRoom) {
        mContext = context;
        Bundle args = new Bundle();
        args.putLong(ARG_CHAT_ROOM_ID, idChatRoom);
        ChatFragment fragment = new ChatFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long idChatRoom = getArguments().getLong(ARG_CHAT_ROOM_ID);

        if (savedInstanceState != null)
            mListState = savedInstanceState.getParcelable(LIST_STATE_KEY);

        mChatRoom = DBUtils.getChatRoom(idChatRoom);
        mHandler = new Handler(Looper.getMainLooper());

        if (mChatRoom.getType() == SpoChatRoom.ONE && mChatRoom.getIdUsers().get(0).contains("phone:")) {
            isNotFoundContact = true;
        }

        mSpoNotificationsManager = SpoNotificationsManager.ins(mContext);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.chat_fragment, container, false);

        if (getActivity() != null)
            getActivity().getWindow().setBackgroundDrawableResource(R.drawable.background_chat);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(mContext);
        linearLayoutManager.setStackFromEnd(true);
        linearLayoutManager.setReverseLayout(false);

        mBinding.chatMessageList.setLayoutManager(linearLayoutManager);
        mBinding.fileList.setLayoutManager(new LinearLayoutManager(mContext));
        mBinding.btnBack.setOnClickListener(this);
        mBinding.chatLabel.setText(mChatRoom.getNameChat());

        if (isNotFoundContact) {
            // Убираем лишнее
            mBinding.btnCall.setVisibility(View.GONE);
            mBinding.btnSend.setVisibility(View.GONE);
            mBinding.addAttachment.setVisibility(View.GONE);
            mBinding.takePicture.setVisibility(View.GONE);
            mBinding.messageInput.setEnabled(false);
            mBinding.messageInput.setCursorVisible(false);
            mBinding.messageInput.setKeyListener(null);

            // Показываем нужное
            mBinding.btnAddContact.setVisibility(View.VISIBLE);
            mBinding.btnAddContact.setOnClickListener(this);
            mBinding.messageInput.setText(R.string.not_found_contact);
            mBinding.messageInput.setTextColor(getResources().getColor(R.color.design_default_color_error, null));

            return mBinding.getRoot();
        }

        mBinding.chatLabel.setOnClickListener(this);
        mBinding.btnCall.setOnClickListener(this);
        mBinding.btnDeleteMessage.setOnClickListener(this);
        mBinding.btnCancelSelect.setOnClickListener(this);
        mBinding.btnSend.setOnClickListener(this);
        mBinding.addAttachment.setOnClickListener(this);
        mBinding.takePicture.setOnClickListener(this);

        initStatusContactChat();

        messageInputParams = mBinding.messageInput.getLayoutParams();
        messageInputContainerParams = mBinding.messageInputContainer.getLayoutParams();

        mBinding.messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int numLines = mBinding.messageInput.getLineCount();

                if (numLines <= 1) {
                    messageInputParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    messageInputContainerParams.height = (int) (48 * getContext().getResources().getDisplayMetrics().density);
                    isSizeSetOver6Lines = false;


                } else if (numLines <= 6) {
                    messageInputParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    messageInputContainerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    isSizeSetOver6Lines = false;
                } else {
                    if (!isSizeSetOver6Lines) {
                        messageInputContainerParams.height = mBinding.messageInputContainer.getHeight();
                        messageInputParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        isSizeSetOver6Lines = true;
                    }
                }
                mBinding.messageInput.setLayoutParams(messageInputParams);
                mBinding.messageInputContainer.setLayoutParams(messageInputContainerParams);

                if (s.length() > 0) {
                    mBinding.addAttachment.setVisibility(View.GONE);
                    mBinding.takePicture.setVisibility((View.GONE));
                } else {
                    mBinding.addAttachment.setVisibility(View.VISIBLE);
                    mBinding.takePicture.setVisibility((View.VISIBLE));
                }

                mBinding.btnSend.setImageDrawable((s.length() > 0) ? AppCompatResources.getDrawable(mContext, R.drawable.ic_send) : AppCompatResources.getDrawable(mContext, R.drawable.ic_mic));
                isRecordEnable = s.length() <= 0;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        if (mChatRoom.getType() == SpoChatRoom.MANY) {
            mBinding.btnCall.setVisibility(View.GONE);
        } else if (DBUtils.getContactForIdUser(mChatRoom.getIdUsers().get(0)) == null) {
            mBinding.btnCall.setVisibility(View.GONE);
        }

        mBinding.btnGoToEnd.setOnClickListener(v -> {
            mBinding.chatMessageList.getLayoutManager().scrollToPosition(mAdapter.getItemCount() - 1);
        });

        mBinding.btnCancelRecordVoice.setOnClickListener(view -> {
            cancelRecordVoice();
        });

        mBinding.btnSend.setOnLongClickListener(view -> {
            // Проиграть мелодию начала записи голоса
            if (isRecordEnable) {
                if (android.os.Build.VERSION.SDK_INT < 33) {
                    if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        VoiceMassagePlayer.stopPlaying();
                        playBeepAndStartRecordVoice();
                        return false;
                    } else {
                    /*if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.RECORD_AUDIO) ) {
                        // Можно отобразить для чего нужно разрешение
                    }*/
                        requestPermission.launch(Manifest.permission.RECORD_AUDIO);
                        requestPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        return true;
                    }

                } else {
                    if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED /*&&
                        ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED*/) {
                        VoiceMassagePlayer.stopPlaying();
                        playBeepAndStartRecordVoice();
                        return false;
                    } else {
                    /*if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.RECORD_AUDIO) ) {
                        // Можно отобразить для чего нужно разрешение
                    }*/
                        requestPermission.launch(Manifest.permission.RECORD_AUDIO);
                        //requestPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        return true;
                    }
                }
            } else {
                return true;
            }
        });

        mBinding.imgMic.setOnClickListener(view -> {
            if (isRecordLock) {
                endRecordVoice();
                sendVoiceMessage();
            }
        });

        mBinding.btnSend.setOnTouchListener((view, motionEvent) -> {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    if (isRecordEnable) {
                        startX = minX = mBinding.imgMic.getX();
                        startY = minY = mBinding.imgMic.getY();
                        dX = startX - motionEvent.getRawX();
                        dY = startY - motionEvent.getRawY();
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (isRecording && !isRecordLock) {
                        float minXCancel = mBinding.recordCancelLayout.getX();
                        float minYCancel = startY;
                        float minXLock = startX;
                        float minYLock = mBinding.imgLock.getY();
                        float fingerX = motionEvent.getRawX();
                        float fingerY = motionEvent.getRawY();

                        if (((fingerX + dX) >= minXCancel) && ((fingerX + dX) <= minXLock) && ((fingerY + dY) >= minYCancel) && ((fingerY + dY) <= (minYCancel + mBinding.btnSend.getHeight()))) {
                            minX = minXCancel;
                            minY = minYCancel;
                        } else if (((fingerX + dX) >= minXLock) && ((fingerX + dX) <= (minXLock + mBinding.btnSend.getWidth())) && ((fingerY + dY) >= minYLock) && ((fingerY + dY) <= minYCancel)) {
                            minX = minXLock;
                            minY = minYLock;
                        }

                        float newX = fingerX + dX;
                        float newY = fingerY + dY;
                        if (newX < minX)
                            newX = minX;
                        else if (newX > startX)
                            newX = startX;

                        if (newY < minY)
                            newY = minY;
                        else if (newY > startY)
                            newY = startY;

                        if (newX == minXCancel && newY == minYCancel) {
                            mBinding.imgMic.setX(startX);
                            mBinding.imgMic.setY(startY);
                            cancelRecordVoice();
                            break;
                        } else if (newX == minXLock && newY == minYLock) {
                            mBinding.imgMic.setX(startX);
                            mBinding.imgMic.setY(startY);
                            lockRecordVoice();
                            break;
                        }

                        mBinding.imgMic.setX(newX);
                        mBinding.imgMic.setY(newY);
                    }

                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP: {
                    Log.d(TAG, "MotionEvent.ACTION_UP: isRecordEnable=" + isRecordEnable + " isRecordLock=" + isRecordLock + " isRecording=" + isRecording);

                    if (isRecordEnable) {
                        if (!isRecordLock && isRecording) {
                            endRecordVoice();
                            mBinding.imgMic.setX(startX);
                            mBinding.imgMic.setY(startY);
                            sendVoiceMessage();
                        }
                    }
                    break;
                }
            }

            return false;
        });
        registerForSelectFiles();
        registerForTakePhoto();
        registerForPermissionCamera();
        return mBinding.getRoot();
    }

    private void initStatusContactChat() {
        if (mChatRoom.getType() != SpoChatRoom.ONE) {
            mBinding.contactStatus.setVisibility(View.GONE);
            return;
        }


        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            LinphoneFriend[] lfs = lc.getFriendList();
            List<String> id_users = mChatRoom.getIdUsers();
            if (id_users.size() == 1) {
                SpoContact contact = DBUtils.getContactForIdUser(id_users.get(0));
                for (LinphoneFriend lf : lfs) {
                    if (lf.getAddress() != null && lf.getAddress().getUserName() != null && contact != null && lf.getAddress().getUserName().equals(contact.getSipNumber())) {
                        PresenceModel presenceModel = lf.getPresenceModelForUri(lf.getAddress().asStringUriOnly());
                        if (presenceModel != null) {
                            mChatRoom.setStatusNote(presenceModel.getNote("EN") == null ? "" : presenceModel.getNote("EN").getContent());
                            mChatRoom.setStatusInt(presenceModel.getBasicStatus().toInt());
                        }
                        break;
                    }
                }
            }
        }

        switch (mChatRoom.getStatusInt()) {
            case 2:
            case 1:
                mBinding.contactStatus.setImageResource(R.drawable.ic_state_offline);
                break;
            case 0:
                if (mChatRoom.getStatusNote().equals("Ready")) {
                    mBinding.contactStatus.setImageResource(R.drawable.ic_state_online);
                } else {
                    mBinding.contactStatus.setImageResource(R.drawable.ic_state_busy);
                }
                break;
        }
        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
                List<String> id_users = mChatRoom.getIdUsers();
                if (id_users.size() == 1) {
                    SpoContact contact = DBUtils.getContactForIdUser(id_users.get(0));
                    if (lf.getAddress() != null && lf.getAddress().getUserName() != null && contact != null && lf.getAddress().getUserName().equals(contact.getSipNumber())) {
                        PresenceModel presenceModel = lf.getPresenceModelForUri(lf.getAddress().asStringUriOnly());
                        if (presenceModel != null) {
                            mChatRoom.setStatusNote(presenceModel.getNote("EN") == null ? "" : presenceModel.getNote("EN").getContent());
                            mChatRoom.setStatusInt(presenceModel.getBasicStatus().toInt());
                        }
                        mHandler.post(() -> {
                            switch (mChatRoom.getStatusInt()) {
                                case 2:
                                case 1:
                                    mBinding.contactStatus.setImageResource(R.drawable.ic_state_offline);
                                    break;
                                case 0:
                                    if (mChatRoom.getStatusNote().equals("Ready")) {
                                        mBinding.contactStatus.setImageResource(R.drawable.ic_state_online);
                                    } else {
                                        mBinding.contactStatus.setImageResource(R.drawable.ic_state_busy);
                                    }
                                    break;
                            }
                        });
                    }
                }
            }
        };
    }

    //Отмена записи
    private void cancelRecordVoice() {

        Log.d(TAG, "cancelRecordVoice" + " isRecording: " + isRecording);
        if (isRecording) {
            Toast.makeText(mContext, "Отмена записи", Toast.LENGTH_SHORT).show();
            endRecordVoice();
            if (recordFileName != null) {
                File file = new File(recordFileName);
                file.delete();
            }
        }
    }

    // Остановка записи
    private void endRecordVoice() {
        Log.d(TAG, "endRecordVoice");

        mBinding.btnCancelRecordVoice.setVisibility(View.GONE);
        mBinding.bottomBar.setVisibility(View.VISIBLE);
        mBinding.recordVoiceBar.setVisibility(View.INVISIBLE);
        mBinding.recordCancelLayout.setVisibility(View.VISIBLE);
        mBinding.recordLockLayout.setVisibility(View.VISIBLE);
        mBinding.imgMic.setImageResource(R.drawable.ic_mic);

        if (isRecording) {
            isRecordLock = false;
            if (timerThread != null) {
                timerThread.interrupt();
                timerThread = null;
            }
            stopRecording();
        }
    }

    private void lockRecordVoice() {
        Log.d(TAG, "lockRecordVoice");
        if (isRecording) {
            mBinding.recordCancelLayout.setVisibility(View.INVISIBLE);
            mBinding.recordLockLayout.setVisibility(View.INVISIBLE);
            mBinding.btnCancelRecordVoice.setVisibility(View.VISIBLE);
            mBinding.imgMic.setImageResource(R.drawable.ic_send);
            isRecordLock = true;
        }
    }

    private void startRecordVoice() {
        Log.d(TAG, "startRecordVoice");
        // Изменить внешний вид поля ввода сообщения
        // mHandler.post(() -> {

        mBinding.bottomBar.setVisibility(View.INVISIBLE);
        mBinding.recordVoiceBar.setVisibility(View.VISIBLE);
        mBinding.timeRecord.setText("00:00");
        //});

        // Запуск записи
        SimpleDateFormat format = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        recordFileName = Utils.createSendedFile(mContext, "/voice_" + format.format(new Date()) + ".m4a").getAbsolutePath();
        startRecording(recordFileName);

        // Запуск таймера
        timerThread = new Thread(new TimerRecord());
        timerThread.start();
    }

    private void playBeepAndStartRecordVoice() {
        isRecording = true;
        Log.d(TAG, "isRecording = TRUE");
        recordIsDone = false;
        try {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }

            player = new MediaPlayer();
            player.setDataSource(mContext.getApplicationInfo().dataDir + "/sound/beep.wav");
            player.setOnCompletionListener(mediaPlayer -> {
                player.release();
                player = null;
                Log.d(TAG, "beep end");
                if (isRecording) {
                    Log.d(TAG, "beep end startRecordVoice()");
                    startRecordVoice();
                }
            });
            player.setVolume(0.5f, 0.5f);
            player.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            player.prepare();
            player.start();
            Log.d(TAG, "beep start()");
        } catch (IOException e) {
            Log.e(TAG, "beep prepare() failed");
        }
    }

    private class TimerRecord implements Runnable {

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            int div;
            int minutes, seconds;

            Log.d(TAG, "TimerRecord start");

            while (isRecording) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                div = (int) ((System.currentTimeMillis() - startTime) / 1000);
                minutes = div / 60;
                seconds = div % 60;
                String time = String.format("%02d:%02d", minutes, seconds);
                mHandler.post(() -> mBinding.timeRecord.setText(time));
                //Log.d("TAG", "SHOW time = " + time);
            }
            Log.d(TAG, "TimerRecord stop");
        }
    }

    private void startRecording(String fileName) {
        Log.d(TAG, "startRecording");

        synchronized (ChatFragment.this) {
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setOutputFile(fileName);
            mMediaRecorder.setMaxDuration(600000);
            mMediaRecorder.setOnInfoListener(this);

            try {
                mMediaRecorder.prepare();
            } catch (IOException e) {
                Log.e(TAG, "prepare() failed");
                return;
            }

            mMediaRecorder.start();
            Log.d(TAG, "mMediaRecorder start()");
        }
    }

    private void stopRecording() {
        Log.d(TAG, "stopRecording");
        synchronized (ChatFragment.this) {
            isRecording = false;
            if (mMediaRecorder != null) {
                try {
                    mMediaRecorder.stop();
                    mMediaRecorder.release();
                    recordIsDone = true;
                } catch (RuntimeException stopException) {
                    Log.e(TAG, "mMediaRecorder.stop() RuntimeException");
                }
                mMediaRecorder = null;
            }

            Log.d(TAG, "isRecording = FALSE");
        }
    }

    @Override
    public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            endRecordVoice();
            sendVoiceMessage();
        }
    }

    public void updateMessageList() {
        ArrayList<SpoChatMessage> messages;
        int indexViewHolderWithNumberUnread = -1;
        numOfChatRoomMessages = DBUtils.getNumMessage(mChatRoom.getId());
        numUnreadMessage = DBUtils.getUnreadMessagesCount(mChatRoom.getId());

        if (numUnreadMessage > 0) {
            hasUnreadMessage = true;
            messages = new ArrayList<>();
            indexMessageBeginLoaded = (numOfChatRoomMessages - numUnreadMessage) - LIMIT_MES_LOAD;
            if (indexMessageBeginLoaded < 0)
                indexMessageBeginLoaded = 0;
            if ((numOfChatRoomMessages - numUnreadMessage - 1) >= 0)
                messages.addAll(Arrays.asList(DBUtils.getSpoChatMessagesRange(mChatRoom.getId(), indexMessageBeginLoaded, numOfChatRoomMessages - numUnreadMessage - 1, false)));
            indexViewHolderWithNumberUnread = messages.size();
            indexMessageEndLoaded = (numOfChatRoomMessages - numUnreadMessage) + LIMIT_MES_LOAD - 1;
            if (indexMessageEndLoaded > numOfChatRoomMessages - 1)
                indexMessageEndLoaded = numOfChatRoomMessages - 1;
            messages.addAll(Arrays.asList(DBUtils.getSpoChatMessagesRange(mChatRoom.getId(), numOfChatRoomMessages - numUnreadMessage, indexMessageEndLoaded + 1, false)));
        } else {
            indexMessageBeginLoaded = numOfChatRoomMessages - LIMIT_MES_LOAD;
            if (indexMessageBeginLoaded < 0) indexMessageBeginLoaded = 0;
            indexMessageEndLoaded = numOfChatRoomMessages - 1;
            if (indexMessageEndLoaded < 0) indexMessageEndLoaded = -1;
            messages = new ArrayList<>(Arrays.asList(DBUtils.getSpoChatMessagesRange(mChatRoom.getId(), indexMessageBeginLoaded, indexMessageEndLoaded + 1, false)));
            hasUnreadMessage = false;
        }

        // Загрузка из бд списка файлов сообщений
        if (mAdapter == null) {
            mAdapter = new ChatMessageAdapter(messages);
            mAdapter.setOnLoadMoreListener(new OnLoadMoreListener() {
                @Override
                public void onLoadOldMessages() {
                    LoadMessage loadMessage = new LoadMessage();
                    loadMessage.execute(true);
                }

                @Override
                public void onLoadUnreadMessages() {
                    LoadMessage loadMessage = new LoadMessage();
                    loadMessage.execute(false);
                }
            });
            mAdapter.setIndexViewHolderUnread(indexViewHolderWithNumberUnread);
        } else {
            mAdapter.setListMessages(messages);
            mAdapter.setIndexViewHolderUnread(indexViewHolderWithNumberUnread);
        }

        mBinding.chatMessageList.setAdapter(mAdapter);

        if (mListState == null) {
            if (numUnreadMessage > 0) {
                int finalIndexViewHolderWithNumberUnread = indexViewHolderWithNumberUnread;
                mBinding.chatMessageList.post(() -> mBinding.chatMessageList.smoothScrollToPosition(finalIndexViewHolderWithNumberUnread));
            } else {
                if (mAdapter.getItemCount() > 0)
                    mBinding.chatMessageList.post(() -> mBinding.chatMessageList.smoothScrollToPosition(mAdapter.getItemCount() - 1));
            }
        } else {
            mBinding.chatMessageList.getLayoutManager().onRestoreInstanceState(mListState);
        }
    }

    private void showOrHideSelectedFiles() {
        if (selectedUriFiles != null && selectedUriFiles.size() > 0) {
            mBinding.fileInputContainer.setVisibility(View.VISIBLE);
            if (mSelectedFileAdapter == null) {
                mSelectedFileAdapter = new SelectFileAdapter(selectedUriFiles);
            } else {
                mSelectedFileAdapter.setUriFiles(selectedUriFiles);
            }
            mBinding.fileList.setAdapter(mSelectedFileAdapter);
        } else {
            mBinding.fileInputContainer.setVisibility(View.GONE);
        }
    }

    private void addSelectedFiles(Uri fileUri) {
        if (fileUri == null)
            return;

        if (selectedUriFiles == null)
            selectedUriFiles = new ArrayList<>();

        mBinding.fileInputContainer.setVisibility(View.VISIBLE);

        if (!selectedUriFiles.contains(fileUri)) {
            if (mSelectedFileAdapter == null) {
                selectedUriFiles.add(fileUri);
                mSelectedFileAdapter = new SelectFileAdapter(selectedUriFiles);
                mBinding.fileList.setAdapter(mSelectedFileAdapter);
            } else {
                if (mSelectedFileAdapter.getItemCount() < MAX_NUM_FILE) {
                    mSelectedFileAdapter.addUri(fileUri);
                    mSelectedFileAdapter.notifyItemInserted(mSelectedFileAdapter.getItemCount() - 1);
                }
            }

            resizeRecyclerView(mBinding.fileList);
        }
        mBinding.btnSend.setImageDrawable(AppCompatResources.getDrawable(mContext, R.drawable.ic_send));
    }

    private void removeSelectedFiles(int index) {
        if (selectedUriFiles == null || selectedUriFiles.size() == 0)
            return;

        mSelectedFileAdapter.remove(index);

        resizeRecyclerView(mBinding.fileList);

        if (selectedUriFiles.size() == 0) {
            mBinding.fileInputContainer.setVisibility(View.GONE);
            if (mBinding.messageInput.length() == 0) {
                mBinding.btnSend.setImageDrawable(AppCompatResources.getDrawable(mContext, R.drawable.ic_mic));
            }
        }
    }

    private void removeSelectedFiles() {
        mBinding.fileInputContainer.setVisibility(View.GONE);
        if (selectedUriFiles == null || selectedUriFiles.size() == 0)
            return;

        selectedUriFiles.clear();
        mSelectedFileAdapter = null;
    }

    public static void resizeRecyclerView(RecyclerView recyclerView) {
        RecyclerView.Adapter mAdapter = recyclerView.getAdapter();

        int totalHeight = 0, maxHeight = 0;

        for (int i = 0; i < mAdapter.getItemCount(); i++) {
            if (i < 4) {
                maxHeight = 0;
            } else if (i == 4) {
                maxHeight = totalHeight + totalHeight / 8;
            }

            totalHeight += Utils.dpToPx(26, mContext);
        }

        if (maxHeight > 0) {
            ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
            params.height = maxHeight;
            recyclerView.setLayoutParams(params);
        } else if (totalHeight > 0) {
            ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
            params.height = totalHeight;
            recyclerView.setLayoutParams(params);

        }
    }

    private void sendVoiceMessage() {
        if (recordIsDone) {
            SpoChatMessage message = SpoChatMessage.createOutgoingVoiceMessage(recordFileName, mChatRoom);
            message.setId(DBUtils.saveMessage(message));
            if (message.getTypeContent() == SpoChatMessage.FILE) {
                ArrayList<SpoFile> files = message.getSpoFiles();
                if (files != null && files.size() > 0) {
                    for (SpoFile file : files) {
                        file.setIdMessage(message.getId());
                        file.setId(DBUtils.saveFile(file));
                    }
                }
            }

            mChatRoom.setTimeLastMessage(message.getDate());
            DBUtils.updateChatRoom(mChatRoom);

            if (hasUnreadMessage) {
                mAdapter.addMessages(mAdapter.getItemCount(), Arrays.asList(DBUtils.getSpoChatMessagesRange(mChatRoom.getId(), indexMessageEndLoaded + 1, numOfChatRoomMessages - 1, false)));
                indexMessageEndLoaded = numOfChatRoomMessages - 1;

                if (DBUtils.getUnreadMessagesCount(mChatRoom.getId()) > 0) {
                    new Thread(() -> DBUtils.setReadStatusMessages(mChatRoom.getId())).start();
                }
                hasUnreadMessage = false;
                mAdapter.setIndexViewHolderUnread(-1);
            }

            mAdapter.addMessage(message);
            mAdapter.notifyItemInserted(mAdapter.getItemCount() - 1);
            mBinding.chatMessageList.getLayoutManager().scrollToPosition(mAdapter.getItemCount() - 1);

            SpoMessagesService.sendMessage(getContext(), message.getId());
            numOfChatRoomMessages += 1;
            if (isSelectMessage) {
                checked.add(checked.size(), false);
            }
        }
    }


    private void sendMessage() {
        String textMessage = mBinding.messageInput.getText().toString();

        if (textMessage.equals("") && (selectedUriFiles == null || selectedUriFiles.size() == 0)) {
            return;
        }

        SpoChatMessage message = SpoChatMessage.createOutgoingMessage(selectedUriFiles, textMessage, mChatRoom);
        message.setId(DBUtils.saveMessage(message));

        if (message.getTypeContent() == SpoChatMessage.FILE) {
            ArrayList<SpoFile> files = message.getSpoFiles();
            if (files != null && files.size() > 0) {
                for (SpoFile file : files) {
                    file.setIdMessage(message.getId());
                    String nameFile = Utils.getFileName(mContext, Uri.parse(file.getUri()));
                    file.setName(nameFile);
                    file.setType(Utils.getTypeFile(nameFile));
                    file.setId(DBUtils.saveFile(file));
                }
            }
        }

        mChatRoom.setTimeLastMessage(message.getDate());
        DBUtils.updateChatRoom(mChatRoom);

        if (hasUnreadMessage) {
            mAdapter.addMessages(mAdapter.getItemCount(), Arrays.asList(DBUtils.getSpoChatMessagesRange(mChatRoom.getId(), indexMessageEndLoaded + 1, numOfChatRoomMessages - 1, false)));
            indexMessageEndLoaded = numOfChatRoomMessages - 1;

            if (DBUtils.getUnreadMessagesCount(mChatRoom.getId()) > 0) {
                new Thread(() -> DBUtils.setReadStatusMessages(mChatRoom.getId())).start();
            }
            hasUnreadMessage = false;
            mAdapter.setIndexViewHolderUnread(-1);
        }

        mAdapter.addMessage(message);
        mAdapter.notifyItemInserted(mAdapter.getItemCount() - 1);
        mBinding.chatMessageList.getLayoutManager().scrollToPosition(mAdapter.getItemCount() - 1);
        removeSelectedFiles();

        SpoMessagesService.sendMessage(getContext(), message.getId());
        numOfChatRoomMessages += 1;
        if (isSelectMessage) {
            checked.add(checked.size(), false);
        }
    }

    /**
     * Функция дозагрузки сообщений из БД
     *
     * @param isOld true - загрузка старых сообщений, false - загрузка новых сообщений (не прочитанных)
     */
    public List<SpoChatMessage> loadMessagesFromDB(boolean isOld) {
        ArrayList<SpoChatMessage> loadedMessages = null;
        numOfChatRoomMessages = DBUtils.getNumMessage(mChatRoom.getId());

        if (isOld) {
            if (indexMessageBeginLoaded != 0) {
                loadedMessages = new ArrayList<>();
                int startLoadIndex = 0;
                int endLoadIndex = indexMessageBeginLoaded - 1;

                if ((endLoadIndex + 1) > LIMIT_MES_LOAD) {
                    startLoadIndex = (endLoadIndex + 1) - LIMIT_MES_LOAD;
                }
                indexMessageBeginLoaded = startLoadIndex;
                loadedMessages.addAll(Arrays.asList(DBUtils.getSpoChatMessagesRange(mChatRoom.getId(), indexMessageBeginLoaded, endLoadIndex, false)));
            }
        } else {
            if (indexMessageEndLoaded != numOfChatRoomMessages - 1) {
                loadedMessages = new ArrayList<>();

                int endLoadIndex = indexMessageEndLoaded + LIMIT_MES_LOAD;
                if (endLoadIndex >= numOfChatRoomMessages) {
                    endLoadIndex = numOfChatRoomMessages - 1;
                }
                loadedMessages.addAll(Arrays.asList(DBUtils.getSpoChatMessagesRange(mChatRoom.getId(), indexMessageEndLoaded + 1, endLoadIndex, false)));
            }
        }
        if (isSelectMessage) {
            assert loadedMessages != null;
            checked.addAll(0, Collections.nCopies(loadedMessages.size(), false));
        }
        return loadedMessages;
    }

    private class ChatMessageHolder extends RecyclerView.ViewHolder implements SpoChatMessageListener {
        private SpoChatMessage mMessage;
        private SimpleDateFormat formatDate;
        private TextView messageText, date;
        private ImageView statusImage;
        private TextView textDate, textUnread;
        private FrameLayout dividerDate, dividerUnread;
        private boolean mHasDateDivider;
        private boolean mHasUnreadDivider;
        private RecyclerView fileList;
        private FileAdapter mFileAdapter;
        private View mView;


        public ChatMessageHolder(@NonNull View itemView, boolean hasDivider, boolean hasUnreadDivider) {
            super(itemView);
            formatDate = new SimpleDateFormat("HH:mm");
            mHasDateDivider = hasDivider;
            mHasUnreadDivider = hasUnreadDivider;

            if (mHasDateDivider) {
                textDate = itemView.findViewById(R.id.textDate);
                dividerDate = itemView.findViewById(R.id.dividerDate);
                dividerDate.setVisibility(View.VISIBLE);
            }

            if (mHasUnreadDivider) {
                textUnread = itemView.findViewById(R.id.textUnread);
                dividerUnread = itemView.findViewById(R.id.dividerUnread);
                dividerUnread.setVisibility(View.VISIBLE);
            }

            fileList = itemView.findViewById(R.id.fileList);
            messageText = itemView.findViewById(R.id.messageText);
            date = itemView.findViewById(R.id.date);
            statusImage = itemView.findViewById(R.id.statusImage);
            mView = itemView;
        }

        public void bind(SpoChatMessage message, int createViewPosition) {
            mMessage = message;
            if (isSelectMessage) {
                if ((checked != null) & (checked.get(createViewPosition))) {
                    mView.setBackgroundColor(mContext.getColor(R.color.colorSelectMessage));
                } else {
                    mView.setBackgroundColor(Color.TRANSPARENT);
                }
            } else {
                mView.setBackgroundColor(Color.TRANSPARENT);
            }
            // Возможно стоит перенести в onViewAttachedToWindow
            if (mMessage.getIsRead() == SpoChatMessage.UNREAD) {
                mMessage.setIsRead(SpoChatMessage.READ);
                DBUtils.updateMessage(mMessage);
            }
            date.setText(formatDate.format(mMessage.getDate()));

            // Отображение типа сообщения
            switch (mMessage.getTypeContent()) {
                //case SpoChatMessage.IMAGE:
                //case SpoChatMessage.VOICE:
                case SpoChatMessage.FILE:
                    if (mMessage.getMessage() != null && mMessage.getMessage().length() > 0) {
                        messageText.setVisibility(View.VISIBLE);
                        messageText.setText(mMessage.getMessage());
                    } else {
                        messageText.setVisibility(View.GONE);
                    }
                    fileList.setLayoutManager(new LinearLayoutManager(mContext));
                    if (mFileAdapter == null) {
                        mFileAdapter = new FileAdapter(mMessage.getSpoFiles());
                        fileList.setAdapter(mFileAdapter);
                    } else {
                        mFileAdapter.setFiles(mMessage.getSpoFiles());
                        mFileAdapter.notifyDataSetChanged();
                    }
                    fileList.setVisibility(View.VISIBLE);
                    break;
                case SpoChatMessage.TEXT:
                    mFileAdapter = null;
                    messageText.setVisibility(View.VISIBLE);
                    messageText.setText(mMessage.getMessage());
                    fileList.setVisibility(View.GONE);
                    break;
            }

            switch (mMessage.getDir()) {
                case SpoChatMessage.OUT: {
                    // Отображение статуса сообщения
                    switch (mMessage.getStatus()) {
                        case SpoChatMessage.SENDING:
                            statusImage.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_status_message_sending, mContext.getTheme()));
                            break;
                        case SpoChatMessage.SENT:
                            statusImage.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_status_message_sent, mContext.getTheme()));
                            break;
                    }

                    break;
                }
                case SpoChatMessage.IN: {
                    date.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                    statusImage.setVisibility(View.GONE);
                    break;
                }
            }

            if (mHasDateDivider) {
                textDate.setText(Utils.timeDividerToHumanDate(message.getDate()));
            }

            if (mHasUnreadDivider) {
                textUnread.setText(mContext.getResources().getQuantityString(R.plurals.info_num_unread_messages, numUnreadMessage, numUnreadMessage));
            }
        }

        @Override
        public void onSpoChatMessageStateChanged(long idMessage, int status) {
            if (idMessage == mMessage.getId()) {
                Log.d(TAG, "onSpoChatMessageStateChanged " + status);
                mMessage.setStatus(status);
                if (status == SpoChatMessage.SENT) {
                    statusImage.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_status_message_sent, mContext.getTheme()));
                } else {
                    statusImage.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_status_message_sending, mContext.getTheme()));
                }
            }
        }

        @Override
        public void onSpoChatMessageReceived(long idMessage) {
        }

        @Override
        public void onSpoLastChatMessageReceived(long idMessage) {
        }

        @Override
        public void onSpoFileStateChanged(long idFile, int status) {
            if (mMessage.getTypeContent() == SpoChatMessage.FILE && mMessage.getSpoFiles() != null && mMessage.getSpoFiles().size() > 0) {
                ArrayList<SpoFile> files = mMessage.getSpoFiles();
                if (files.contains(DBUtils.getSpoFile(idFile))) {
                    mAdapter.notifyItemChanged(getAbsoluteAdapterPosition());
                }
            }
        }

        @Override
        public void onSpoFileSendingStatus(long idFile, int percentSending) {
        }


        private class FileHolder extends RecyclerView.ViewHolder implements View.OnClickListener, SpoChatMessageListener, VoiceMassagePlayer.VoiceMassagePlayerListener {
            private SendFileCellBinding mBinding;
            private SpoFile mFile;
            private int mStatus;
            private boolean isPlaying = false;

            public FileHolder(@NonNull View itemView) {
                super(itemView);
                mBinding = DataBindingUtil.bind(itemView);
            }

            public void bind(SpoFile file) {
                mFile = file;
                mBinding.nameFile.setText(Utils.getFileName(mFile.getName()));
                createFileUI(mFile.getStatus());
                mBinding.sendFileHolder.setOnClickListener(this);
                mBinding.deleteSendHolder.setOnClickListener(this);
                mBinding.errorSendHolder.setOnClickListener(this);
                // TODO не подписываться если файл принят
                SpoListenerManager.addListener(this);
            }

            private void createFileUI(int status) {
                mStatus = status;
                mBinding.progressSendHolder.setVisibility(View.GONE);
                mBinding.deleteSendHolder.setVisibility(View.GONE);
                mBinding.errorSendHolder.setVisibility(View.GONE);
                mBinding.okSendHolder.setVisibility(View.GONE);
                mBinding.downloadSendHolder.setVisibility(View.GONE);

                switch (mStatus) {
                    case STATUS_SEND_RECEIVE: {
                        mBinding.progressSendHolder.setVisibility(View.VISIBLE);
                        mBinding.progressBar.setIndeterminate(true);
                        break;
                    }
                    case STATUS_READY_TO_DOWNLOAD: {
                        mBinding.downloadSendHolder.setVisibility(View.VISIBLE);
                        break;
                    }
                    case STATUS_OK: {
                        mBinding.okSendHolder.setVisibility(View.VISIBLE);
                        switch (mFile.getType()) {
                            case SpoFile.TYPE_IMAGE: {
                                mBinding.imageOk.setImageDrawable(AppCompatResources.getDrawable(mContext, R.drawable.ic_image));
                                break;
                            }
                            case SpoFile.TYPE_VOICE: {
                                mBinding.imageOk.setImageDrawable(AppCompatResources.getDrawable(mContext, R.drawable.ic_play));
                                break;
                            }
                            case SpoFile.TYPE_OTHER: {
                                mBinding.imageOk.setImageDrawable(AppCompatResources.getDrawable(mContext, R.drawable.ic_file));
                                break;
                            }
                        }
                        break;
                    }
                    case STATUS_ERROR: {
                        mBinding.errorSendHolder.setVisibility(View.VISIBLE);
                        mBinding.deleteSendHolder.setVisibility(View.VISIBLE);
                        break;
                    }
                }
            }

            private void setSendingStatus(int percentSending) {
                if (mBinding.progressBar.isIndeterminate()) {
                    mBinding.progressBar.setIndeterminate(false);
                }
                mBinding.progressBar.setProgress(percentSending);
            }

            @Override
            public void onClick(View v) {
                int viewId = v.getId();

                if (viewId == R.id.sendFileHolder) {
                    switch (mStatus) {
                        case STATUS_SEND_RECEIVE: {
                            Intent intent = new Intent(getContext(), SpoMessagesService.class);
                            intent.setAction(SpoMessagesService.ACTION_STOP_SENDING_FILE);
                            intent.putExtra(SpoMessagesService.FILE_ID_KEY, mFile.getId());
                            getContext().startService(intent);
                            break;
                        }
                        case STATUS_READY_TO_DOWNLOAD: {
                            /*if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) ) {
                                    // Можно отобразить для чего нужно разрешение
                                }
                                requestPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                            } else {*/
                            createFileUI(SpoFile.STATUS_SEND_RECEIVE);
                            Intent intent = new Intent(getContext(), SpoMessagesService.class);
                            intent.setAction(SpoMessagesService.ACTION_DOWNLOAD_FILE);
                            long id = mFile.getId();
                            intent.putExtra(SpoMessagesService.FILE_ID_KEY, mFile.getId());
                            getContext().startService(intent);
                            //}
                            break;
                        }
                        case STATUS_OK: {
                            Uri fileUri = Uri.parse(mFile.getUri());
                            String pathFile = FileUtils.getPath(mContext, fileUri);

                            if (pathFile != null) {
                                File file = new File(pathFile);
                                if (!file.exists()) {
                                    Toast.makeText(mContext, R.string.file_not_found, Toast.LENGTH_SHORT).show();
                                    mFile.setStatus(STATUS_READY_TO_DOWNLOAD);
                                    DBUtils.updateFile(mFile);
                                    DBUtils.updateChatRoom(mChatRoom);
                                    mBinding.imageOk.setImageDrawable(AppCompatResources.getDrawable(mContext, R.drawable.ic_file_download));
                                    updateMessageList();
                                    break;
                                }

                                switch (mFile.getType()) {
                                    case SpoFile.TYPE_IMAGE:
                                    case SpoFile.TYPE_OTHER: {
                                        String fileName = Utils.getFileName(mContext, fileUri);
                                        if (fileName != null) {
                                            String mime = Utils.getMimeType(fileName);

                                            Uri uri;
                                            if (mFile.getDir() == SpoFile.DIR_IN) {
                                                uri = FileProvider.getUriForFile(mContext, BuildConfig.APPLICATION_ID + ".provider", file);
                                            } else {
                                                String strUri = mFile.getUri();
                                                if (strUri.startsWith("file:")) {
                                                    uri = FileProvider.getUriForFile(mContext, BuildConfig.APPLICATION_ID + ".provider", file);
                                                } else {
                                                    uri = Uri.parse(mFile.getUri());
                                                }
                                            }
                                            Intent intent = new Intent(Intent.ACTION_VIEW);
                                            if (mime != null)
                                                intent.setDataAndType(uri, mime);
                                            else
                                                intent.setData(uri);
                                            intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                            if (intent.resolveActivity(mContext.getPackageManager()) != null) {
                                                startActivity(intent);
                                            } else {
                                                Toast.makeText(mContext, R.string.error_no_activity_found, Toast.LENGTH_SHORT).show();
                                            }
                                        } else {
                                            Toast.makeText(mContext, R.string.file_not_found, Toast.LENGTH_SHORT).show();
                                        }
                                        break;
                                    }
                                    case SpoFile.TYPE_VOICE: {
                                        if (isPlaying) {
                                            VoiceMassagePlayer.stopPlaying();
                                        } else {
                                            mBinding.imageOk.setImageDrawable(AppCompatResources.getDrawable(mContext, R.drawable.ic_stop));
                                            mBinding.progressAudioLayout.setVisibility(View.VISIBLE);
                                            mBinding.fileNameLayout.setVisibility(View.GONE);
                                            mBinding.progressAudio.setProgress(0);
                                            isPlaying = true;
                                            VoiceMassagePlayer.startPlaying(pathFile, this);
                                        }
                                        break;
                                    }
                                }
                            } else {
                                Toast.makeText(mContext, R.string.file_not_found, Toast.LENGTH_SHORT).show();
                            }
                            break;
                        }
                    }
                } else if (viewId == R.id.errorSendHolder) {
                    createFileUI(SpoFile.STATUS_SEND_RECEIVE);
                    Intent intent = new Intent(getContext(), SpoMessagesService.class);
                    if (mFile.getDir() == SpoFile.DIR_OUT) {
                        intent.setAction(SpoMessagesService.ACTION_SEND_FILE);
                    } else {
                        intent.setAction(SpoMessagesService.ACTION_DOWNLOAD_FILE);
                    }
                    intent.putExtra(SpoMessagesService.FILE_ID_KEY, mFile.getId());
                    getContext().startService(intent);
                } else if (viewId == R.id.deleteSendHolder) {
                    if (mFileAdapter.getItemCount() == 1) {
                        String text = mMessage.getMessage();
                        if (text == null || text.length() == 0)
                            mAdapter.removeMessage(mMessage);
                        else
                            mFileAdapter.removeFile(mFile);
                    } else {
                        mFileAdapter.removeFile(mFile);
                    }

                    Intent intent = new Intent(getContext(), SpoMessagesService.class);
                    intent.setAction(SpoMessagesService.ACTION_STOP_SENDING_FILE);
                    intent.putExtra(SpoMessagesService.FILE_ID_KEY, mFile.getId());
                    getContext().startService(intent);
                }
            }

            @Override
            public void onSpoChatMessageStateChanged(long idMessage, int status) {
            }

            @Override
            public void onSpoChatMessageReceived(long idMessage) {
            }

            @Override
            public void onSpoLastChatMessageReceived(long idMessage) {
            }

            @Override
            public void onSpoFileStateChanged(long idFile, int status) {
                if (idFile == mFile.getId()) {
                    SpoFile file = DBUtils.getSpoFile(idFile);
                    mFile.setUri(file.getUri());
                    mFile.setStatus(file.getStatus());
                    mFile.setUrlDownload(file.getUrlDownload());
                    mFile.setIdFile(file.getIdFile());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> createFileUI(status));
                    }
                }
            }

            @Override
            public void onSpoFileSendingStatus(long idFile, int percentSending) {
                if (idFile == mFile.getId()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> setSendingStatus(percentSending));
                    }
                }
            }

            @Override
            public void onCompletionPlay() {
                Log.d(TAG, "onCompletionPlay");
                mBinding.imageOk.setImageDrawable(AppCompatResources.getDrawable(mContext, R.drawable.ic_play));
                mBinding.progressAudioLayout.setVisibility(View.GONE);
                mBinding.fileNameLayout.setVisibility(View.VISIBLE);
                isPlaying = false;
            }

            @Override
            public void onProgressPlay(int progress) {
                mHandler.post(() -> {
                    mBinding.progressAudio.setProgress(progress);
                    String currentTime = String.format("%02d:%02d", progress / 1000 / 60, progress / 1000 % 60);
                    mBinding.currentTime.setText(currentTime);
                });
            }

            @Override
            public void onDurationPlay(int duration) {
                mHandler.post(() -> {
                    mBinding.progressAudio.setMax(duration);
                    String durationTime = String.format("%02d:%02d", duration / 1000 / 60, duration / 1000 % 60);
                    mBinding.durationTime.setText(durationTime);
                });
            }
        }

        private class FileAdapter extends RecyclerView.Adapter<FileHolder> {
            private ArrayList<SpoFile> mFiles;

            public FileAdapter(ArrayList<SpoFile> files) {
                mFiles = files;
            }

            @NonNull
            @Override
            public FileHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.send_file_cell, parent, false);
                return new FileHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull FileHolder holder, int position) {

                holder.bind(mFiles.get(position));
            }

            @Override
            public void onViewRecycled(@NonNull FileHolder holder) {
                super.onViewRecycled(holder);
            }

            @Override
            public void onViewAttachedToWindow(@NonNull FileHolder holder) {
                super.onViewAttachedToWindow(holder);
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull FileHolder holder) {
                super.onViewDetachedFromWindow(holder);
            }

            @Override
            public int getItemCount() {
                if (mFiles == null)
                    return 0;
                else
                    return mFiles.size();
            }

            public void setFiles(ArrayList<SpoFile> files) {
                mFiles = files;
                notifyDataSetChanged();
            }

            public void removeFile(SpoFile file) {
                mFiles.remove(file);
                notifyDataSetChanged(); // Для предоствращения анимации удаления
            }
        }
    }

    private class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageHolder> {
        private boolean isLoading;
        private OnLoadMoreListener mOnLoadMoreListener;
        private ArrayList<SpoChatMessage> listMessages;
        private int selectPosition = -1;
        private int indexViewHolderUnread = -1;


        private ChatBubbleInBinding mChatBubbleInBinding;
        private ChatBubbleInFirstBinding mChatBubbleInFirstBinding;
        private ChatBubbleOutBinding mChatBubbleOutBinding;
        private ChatBubbleOutFirstBinding mChatBubbleOutFirstBinding;
        private ChatDateDividerInBinding mChatDateDividerInBinding;
        private ChatDateDividerOutBinding mChatDateDividerOutBinding;


        public ChatMessageAdapter(List<SpoChatMessage> messages) {
            listMessages = new ArrayList<>(messages);
            isSelectMessage = false;
        }

        @Override
        public int getItemViewType(int position) {
            SpoChatMessage message = listMessages.get(position);

            if (message.getDir() == SpoChatMessage.IN) {
                if (position == 0) {
                    if (isDifferentDay(new Date(message.getDate()), new Date()))
                        return indexViewHolderUnread == position ? MESSAGE_IN_FIRST_DATE_UNREAD : MESSAGE_IN_FIRST_DATE;
                    else
                        return indexViewHolderUnread == position ? MESSAGE_IN_FIRST_UNREAD : MESSAGE_IN_FIRST;
                } else {
                    if (isDifferentDay(new Date(message.getDate()), new Date(listMessages.get(position - 1).getDate()))) {
                        return indexViewHolderUnread == position ? MESSAGE_IN_FIRST_DATE_UNREAD : MESSAGE_IN_FIRST_DATE;
                    } else {
                        if (listMessages.get(position - 1).getDir() == SpoChatMessage.OUT) {
                            return indexViewHolderUnread == position ? MESSAGE_IN_FIRST_UNREAD : MESSAGE_IN_FIRST;
                        } else {
                            return indexViewHolderUnread == position ? MESSAGE_IN_FIRST_UNREAD : MESSAGE_IN;
                        }
                    }
                }
            } else {
                if (position == 0) {
                    if (listMessages.size() > 1 && isDifferentDay(new Date(message.getDate()), new Date()))
                        return MESSAGE_OUT_FIRST_DATE;
                    else
                        return MESSAGE_OUT_FIRST;
                } else {
                    if (isDifferentDay(new Date(message.getDate()), new Date(listMessages.get(position - 1).getDate()))) {
                        return MESSAGE_OUT_FIRST_DATE;
                    } else {
                        if (listMessages.get(position - 1).getDir() == SpoChatMessage.IN) {
                            return MESSAGE_OUT_FIRST;
                        } else {
                            return MESSAGE_OUT;
                        }
                    }
                }
            }
        }

        private boolean isDifferentDay(Date date1, Date date2) {
            GregorianCalendar calendar1 = new GregorianCalendar();
            GregorianCalendar calendar2 = new GregorianCalendar();
            calendar1.setTime(date1);
            calendar2.setTime(date2);

            return calendar1.get(Calendar.DAY_OF_YEAR) != calendar2.get(Calendar.DAY_OF_YEAR);
        }

        @NonNull
        @Override
        public ChatMessageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            boolean hasDateDivider = false;
            boolean hasUnreadDivider = false;
            switch (viewType) {
                case MESSAGE_IN: {
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_bubble_in, parent, false);
                    mChatBubbleInBinding = DataBindingUtil.bind(view);
                    break;
                }
                case MESSAGE_IN_FIRST: {
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_bubble_in_first, parent, false);
                    mChatBubbleInFirstBinding = DataBindingUtil.bind(view);
                    break;
                }
                case MESSAGE_IN_FIRST_DATE: {
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_date_divider_in, parent, false);
                    hasDateDivider = true;
                    mChatDateDividerInBinding = DataBindingUtil.bind(view);
                    break;
                }
                case MESSAGE_IN_FIRST_DATE_UNREAD: {
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_date_divider_in, parent, false);
                    hasDateDivider = true;
                    hasUnreadDivider = true;
                    mChatDateDividerInBinding = DataBindingUtil.bind(view);
                    break;
                }
                case MESSAGE_IN_FIRST_UNREAD: {
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_date_divider_in, parent, false);
                    hasUnreadDivider = true;
                    mChatDateDividerInBinding = DataBindingUtil.bind(view);
                    break;
                }
                case MESSAGE_OUT: {
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_bubble_out, parent, false);
                    mChatBubbleOutBinding = DataBindingUtil.bind(view);
                    break;
                }
                case MESSAGE_OUT_FIRST: {
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_bubble_out_first, parent, false);
                    mChatBubbleOutFirstBinding = DataBindingUtil.bind(view);
                    break;
                }
                case MESSAGE_OUT_FIRST_DATE: {
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_date_divider_out, parent, false);
                    hasDateDivider = true;
                    mChatDateDividerOutBinding = DataBindingUtil.bind(view);
                    break;
                }
            }
            return new ChatMessageHolder(view, hasDateDivider, hasUnreadDivider);
        }


        public void setShowSelectedMessages(boolean show) {
            isSelectMessage = show;
            if (isSelectMessage) {
                countSetChecked = 0;
                checked = new ArrayList<>(Collections.nCopies(listMessages.size(), false));
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onBindViewHolder(@NonNull ChatMessageHolder holder, int position) {
            SpoChatMessage message = listMessages.get(position);

            holder.bind(message, position);

            holder.itemView.setOnClickListener(v -> {
                int mPosition = holder.getAbsoluteAdapterPosition();
                if (isSelectMessage) {
                    checked.set(mPosition, !checked.get(mPosition));
                    if (checked.get(mPosition)) {
                        countSetChecked++;
                    } else {
                        countSetChecked--;
                    }
                }
                mAdapter.notifyItemChanged(mPosition);
            });

            holder.itemView.setOnLongClickListener(v -> {
                int mPosition = holder.getAbsoluteAdapterPosition();
                mBinding.btnCall.setVisibility(View.GONE);
                mBinding.btnDeleteMessage.setVisibility(View.VISIBLE);
                mBinding.btnCancelSelect.setVisibility(View.VISIBLE);

                if (!isSelectMessage) {
                    setShowSelectedMessages(true);
                }
                checked.set(mPosition, true);
                countSetChecked++;
                mAdapter.notifyItemChanged(mPosition);
                return true;
            });

            mBinding.btnCancelSelect.setOnClickListener(v -> {
                mBinding.btnCall.setVisibility(View.VISIBLE);
                mBinding.btnDeleteMessage.setVisibility(View.GONE);
                mBinding.btnCancelSelect.setVisibility(View.GONE);
                checked = null;
                isSelectMessage = false;
                mAdapter.notifyDataSetChanged();
            });


            mBinding.btnDeleteMessage.setOnClickListener(v -> {
                for (int positions = (checked.size() - 1); positions >= 0; positions--) {
                    if (checked.get(positions)) {
                        checked.set(positions, false);
                        SpoChatMessage messages = mAdapter.getMessage(positions);
                        if (messages.getStatus() == SpoChatMessage.SENDING) {
                            Intent intent = new Intent(getContext(), SpoMessagesService.class);
                            intent.setAction(SpoMessagesService.ACTION_STOP_SENDING_MESSAGE);
                            intent.putExtra(SpoMessagesService.MESSAGE_ID_KEY, messages.getId());
                            getContext().startService(intent);
                        }

                        if (messages.getTypeContent() == SpoChatMessage.FILE) {
                            ArrayList<SpoFile> files = messages.getSpoFiles();
                            if (files != null && files.size() > 0) {
                                for (SpoFile file : files) {
                                    if (file.getStatus() == SpoFile.STATUS_SEND_RECEIVE) {
                                        Intent intent = new Intent(getContext(), SpoMessagesService.class);
                                        intent.setAction(SpoMessagesService.ACTION_STOP_SENDING_FILE);
                                        intent.putExtra(SpoMessagesService.FILE_ID_KEY, file.getId());
                                        getContext().startService(intent);
                                    }
                                    DBUtils.deleteFile(file);
                                }
                            }
                        }
                        DBUtils.deleteMessage(messages);
                        mAdapter.removeMessage(positions);
                        mAdapter.notifyItemRemoved(positions);
                    }
                }
                mBinding.btnCall.setVisibility(View.VISIBLE);
                mBinding.btnDeleteMessage.setVisibility(View.GONE);
                mBinding.btnCancelSelect.setVisibility(View.GONE);
            });

            if (message.getStatus() == SpoChatMessage.SENDING)
                SpoListenerManager.addListener(holder);

            if (mOnLoadMoreListener != null && !isLoading) {
                if (position <= LIMIT_MES_LOAD / 4 && indexMessageBeginLoaded != 0) {
                    isLoading = true;
                    mOnLoadMoreListener.onLoadOldMessages();
                } else if (position >= (listMessages.size() - 1) - LIMIT_MES_LOAD / 4 && indexMessageEndLoaded < (numOfChatRoomMessages - 1)) {
                    isLoading = true;
                    mOnLoadMoreListener.onLoadUnreadMessages();
                }

                if (hasUnreadMessage && position == (listMessages.size() - 1) && !isLoading && indexMessageEndLoaded == (numOfChatRoomMessages - 1)) {
                    hasUnreadMessage = false;
                }
            }
        }

        @Override
        public void onViewRecycled(ChatMessageHolder holder) {
            holder.itemView.setOnLongClickListener(null);
            SpoListenerManager.removeListener(holder);
            if (holder.mMessage.getSpoFiles() != null) {
                for (int i = 0; i < holder.mMessage.getSpoFiles().size(); i++) {
                    SpoListenerManager.removeListener((SpoChatMessageListener) holder.fileList.findViewHolderForAdapterPosition(i));
                }
            }
            super.onViewRecycled(holder);
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull ChatMessageHolder holder) {
            super.onViewDetachedFromWindow(holder);
            if (holder.getAbsoluteAdapterPosition() == listMessages.size() - 1 && listMessages.size() > 0) {
                mBinding.btnGoToEnd.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onViewAttachedToWindow(@NonNull ChatMessageHolder holder) {
            super.onViewAttachedToWindow(holder);
            if (holder.getAbsoluteAdapterPosition() == listMessages.size() - 1) {
                mBinding.btnGoToEnd.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return listMessages.size();
        }

        public void setListMessages(List<SpoChatMessage> listMessages) {
            this.listMessages = new ArrayList<>(listMessages);
        }

        public void addMessage(SpoChatMessage message) {
            listMessages.add(message);
            indexMessageEndLoaded++;
        }

        public void addMessages(int index, List<SpoChatMessage> message) {
            listMessages.addAll(index, message);
        }

        public int getSelectPosition() {
            return selectPosition;
        }

        public void setSelectPosition(int selectPosition) {
            this.selectPosition = selectPosition;
        }

        public void setOnLoadMoreListener(OnLoadMoreListener listener) {
            mOnLoadMoreListener = listener;
        }

        public void endLoading() {
            this.isLoading = false;
        }

        public void setIndexViewHolderUnread(int index) {
            indexViewHolderUnread = index;
        }

        public int getIndexViewHolderUnread() {
            return indexViewHolderUnread;
        }

        public SpoChatMessage getMessage(int position) {
            return listMessages.get(position);
        }

        public void removeMessage(int position) {
            listMessages.remove(position);
        }

        public void removeMessage(SpoChatMessage message) {
            for (int i = 0; i < listMessages.size(); i++) {
                if (listMessages.get(i).getId() == message.getId()) {
                    listMessages.remove(i);
                    notifyItemRemoved(i);
                    break;
                }
            }
        }
    }

    private class SelectFileHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private Uri uriFile;
        private SelectFileCellBinding mBinding;

        public SelectFileHolder(@NonNull View itemView) {
            super(itemView);
            mBinding = DataBindingUtil.bind(itemView);
            mBinding.btnRemove.setOnClickListener(this);
        }

        public void bind(Uri uri) {
            uriFile = uri;
            mBinding.nameFile.setText(Utils.getFileName(getContext(), uriFile));
        }

        @Override
        public void onClick(View v) {
            int position = getAbsoluteAdapterPosition();
            removeSelectedFiles(position);
        }
    }

    private class SelectFileAdapter extends RecyclerView.Adapter<SelectFileHolder> {
        private ArrayList<Uri> uriFiles;

        public SelectFileAdapter(ArrayList<Uri> uris) {
            uriFiles = uris;
        }

        @NonNull
        @Override
        public SelectFileHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.select_file_cell, parent, false);
            return new SelectFileHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SelectFileHolder holder, int position) {
            holder.bind(uriFiles.get(position));
        }

        @Override
        public int getItemCount() {
            return uriFiles.size();
        }

        public void setUriFiles(ArrayList<Uri> uriFile) {
            this.uriFiles = uriFile;
            notifyDataSetChanged();
        }

        public void addUri(Uri fileUri) {
            uriFiles.add(fileUri);
        }

        public void remove(int index) {
            uriFiles.remove(index);
            notifyItemRemoved(index);
        }

    }

    private class LoadMessage extends AsyncTask<Boolean, Void, List<SpoChatMessage>> {
        private boolean isOld;

        @Override
        protected List<SpoChatMessage> doInBackground(Boolean... booleans) {
            isOld = booleans[0];
            return loadMessagesFromDB(isOld);
        }

        @Override
        protected void onPostExecute(List<SpoChatMessage> loadedMessages) {
            if (loadedMessages != null) {
                if (isOld) {
                    mAdapter.addMessages(0, loadedMessages);
                    mAdapter.notifyItemRangeInserted(0, loadedMessages.size());
                    if (hasUnreadMessage)
                        mAdapter.setIndexViewHolderUnread(mAdapter.getIndexViewHolderUnread() + loadedMessages.size());

                } else {
                    int posStart = mAdapter.getItemCount();
                    int itemCount = loadedMessages.size();
                    mAdapter.addMessages(posStart, loadedMessages);
                    mAdapter.notifyItemRangeInserted(posStart, itemCount);
                    indexMessageEndLoaded += itemCount;
                }
            }
            mAdapter.endLoading();
        }
    }

    @Override
    public void onSpoChatMessageStateChanged(long idMessage, int status) {
    }

    @Override
    public void onSpoChatMessageReceived(long idMessage) {
        if (getActivity() != null) {
            SpoChatMessage msg = DBUtils.getChatMessageById(idMessage);
            if (mChatRoom.getId() == msg.getIdChatRoom()) {
                getActivity().runOnUiThread(() -> {
                    if (!hasUnreadMessage) {
                        if (msg.getTypeContent() == SpoChatMessage.FILE) {
                            ArrayList<SpoFile> files = new ArrayList<>(Arrays.asList(DBUtils.getSpoFiles(msg.getId())));
                            if (files != null && files.size() > 0) {
                                msg.setSpoFiles(files);
                            }
                        }

                        mAdapter.addMessage(msg);
                        mAdapter.notifyItemInserted(mAdapter.getItemCount() - 1);
                        mBinding.chatMessageList.getLayoutManager().scrollToPosition(mAdapter.getItemCount() - 1);
                    }
                    numOfChatRoomMessages += 1;
                    if (isSelectMessage) {
                        checked.add(checked.size(), false);
                    }
                });
            }
        }
    }

    @Override
    public void onSpoLastChatMessageReceived(long idMessage) {
        SpoChatMessage msg = DBUtils.getChatMessageById(idMessage);
        if (mChatRoom.getId() != msg.getIdChatRoom()) {
            SpoChatRoom chatRoom = DBUtils.getChatRoom(msg.getIdChatRoom());
            mSpoNotificationsManager.displayMessageNotification(chatRoom, msg);
        }
    }

    @Override
    public void onSpoFileStateChanged(long idFile, int status) {
    }

    @Override
    public void onSpoFileSendingStatus(long idFile, int percentSending) {

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnSend) {
            sendMessage();
            mBinding.messageInput.setText("");
        } else if (id == R.id.btnBack) {
            Utils.hideKeyboardFrom(mContext, v);
            getActivity().finish();
        } else if (id == R.id.btnAddContact) {
            Fragment fragment = ContactChangeFragment.newInstance(mContext, true, 0);
            Bundle args = new Bundle();
            args.putString(ContactChangeFragment.ARG_NUMBER, mChatRoom.getNameChat());
            fragment.setArguments(args);
            ((SecondaryActivity) getActivity()).displayFragment(fragment, false);
        } else if (id == R.id.chatLabel) {
            if (mChatRoom.getType() == SpoChatRoom.ONE) {
                SpoContact contact = DBUtils.getContactForIdUser(mChatRoom.getIdUsers().get(0));
                if (contact != null) {
                    Fragment fragment = ContactInfoFragment.newInstance(mContext, contact.getId());
                    ((SecondaryActivity) getActivity()).displayFragment(fragment, true);
                }
            } else {
                Fragment fragment = CreateGroupFragment.newInstance(mContext, mChatRoom.getId());
                ((SecondaryActivity) getActivity()).displayFragment(fragment, true);
            }
        } else if (id == R.id.addAttachment) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setType("*/*");
            selectExternalFileResult.launch(intent);
        } else if (id == R.id.takePicture) {
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestCameraPermission();
            } else {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (intent.resolveActivity(mContext.getPackageManager()) != null) {
                    File photoFile = null;
                    try {
                        photoFile = createImageFile();
                    } catch (IOException ex) {
                    }
                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        Uri photoURI = FileProvider.getUriForFile(mContext, BuildConfig.APPLICATION_ID + ".provider", photoFile);
                        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setClipData(ClipData.newRawUri(null, photoURI));
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                        takePhotoResult.launch(intent);
                    }
                }
            }
        } else if (id == R.id.btnCall) {
            if (RegistrationState.flagRegistrationOk == true) {
                SpoContact contact = DBUtils.getContactForIdUser(mChatRoom.getIdUsers().get(0));
                LinphoneManager.getInstance().newOutgoingCall(contact.getSipNumber(), "");
            } else {
                Toast.makeText(getActivity(), "Нет соединения с СКЗИ", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        //TODO: переделать принимающий каталог
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = timeStamp + "_";
        //File storageDir = mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = Utils.createSendedFile(mContext, imageFileName + ".jpg");
        //File image = File.createTempFile(
        //        imageFileName,  /* prefix */
        //        ".jpg",         /* suffix */
        //        storageDir      /* directory */

        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    public void registerForTakePhoto() {
        takePhotoResult = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        if (currentPhotoPath != null) {
                            File takePhotoFile = new File(currentPhotoPath);
                            Uri fileUri = Uri.fromFile(takePhotoFile);
                            addSelectedFiles(fileUri);
                        }
                    }
                });
    }

    public void registerForSelectFiles() {
        selectExternalFileResult = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    boolean isShowToastMaxSize = false;
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent resultData = result.getData();
                        Uri fileUri = resultData.getData();
                        if (fileUri != null) {
                            mContext.getContentResolver().takePersistableUriPermission(fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            String filePath = FileUtils.getPath(mContext, fileUri);
                            if (filePath != null) {
                                if (new File(filePath).length() > MAX_SIZE_FILE) {
                                    Toast.makeText(getContext(), R.string.over_size_file, Toast.LENGTH_SHORT).show();
                                } else {
                                    addSelectedFiles(fileUri);
                                }
                            }
                        } else {
                            ClipData files = resultData.getClipData();
                            if (files != null) {
                                int numFiles = files.getItemCount();
                                if (numFiles > MAX_NUM_FILE) {
                                    numFiles = MAX_NUM_FILE;
                                }

                                for (int i = 0; i < numFiles; i++) {
                                    String filePath = FileUtils.getPath(mContext, files.getItemAt(i).getUri());
                                    mContext.getContentResolver().takePersistableUriPermission(files.getItemAt(i).getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                    if (filePath != null) {
                                        if (new File(filePath).length() > MAX_SIZE_FILE) {
                                            if (!isShowToastMaxSize) {
                                                Toast.makeText(getContext(), R.string.over_size_file, Toast.LENGTH_SHORT).show();
                                                isShowToastMaxSize = true;
                                            }
                                        } else {
                                            addSelectedFiles(files.getItemAt(i).getUri());
                                        }
                                    }
                                }
                            }
                        }


                    }
                });
    }

    public void registerForPermissionCamera() {
        permissionCameraResult = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (!isGranted) {
                Toast.makeText(mContext, "Права для работы камеры не предоставлены", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        showOrHideSelectedFiles();
        updateMessageList();
        mListState = null;
        SpoListenerManager.addListener(this);

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null && mListener != null) {
            lc.addListener(mListener);
        }
//        if(LinphoneManager.getLc().isIncall()){
//            binding.backToCall.setVisibility(View.VISIBLE);
//            binding.startCall.setVisibility(View.GONE);
//        } else {
//            binding.backToCall.setVisibility(View.GONE);
//            binding.startCall.setVisibility(View.VISIBLE);
//        }
    }

    @Override
    public void onPause() {
        VoiceMassagePlayer.stopPlaying();
        cancelRecordVoice();
        SpoListenerManager.removeListener(this);
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null && mListener != null) {
            lc.removeListener(mListener);
        }
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mListState = mBinding.chatMessageList.getLayoutManager().onSaveInstanceState();
        outState.putParcelable(LIST_STATE_KEY, mListState);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Intent intent = new Intent(getContext(), SpoMessagesService.class);
        intent.setAction(SpoMessagesService.ACTION_SET_DELAY_REQUEST);
        intent.putExtra(SpoMessagesService.DELAY_KEY, true);
        context.startService(intent);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Intent intent = new Intent(getContext(), SpoMessagesService.class);
        intent.setAction(SpoMessagesService.ACTION_SET_DELAY_REQUEST);
        intent.putExtra(SpoMessagesService.DELAY_KEY, false);
        getContext().startService(intent);
        new Thread(() -> DBUtils.saveDataBase()).start();
    }

    private ActivityResultLauncher<String> requestPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
    });

    private void requestCameraPermission() {
        permissionCameraResult.launch(Manifest.permission.CAMERA);
    }

    public ChatMessageAdapter getAdapter() {
        return mAdapter;
    }
}
