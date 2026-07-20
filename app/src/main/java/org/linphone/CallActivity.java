package org.linphone;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.Html;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableField;
import androidx.fragment.app.FragmentTransaction;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.Reason;
import org.linphone.mediastream.Log;
import org.pniei.portal.R;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.databinding.CallBinding;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;

public class CallActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int PERMISSIONS_ENABLED_MIC = 204;
    private static CallActivity instance;
    private Handler mHandler = new Handler();

    private StatusFragment status;
    private CallAudioFragment audioCallFragment;

    private boolean isSpeakerEnabled = false;
    private boolean isMicMuted = false;
    private boolean isTransferAllowed;

    private Dialog dialog = null;
    private HeadsetReceiver headsetReceiver;

    private LayoutInflater inflater;
    private ViewGroup container;

    private LinphoneCoreListenerBase mListener;
    private Timer mTimer;

    private CallBinding mBinding;
    private Dtmf dtmfNumpad;

    public class Dtmf {
        private ObservableField<String> number;
        private LinphoneCore lc;

        public Dtmf() {
            number = new ObservableField<>("");
            number.set(" ");
            lc = LinphoneManager.getLc();
        }

        public void sendDtmf(String digit) {
            final char mKeyCode = digit.charAt(0);
            number.set(number.get() + digit);
            if (lc.isInCall()) {
                lc.sendDtmf(mKeyCode);
            }
        }

        public ObservableField<String> getNumber() {
            return number;
        }
    }

    public static CallActivity instance() {
        return instance;
    }

    public static boolean isInstanciated() {
        return instance != null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.initTheme(this);
        super.onCreate(savedInstanceState);
        instance = this;

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        mBinding = DataBindingUtil.setContentView(this, R.layout.call);
        dtmfNumpad = new Dtmf();
        mBinding.setDtmfnumber(dtmfNumpad);

        // Наушники
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.HEADSET_PLUG");
        headsetReceiver = new HeadsetReceiver();
        registerReceiver(headsetReceiver, intentFilter);

        isTransferAllowed = getApplicationContext().getResources().getBoolean(R.bool.allow_transfers);

        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void callState(LinphoneCore lc, final LinphoneCall call, State state, String message) {
                if (LinphoneManager.getLc().getCallsNb() == 0) {
                    finish();
                    return;
                }
                if (state == State.IncomingReceived) {
                    startIncomingCallActivity();
                    return;
                } else if (state == State.Paused || state == State.Pausing) {
                    LinphoneManager.getLc().getCurrentCall();
                } else if (state == State.Resuming) {
                    // Не показываем видео
                } else if (state == State.StreamsRunning) {
                    enableAndRefreshInCallActions();
                    if (status != null) {
                        mBinding.videoProgress.setVisibility(View.GONE);
                    }
                } else if (state == State.OutgoingInit) {
                    setAudioManagerInCallMode();
                    requestAudioFocus();
                    startBluetooth();
                } else if (state == State.StreamsRunning) {
                    startBluetooth();
                    setAudioManagerInCallMode();
                }
                // Убираем всё, что связано с видео (CallUpdatedByRemote и т.д.)
            }
        };

        initUI();
        registerForCallResult();
    }

    private void setAudioManagerInCallMode() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager.getMode() != AudioManager.MODE_IN_COMMUNICATION) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        }
        audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    }

    private void requestAudioFocus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    }

    private void startBluetooth() {
        if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
            BluetoothManager.getInstance().routeAudioToBluetooth();
        }
    }

    private void initUI() {
        inflater = LayoutInflater.from(this);
        container = findViewById(R.id.topLayout);

        // Кнопки
        mBinding.micro.setOnClickListener(this);
        mBinding.speaker.setOnClickListener(this);
        mBinding.hangUp.setOnClickListener(this);
        mBinding.dialer.setOnClickListener(this);
        mBinding.pause.setOnClickListener(this);
        enabledPauseButton(false);

        // Настройка динамика / гарнитуры
        if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
            mBinding.speaker.setVisibility(View.INVISIBLE);
            mBinding.audioRoute.setVisibility(View.VISIBLE);
        } else {
            mBinding.speaker.setVisibility(View.VISIBLE);
            mBinding.audioRoute.setVisibility(View.INVISIBLE);
        }

        LinphoneManager.getInstance().changeStatusToOnThePhone();
    }

    private void enabledPauseButton(boolean enabled) {
        mBinding.pause.setEnabled(enabled);
    }

    private void enableAndRefreshInCallActions() {
        if (LinphoneManager.getLc().getCurrentCall() != null && !LinphoneManager.getLc().getCurrentCall().mediaInProgress()) {
            enabledPauseButton(true);
        } else {
            enabledPauseButton(false);
        }
        mBinding.micro.setEnabled(true);
        mBinding.speaker.setEnabled(true);
        mBinding.pause.setEnabled(true);
        mBinding.dialer.setEnabled(true);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.micro) {
            int recordAudio = getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
            if (recordAudio == PackageManager.PERMISSION_GRANTED) {
                toggleMicro();
            } else {
                checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_ENABLED_MIC);
            }
        } else if (id == R.id.speaker) {
            toggleSpeaker();
        } else if (id == R.id.pause) {
            pauseOrResumeCall(LinphoneManager.getLc().getCurrentCall());
        } else if (id == R.id.hangUp) {
            hangUp();
        } else if (id == R.id.dialer) {
            hideOrDisplayNumpad();
        }
    }

    private void toggleMicro() {
        LinphoneCore lc = LinphoneManager.getLc();
        isMicMuted = !isMicMuted;
        lc.muteMic(isMicMuted);
        if (isMicMuted) {
            mBinding.micro.setImageResource(R.drawable.ic_mic_off);
        } else {
            mBinding.micro.setImageResource(R.drawable.ic_mic);
        }
    }

    private void toggleSpeaker() {
        isSpeakerEnabled = !isSpeakerEnabled;
        LinphoneManager.getInstance().enableProximitySensing(!isSpeakerEnabled);
        if (isSpeakerEnabled) {
            LinphoneManager.getInstance().routeAudioToSpeaker();
            mBinding.speaker.setImageResource(R.drawable.ic_volume_up);
        } else {
            Log.d("Toggle speaker off, routing back to earpiece");
            LinphoneManager.getInstance().routeAudioToReceiver();
            mBinding.speaker.setImageResource(R.drawable.ic_volume_off);
        }
        LinphoneManager.getLc().enableSpeaker(isSpeakerEnabled);
    }

    private void pauseOrResumeCall(LinphoneCall call) {
        LinphoneCore lc = LinphoneManager.getLc();
        if (call != null && LinphoneManager.getLc().getCurrentCall() == call) {
            lc.pauseCall(call);
        } else if (call != null) {
            if (call.getState() == State.Paused) {
                // Возобновляем
                if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
                    // Режим TT не используется
                }
                // Проверка контакта
                LinphoneAddress address = call.getRemoteAddress();
                SpoContact contact = DBUtils.getContactForNumber(address.getUserName());
                // ...
                lc.resumeCall(call);
            }
        }
    }

    private void hangUp() {
        LinphoneCore lc = LinphoneManager.getLc();
        LinphoneCall currentCall = lc.getCurrentCall();
        if (currentCall != null) {
            lc.terminateCall(currentCall);
        } else if (lc.isInConference()) {
            lc.terminateConference();
        } else {
            lc.terminateAllCalls();
        }
    }

    private void hideOrDisplayNumpad() {
        if (mBinding.numpad.getVisibility() == View.VISIBLE) {
            mBinding.numpad.setVisibility(View.GONE);
        } else {
            mBinding.numpad.setVisibility(View.VISIBLE);
        }
    }

    private void startIncomingCallActivity() {
        startActivity(new Intent(this, CallIncomingActivity.class));
    }

    public void updateStatusFragment(StatusFragment statusFragment) {
        status = statusFragment;
    }

    public void bindAudioFragment(CallAudioFragment fragment) {
        audioCallFragment = fragment;
    }

    // --- Отображение контакта и таймера ---

    private void displayCurrentCall(LinphoneCall call) {
        LinphoneAddress address = call.getRemoteAddress();
        SpoContact contact = DBUtils.getContactForNumber(address.getUserName());
        if (contact != null) {
            mBinding.contactNumber.setText(contact.getSipNumber());
            mBinding.contactName.setText(contact.getFullName());
            if (contact.getUriPhoto() != null) {
                Bitmap bm = null;
                try {
                    bm = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.parse(contact.getUriPhoto()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (bm != null) {
                    mBinding.contactPicture.setImageBitmap(Utils.getCroppedBitmap(bm, 256, 256, 256));
                } else {
                    mBinding.contactPicture.setImageResource(R.drawable.ic_avatar_one);
                }
            } else {
                mBinding.contactNumber.setText(address.getUserName());
            }
            registerCallDurationTimer(null, call);
        }
    }

    private void registerCallDurationTimer(View v, LinphoneCall call) {
        int callDuration = call.getDuration();
        if (callDuration == 0 && call.getState() != State.StreamsRunning) {
            return;
        }
        Chronometer timer;
        if (v == null) {
            timer = (Chronometer) findViewById(R.id.currentCallTimer);
        } else {
            timer = (Chronometer) v.findViewById(R.id.call_timer);
        }
        if (timer == null) return;
        timer.setBase(SystemClock.elapsedRealtime() - 1000L * callDuration);
        timer.start();
    }

    public void refreshCallList(Resources resources) {
        List<LinphoneCall> pausedCalls = LinphoneUtils.getCallsInState(LinphoneManager.getLc(), Arrays.asList(State.PausedByRemote));

        if (LinphoneManager.getLc().getCallsNb() > 1) {
            mBinding.callsList.setVisibility(View.VISIBLE);
        }
        if (LinphoneManager.getLc().getCurrentCall() != null) {
            mBinding.activeCall.setVisibility(View.VISIBLE);
            mBinding.avatarLayout.setVisibility(View.VISIBLE);
            // Не показываем видео
        } else {
            mBinding.activeCall.setVisibility(View.GONE);
            mBinding.noCurrentCall.setVisibility(View.VISIBLE);
            if (LinphoneManager.getLc().getCallsNb() == 1) {
                mBinding.callsList.setVisibility(View.VISIBLE);
            }
        }

        if (mBinding.callsList != null) {
            mBinding.callsList.removeAllViews();
            int index = 0;
            if (LinphoneManager.getLc().getCallsNb() == 0) {
                goBackToDialer();
                return;
            }
            for (LinphoneCall call : LinphoneManager.getLc().getCalls()) {
                if (!call.isInConference()) {
                    if (call != LinphoneManager.getLc().getCurrentCall() && !call.isInConference()) {
                        displayPausedCalls(resources, call, index);
                        index++;
                    } else {
                        displayCurrentCall(call);
                    }
                }
            }
        }

        if (pausedCalls.size() == 1) {
            mBinding.remotePause.setVisibility(View.VISIBLE);
        } else {
            mBinding.remotePause.setVisibility(View.GONE);
        }
    }

    private void displayPausedCalls(Resources resources, LinphoneCall call, int index) {
        LinearLayout callView = (LinearLayout) inflater.inflate(R.layout.call_inactive_row, container, false);
        // ... заполнение данных
    }

    private void goBackToDialer() {
        Intent intent = new Intent();
        intent.putExtra("Transfer", false);
        setResult(Activity.RESULT_FIRST_USER, intent);
        finish();
    }

    private void checkAndRequestPermission(String permission, int result) {
        if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, result);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_ENABLED_MIC) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleMicro();
            }
        }
    }

    // --- Жизненный цикл ---

    @Override
    protected void onResume() {
        super.onResume();
        instance = this;
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }
        isSpeakerEnabled = LinphoneManager.getLc().isSpeakerEnabled();
        refreshCallList(getResources());
    }

    @Override
    protected void onPause() {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        LinphoneManager.getInstance().changeStatusToOnline();
        LinphoneManager.getInstance().enableProximitySensing(false);
        unregisterReceiver(headsetReceiver);
        if (mTimer != null) {
            mTimer.cancel();
        }
        instance = null;
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (LinphoneUtils.onKeyVolumeAdjust(keyCode)) return true;
        if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    // --- Внутренний класс для наушников ---

    public class HeadsetReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
                if (intent.hasExtra("state")) {
                    switch (intent.getIntExtra("state", 0)) {
                        case 0: // отключены
                            isSpeakerEnabled = true;
                            mBinding.speaker.setEnabled(true);
                            break;
                        case 1: // подключены
                            isSpeakerEnabled = false;
                            mBinding.speaker.setEnabled(false);
                            break;
                    }
                }
                refreshCallList(getResources());
            }
        }
    }
}