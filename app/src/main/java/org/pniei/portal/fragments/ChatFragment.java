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
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private WeakReference<Context> mContextRef;
    private ViewGroup.LayoutParams messageInputParams;
    private ViewGroup.LayoutParams messageInputContainerParams;
    boolean isSizeSetOver6Lines = false;
    private boolean isNotFoundContact = false;
    private boolean hasUnreadMessage = false;
    private int numUnreadMessage;
    private int indexMessageBeginLoaded, indexMessageEndLoaded;
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
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private boolean isSelectMessage;
    private ArrayList<Boolean> checked;
    int countSetChecked;

    public static ChatFragment newInstance(Context context, long idChatRoom) {
        Bundle args = new Bundle();
        args.putLong(ARG_CHAT_ROOM_ID, idChatRoom);
        ChatFragment fragment = new ChatFragment();
        fragment.setArguments(args);
        fragment.mContextRef = new WeakReference<>(context);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        assert getArguments() != null;
        long idChatRoom = getArguments().getLong(ARG_CHAT_ROOM_ID);

        if (savedInstanceState != null) {
            mListState = savedInstanceState.getParcelable(LIST_STATE_KEY);
        }

        mChatRoom = DBUtils.getChatRoom(idChatRoom);
        mHandler = new Handler(Looper.getMainLooper());

        Context context = getContextSafe();
        if (context != null) {
            mSpoNotificationsManager = SpoNotificationsManager.ins(context);
        }

        if (mChatRoom != null && mChatRoom.getType() == SpoChatRoom.ONE
                && mChatRoom.getIdUsers() != null && !mChatRoom.getIdUsers().isEmpty()
                && mChatRoom.getIdUsers().get(0).contains("phone:")) {
            isNotFoundContact = true;
        }
    }

    private Context getContextSafe() {
        return mContextRef != null ? mContextRef.get() : null;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.chat_fragment, container, false);

        Context context = getContextSafe();
        if (context == null) {
            return mBinding.getRoot();
        }

        if (getActivity() != null) {
            getActivity().getWindow().setBackgroundDrawableResource(R.drawable.background_chat);
        }

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
        linearLayoutManager.setStackFromEnd(true);
        linearLayoutManager.setReverseLayout(false);

        mBinding.chatMessageList.setLayoutManager(linearLayoutManager);
        mBinding.fileList.setLayoutManager(new LinearLayoutManager(context));
        mBinding.btnBack.setOnClickListener(this);

        if (mChatRoom != null) {
            mBinding.chatLabel.setText(mChatRoom.getNameChat());
        }

        if (isNotFoundContact) {
            setupNotFoundContactView();
            return mBinding.getRoot();
        }

        setupNormalView();
        registerForSelectFiles();
        registerForTakePhoto();
        registerForPermissionCamera();
        initStatusContactChat();

        return mBinding.getRoot();
    }

    private void setupNotFoundContactView() {
        mBinding.btnCall.setVisibility(View.GONE);
        mBinding.btnSend.setVisibility(View.GONE);
        mBinding.addAttachment.setVisibility(View.GONE);
        mBinding.takePicture.setVisibility(View.GONE);
        mBinding.messageInput.setEnabled(false);
        mBinding.messageInput.setCursorVisible(false);
        mBinding.messageInput.setKeyListener(null);
        mBinding.btnAddContact.setVisibility(View.VISIBLE);
        mBinding.btnAddContact.setOnClickListener(this);
        mBinding.messageInput.setText(R.string.not_found_contact);
        mBinding.messageInput.setTextColor(getResources().getColor(R.color.design_default_color_error, null));
    }

    private void setupNormalView() {
        Context context = getContextSafe();
        if (context == null) return;

        mBinding.chatLabel.setOnClickListener(this);
        mBinding.btnCall.setOnClickListener(this);
        mBinding.btnDeleteMessage.setOnClickListener(this);
        mBinding.btnCancelSelect.setOnClickListener(this);
        mBinding.btnSend.setOnClickListener(this);
        mBinding.addAttachment.setOnClickListener(this);
        mBinding.takePicture.setOnClickListener(this);

        messageInputParams = mBinding.messageInput.getLayoutParams();
        messageInputContainerParams = mBinding.messageInputContainer.getLayoutParams();

        setupTextWatcher();

        if (mChatRoom != null) {
            if (mChatRoom.getType() == SpoChatRoom.MANY) {
                mBinding.btnCall.setVisibility(View.GONE);
            } else if (mChatRoom.getIdUsers() != null && !mChatRoom.getIdUsers().isEmpty()) {
                SpoContact contact = DBUtils.getContactForIdUser(mChatRoom.getIdUsers().get(0));
                if (contact == null) {
                    mBinding.btnCall.setVisibility(View.GONE);
                }
            }
        }

        setupRecordVoiceUI();
    }

    private void setupTextWatcher() {
        Context context = getContextSafe();
        if (context == null) return;

        mBinding.messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int numLines = mBinding.messageInput.getLineCount();

                if (numLines <= 1) {
                    messageInputParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    messageInputContainerParams.height = (int) (48 * requireContext().getResources().getDisplayMetrics().density);
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
                    mBinding.takePicture.setVisibility(View.GONE);
                } else {
                    mBinding.addAttachment.setVisibility(View.VISIBLE);
                    mBinding.takePicture.setVisibility(View.VISIBLE);
                }

                mBinding.btnSend.setImageDrawable((s.length() > 0) ?
                        AppCompatResources.getDrawable(context, R.drawable.ic_send) :
                        AppCompatResources.getDrawable(context, R.drawable.ic_mic));
                isRecordEnable = s.length() <= 0;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupRecordVoiceUI() {
        Context context = getContextSafe();
        if (context == null) return;

        mBinding.btnGoToEnd.setOnClickListener(v -> {
            if (mBinding.chatMessageList.getLayoutManager() != null && mAdapter != null) {
                mBinding.chatMessageList.getLayoutManager().scrollToPosition(mAdapter.getItemCount() - 1);
            }
        });

        mBinding.btnCancelRecordVoice.setOnClickListener(view -> cancelRecordVoice());

        mBinding.btnSend.setOnLongClickListener(view -> {
            if (isRecordEnable) {
                if (android.os.Build.VERSION.SDK_INT < 33) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        VoiceMassagePlayer.stopPlaying();
                        playBeepAndStartRecordVoice();
                        return false;
                    } else {
                        requestPermission.launch(Manifest.permission.RECORD_AUDIO);
                        requestPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        return true;
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        VoiceMassagePlayer.stopPlaying();
                        playBeepAndStartRecordVoice();
                        return false;
                    } else {
                        requestPermission.launch(Manifest.permission.RECORD_AUDIO);
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
                        handleRecordMove(motionEvent);
                    }
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP: {
                    Log.d(TAG, "MotionEvent.ACTION_UP: isRecordEnable=" + isRecordEnable + " isRecordLock=" + isRecordLock + " isRecording=" + isRecording);
                    if (isRecordEnable && !isRecordLock && isRecording) {
                        endRecordVoice();
                        mBinding.imgMic.setX(startX);
                        mBinding.imgMic.setY(startY);
                        sendVoiceMessage();
                    }
                    break;
                }
            }
            return false;
        });
    }

    private void handleRecordMove(MotionEvent motionEvent) {
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
        if (newX < minX) newX = minX;
        else if (newX > startX) newX = startX;

        if (newY < minY) newY = minY;
        else if (newY > startY) newY = startY;

        if (newX == minXCancel && newY == minYCancel) {
            mBinding.imgMic.setX(startX);
            mBinding.imgMic.setY(startY);
            cancelRecordVoice();
        } else if (newX == minXLock && newY == minYLock) {
            mBinding.imgMic.setX(startX);
            mBinding.imgMic.setY(startY);
            lockRecordVoice();
        } else {
            mBinding.imgMic.setX(newX);
            mBinding.imgMic.setY(newY);
        }
    }

    private void initStatusContactChat() {
        if (mChatRoom == null || mChatRoom.getType() != SpoChatRoom.ONE) {
            if (mBinding != null) {
                mBinding.contactStatus.setVisibility(View.GONE);
            }
            return;
        }

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            LinphoneFriend[] lfs = lc.getFriendList();
            List<String> id_users = mChatRoom.getIdUsers();
            if (id_users != null && id_users.size() == 1) {
                SpoContact contact = DBUtils.getContactForIdUser(id_users.get(0));
                for (LinphoneFriend lf : lfs) {
                    if (lf.getAddress() != null && lf.getAddress().getUserName() != null &&
                            contact != null && lf.getAddress().getUserName().equals(contact.getSipNumber())) {
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

        updateContactStatus();

        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
                if (mChatRoom == null || mChatRoom.getIdUsers() == null) return;

                List<String> id_users = mChatRoom.getIdUsers();
                if (id_users.size() == 1) {
                    SpoContact contact = DBUtils.getContactForIdUser(id_users.get(0));
                    if (lf.getAddress() != null && lf.getAddress().getUserName() != null &&
                            contact != null && lf.getAddress().getUserName().equals(contact.getSipNumber())) {
                        PresenceModel presenceModel = lf.getPresenceModelForUri(lf.getAddress().asStringUriOnly());
                        if (presenceModel != null) {
                            mChatRoom.setStatusNote(presenceModel.getNote("EN") == null ? "" : presenceModel.getNote("EN").getContent());
                            mChatRoom.setStatusInt(presenceModel.getBasicStatus().toInt());
                        }
                        if (mHandler != null) {
                            mHandler.post(ChatFragment.this::updateContactStatus);
                        }
                    }
                }
            }
        };
    }

    private void updateContactStatus() {
        if (mChatRoom == null || mBinding == null) return;

        switch (mChatRoom.getStatusInt()) {
            case 2:
            case 1:
                mBinding.contactStatus.setImageResource(R.drawable.ic_state_offline);
                break;
            case 0:
                if ("Ready".equals(mChatRoom.getStatusNote())) {
                    mBinding.contactStatus.setImageResource(R.drawable.ic_state_online);
                } else {
                    mBinding.contactStatus.setImageResource(R.drawable.ic_state_busy);
                }
                break;
        }
    }

    private void cancelRecordVoice() {
        Log.d(TAG, "cancelRecordVoice isRecording: " + isRecording);

        if (isRecording) {
            Toast.makeText(getContextSafe(), "Отмена записи", Toast.LENGTH_SHORT).show();
            endRecordVoice();
            deleteRecordFile();
        }
    }

    private void deleteRecordFile() {
        if (recordFileName == null || recordFileName.isEmpty()) {
            Log.w(TAG, "recordFileName is null or empty");
            return;
        }

        File file = new File(recordFileName);
        if (!file.exists()) {
            Log.w(TAG, "Record file does not exist: " + recordFileName);
            return;
        }

        boolean deleted = file.delete();
        if (deleted) {
            Log.d(TAG, "Record file deleted: " + recordFileName);
            recordFileName = null;
        } else {
            Log.e(TAG, "Failed to delete record file: " + recordFileName);
        }
    }

    private void endRecordVoice() {
        Log.d(TAG, "endRecordVoice");

        if (mBinding != null) {
            mBinding.btnCancelRecordVoice.setVisibility(View.GONE);
            mBinding.bottomBar.setVisibility(View.VISIBLE);
            mBinding.recordVoiceBar.setVisibility(View.INVISIBLE);
            mBinding.recordCancelLayout.setVisibility(View.VISIBLE);
            mBinding.recordLockLayout.setVisibility(View.VISIBLE);
            mBinding.imgMic.setImageResource(R.drawable.ic_mic);
        }

        if (isRecording) {
            isRecordLock = false;
            stopTimerThread();
            stopRecording();
        }
    }

    private void stopTimerThread() {
        if (timerThread != null) {
            timerThread.interrupt();
            try {
                timerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            timerThread = null;
        }
    }

    private void lockRecordVoice() {
        Log.d(TAG, "lockRecordVoice");
        if (isRecording && mBinding != null) {
            mBinding.recordCancelLayout.setVisibility(View.INVISIBLE);
            mBinding.recordLockLayout.setVisibility(View.INVISIBLE);
            mBinding.btnCancelRecordVoice.setVisibility(View.VISIBLE);
            mBinding.imgMic.setImageResource(R.drawable.ic_send);
            isRecordLock = true;
        }
    }

    @SuppressLint("SetTextI18n")
    private void startRecordVoice() {
        Log.d(TAG, "startRecordVoice");

        if (mBinding != null) {
            mBinding.bottomBar.setVisibility(View.INVISIBLE);
            mBinding.recordVoiceBar.setVisibility(View.VISIBLE);
            mBinding.timeRecord.setText("00:00");
        }

        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat format = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");

        Context context = getContextSafe();
        if (context == null) return;

        File recordFile = Utils.createSendedFile(context, "/voice_" + format.format(new Date()) + ".m4a");
        if (recordFile == null) return;

        recordFileName = recordFile.getAbsolutePath();
        startRecording(recordFileName);

        timerThread = new Thread(new TimerRecord());
        timerThread.start();
    }

    private void playBeepAndStartRecordVoice() {
        isRecording = true;
        Log.d(TAG, "isRecording = TRUE");
        recordIsDone = false;

        Context context = getContextSafe();
        if (context == null) return;

        try {
            if (player != null) {
                releasePlayer();
            }

            player = new MediaPlayer();
            player.setDataSource(context.getApplicationInfo().dataDir + "/sound/beep.wav");
            player.setOnCompletionListener(mediaPlayer -> {
                releasePlayer();
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
            Log.e(TAG, "beep prepare() failed: " + e.getMessage());
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
                player.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing player: " + e.getMessage());
            }
            player = null;
        }
    }

    private class TimerRecord implements Runnable {
        private volatile boolean isRunning = true;

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            Log.d(TAG, "TimerRecord start");

            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Log.d(TAG, "TimerRecord interrupted");
                    Thread.currentThread().interrupt();
                    break;
                }

                if (!isRunning) break;

                long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                int minutes = (int) (elapsedSeconds / 60);
                int seconds = (int) (elapsedSeconds % 60);

                @SuppressLint("DefaultLocale")
                String time = String.format("%02d:%02d", minutes, seconds);

                if (mHandler != null) {
                    mHandler.post(() -> {
                        if (mBinding != null) {
                            mBinding.timeRecord.setText(time);
                        }
                    });
                }
            }

            Log.d(TAG, "TimerRecord stop");
        }

        public void stop() {
            isRunning = false;
        }
    }

    private void startRecording(String fileName) {
        Log.d(TAG, "startRecording");

        synchronized (this) {
            try {
                mMediaRecorder = new MediaRecorder();
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mMediaRecorder.setOutputFile(fileName);
                mMediaRecorder.setMaxDuration(600000);
                mMediaRecorder.setOnInfoListener(this);
                mMediaRecorder.prepare();
                mMediaRecorder.start();
                Log.d(TAG, "mMediaRecorder start()");
            } catch (IOException e) {
                Log.e(TAG, "prepare() failed: " + e.getMessage());
                releaseMediaRecorder();
            } catch (RuntimeException e) {
                Log.e(TAG, "startRecording() failed: " + e.getMessage());
                releaseMediaRecorder();
            }
        }
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            try {
                mMediaRecorder.release();
            } catch (RuntimeException e) {
                Log.e(TAG, "mMediaRecorder.release() error: " + e.getMessage());
            }
            mMediaRecorder = null;
        }
    }

    private void stopRecording() {
        Log.d(TAG, "stopRecording");
        synchronized (this) {
            isRecording = false;
            if (mMediaRecorder != null) {
                try {
                    mMediaRecorder.stop();
                    recordIsDone = true;
                } catch (RuntimeException stopException) {
                    Log.e(TAG, "mMediaRecorder.stop() RuntimeException: " + stopException.getMessage());
                    recordIsDone = false;
                } finally {
                    releaseMediaRecorder();
                }
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
        if (mChatRoom == null) return;

        ArrayList<SpoChatMessage> messages;
        int indexViewHolderWithNumberUnread = -1;
        numOfChatRoomMessages = DBUtils.getNumMessage(mChatRoom.getId());
        numUnreadMessage = DBUtils.getUnreadMessagesCount(mChatRoom.getId());

        if (numUnreadMessage > 0) {
            hasUnreadMessage = true;
            messages = new ArrayList<>();
            indexMessageBeginLoaded = (numOfChatRoomMessages - numUnreadMessage) - LIMIT_MES_LOAD;
            if (indexMessageBeginLoaded < 0) indexMessageBeginLoaded = 0;

            if ((numOfChatRoomMessages - numUnreadMessage - 1) >= 0) {
                SpoChatMessage[] range = DBUtils.getSpoChatMessagesRange(
                        mChatRoom.getId(),
                        indexMessageBeginLoaded,
                        numOfChatRoomMessages - numUnreadMessage - 1,
                        false);
                if (range != null) {
                    messages.addAll(Arrays.asList(range));
                }
            }

            indexViewHolderWithNumberUnread = messages.size();
            indexMessageEndLoaded = (numOfChatRoomMessages - numUnreadMessage) + LIMIT_MES_LOAD - 1;
            if (indexMessageEndLoaded > numOfChatRoomMessages - 1) {
                indexMessageEndLoaded = numOfChatRoomMessages - 1;
            }

            SpoChatMessage[] range2 = DBUtils.getSpoChatMessagesRange(
                    mChatRoom.getId(),
                    numOfChatRoomMessages - numUnreadMessage,
                    indexMessageEndLoaded + 1,
                    false);
            if (range2 != null) {
                messages.addAll(Arrays.asList(range2));
            }
        } else {
            indexMessageBeginLoaded = numOfChatRoomMessages - LIMIT_MES_LOAD;
            if (indexMessageBeginLoaded < 0) indexMessageBeginLoaded = 0;
            indexMessageEndLoaded = numOfChatRoomMessages - 1;
            if (indexMessageEndLoaded < 0) indexMessageEndLoaded = -1;

            SpoChatMessage[] range = DBUtils.getSpoChatMessagesRange(
                    mChatRoom.getId(),
                    indexMessageBeginLoaded,
                    indexMessageEndLoaded + 1,
                    false);
            messages = new ArrayList<>();
            if (range != null) {
                messages.addAll(Arrays.asList(range));
            }
            hasUnreadMessage = false;
        }

        if (mAdapter == null) {
            mAdapter = new ChatMessageAdapter(messages);
            mAdapter.setOnLoadMoreListener(new OnLoadMoreListener() {
                @Override
                public void onLoadOldMessages() {
                    loadMessages(true);
                }

                @Override
                public void onLoadUnreadMessages() {
                    loadMessages(false);
                }
            });
            mAdapter.setIndexViewHolderUnread(indexViewHolderWithNumberUnread);
        } else {
            mAdapter.setListMessages(messages);
            mAdapter.setIndexViewHolderUnread(indexViewHolderWithNumberUnread);
        }

        if (mBinding != null) {
            mBinding.chatMessageList.setAdapter(mAdapter);

            if (mListState == null) {
                if (numUnreadMessage > 0) {
                    int finalIndexViewHolderWithNumberUnread = indexViewHolderWithNumberUnread;
                    mBinding.chatMessageList.post(() -> {
                        if (mBinding != null) {
                            mBinding.chatMessageList.smoothScrollToPosition(finalIndexViewHolderWithNumberUnread);
                        }
                    });
                } else {
                    if (mAdapter.getItemCount() > 0) {
                        mBinding.chatMessageList.post(() -> {
                            if (mBinding != null) {
                                mBinding.chatMessageList.smoothScrollToPosition(mAdapter.getItemCount() - 1);
                            }
                        });
                    }
                }
            } else {
                if (mBinding.chatMessageList.getLayoutManager() != null) {
                    mBinding.chatMessageList.getLayoutManager().onRestoreInstanceState(mListState);
                }
            }
        }
    }

    private void showOrHideSelectedFiles() {
        if (selectedUriFiles != null && !selectedUriFiles.isEmpty()) {
            if (mBinding != null) {
                mBinding.fileInputContainer.setVisibility(View.VISIBLE);
                if (mSelectedFileAdapter == null) {
                    mSelectedFileAdapter = new SelectFileAdapter(selectedUriFiles);
                } else {
                    mSelectedFileAdapter.setUriFiles(selectedUriFiles);
                }
                mBinding.fileList.setAdapter(mSelectedFileAdapter);
            }
        } else {
            if (mBinding != null) {
                mBinding.fileInputContainer.setVisibility(View.GONE);
            }
        }
    }

    private void addSelectedFiles(Uri fileUri) {
        if (fileUri == null || mBinding == null) return;

        if (selectedUriFiles == null) {
            selectedUriFiles = new ArrayList<>();
        }

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

        Context context = getContextSafe();
        if (context != null) {
            mBinding.btnSend.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_send));
        }
    }

    private void removeSelectedFiles(int index) {
        if (selectedUriFiles == null || selectedUriFiles.isEmpty() || mSelectedFileAdapter == null) {
            return;
        }

        mSelectedFileAdapter.remove(index);
        resizeRecyclerView(mBinding.fileList);

        if (selectedUriFiles.isEmpty()) {
            if (mBinding != null) {
                mBinding.fileInputContainer.setVisibility(View.GONE);
                if (mBinding.messageInput.length() == 0) {
                    Context context = getContextSafe();
                    if (context != null) {
                        mBinding.btnSend.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_mic));
                    }
                }
            }
        }
    }

    private void removeSelectedFiles() {
        if (mBinding != null) {
            mBinding.fileInputContainer.setVisibility(View.GONE);
        }
        if (selectedUriFiles == null || selectedUriFiles.isEmpty()) {
            return;
        }
        selectedUriFiles.clear();
        mSelectedFileAdapter = null;
    }

    public static void resizeRecyclerView(RecyclerView recyclerView) {
        if (recyclerView == null) return;

        RecyclerView.Adapter<?> mAdapter = recyclerView.getAdapter();
        if (mAdapter == null) return;

        int totalHeight = 0, maxHeight = 0;

        for (int i = 0; i < mAdapter.getItemCount(); i++) {
            if (i < 4) {
                maxHeight = 0;
            } else if (i == 4) {
                maxHeight = totalHeight + totalHeight / 8;
            }
            totalHeight += (int) Utils.dpToPx(26, recyclerView.getContext());
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
        if (!recordIsDone || mChatRoom == null) return;

        SpoChatMessage message = SpoChatMessage.createOutgoingVoiceMessage(recordFileName, mChatRoom);
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

        mChatRoom.setTimeLastMessage(message.getDate());
        DBUtils.updateChatRoom(mChatRoom);

        if (hasUnreadMessage) {
            SpoChatMessage[] range = DBUtils.getSpoChatMessagesRange(
                    mChatRoom.getId(),
                    indexMessageEndLoaded + 1,
                    numOfChatRoomMessages - 1,
                    false);
            if (range != null) {
                mAdapter.addMessages(mAdapter.getItemCount(), Arrays.asList(range));
            }
            indexMessageEndLoaded = numOfChatRoomMessages - 1;

            if (DBUtils.getUnreadMessagesCount(mChatRoom.getId()) > 0) {
                new Thread(() -> DBUtils.setReadStatusMessages(mChatRoom.getId())).start();
            }
            hasUnreadMessage = false;
            mAdapter.setIndexViewHolderUnread(-1);
        }

        mAdapter.addMessage(message);
        mAdapter.notifyItemInserted(mAdapter.getItemCount() - 1);

        if (mBinding != null && mBinding.chatMessageList.getLayoutManager() != null) {
            mBinding.chatMessageList.getLayoutManager().scrollToPosition(mAdapter.getItemCount() - 1);
        }

        Context context = getContextSafe();
        if (context != null) {
            SpoMessagesService.sendMessage(context, message.getId());
        }

        numOfChatRoomMessages += 1;
        if (isSelectMessage && checked != null) {
            checked.add(false);
        }
    }

    private void sendMessage() {
        if (mChatRoom == null) return;

        String textMessage = mBinding.messageInput.getText().toString();

        if (textMessage.isEmpty() && (selectedUriFiles == null || selectedUriFiles.isEmpty())) {
            return;
        }

        SpoChatMessage message = SpoChatMessage.createOutgoingMessage(selectedUriFiles, textMessage, mChatRoom);
        message.setId(DBUtils.saveMessage(message));

        if (message.getTypeContent() == SpoChatMessage.FILE) {
            ArrayList<SpoFile> files = message.getSpoFiles();
            if (files != null && !files.isEmpty()) {
                Context context = getContextSafe();
                for (SpoFile file : files) {
                    file.setIdMessage(message.getId());
                    if (context != null) {
                        String nameFile = Utils.getFileName(context, Uri.parse(file.getUri()));
                        file.setName(nameFile);
                        if (nameFile != null) {
                            file.setType(Utils.getTypeFile(nameFile));
                        }
                    }
                    file.setId(DBUtils.saveFile(file));
                }
            }
        }

        mChatRoom.setTimeLastMessage(message.getDate());
        DBUtils.updateChatRoom(mChatRoom);

        if (hasUnreadMessage) {
            SpoChatMessage[] range = DBUtils.getSpoChatMessagesRange(
                    mChatRoom.getId(),
                    indexMessageEndLoaded + 1,
                    numOfChatRoomMessages - 1,
                    false);
            if (range != null) {
                mAdapter.addMessages(mAdapter.getItemCount(), Arrays.asList(range));
            }
            indexMessageEndLoaded = numOfChatRoomMessages - 1;

            if (DBUtils.getUnreadMessagesCount(mChatRoom.getId()) > 0) {
                new Thread(() -> DBUtils.setReadStatusMessages(mChatRoom.getId())).start();
            }
            hasUnreadMessage = false;
            mAdapter.setIndexViewHolderUnread(-1);
        }

        mAdapter.addMessage(message);
        mAdapter.notifyItemInserted(mAdapter.getItemCount() - 1);

        if (mBinding != null && mBinding.chatMessageList.getLayoutManager() != null) {
            mBinding.chatMessageList.getLayoutManager().scrollToPosition(mAdapter.getItemCount() - 1);
        }

        removeSelectedFiles();

        Context context = getContextSafe();
        if (context != null) {
            SpoMessagesService.sendMessage(context, message.getId());
        }

        numOfChatRoomMessages += 1;
        if (isSelectMessage && checked != null) {
            checked.add(false);
        }
    }

    private void loadMessages(boolean isOld) {
        mExecutor.execute(() -> {
            List<SpoChatMessage> loadedMessages = loadMessagesFromDB(isOld);
            if (mHandler != null) {
                mHandler.post(() -> {
                    if (loadedMessages != null && !loadedMessages.isEmpty() && mAdapter != null) {
                        if (isOld) {
                            mAdapter.addMessages(0, loadedMessages);
                            mAdapter.notifyItemRangeInserted(0, loadedMessages.size());
                            if (hasUnreadMessage && mAdapter.getIndexViewHolderUnread() >= 0) {
                                mAdapter.setIndexViewHolderUnread(mAdapter.getIndexViewHolderUnread() + loadedMessages.size());
                            }
                        } else {
                            int posStart = mAdapter.getItemCount();
                            mAdapter.addMessages(posStart, loadedMessages);
                            mAdapter.notifyItemRangeInserted(posStart, loadedMessages.size());
                            indexMessageEndLoaded += loadedMessages.size();
                        }
                    }
                    if (mAdapter != null) {
                        mAdapter.endLoading();
                    }
                });
            }
        });
    }

    private List<SpoChatMessage> loadMessagesFromDB(boolean isOld) {
        if (mChatRoom == null) return null;

        ArrayList<SpoChatMessage> loadedMessages = null;
        numOfChatRoomMessages = DBUtils.getNumMessage(mChatRoom.getId());

        if (isOld) {
            if (indexMessageBeginLoaded != 0) {
                int startLoadIndex = 0;
                int endLoadIndex = indexMessageBeginLoaded - 1;

                if ((endLoadIndex + 1) > LIMIT_MES_LOAD) {
                    startLoadIndex = (endLoadIndex + 1) - LIMIT_MES_LOAD;
                }
                indexMessageBeginLoaded = startLoadIndex;
                SpoChatMessage[] range = DBUtils.getSpoChatMessagesRange(
                        mChatRoom.getId(),
                        indexMessageBeginLoaded,
                        endLoadIndex,
                        false);
                if (range != null) {
                    loadedMessages = new ArrayList<>(Arrays.asList(range));
                }
            }
        } else {
            if (indexMessageEndLoaded != numOfChatRoomMessages - 1) {
                int endLoadIndex = indexMessageEndLoaded + LIMIT_MES_LOAD;
                if (endLoadIndex >= numOfChatRoomMessages) {
                    endLoadIndex = numOfChatRoomMessages - 1;
                }
                SpoChatMessage[] range = DBUtils.getSpoChatMessagesRange(
                        mChatRoom.getId(),
                        indexMessageEndLoaded + 1,
                        endLoadIndex,
                        false);
                if (range != null) {
                    loadedMessages = new ArrayList<>(Arrays.asList(range));
                }
            }
        }

        if (isSelectMessage && loadedMessages != null && checked != null) {
            checked.addAll(0, Collections.nCopies(loadedMessages.size(), false));
        }

        return loadedMessages;
    }

    @Override
    public void onSpoChatMessageStateChanged(long idMessage, int status) {
        // handled in adapter
    }

    @Override
    public void onSpoChatMessageReceived(long idMessage) {
        if (getActivity() == null || mChatRoom == null) return;

        SpoChatMessage msg = DBUtils.getChatMessageById(idMessage);
        if (msg == null || mChatRoom.getId() != msg.getIdChatRoom()) return;

        getActivity().runOnUiThread(() -> {
            if (!hasUnreadMessage) {
                if (msg.getTypeContent() == SpoChatMessage.FILE) {
                    SpoFile[] files = DBUtils.getSpoFiles(msg.getId());
                    if (files != null && files.length > 0) {
                        msg.setSpoFiles(new ArrayList<>(Arrays.asList(files)));
                    }
                }

                if (mAdapter != null) {
                    mAdapter.addMessage(msg);
                    mAdapter.notifyItemInserted(mAdapter.getItemCount() - 1);
                    if (mBinding != null && mBinding.chatMessageList.getLayoutManager() != null) {
                        mBinding.chatMessageList.getLayoutManager().scrollToPosition(mAdapter.getItemCount() - 1);
                    }
                }
            }
            numOfChatRoomMessages += 1;
            if (isSelectMessage && checked != null) {
                checked.add(false);
            }
        });
    }

    @Override
    public void onSpoLastChatMessageReceived(long idMessage) {
        SpoChatMessage msg = DBUtils.getChatMessageById(idMessage);
        if (msg == null || mChatRoom == null || mChatRoom.getId() == msg.getIdChatRoom()) return;

        SpoChatRoom chatRoom = DBUtils.getChatRoom(msg.getIdChatRoom());
        if (chatRoom != null && mSpoNotificationsManager != null) {
            mSpoNotificationsManager.displayMessageNotification(chatRoom, msg);
        }
    }

    @Override
    public void onSpoFileStateChanged(long idFile, int status) {
        // handled in adapter
    }

    @Override
    public void onSpoFileSendingStatus(long idFile, int percentSending) {
        // handled in adapter
    }

    @Override
    public void onClick(View v) {
        Context context = getContextSafe();
        if (context == null || getActivity() == null) return;

        int id = v.getId();
        if (id == R.id.btnSend) {
            sendMessage();
            mBinding.messageInput.setText("");
        } else if (id == R.id.btnBack) {
            Utils.hideKeyboardFrom(context, v);
            requireActivity().finish();
        } else if (id == R.id.btnAddContact) {
            if (mChatRoom == null) return;
            Fragment fragment = ContactChangeFragment.newInstance(context, true, 0);
            Bundle args = new Bundle();
            args.putString(ContactChangeFragment.ARG_NUMBER, mChatRoom.getNameChat());
            fragment.setArguments(args);
            ((SecondaryActivity) requireActivity()).displayFragment(fragment, false);
        } else if (id == R.id.chatLabel) {
            if (mChatRoom == null) return;

            if (mChatRoom.getType() == SpoChatRoom.ONE && mChatRoom.getIdUsers() != null && !mChatRoom.getIdUsers().isEmpty()) {
                SpoContact contact = DBUtils.getContactForIdUser(mChatRoom.getIdUsers().get(0));
                if (contact != null) {
                    Fragment fragment = ContactInfoFragment.newInstance(context, contact.getId());
                    ((SecondaryActivity) requireActivity()).displayFragment(fragment, true);
                }
            } else {
                Fragment fragment = CreateGroupFragment.newInstance(context, mChatRoom.getId());
                ((SecondaryActivity) requireActivity()).displayFragment(fragment, true);
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
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestCameraPermission();
            } else {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    try {
                        File photoFile = createImageFile();
                        if (photoFile != null) {
                            Uri photoURI = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", photoFile);
                            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.setClipData(ClipData.newRawUri(null, photoURI));
                            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                            takePhotoResult.launch(intent);
                        }
                    } catch (IOException ignored) {
                        Log.e(TAG, "Error creating image file");
                    }
                }
            }
        } else if (id == R.id.btnCall) {
            if (RegistrationState.flagRegistrationOk && mChatRoom != null && mChatRoom.getIdUsers() != null && !mChatRoom.getIdUsers().isEmpty()) {
                SpoContact contact = DBUtils.getContactForIdUser(mChatRoom.getIdUsers().get(0));
                if (contact != null) {
                    LinphoneManager.getInstance().newOutgoingCall(contact.getSipNumber(), "");
                }
            } else {
                Toast.makeText(getActivity(), "Нет соединения с СКЗИ", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private File createImageFile() throws IOException {
        @SuppressLint("SimpleDateFormat")
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = timeStamp + "_";

        Context context = getContextSafe();
        if (context == null) return null;

        File image = Utils.createSendedFile(context, imageFileName + ".jpg");
        if (image != null) {
            currentPhotoPath = image.getAbsolutePath();
        }
        return image;
    }

    public void registerForTakePhoto() {
        takePhotoResult = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && currentPhotoPath != null) {
                        File takePhotoFile = new File(currentPhotoPath);
                        Uri fileUri = Uri.fromFile(takePhotoFile);
                        addSelectedFiles(fileUri);
                    }
                });
    }

    public void registerForSelectFiles() {
        Context context = getContextSafe();
        if (context == null) return;

        selectExternalFileResult = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

                    boolean isShowToastMaxSize = false;
                    Intent resultData = result.getData();
                    Uri fileUri = resultData.getData();

                    if (fileUri != null) {
                        try {
                            context.getContentResolver().takePersistableUriPermission(fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            Log.e(TAG, "Failed to take permission: " + e.getMessage());
                        }

                        String filePath = FileUtils.getPath(context, fileUri);
                        if (filePath != null && new File(filePath).length() > MAX_SIZE_FILE) {
                            Toast.makeText(context, R.string.over_size_file, Toast.LENGTH_SHORT).show();
                        } else if (filePath != null) {
                            addSelectedFiles(fileUri);
                        }
                    } else {
                        ClipData files = resultData.getClipData();
                        if (files != null) {
                            int numFiles = Math.min(files.getItemCount(), MAX_NUM_FILE);
                            for (int i = 0; i < numFiles; i++) {
                                try {
                                    context.getContentResolver().takePersistableUriPermission(
                                            files.getItemAt(i).getUri(),
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                } catch (SecurityException e) {
                                    Log.e(TAG, "Failed to take permission: " + e.getMessage());
                                }

                                String filePath = FileUtils.getPath(context, files.getItemAt(i).getUri());
                                if (filePath != null) {
                                    if (new File(filePath).length() > MAX_SIZE_FILE) {
                                        if (!isShowToastMaxSize) {
                                            Toast.makeText(context, R.string.over_size_file, Toast.LENGTH_SHORT).show();
                                            isShowToastMaxSize = true;
                                        }
                                    } else {
                                        addSelectedFiles(files.getItemAt(i).getUri());
                                    }
                                }
                            }
                        }
                    }
                });
    }

    public void registerForPermissionCamera() {
        permissionCameraResult = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        Toast.makeText(getContextSafe(), "Права для работы камеры не предоставлены", Toast.LENGTH_SHORT).show();
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
    }

    @Override
    public void onPause() {
        super.onPause();

        VoiceMassagePlayer.stopPlaying();
        cancelRecordVoice();

        releasePlayer();

        SpoListenerManager.removeListener(this);

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null && mListener != null) {
            lc.removeListener(mListener);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        stopTimerThread();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mBinding != null) {
            mBinding = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releasePlayer();
        releaseMediaRecorder();
        mExecutor.shutdownNow();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mBinding != null && mBinding.chatMessageList.getLayoutManager() != null) {
            mListState = mBinding.chatMessageList.getLayoutManager().onSaveInstanceState();
            outState.putParcelable(LIST_STATE_KEY, mListState);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContextRef = new WeakReference<>(context);

        Intent intent = new Intent(context, SpoMessagesService.class);
        intent.setAction(SpoMessagesService.ACTION_SET_DELAY_REQUEST);
        intent.putExtra(SpoMessagesService.DELAY_KEY, true);
        context.startService(intent);
    }

    @Override
    public void onDetach() {
        super.onDetach();

        Context context = getContextSafe();
        if (context != null) {
            Intent intent = new Intent(context, SpoMessagesService.class);
            intent.setAction(SpoMessagesService.ACTION_SET_DELAY_REQUEST);
            intent.putExtra(SpoMessagesService.DELAY_KEY, false);
            context.startService(intent);
        }

        new Thread(DBUtils::saveDataBase).start();
        mContextRef = null;
    }

    private final ActivityResultLauncher<String> requestPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {});

    private void requestCameraPermission() {
        permissionCameraResult.launch(Manifest.permission.CAMERA);
    }

    public ChatMessageAdapter getAdapter() {
        return mAdapter;
    }

    private class ChatMessageHolder extends RecyclerView.ViewHolder implements SpoChatMessageListener {
        private SpoChatMessage mMessage;
        private final SimpleDateFormat formatDate;
        private final TextView messageText;
        private final TextView date;
        private final ImageView statusImage;
        private TextView textDate, textUnread;
        private final boolean mHasDateDivider;
        private final boolean mHasUnreadDivider;
        private final RecyclerView fileList;
        private FileAdapter mFileAdapter;
        private final View mView;

        @SuppressLint("SimpleDateFormat")
        public ChatMessageHolder(@NonNull View itemView, boolean hasDivider, boolean hasUnreadDivider) {
            super(itemView);
            formatDate = new SimpleDateFormat("HH:mm");
            mHasDateDivider = hasDivider;
            mHasUnreadDivider = hasUnreadDivider;

            if (mHasDateDivider) {
                textDate = itemView.findViewById(R.id.textDate);
                FrameLayout dividerDate = itemView.findViewById(R.id.dividerDate);
                dividerDate.setVisibility(View.VISIBLE);
            }

            if (mHasUnreadDivider) {
                textUnread = itemView.findViewById(R.id.textUnread);
                FrameLayout dividerUnread = itemView.findViewById(R.id.dividerUnread);
                dividerUnread.setVisibility(View.VISIBLE);
            }

            fileList = itemView.findViewById(R.id.fileList);
            messageText = itemView.findViewById(R.id.messageText);
            date = itemView.findViewById(R.id.date);
            statusImage = itemView.findViewById(R.id.statusImage);
            mView = itemView;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void bind(SpoChatMessage message, int createViewPosition) {
            if (message == null) return;

            mMessage = message;

            Context context = getContextSafe();
            if (context == null) return;

            if (isSelectMessage && checked != null && createViewPosition < checked.size()) {
                if (checked.get(createViewPosition)) {
                    mView.setBackgroundColor(context.getColor(R.color.colorSelectMessage));
                } else {
                    mView.setBackgroundColor(Color.TRANSPARENT);
                }
            } else {
                mView.setBackgroundColor(Color.TRANSPARENT);
            }

            if (mMessage.getIsRead() == SpoChatMessage.UNREAD) {
                mMessage.setIsRead(SpoChatMessage.READ);
                DBUtils.updateMessage(mMessage);
            }

            date.setText(formatDate.format(new Date(mMessage.getDate())));

            switch (mMessage.getTypeContent()) {
                case SpoChatMessage.FILE:
                    if (mMessage.getMessage() != null && !mMessage.getMessage().isEmpty()) {
                        messageText.setVisibility(View.VISIBLE);
                        messageText.setText(mMessage.getMessage());
                    } else {
                        messageText.setVisibility(View.GONE);
                    }
                    fileList.setLayoutManager(new LinearLayoutManager(context));
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

            if (mMessage.getDir() == SpoChatMessage.OUT) {
                switch (mMessage.getStatus()) {
                    case SpoChatMessage.SENDING:
                        statusImage.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_status_message_sending, context.getTheme()));
                        break;
                    case SpoChatMessage.SENT:
                        statusImage.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_status_message_sent, context.getTheme()));
                        break;
                }
            } else {
                date.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                statusImage.setVisibility(View.GONE);
            }

            if (mHasDateDivider && textDate != null) {
                textDate.setText(Utils.timeDividerToHumanDate(message.getDate()));
            }

            if (mHasUnreadDivider && textUnread != null && context != null) {
                textUnread.setText(context.getResources().getQuantityString(R.plurals.info_num_unread_messages, numUnreadMessage, numUnreadMessage));
            }
        }

        @Override
        public void onSpoChatMessageStateChanged(long idMessage, int status) {
            if (mMessage != null && idMessage == mMessage.getId()) {
                Log.d(TAG, "onSpotChatMessageStateChanged " + status);
                mMessage.setStatus(status);
                Context context = getContextSafe();
                if (context != null && statusImage != null) {
                    if (status == SpoChatMessage.SENT) {
                        statusImage.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_status_message_sent, context.getTheme()));
                    } else {
                        statusImage.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_status_message_sending, context.getTheme()));
                    }
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
            if (mMessage != null && mMessage.getTypeContent() == SpoChatMessage.FILE &&
                    mMessage.getSpoFiles() != null && !mMessage.getSpoFiles().isEmpty()) {
                SpoFile file = DBUtils.getSpoFile(idFile);
                if (file != null && mMessage.getSpoFiles().contains(file) && mAdapter != null) {
                    mAdapter.notifyItemChanged(getAbsoluteAdapterPosition());
                }
            }
        }

        @Override
        public void onSpoFileSendingStatus(long idFile, int percentSending) {
        }

        private class FileHolder extends RecyclerView.ViewHolder implements View.OnClickListener, SpoChatMessageListener, VoiceMassagePlayer.VoiceMassagePlayerListener {
            private final SendFileCellBinding mBinding;
            private SpoFile mFile;
            private int mStatus;
            private boolean isPlaying = false;

            public FileHolder(@NonNull View itemView) {
                super(itemView);
                mBinding = DataBindingUtil.bind(itemView);
            }

            public void bind(SpoFile file) {
                if (file == null || mBinding == null) return;

                mFile = file;
                if (mBinding.nameFile != null) {
                    mBinding.nameFile.setText(Utils.getFileName(mFile.getName()));
                }
                createFileUI(mFile.getStatus());

                if (mBinding.sendFileHolder != null) {
                    mBinding.sendFileHolder.setOnClickListener(this);
                }
                if (mBinding.deleteSendHolder != null) {
                    mBinding.deleteSendHolder.setOnClickListener(this);
                }
                if (mBinding.errorSendHolder != null) {
                    mBinding.errorSendHolder.setOnClickListener(this);
                }

                SpoListenerManager.addListener(this);
            }

            private void createFileUI(int status) {
                if (mBinding == null) return;

                mStatus = status;
                mBinding.progressSendHolder.setVisibility(View.GONE);
                mBinding.deleteSendHolder.setVisibility(View.GONE);
                mBinding.errorSendHolder.setVisibility(View.GONE);
                mBinding.okSendHolder.setVisibility(View.GONE);
                mBinding.downloadSendHolder.setVisibility(View.GONE);

                Context context = getContextSafe();

                switch (mStatus) {
                    case STATUS_SEND_RECEIVE: {
                        mBinding.progressSendHolder.setVisibility(View.VISIBLE);
                        if (mBinding.progressBar != null) {
                            mBinding.progressBar.setIndeterminate(true);
                        }
                        break;
                    }
                    case STATUS_READY_TO_DOWNLOAD: {
                        mBinding.downloadSendHolder.setVisibility(View.VISIBLE);
                        break;
                    }
                    case STATUS_OK: {
                        mBinding.okSendHolder.setVisibility(View.VISIBLE);
                        if (mBinding.imageOk != null && context != null) {
                            switch (mFile.getType()) {
                                case SpoFile.TYPE_IMAGE:
                                    mBinding.imageOk.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_image));
                                    break;
                                case SpoFile.TYPE_VOICE:
                                    mBinding.imageOk.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_play));
                                    break;
                                case SpoFile.TYPE_OTHER:
                                default:
                                    mBinding.imageOk.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_file));
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
                if (mBinding == null) return;

                if (mBinding.progressBar != null && mBinding.progressBar.isIndeterminate()) {
                    mBinding.progressBar.setIndeterminate(false);
                }
                if (mBinding.progressBar != null) {
                    mBinding.progressBar.setProgress(percentSending);
                }
            }

            @Override
            public void onClick(View v) {
                if (v == null || mFile == null) return;

                Context context = getContextSafe();
                if (context == null) return;

                int viewId = v.getId();

                if (viewId == R.id.sendFileHolder) {
                    handleFileClick(context);
                } else if (viewId == R.id.errorSendHolder) {
                    createFileUI(SpoFile.STATUS_SEND_RECEIVE);
                    Intent intent = new Intent(context, SpoMessagesService.class);
                    if (mFile.getDir() == SpoFile.DIR_OUT) {
                        intent.setAction(SpoMessagesService.ACTION_SEND_FILE);
                    } else {
                        intent.setAction(SpoMessagesService.ACTION_DOWNLOAD_FILE);
                    }
                    intent.putExtra(SpoMessagesService.FILE_ID_KEY, mFile.getId());
                    context.startService(intent);
                } else if (viewId == R.id.deleteSendHolder) {
                    if (mFileAdapter != null && mFileAdapter.getItemCount() == 1) {
                        String text = mMessage != null ? mMessage.getMessage() : null;
                        if (text == null || text.isEmpty()) {
                            if (mAdapter != null) {
                                mAdapter.removeMessage(mMessage);
                            }
                        } else {
                            mFileAdapter.removeFile(mFile);
                        }
                    } else if (mFileAdapter != null) {
                        mFileAdapter.removeFile(mFile);
                    }

                    Intent intent = new Intent(context, SpoMessagesService.class);
                    intent.setAction(SpoMessagesService.ACTION_STOP_SENDING_FILE);
                    intent.putExtra(SpoMessagesService.FILE_ID_KEY, mFile.getId());
                    context.startService(intent);
                }
            }

            private void handleFileClick(Context context) {
                switch (mStatus) {
                    case STATUS_SEND_RECEIVE: {
                        Intent intent = new Intent(context, SpoMessagesService.class);
                        intent.setAction(SpoMessagesService.ACTION_STOP_SENDING_FILE);
                        intent.putExtra(SpoMessagesService.FILE_ID_KEY, mFile.getId());
                        context.startService(intent);
                        break;
                    }
                    case STATUS_READY_TO_DOWNLOAD: {
                        createFileUI(SpoFile.STATUS_SEND_RECEIVE);
                        Intent intent = new Intent(context, SpoMessagesService.class);
                        intent.setAction(SpoMessagesService.ACTION_DOWNLOAD_FILE);
                        intent.putExtra(SpoMessagesService.FILE_ID_KEY, mFile.getId());
                        context.startService(intent);
                        break;
                    }
                    case STATUS_OK: {
                        openFile(context);
                        break;
                    }
                }
            }

            private void openFile(Context context) {
                Uri fileUri = Uri.parse(mFile.getUri());
                String pathFile = FileUtils.getPath(context, fileUri);

                if (pathFile == null) {
                    Toast.makeText(context, R.string.file_not_found, Toast.LENGTH_SHORT).show();
                    return;
                }

                File file = new File(pathFile);
                if (!file.exists()) {
                    Toast.makeText(context, R.string.file_not_found, Toast.LENGTH_SHORT).show();
                    mFile.setStatus(STATUS_READY_TO_DOWNLOAD);
                    DBUtils.updateFile(mFile);
                    DBUtils.updateChatRoom(mChatRoom);
                    if (mBinding != null) {
                        mBinding.imageOk.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_file_download));
                    }
                    updateMessageList();
                    return;
                }

                switch (mFile.getType()) {
                    case SpoFile.TYPE_IMAGE:
                    case SpoFile.TYPE_OTHER: {
                        openFileWithIntent(context, file);
                        break;
                    }
                    case SpoFile.TYPE_VOICE: {
                        playVoiceFile(context, pathFile);
                        break;
                    }
                }
            }

            private void openFileWithIntent(Context context, File file) {
                String fileName = Utils.getFileName(context, Uri.fromFile(file));
                if (fileName == null) {
                    Toast.makeText(context, R.string.file_not_found, Toast.LENGTH_SHORT).show();
                    return;
                }

                String mime = Utils.getMimeType(fileName);
                Uri uri;

                if (mFile.getDir() == SpoFile.DIR_IN) {
                    uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);
                } else {
                    String strUri = mFile.getUri();
                    if (strUri != null && strUri.startsWith("file:")) {
                        uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);
                    } else {
                        uri = Uri.parse(mFile.getUri());
                    }
                }

                Intent intent = new Intent(Intent.ACTION_VIEW);
                if (mime != null) {
                    intent.setDataAndType(uri, mime);
                } else {
                    intent.setData(uri);
                }
                intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(context, R.string.error_no_activity_found, Toast.LENGTH_SHORT).show();
                }
            }

            private void playVoiceFile(Context context, String pathFile) {
                if (isPlaying) {
                    VoiceMassagePlayer.stopPlaying();
                } else {
                    if (mBinding != null) {
                        mBinding.imageOk.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_stop));
                        mBinding.progressAudioLayout.setVisibility(View.VISIBLE);
                        mBinding.fileNameLayout.setVisibility(View.GONE);
                        mBinding.progressAudio.setProgress(0);
                    }
                    isPlaying = true;
                    VoiceMassagePlayer.startPlaying(pathFile, this);
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
                    if (file != null) {
                        mFile.setUri(file.getUri());
                        mFile.setStatus(file.getStatus());
                        mFile.setUrlDownload(file.getUrlDownload());
                        mFile.setIdFile(file.getIdFile());
                    }
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> createFileUI(status));
                    }
                }
            }

            @Override
            public void onSpoFileSendingStatus(long idFile, int percentSending) {
                if (idFile == mFile.getId() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> setSendingStatus(percentSending));
                }
            }

            @Override
            public void onCompletionPlay() {
                Log.d(TAG, "onCompletionPlay");
                Context context = getContextSafe();
                if (context != null && mBinding != null) {
                    mBinding.imageOk.setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_play));
                    mBinding.progressAudioLayout.setVisibility(View.GONE);
                    mBinding.fileNameLayout.setVisibility(View.VISIBLE);
                }
                isPlaying = false;
            }

            @Override
            public void onProgressPlay(int progress) {
                if (mHandler != null) {
                    mHandler.post(() -> {
                        if (mBinding != null) {
                            mBinding.progressAudio.setProgress(progress);
                            @SuppressLint("DefaultLocale")
                            String currentTime = String.format("%02d:%02d", progress / 1000 / 60, progress / 1000 % 60);
                            mBinding.currentTime.setText(currentTime);
                        }
                    });
                }
            }

            @Override
            public void onDurationPlay(int duration) {
                if (mHandler != null) {
                    mHandler.post(() -> {
                        if (mBinding != null) {
                            mBinding.progressAudio.setMax(duration);
                            @SuppressLint("DefaultLocale")
                            String durationTime = String.format("%02d:%02d", duration / 1000 / 60, duration / 1000 % 60);
                            mBinding.durationTime.setText(durationTime);
                        }
                    });
                }
            }
        }

        private class FileAdapter extends RecyclerView.Adapter<FileHolder> {
            private ArrayList<SpoFile> mFiles;

            public FileAdapter(ArrayList<SpoFile> files) {
                mFiles = files != null ? files : new ArrayList<>();
            }

            @NonNull
            @Override
            public FileHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.send_file_cell, parent, false);
                return new FileHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull FileHolder holder, int position) {
                if (position < mFiles.size()) {
                    holder.bind(mFiles.get(position));
                }
            }

            @Override
            public int getItemCount() {
                return mFiles != null ? mFiles.size() : 0;
            }

            @SuppressLint("NotifyDataSetChanged")
            public void setFiles(ArrayList<SpoFile> files) {
                mFiles = files != null ? files : new ArrayList<>();
                notifyDataSetChanged();
            }

            @SuppressLint("NotifyDataSetChanged")
            public void removeFile(SpoFile file) {
                if (mFiles != null && file != null) {
                    mFiles.remove(file);
                    notifyDataSetChanged();
                }
            }
        }
    }

    private class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageHolder> {
        private boolean isLoading;
        private OnLoadMoreListener mOnLoadMoreListener;
        private ArrayList<SpoChatMessage> listMessages;
        private int selectPosition = -1;
        private int indexViewHolderUnread = -1;

        public ChatMessageAdapter(List<SpoChatMessage> messages) {
            listMessages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
            isSelectMessage = false;
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= listMessages.size()) {
                return MESSAGE_IN;
            }

            SpoChatMessage message = listMessages.get(position);
            if (message == null) {
                return MESSAGE_IN;
            }

            if (message.getDir() == SpoChatMessage.IN) {
                if (position == 0) {
                    if (isDifferentDay(new Date(message.getDate()), new Date()))
                        return indexViewHolderUnread == position ? MESSAGE_IN_FIRST_DATE_UNREAD : MESSAGE_IN_FIRST_DATE;
                    else
                        return indexViewHolderUnread == position ? MESSAGE_IN_FIRST_UNREAD : MESSAGE_IN_FIRST;
                } else {
                    SpoChatMessage prevMessage = listMessages.get(position - 1);
                    if (prevMessage == null) {
                        return MESSAGE_IN;
                    }
                    if (isDifferentDay(new Date(message.getDate()), new Date(prevMessage.getDate()))) {
                        return indexViewHolderUnread == position ? MESSAGE_IN_FIRST_DATE_UNREAD : MESSAGE_IN_FIRST_DATE;
                    } else {
                        if (prevMessage.getDir() == SpoChatMessage.OUT) {
                            return indexViewHolderUnread == position ? MESSAGE_IN_FIRST_UNREAD : MESSAGE_IN_FIRST;
                        } else {
                            return indexViewHolderUnread == position ? MESSAGE_IN_FIRST_UNREAD : MESSAGE_IN;
                        }
                    }
                }
            } else {
                if (position == 0) {
                    if (listMessages.size() > 1) {
                        SpoChatMessage nextMessage = listMessages.get(1);
                        if (nextMessage != null && isDifferentDay(new Date(message.getDate()), new Date(nextMessage.getDate()))) {
                            return MESSAGE_OUT_FIRST_DATE;
                        }
                    }
                    return MESSAGE_OUT_FIRST;
                } else {
                    SpoChatMessage prevMessage = listMessages.get(position - 1);
                    if (prevMessage == null) {
                        return MESSAGE_OUT;
                    }
                    if (isDifferentDay(new Date(message.getDate()), new Date(prevMessage.getDate()))) {
                        return MESSAGE_OUT_FIRST_DATE;
                    } else {
                        if (prevMessage.getDir() == SpoChatMessage.IN) {
                            return MESSAGE_OUT_FIRST;
                        } else {
                            return MESSAGE_OUT;
                        }
                    }
                }
            }
        }

        private boolean isDifferentDay(Date date1, Date date2) {
            if (date1 == null || date2 == null) return true;

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
                case MESSAGE_IN:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_bubble_in, parent, false);
                    break;
                case MESSAGE_IN_FIRST:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_bubble_in_first, parent, false);
                    break;
                case MESSAGE_IN_FIRST_DATE:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_date_divider_in, parent, false);
                    hasDateDivider = true;
                    break;
                case MESSAGE_IN_FIRST_DATE_UNREAD:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_date_divider_in, parent, false);
                    hasDateDivider = true;
                    hasUnreadDivider = true;
                    break;
                case MESSAGE_IN_FIRST_UNREAD:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_date_divider_in, parent, false);
                    hasUnreadDivider = true;
                    break;
                case MESSAGE_OUT:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_bubble_out, parent, false);
                    break;
                case MESSAGE_OUT_FIRST:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_bubble_out_first, parent, false);
                    break;
                case MESSAGE_OUT_FIRST_DATE:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_date_divider_out, parent, false);
                    hasDateDivider = true;
                    break;
            }

            if (view == null) {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_bubble_in, parent, false);
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
            if (position >= listMessages.size() || listMessages.get(position) == null) {
                return;
            }

            SpoChatMessage message = listMessages.get(position);
            holder.bind(message, position);

            holder.itemView.setOnClickListener(v -> {
                int mPosition = holder.getAbsoluteAdapterPosition();
                if (isSelectMessage && checked != null && mPosition < checked.size()) {
                    checked.set(mPosition, !checked.get(mPosition));
                    if (checked.get(mPosition)) {
                        countSetChecked++;
                    } else {
                        countSetChecked--;
                    }
                    mAdapter.notifyItemChanged(mPosition);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (mBinding == null) return false;

                int mPosition = holder.getAbsoluteAdapterPosition();
                mBinding.btnCall.setVisibility(View.GONE);
                mBinding.btnDeleteMessage.setVisibility(View.VISIBLE);
                mBinding.btnCancelSelect.setVisibility(View.VISIBLE);

                if (!isSelectMessage) {
                    setShowSelectedMessages(true);
                }
                if (checked != null && mPosition < checked.size()) {
                    checked.set(mPosition, true);
                    countSetChecked++;
                }
                mAdapter.notifyItemChanged(mPosition);
                return true;
            });

            setupSelectButtons();

            if (message.getStatus() == SpoChatMessage.SENDING) {
                SpoListenerManager.addListener(holder);
            }

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

        private void setupSelectButtons() {
            if (mBinding == null) return;

            mBinding.btnCancelSelect.setOnClickListener(v -> {
                mBinding.btnCall.setVisibility(View.VISIBLE);
                mBinding.btnDeleteMessage.setVisibility(View.GONE);
                mBinding.btnCancelSelect.setVisibility(View.GONE);
                checked = null;
                isSelectMessage = false;
                mAdapter.notifyDataSetChanged();
            });

            mBinding.btnDeleteMessage.setOnClickListener(v -> {
                deleteSelectedMessages();
                mBinding.btnCall.setVisibility(View.VISIBLE);
                mBinding.btnDeleteMessage.setVisibility(View.GONE);
                mBinding.btnCancelSelect.setVisibility(View.GONE);
            });
        }

        private void deleteSelectedMessages() {
            if (checked == null || mChatRoom == null) return;

            Context context = getContextSafe();
            if (context == null) return;

            for (int positions = checked.size() - 1; positions >= 0; positions--) {
                if (checked.get(positions) && positions < listMessages.size()) {
                    SpoChatMessage messages = listMessages.get(positions);
                    if (messages == null) continue;

                    if (messages.getStatus() == SpoChatMessage.SENDING) {
                        Intent intent = new Intent(context, SpoMessagesService.class);
                        intent.setAction(SpoMessagesService.ACTION_STOP_SENDING_MESSAGE);
                        intent.putExtra(SpoMessagesService.MESSAGE_ID_KEY, messages.getId());
                        context.startService(intent);
                    }

                    if (messages.getTypeContent() == SpoChatMessage.FILE) {
                        ArrayList<SpoFile> files = messages.getSpoFiles();
                        if (files != null && !files.isEmpty()) {
                            for (SpoFile file : files) {
                                if (file != null && file.getStatus() == SpoFile.STATUS_SEND_RECEIVE) {
                                    Intent intent = new Intent(context, SpoMessagesService.class);
                                    intent.setAction(SpoMessagesService.ACTION_STOP_SENDING_FILE);
                                    intent.putExtra(SpoMessagesService.FILE_ID_KEY, file.getId());
                                    context.startService(intent);
                                }
                                if (file != null) {
                                    DBUtils.deleteFile(file);
                                }
                            }
                        }
                    }
                    DBUtils.deleteMessage(messages);
                    removeMessage(positions);
                }
            }
        }

        @Override
        public void onViewRecycled(ChatMessageHolder holder) {
            super.onViewRecycled(holder);
            if (holder != null) {
                holder.itemView.setOnLongClickListener(null);
                SpoListenerManager.removeListener(holder);
                if (holder.mMessage != null && holder.mMessage.getSpoFiles() != null && holder.fileList != null) {
                    for (int i = 0; i < holder.mMessage.getSpoFiles().size(); i++) {
                        RecyclerView.ViewHolder vh = holder.fileList.findViewHolderForAdapterPosition(i);
                        if (vh instanceof SpoChatMessageListener) {
                            SpoListenerManager.removeListener((SpoChatMessageListener) vh);
                        }
                    }
                }
            }
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull ChatMessageHolder holder) {
            super.onViewDetachedFromWindow(holder);
            if (holder != null && holder.getAbsoluteAdapterPosition() == listMessages.size() - 1 && !listMessages.isEmpty()) {
                if (mBinding != null) {
                    mBinding.btnGoToEnd.setVisibility(View.VISIBLE);
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(@NonNull ChatMessageHolder holder) {
            super.onViewAttachedToWindow(holder);
            if (holder != null && holder.getAbsoluteAdapterPosition() == listMessages.size() - 1 && mBinding != null) {
                mBinding.btnGoToEnd.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return listMessages != null ? listMessages.size() : 0;
        }

        public void setListMessages(List<SpoChatMessage> listMessages) {
            this.listMessages = listMessages != null ? new ArrayList<>(listMessages) : new ArrayList<>();
        }

        public void addMessage(SpoChatMessage message) {
            if (message != null && listMessages != null) {
                listMessages.add(message);
                indexMessageEndLoaded++;
            }
        }

        public void addMessages(int index, List<SpoChatMessage> message) {
            if (message == null || message.isEmpty() || listMessages == null) {
                return;
            }
            if (index < 0) {
                index = 0;
            }
            if (index > listMessages.size()) {
                index = listMessages.size();
            }
            listMessages.addAll(index, message);
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
            if (listMessages != null && position >= 0 && position < listMessages.size()) {
                return listMessages.get(position);
            }
            return null;
        }

        public void removeMessage(int position) {
            if (listMessages != null && position >= 0 && position < listMessages.size()) {
                listMessages.remove(position);
                notifyItemRemoved(position);
            }
        }

        public void removeMessage(SpoChatMessage message) {
            if (message == null || listMessages == null) return;

            for (int i = 0; i < listMessages.size(); i++) {
                SpoChatMessage msg = listMessages.get(i);
                if (msg != null && msg.getId() == message.getId()) {
                    listMessages.remove(i);
                    notifyItemRemoved(i);
                    break;
                }
            }
        }
    }

    private class SelectFileHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final SelectFileCellBinding mBinding;

        public SelectFileHolder(@NonNull View itemView) {
            super(itemView);
            mBinding = DataBindingUtil.bind(itemView);
            if (mBinding != null && mBinding.btnRemove != null) {
                mBinding.btnRemove.setOnClickListener(this);
            }
        }

        public void bind(Uri uri) {
            Context context = getContextSafe();
            if (mBinding != null && context != null) {
                mBinding.nameFile.setText(Utils.getFileName(context, uri));
            }
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
            uriFiles = uris != null ? uris : new ArrayList<>();
        }

        @NonNull
        @Override
        public SelectFileHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.select_file_cell, parent, false);
            return new SelectFileHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SelectFileHolder holder, int position) {
            if (position < uriFiles.size()) {
                holder.bind(uriFiles.get(position));
            }
        }

        @Override
        public int getItemCount() {
            return uriFiles != null ? uriFiles.size() : 0;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void setUriFiles(ArrayList<Uri> uriFile) {
            this.uriFiles = uriFile != null ? uriFile : new ArrayList<>();
            notifyDataSetChanged();
        }

        public void addUri(Uri fileUri) {
            if (uriFiles != null && fileUri != null) {
                uriFiles.add(fileUri);
            }
        }

        public void remove(int index) {
            if (uriFiles != null && index >= 0 && index < uriFiles.size()) {
                uriFiles.remove(index);
                notifyItemRemoved(index);
            }
        }
    }
}