package org.linphone;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableField;
import org.pniei.portal.R;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.databinding.CallBinding;
import org.pniei.portal.database.SpoContact;

import android.provider.MediaStore;
import android.text.Html;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneCallStats.LinphoneAddressFamily;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphonePlayer;
import org.linphone.core.PayloadType;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CallActivity extends AppCompatActivity implements OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {
	private final static int SECONDS_BEFORE_HIDING_CONTROLS = 4000;
	private final static int SECONDS_BEFORE_DENYING_CALL_UPDATE = 30000;
	private static final int PERMISSIONS_REQUEST_CAMERA = 202;
	private static final int PERMISSIONS_ENABLED_CAMERA = 203;
	private static final int PERMISSIONS_ENABLED_MIC = 204;

	private static CallActivity instance;

	private Handler mControlsHandler = new Handler();
	private Runnable mControls;
	private StatusFragment status;
	private CallAudioFragment audioCallFragment;
	private CallVideoFragment videoCallFragment;
	private boolean isSpeakerEnabled = false, isMicMuted = false, isTransferAllowed, isVideoAsk;
	private int cameraNumber;
	private CountDownTimer timer;
	private boolean isVideoCallPaused = false;
	private Dialog dialog = null;
	private static long TimeRemind = 0;
	private HeadsetReceiver headsetReceiver;
	private LayoutInflater inflater;
	private ViewGroup container;
	private LinphoneCoreListenerBase mListener;

	private Handler mHandler = new Handler();
	private Timer mTimer;
	private TimerTask mTask;
	private HashMap<String, String> mEncoderTexts;
	private HashMap<String, String> mDecoderTexts;
	private boolean oldIsSpeakerEnabled = false;
	private CallBinding mBinding;
	private Dtmf dtmfNumpad;

	public class Dtmf {
		private ObservableField<String> number;
		private LinphoneCore lc;

		public Dtmf() {
			number = new ObservableField<>();
			number.set("");
			lc = LinphoneManager.getLc();
		}

		public void sendDtmf(String digit) {
			final char mKeyCode = digit.charAt(0);
			//if(number.get().length() < 10)
				number.set(number.get()+digit);
			if (lc.isIncall()) {
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

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		//setContentView(R.layout.call);
		mBinding = DataBindingUtil.setContentView(this, R.layout.call);

		dtmfNumpad = new Dtmf();
		mBinding.setDtmfnumber(dtmfNumpad);

		//Earset Connectivity Broadcast Processing
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("android.intent.action.HEADSET_PLUG");
		headsetReceiver = new HeadsetReceiver();
		registerReceiver(headsetReceiver, intentFilter);

		isTransferAllowed = getApplicationContext().getResources().getBoolean(R.bool.allow_transfers);

		cameraNumber = AndroidCameraConfiguration.retrieveCameras().length;

		mEncoderTexts = new HashMap<String, String>();
		mDecoderTexts = new HashMap<String, String>();

		mListener = new LinphoneCoreListenerBase() {
			@Override
			public void callState(LinphoneCore lc, final LinphoneCall call, LinphoneCall.State state, String message) {
				if (LinphoneManager.getLc().getCallsNb() == 0) {
					if (status != null) {
						LinphoneService.instance().removeSasNotification();
					}
					finish();
					return;
				}

				if (state == State.IncomingReceived) {
					startIncomingCallActivity();
					return;
				} else if (state == State.Paused || state == State.PausedByRemote ||  state == State.Pausing) {
					if(LinphoneManager.getLc().getCurrentCall() != null) {
						enabledVideoButton(false);
					}
					if(isVideoEnabled(call)){
						showAudioView();
					}
				} else if (state == State.Resuming) {
					if(LinphonePreferences.instance().isVideoEnabled()){
						//status.refreshStatusItems(call, isVideoEnabled(call));
						if(call.getCurrentParams().getVideoEnabled()){
							showVideoView();
						}
					}
					if(LinphoneManager.getLc().getCurrentCall() != null) {
						enabledVideoButton(true);
					}
				} else if (state == State.StreamsRunning) {
					switchVideo(isVideoEnabled(call));
					enableAndRefreshInCallActions();

					if (status != null) {
						mBinding.videoProgress.setVisibility(View.GONE);
						//status.refreshStatusItems(call, isVideoEnabled(call));
					}
				} else if (state == State.CallUpdatedByRemote) {
					// If the correspondent proposes video while audio call
					boolean videoEnabled = LinphonePreferences.instance().isVideoEnabled();
					if (!videoEnabled) {
						acceptCallUpdate(false);
					}

					boolean remoteVideo = call.getRemoteParams().getVideoEnabled();
					boolean localVideo = call.getCurrentParams().getVideoEnabled();
					boolean autoAcceptCameraPolicy = LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests();
					if (remoteVideo && !localVideo && !autoAcceptCameraPolicy && !LinphoneManager.getLc().isInConference()) {
							showAcceptCallUpdateDialog();
							createTimerForDialog(SECONDS_BEFORE_DENYING_CALL_UPDATE);
					}
				}

				refreshIncallUi();
			}
		};

		if (findViewById(R.id.fragmentContainer) != null) {
			initUI();

			if (LinphoneManager.getLc().getCallsNb() > 0) {
				LinphoneCall call = LinphoneManager.getLc().getCalls()[0];

				if (LinphoneUtils.isCallEstablished(call)) {
					enableAndRefreshInCallActions();
				}
			}
			if (savedInstanceState != null) {
				// Fragment already created, no need to create it again (else it will generate a memory leak with duplicated fragments)
				isSpeakerEnabled = savedInstanceState.getBoolean("Speaker");
				isMicMuted = savedInstanceState.getBoolean("Mic");
				isVideoCallPaused = savedInstanceState.getBoolean("VideoCallPaused");
				if (savedInstanceState.getBoolean("AskingVideo")) {
					showAcceptCallUpdateDialog();
					TimeRemind = savedInstanceState.getLong("TimeRemind");
					createTimerForDialog(TimeRemind);
				}
                /*if (status != null && savedInstanceState.getBoolean("AskingZrtp")) {
                    status.setisZrtpAsk(savedInstanceState.getBoolean("AskingZrtp"));
                }*/
				refreshInCallActions();
				return;
			} else {
				isSpeakerEnabled = LinphoneManager.getLc().isSpeakerEnabled();
				isMicMuted = LinphoneManager.getLc().isMicMuted();
			}

			Fragment callFragment;
			if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
				callFragment = new CallVideoFragment();
				videoCallFragment = (CallVideoFragment) callFragment;
				displayVideoCall(false);
				LinphoneManager.getInstance().routeAudioToSpeaker();
				isSpeakerEnabled = true;
			} else {
				callFragment = new CallAudioFragment();
				audioCallFragment = (CallAudioFragment) callFragment;
			}

			if(BluetoothManager.getInstance().isBluetoothHeadsetAvailable()){
				BluetoothManager.getInstance().routeAudioToBluetooth();
			}

			callFragment.setArguments(getIntent().getExtras());
			getSupportFragmentManager().beginTransaction().add(R.id.fragmentContainer, callFragment).commitAllowingStateLoss();
		}
	}

	public void createTimerForDialog(long time) {
		timer = new CountDownTimer(time , 1000) {
			public void onTick(long millisUntilFinished) {
				TimeRemind = millisUntilFinished;
			}
			public void onFinish() {
				if (dialog != null) {
					dialog.dismiss();
					dialog = null;
				}
				acceptCallUpdate(false);
			}
		}.start();
	}

	private boolean isVideoEnabled(LinphoneCall call) {
		if(call != null){
			return call.getCurrentParams().getVideoEnabled();
		}
		return false;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putBoolean("Speaker", LinphoneManager.getLc().isSpeakerEnabled());
		outState.putBoolean("Mic", LinphoneManager.getLc().isMicMuted());
		outState.putBoolean("VideoCallPaused", isVideoCallPaused);
		outState.putBoolean("AskingVideo", isVideoAsk);
		outState.putLong("TimeRemind", TimeRemind);
        //if (status != null) outState.putBoolean("AskingZrtp", status.getisZrtpAsk());
		if (dialog != null) dialog.dismiss();
		super.onSaveInstanceState(outState);
	}

	private boolean isTablet() {
		return getResources().getBoolean(R.bool.isTablet);
	}

	private void initUI() {
		inflater = LayoutInflater.from(this);
		container = (ViewGroup) findViewById(R.id.topLayout);

		//TopBar
		mBinding.video.setOnClickListener(this);
		enabledVideoButton(false);
		mBinding.videoProgress.setVisibility(View.GONE);
		mBinding.micro.setOnClickListener(this);
		mBinding.speaker.setOnClickListener(this);

		//BottonBar
		mBinding.hangUp.setOnClickListener(this);
		mBinding.dialer.setOnClickListener(this);
		mBinding.numpad.getBackground().setAlpha(240);

		//Others
		mBinding.pause.setOnClickListener(this);
		enabledPauseButton(false);

		mBinding.switchCamera.setOnClickListener(this);

		mBinding.audioRoute.setOnClickListener(this);
		mBinding.routeSpeaker.setOnClickListener(this);
		mBinding.routeEarpiece.setOnClickListener(this);
		mBinding.routeBluetooth.setOnClickListener(this);

		if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
			try {
				mBinding.audioRoute.setVisibility(View.VISIBLE);
				mBinding.speaker.setVisibility(View.INVISIBLE);
			} catch (NullPointerException npe) { Log.e("Bluetooth: Audio routes menu disabled on tablets for now (2)"); }
		} else {
			try {
				mBinding.audioRoute.setVisibility(View.INVISIBLE);
				mBinding.speaker.setVisibility(View.VISIBLE);
			} catch (NullPointerException npe) { Log.e("Bluetooth: Audio routes menu disabled on tablets for now (3)"); }
		}

		//createInCallStats();
		LinphoneManager.getInstance().changeStatusToOnThePhone();
	}

	public void checkAndRequestPermission(String permission, int result) {
		int permissionGranted = getPackageManager().checkPermission(permission, getPackageName());
		Log.i("[Permission] " + permission + " is " + (permissionGranted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

		if (permissionGranted != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this, new String[] { permission }, result);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, final int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		for (int i = 0; i < permissions.length; i++) {
			Log.i("[Permission] " + permissions[i] + " is " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
		}

		switch (requestCode) {
			case PERMISSIONS_REQUEST_CAMERA:
				UIThreadDispatcher.dispatch(new Runnable() {
					@Override
					public void run() {
						acceptCallUpdate(grantResults[0] == PackageManager.PERMISSION_GRANTED);
					}
				});
				break;
			case PERMISSIONS_ENABLED_CAMERA:
				UIThreadDispatcher.dispatch(new Runnable() {
					@Override
					public void run() {
						disableVideo(grantResults[0] != PackageManager.PERMISSION_GRANTED);
					}
				});
				break;
			case PERMISSIONS_ENABLED_MIC:
				UIThreadDispatcher.dispatch(new Runnable() {
					@Override
					public void run() {
						if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
							toggleMicro();
						}
					}
				});
				break;
		}
	}

	/*public void createInCallStats() {

		mBinding.menu.setOnClickListener(view -> {
			if (mBinding.sideMenu.isDrawerVisible(Gravity.LEFT)) {
				mBinding.sideMenu.closeDrawer(mBinding.sideMenuContent);
			} else {
				mBinding.sideMenu.openDrawer(mBinding.sideMenuContent);
			}
		});

		initCallStatsRefresher(LinphoneManager.getLc().getCurrentCall(), findViewById(R.id.incall_stats));
	}*/

	private void refreshIncallUi(){
		refreshInCallActions();
		refreshCallList(getResources());
		enableAndRefreshInCallActions();
	}

	protected void setSpeakerEnabled(boolean enabled){
		isSpeakerEnabled = enabled;
	}

	protected void refreshInCallActions() {
		if (!LinphonePreferences.instance().isVideoEnabled()) {
			enabledVideoButton(false);
		} else {
			if(mBinding.video.isEnabled()) {
				if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
					mBinding.videoProgress.setVisibility(View.INVISIBLE);
				}
			}
		}

		if (isSpeakerEnabled) {
			mBinding.speaker.setImageResource(R.drawable.ic_volume_up);
		} else {
			mBinding.speaker.setImageResource(R.drawable.ic_volume_off);
		}

		if (getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName()) != PackageManager.PERMISSION_GRANTED) {
			isMicMuted = true;
		}
		if (isMicMuted) {
			mBinding.micro.setImageResource(R.drawable.ic_mic_off);
		} else {
			mBinding.micro.setImageResource(R.drawable.ic_mic);
		}

		try {
			mBinding.routeSpeaker.setBackgroundColor(R.attr.colorPrimaryLite);
			if (BluetoothManager.getInstance().isUsingBluetoothAudioRoute()) {
				isSpeakerEnabled = false;
				mBinding.routeEarpiece.setBackgroundColor(R.attr.colorPrimaryLite);
				mBinding.routeBluetooth.setBackgroundColor(R.attr.colorSecondaryLight);
//				mBinding.routeEarpiece.setImageResource(R.drawable.route_earpiece);
//				mBinding.routeBluetooth.setImageResource(R.drawable.route_bluetooth_selected);
				return;
			} else {
				mBinding.routeEarpiece.setBackgroundColor(R.attr.colorSecondaryLight);
				mBinding.routeBluetooth.setBackgroundColor(R.attr.colorPrimaryLite);
//				mBinding.routeEarpiece.setImageResource(R.drawable.route_earpiece_selected);
//				mBinding.routeBluetooth.setImageResource(R.drawable.route_bluetooth);
			}

			if (isSpeakerEnabled) {
				mBinding.routeSpeaker.setBackgroundColor(R.attr.colorSecondaryLight);
				mBinding.routeEarpiece.setBackgroundColor(R.attr.colorPrimaryLite);
				mBinding.routeBluetooth.setBackgroundColor(R.attr.colorPrimaryLite);
//				mBinding.routeSpeaker.setImageResource(R.drawable.route_speaker_selected);
//				mBinding.routeEarpiece.setImageResource(R.drawable.route_earpiece);
//				mBinding.routeBluetooth.setImageResource(R.drawable.route_bluetooth);
			}
		} catch (NullPointerException npe) {
			Log.e("Bluetooth: Audio routes menu disabled on tablets for now (4)");
		}
	}

	private void enableAndRefreshInCallActions() {
		if(LinphoneManager.getLc().getCurrentCall() != null && LinphonePreferences.instance().isVideoEnabled() && !LinphoneManager.getLc().getCurrentCall().mediaInProgress()) {
			enabledVideoButton(true);
		} else {
			enabledVideoButton(false);
		}
		if(LinphoneManager.getLc().getCurrentCall() != null && !LinphoneManager.getLc().getCurrentCall().mediaInProgress()){
			enabledPauseButton(true);
		} else {
			enabledPauseButton(false);
		}
		mBinding.micro.setEnabled(true);
		if(!isTablet()){
			mBinding.speaker.setEnabled(true);
		}
		mBinding.pause.setEnabled(true);
		mBinding.dialer.setEnabled(true);
	}

	public void updateStatusFragment(StatusFragment statusFragment) {
		status = statusFragment;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
			//displayVideoCallControlsIfHidden();
		}

		if (id == R.id.video) {
			int camera = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
			Log.i("[Permission] Camera permission is " + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

			if (camera == PackageManager.PERMISSION_GRANTED) {
				disableVideo(isVideoEnabled(LinphoneManager.getLc().getCurrentCall()));
			} else {
				checkAndRequestPermission(Manifest.permission.CAMERA, PERMISSIONS_ENABLED_CAMERA);
			}
		} else if (id == R.id.micro) {
			int recordAudio = getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
			Log.i("[Permission] Record audio permission is " + (recordAudio == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

			if (recordAudio == PackageManager.PERMISSION_GRANTED) {
				toggleMicro();
			} else {
				checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_ENABLED_MIC);
			}
		} else if (id == R.id.speaker) {
			toggleSpeaker();
		} else if (id == R.id.pause) {
			pauseOrResumeCall(LinphoneManager.getLc().getCurrentCall());
		} else if (id == R.id.hang_up) {
			hangUp();
		} else if (id == R.id.dialer)  {
			hideOrDisplayNumpad();
		} /*else if (id == R.id.chat) {
			goToChatList();
		}*/ else if (id == R.id.switchCamera)  {
			if (videoCallFragment != null) {
				videoCallFragment.switchCamera();
			}
		} else if (id == R.id.audio_route) {
			hideOrDisplayAudioRoutes();
		} else if (id == R.id.route_bluetooth) {
			if (BluetoothManager.getInstance().routeAudioToBluetooth()) {
				isSpeakerEnabled = false;
				mBinding.routeBluetooth.setBackgroundColor(R.attr.colorSecondaryLight);
				mBinding.routeSpeaker.setBackgroundColor(R.attr.colorPrimaryLite);
				mBinding.routeEarpiece.setBackgroundColor(R.attr.colorPrimaryLite);
//				mBinding.routeBluetooth.setImageResource(R.drawable.route_bluetooth_selected);
//				mBinding.routeSpeaker.setImageResource(R.drawable.route_speaker);
//				mBinding.routeEarpiece.setImageResource(R.drawable.route_earpiece);
			}
			hideOrDisplayAudioRoutes();
		} else if (id == R.id.route_earpiece) {
			LinphoneManager.getInstance().routeAudioToReceiver();
			isSpeakerEnabled = false;
			mBinding.routeBluetooth.setBackgroundColor(R.attr.colorPrimaryLite);
			mBinding.routeSpeaker.setBackgroundColor(R.attr.colorPrimaryLite);
			mBinding.routeEarpiece.setBackgroundColor(R.attr.colorSecondaryLight);
//			routeBluetooth.setImageResource(R.drawable.route_bluetooth);
//			routeSpeaker.setImageResource(R.drawable.route_speaker);
//			routeEarpiece.setImageResource(R.drawable.route_earpiece_selected);
			hideOrDisplayAudioRoutes();
		} else if (id == R.id.route_speaker) {
			LinphoneManager.getInstance().routeAudioToSpeaker();
			isSpeakerEnabled = true;
			mBinding.routeBluetooth.setBackgroundColor(R.attr.colorPrimaryLite);
			mBinding.routeSpeaker.setBackgroundColor(R.attr.colorSecondaryLight);
			mBinding.routeEarpiece.setBackgroundColor(R.attr.colorPrimaryLite);
//			routeBluetooth.setImageResource(R.drawable.route_bluetooth);
//			routeSpeaker.setImageResource(R.drawable.route_speaker_selected);
//			routeEarpiece.setImageResource(R.drawable.route_earpiece);
			hideOrDisplayAudioRoutes();
		} else if (id == R.id.call_pause) {
			LinphoneCall call = (LinphoneCall) v.getTag();
			pauseOrResumeCall(call);
		}
	}

	private void enabledVideoButton(boolean enabled){
		if(enabled) {
			mBinding.video.setEnabled(true);
		} else {
			mBinding.video.setEnabled(false);
		}
	}

	private void enabledPauseButton(boolean enabled){
		if(enabled) {
			mBinding.pause.setEnabled(true);
		} else {
			mBinding.pause.setEnabled(false);
		}
	}

	private void disableVideo(final boolean videoDisabled) {
		final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if (call == null) {
			return;
		}

		if (videoDisabled) {
			LinphoneCallParams params = LinphoneManager.getLc().createCallParams(call);
			params.setVideoEnabled(false);
			LinphoneManager.getLc().updateCall(call, params);
		} else {
			mBinding.videoProgress.setVisibility(View.VISIBLE);
			if (call.getRemoteParams() != null && !call.getRemoteParams().isLowBandwidthEnabled()) {
				LinphoneManager.getInstance().addVideo();
			} else {
				displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
			}
		}
	}

	public void displayCustomToast(final String message, final int duration) {
		LayoutInflater inflater = getLayoutInflater();
		View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

		TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
		toastText.setText(message);

		final Toast toast = new Toast(getApplicationContext());
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.setDuration(duration);
		toast.setView(layout);
		toast.show();
	}

	private void switchVideo(final boolean displayVideo) {
		final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if (call == null) {
			return;
		}

		//Check if the call is not terminated
		if(call.getState() == State.CallEnd || call.getState() == State.CallReleased) return;

		if (!displayVideo) {
			showAudioView();
		} else {
			if (!call.getRemoteParams().isLowBandwidthEnabled()) {
				LinphoneManager.getInstance().addVideo();
				if (videoCallFragment == null || !videoCallFragment.isVisible())
					showVideoView();
			} else {
				displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
			}
		}
	}

	private void showAudioView() {
		if (LinphoneManager.getLc().getCurrentCall() != null) {
			if (!isSpeakerEnabled) {
				LinphoneManager.getInstance().enableProximitySensing(true);
			}
		}
		replaceFragmentVideoByAudio();
		displayAudioCall();
		showStatusBar();
		removeCallbacks();
	}

	private void showVideoView() {
		if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
			Log.w("Bluetooth not available, using speaker");
			LinphoneManager.getInstance().routeAudioToSpeaker();
			isSpeakerEnabled = true;
		}
		refreshInCallActions();

		LinphoneManager.getInstance().enableProximitySensing(false);

		replaceFragmentAudioByVideo();
		hideStatusBar();
	}

	private void displayNoCurrentCall(boolean display){
		if(!display) {
			mBinding.activeCall.setVisibility(View.VISIBLE);
			mBinding.noCurrentCall.setVisibility(View.GONE);
		} else {
			mBinding.activeCall.setVisibility(View.GONE);
			mBinding.noCurrentCall.setVisibility(View.VISIBLE);
		}
	}

	private void displayCallPaused(boolean display){
		if(display){
			mBinding.remotePause.setVisibility(View.VISIBLE);
		} else {
			mBinding.remotePause.setVisibility(View.GONE);
		}
	}

	private void displayAudioCall(){
		mBinding.menu.setVisibility(View.VISIBLE);
		mBinding.activeCall.setVisibility(View.VISIBLE);
		mBinding.avatarLayout.setVisibility(View.VISIBLE);
		mBinding.switchCamera.setVisibility(View.GONE);
	}

	private void replaceFragmentVideoByAudio() {
		audioCallFragment = new CallAudioFragment();
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.replace(R.id.fragmentContainer, audioCallFragment);
		try {
			transaction.commitAllowingStateLoss();
		} catch (Exception e) {
		}
	}

	private void replaceFragmentAudioByVideo() {
//		Hiding controls to let displayVideoCallControlsIfHidden add them plus the callback
		videoCallFragment = new CallVideoFragment();

		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.replace(R.id.fragmentContainer, videoCallFragment);
		try {
			transaction.commitAllowingStateLoss();
		} catch (Exception e) {
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

	protected void toggleSpeaker() {
		isSpeakerEnabled = !isSpeakerEnabled;
		if (LinphoneManager.getLc().getCurrentCall() != null) {
			if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall()))
				LinphoneManager.getInstance().enableProximitySensing(false);
			else
				LinphoneManager.getInstance().enableProximitySensing(!isSpeakerEnabled);
		}
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

	public void pauseOrResumeCall(LinphoneCall call) {
		LinphoneCore lc = LinphoneManager.getLc();
		if (call != null && LinphoneManager.getLc().getCurrentCall() == call) {
			lc.pauseCall(call);
			if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
				isVideoCallPaused = true;
			}
		} else if (call != null) {
			if (call.getState() == State.Paused) {
				if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
					LinphoneAddress address = call.getRemoteAddress();
					SpoContact contact = DBUtils.getContactForNumber(address.getUserName());
					if (contact != null)
						CryptUtils.initCryptSound(Integer.parseInt(PrefsUtils.ins().getIdTT()), Integer.parseInt(contact.getIdUser()), true);
					else
						CryptUtils.initCryptSound(0, 0, false);
				} else {
					CryptUtils.initCryptSound(0, 0, false);
				}

				lc.resumeCall(call);
				if (isVideoCallPaused) {
					isVideoCallPaused = false;
				}
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

	public void displayVideoCall(boolean display){
		if(display) {
			showStatusBar();
			mBinding.menu.setVisibility(View.VISIBLE);
			mBinding.activeCall.setVisibility(View.VISIBLE);
			mBinding.avatarLayout.setVisibility(View.GONE);
			mBinding.callsList.setVisibility(View.VISIBLE);
			if (cameraNumber > 1) {
				mBinding.switchCamera.setVisibility(View.VISIBLE);
			}
		} else {
			hideStatusBar();
			mBinding.menu.setVisibility(View.GONE);
			mBinding.activeCall.setVisibility(View.GONE);
			mBinding.switchCamera.setVisibility(View.GONE);
			mBinding.callsList.setVisibility(View.GONE);
		}
	}


	public void displayVideoCallControlsIfHidden() {
		if (mBinding.menu != null) {
			if (mBinding.menu.getVisibility() != View.VISIBLE) {
				displayVideoCall(true);
				resetControlsHidingCallBack(SECONDS_BEFORE_HIDING_CONTROLS);
			} else {
				resetControlsHidingCallBack(0);
			}
		}
	}

	public void resetControlsHidingCallBack(int second) {
		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;

		if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall()) && mControlsHandler != null) {
			mControlsHandler.postDelayed(mControls = () -> {
				hideNumpad();
				mBinding.video.setEnabled(true);
				displayVideoCall(false);
				mBinding.numpad.setVisibility(View.GONE);
			}, second);
		}
	}

	public void removeCallbacks() {
		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;
	}

	private void hideNumpad() {
		if (mBinding.numpad == null || mBinding.numpad.getVisibility() != View.VISIBLE) {
			return;
		}
		mBinding.numpad.setVisibility(View.GONE);
	}

	private void hideOrDisplayNumpad() {
		if (mBinding.numpad == null) {
			return;
		}

		if (mBinding.numpad.getVisibility() == View.VISIBLE) {
			hideNumpad();
		} else {
			mBinding.numpad.setVisibility(View.VISIBLE);
		}
	}

	private void hideOrDisplayAudioRoutes() {
		if (mBinding.menuAudioRoute.getVisibility() == View.VISIBLE) {
			mBinding.menuAudioRoute.setVisibility(View.GONE);
		} else {
			mBinding.menuAudioRoute.setVisibility(View.VISIBLE);
		}
	}

	public void goBackToDialer() {
		Intent intent = new Intent();
		intent.putExtra("Transfer", false);
		setResult(Activity.RESULT_FIRST_USER, intent);
		finish();
	}

	private void goToChatList() {
		Intent intent = new Intent();
		intent.putExtra("chat", true);
		setResult(Activity.RESULT_FIRST_USER, intent);
		finish();
	}

	public void acceptCallUpdate(boolean accept) {
		if (timer != null) {
			timer.cancel();
		}

		LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if (call == null) {
			return;
		}

		LinphoneCallParams params = LinphoneManager.getLc().createCallParams(call);
		if (accept) {
			params.setVideoEnabled(true);
			LinphoneManager.getLc().enableVideo(true, true);
		}

		try {
			LinphoneManager.getLc().acceptCallUpdate(call, params);
		} catch (LinphoneCoreException e) {
			Log.e(e);
		}
	}

	public void startIncomingCallActivity() {
		startActivity(new Intent(this, CallIncomingActivity.class));
	}

	public void hideStatusBar() {
		if (isTablet()) {
			return;
		}

		findViewById(R.id.status).setVisibility(View.GONE);
		findViewById(R.id.fragmentContainer).setPadding(0, 0, 0, 0);
	}

	public void showStatusBar() {
		if (isTablet()) {
			return;
		}

		if (status != null && !status.isVisible()) {
			// Hack to ensure statusFragment is visible after coming back to
			// dialer from chat
			status.getView().setVisibility(View.VISIBLE);
		}
		findViewById(R.id.status).setVisibility(View.VISIBLE);
		//findViewById(R.id.fragmentContainer).setPadding(0, LinphoneUtils.pixelsToDpi(getResources(), 40), 0, 0);
	}


	private void showAcceptCallUpdateDialog() {
		dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.colorC));
		d.setAlpha(200);
		dialog.setContentView(R.layout.dialog);
		dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
		dialog.getWindow().setBackgroundDrawable(d);

		TextView customText = (TextView) dialog.findViewById(R.id.customText);
		customText.setText(getResources().getString(R.string.add_video_dialog));
		Button delete = (Button) dialog.findViewById(R.id.delete_button);
		delete.setText(R.string.accept);
		Button cancel = (Button) dialog.findViewById(R.id.cancel);
		cancel.setText(R.string.decline);
        isVideoAsk = true;

		delete.setOnClickListener(view -> {
			int camera = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
			Log.i("[Permission] Camera permission is " + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

			if (camera == PackageManager.PERMISSION_GRANTED) {
				CallActivity.instance().acceptCallUpdate(true);
			} else {
				checkAndRequestPermission(Manifest.permission.CAMERA, PERMISSIONS_REQUEST_CAMERA);
			}
			isVideoAsk = false;
			dialog.dismiss();
			dialog = null;
		});

		cancel.setOnClickListener(view -> {
			if (CallActivity.isInstanciated()) {
				CallActivity.instance().acceptCallUpdate(false);
			}
			isVideoAsk = false;
			dialog.dismiss();
			dialog = null;
		});
		dialog.show();
	}

	@Override
	protected void onResume() {

		instance = this;
		super.onResume();

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}
		isSpeakerEnabled = LinphoneManager.getLc().isSpeakerEnabled();

		refreshIncallUi();
		handleViewIntent();

		if (!isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
			if (!isSpeakerEnabled) {
				LinphoneManager.getInstance().enableProximitySensing(true);
				removeCallbacks();
			}
		}
	}

	private void handleViewIntent() {
		Intent intent = getIntent();
		if(intent != null && intent.getAction() == "android.intent.action.VIEW") {
			LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
			if(call != null && isVideoEnabled(call)) {
				LinphonePlayer player = call.getPlayer();
				String path = intent.getData().getPath();
				Log.i("Openning " + path);
				int openRes = player.open(path);
				if(openRes == -1) {
					String message = "Could not open " + path;
					Log.e(message);
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
					return;
				}
				Log.i("Start playing");
				if(player.start() == -1) {
					player.close();
					String message = "Could not start playing " + path;
					Log.e(message);
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
				}
			}
		}
	}

	@Override
	protected void onPause() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}

		super.onPause();

		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;
	}

	@Override
	protected void onDestroy() {
		LinphoneManager.getInstance().changeStatusToOnline();
		LinphoneManager.getInstance().enableProximitySensing(false);

		unregisterReceiver(headsetReceiver);

		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;
		mControlsHandler = null;

		unbindDrawables(findViewById(R.id.topLayout));
		if (mTimer != null) {
			mTimer.cancel();
		}
		instance = null;
		super.onDestroy();
		System.gc();
	}

	private void unbindDrawables(View view) {
		if (view.getBackground() != null) {
			view.getBackground().setCallback(null);
		}
		if (view instanceof ImageView) {
			view.setOnClickListener(null);
		}
		if (view instanceof ViewGroup && !(view instanceof AdapterView)) {
			for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
				unbindDrawables(((ViewGroup) view).getChildAt(i));
			}
			((ViewGroup) view).removeAllViews();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneUtils.onKeyVolumeAdjust(keyCode)) return true;
		if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) return true;
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onBackPressed() {
		if (dialog != null) {
			acceptCallUpdate(false);
			dialog.dismiss();
			dialog = null;
		}
		return;
	}

	public void bindAudioFragment(CallAudioFragment fragment) {
		audioCallFragment = fragment;
	}

	public void bindVideoFragment(CallVideoFragment fragment) {
		videoCallFragment = fragment;
	}


	//CALL INFORMATION
	private void displayCurrentCall(LinphoneCall call){
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
			}
		} else {
			mBinding.contactNumber.setText(address.getUserName());
		}
		registerCallDurationTimer(null, call);
	}

	private void displayPausedCalls(Resources resources, final LinphoneCall call, int index) {
		// Control Row
		LinearLayout callView;

		if(call != null) {
			callView = (LinearLayout) inflater.inflate(R.layout.call_inactive_row, container, false);
			callView.setId(index+1);

			TextView contactName = (TextView) callView.findViewById(R.id.contactName);
			ImageView contactImage = (ImageView) callView.findViewById(R.id.contactPicture);

			LinphoneAddress lAddress = call.getRemoteAddress();
			setContactInformation(contactName, contactImage, lAddress);
			displayCallStatusIconAndReturnCallPaused(callView, call);
			registerCallDurationTimer(callView, call);
			mBinding.callsList.addView(callView);
		}

	}

	private void setContactInformation(TextView contactName, ImageView contactPicture,  LinphoneAddress lAddress) {
		SpoContact contact = DBUtils.getContactForNumber(lAddress.getUserName());
		if (contact == null) {
			contactName.setText(lAddress.getUserName());
			contactPicture.setImageDrawable(getDrawable(R.drawable.ic_avatar_one));
		} else {
			contactName.setText(contact.getFullName());
			if (contact.getUriPhoto() != null) {
				Bitmap bm = null;
				try {
					bm = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.parse(contact.getUriPhoto()));
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (bm != null) {
					contactPicture.setImageBitmap(Utils.getCroppedBitmap(bm, 256, 256, 256));
				} else {
					contactPicture.setImageResource(R.drawable.ic_avatar_one);
				}
			} else {
				contactPicture.setImageDrawable(getDrawable(R.drawable.ic_avatar_one));
			}
		}
	}

	private boolean displayCallStatusIconAndReturnCallPaused(LinearLayout callView, LinphoneCall call) {
		boolean isCallPaused;
		ImageView callState = (ImageView) callView.findViewById(R.id.call_pause);
		callState.setTag(call);
		callState.setOnClickListener(this);

		if (call.getState() == State.Paused || call.getState() == State.PausedByRemote || call.getState() == State.Pausing) {
			isCallPaused = true;
		} else if (call.getState() == State.OutgoingInit || call.getState() == State.OutgoingProgress || call.getState() == State.OutgoingRinging) {
			isCallPaused = false;
		} else {
			isCallPaused = false;
		}

		return isCallPaused;
	}

	private void registerCallDurationTimer(View v, LinphoneCall call) {
		int callDuration = call.getDuration();
		if (callDuration == 0 && call.getState() != State.StreamsRunning) {
			return;
		}

		Chronometer timer;
		if(v == null){
			timer = (Chronometer) findViewById(R.id.currentCallTimer);
		} else {
			timer = (Chronometer) v.findViewById(R.id.call_timer);
		}

		if (timer == null) {
			throw new IllegalArgumentException("no callee_duration view found");
		}

		timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
		timer.start();
	}

	public void refreshCallList(Resources resources) {
		List<LinphoneCall> pausedCalls = LinphoneUtils.getCallsInState(LinphoneManager.getLc(), Arrays.asList(State.PausedByRemote));

		//MultiCalls
		if(LinphoneManager.getLc().getCallsNb() > 1){
			mBinding.callsList.setVisibility(View.VISIBLE);
		}

		//Active call
		if(LinphoneManager.getLc().getCurrentCall() != null) {
			displayNoCurrentCall(false);
			if(isVideoEnabled(LinphoneManager.getLc().getCurrentCall()) && pausedCalls.size() == 0) {
				displayVideoCall(false);
			} else {
				displayAudioCall();
			}
		} else {
			showAudioView();
			displayNoCurrentCall(true);
			if(LinphoneManager.getLc().getCallsNb() == 1) {
				mBinding.callsList.setVisibility(View.VISIBLE);
			}
		}


		if(mBinding.callsList != null) {
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

		//Paused by remote
		if (pausedCalls.size() == 1) {
			displayCallPaused(true);
		} else {
			displayCallPaused(false);
		}
	}

	@SuppressWarnings("deprecation")
	private void formatText(TextView tv, String name, String value) {
		tv.setText(Html.fromHtml("<b>" + name + " </b>" + value));
	}

	private String getEncoderText(String mime){
		String ret = mEncoderTexts.get(mime);
		if (ret == null){
			org.linphone.mediastream.Factory msfactory = LinphoneManager.getLc().getMSFactory();
			ret = msfactory.getEncoderText(mime);
			mEncoderTexts.put(mime, ret);
		}
		return ret;
	}
	private String getDecoderText(String mime){
		String ret = mDecoderTexts.get(mime);
		if (ret == null){
			org.linphone.mediastream.Factory msfactory = LinphoneManager.getLc().getMSFactory();
			ret = msfactory.getDecoderText(mime);
			mDecoderTexts.put(mime, ret);
		}
		return ret;
	}

	private void displayMediaStats(LinphoneCallParams params, LinphoneCallStats stats
			, PayloadType media , View layout, TextView title, TextView codec, TextView dl
			, TextView ul, TextView edl, TextView ice, TextView ip, TextView senderLossRate
			, TextView receiverLossRate, TextView enc, TextView dec, TextView videoResolutionSent
			, TextView videoResolutionReceived, TextView videoFpsSent, TextView videoFpsReceived
			, boolean isVideo, TextView jitterBuffer) {
		if (stats != null) {
			String mime = null;

			layout.setVisibility(View.VISIBLE);
			title.setVisibility(TextView.VISIBLE);
			if (media != null) {
				mime = media.getMime();
				formatText(codec, getString(R.string.call_stats_codec),
						mime + " / " + (media.getRate() / 1000) + "kHz");
			}
			if (mime != null ){
				formatText(enc, getString(R.string.call_stats_encoder_name), getEncoderText(mime));
				formatText(dec, getString(R.string.call_stats_decoder_name), getDecoderText(mime));
			}
			formatText(dl, getString(R.string.call_stats_download),
					String.valueOf((int) stats.getDownloadBandwidth()) + " kbits/s");
			formatText(ul, getString(R.string.call_stats_upload),
					String.valueOf((int) stats.getUploadBandwidth()) + " kbits/s");
			if (isVideo) {
				formatText(edl, getString(R.string.call_stats_estimated_download),
						String.valueOf((int) stats.getEstimatedDownloadBandwidth()) + " kbits/s");
			}
			formatText(ice, getString(R.string.call_stats_ice),
					stats.getIceState().toString());
			formatText(ip, getString(R.string.call_stats_ip),
					(stats.getIpFamilyOfRemote() == LinphoneAddressFamily.INET_6.getInt()) ?
							"IpV6" : (stats.getIpFamilyOfRemote() == LinphoneAddressFamily.INET.getInt()) ?
							"IpV4" : "Unknown");
			formatText(senderLossRate, getString(R.string.call_stats_sender_loss_rate),
					new DecimalFormat("##.##").format(stats.getSenderLossRate()) + "%");
			formatText(receiverLossRate, getString(R.string.call_stats_receiver_loss_rate),
					new DecimalFormat("##.##").format(stats.getReceiverLossRate())+ "%");
			if (isVideo) {
				formatText(videoResolutionSent,
						getString(R.string.call_stats_video_resolution_sent),
						"\u2191 " + params.getSentVideoSize().toDisplayableString());
				formatText(videoResolutionReceived,
						getString(R.string.call_stats_video_resolution_received),
						"\u2193 " + params.getReceivedVideoSize().toDisplayableString());
				formatText(videoFpsSent,
						getString(R.string.call_stats_video_fps_sent),
						"\u2191 " + params.getSentFramerate());
				formatText(videoFpsReceived,
						getString(R.string.call_stats_video_fps_received),
						"\u2193 " + params.getReceivedFramerate());
			} else {
				formatText(jitterBuffer, getString(R.string.call_stats_jitter_buffer),
						new DecimalFormat("##.##").format(stats.getJitterBufferSize()) + " ms");
			}
		} else {
			layout.setVisibility(View.GONE);
			title.setVisibility(TextView.GONE);
		}
	}

	/*public void initCallStatsRefresher(final LinphoneCall call, final View view) {
		if (mTimer != null && mTask != null) {
			return;
		}

		final TextView titleAudio = (TextView) view.findViewById(R.id.call_stats_audio);
		final TextView titleVideo = (TextView) view.findViewById(R.id.call_stats_video);
		final TextView codecAudio = (TextView) view.findViewById(R.id.codec_audio);
		final TextView codecVideo = (TextView) view.findViewById(R.id.codec_video);
		final TextView encoderAudio = (TextView) view.findViewById(R.id.encoder_audio);
		final TextView decoderAudio = (TextView) view.findViewById(R.id.decoder_audio);
		final TextView encoderVideo = (TextView) view.findViewById(R.id.encoder_video);
		final TextView decoderVideo = (TextView) view.findViewById(R.id.decoder_video);
		final TextView dlAudio = (TextView) view.findViewById(R.id.downloadBandwith_audio);
		final TextView ulAudio = (TextView) view.findViewById(R.id.uploadBandwith_audio);
		final TextView dlVideo = (TextView) view.findViewById(R.id.downloadBandwith_video);
		final TextView ulVideo = (TextView) view.findViewById(R.id.uploadBandwith_video);
		final TextView edlVideo = (TextView) view.findViewById(R.id.estimatedDownloadBandwidth_video);
		final TextView iceAudio = (TextView) view.findViewById(R.id.ice_audio);
		final TextView iceVideo = (TextView) view.findViewById(R.id.ice_video);
		final TextView videoResolutionSent = (TextView) view.findViewById(R.id.video_resolution_sent);
		final TextView videoResolutionReceived = (TextView) view.findViewById(R.id.video_resolution_received);
		final TextView videoFpsSent = (TextView) view.findViewById(R.id.video_fps_sent);
		final TextView videoFpsReceived = (TextView) view.findViewById(R.id.video_fps_received);
		final TextView senderLossRateAudio = (TextView) view.findViewById(R.id.senderLossRateAudio);
		final TextView receiverLossRateAudio = (TextView) view.findViewById(R.id.receiverLossRateAudio);
		final TextView senderLossRateVideo = (TextView) view.findViewById(R.id.senderLossRateVideo);
		final TextView receiverLossRateVideo = (TextView) view.findViewById(R.id.receiverLossRateVideo);
		final TextView ipAudio = (TextView) view.findViewById(R.id.ip_audio);
		final TextView ipVideo = (TextView) view.findViewById(R.id.ip_video);
		final TextView jitterBufferAudio = (TextView) view.findViewById(R.id.jitterBufferAudio);
		final View videoLayout = view.findViewById(R.id.callStatsVideo);
		final View audioLayout = view.findViewById(R.id.callStatsAudio);


	 	mTimer = new Timer();
		mTask = new TimerTask() {
			@Override
			public void run() {
				if (call == null) {
					mTimer.cancel();
					return;
				}

				if (titleAudio == null || codecAudio == null || dlVideo == null || edlVideo == null || iceAudio == null
						|| videoResolutionSent == null || videoLayout == null || titleVideo == null
						|| ipVideo == null || ipAudio == null || codecVideo == null
						|| dlAudio == null || ulAudio == null || ulVideo == null || iceVideo == null
						|| videoResolutionReceived == null) {
					mTimer.cancel();
					return;
				}

				mHandler.post(() -> {
					if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() == null) return;
					synchronized(LinphoneManager.getLc()) {
						//if (LinphoneActivity.isInstanciated()) {
							LinphoneCallParams params = call.getCurrentParams();
							if (params != null) {
								LinphoneCallStats audioStats = call.getAudioStats();
								LinphoneCallStats videoStats = null;

								if (params.getVideoEnabled())
									videoStats = call.getVideoStats();

								PayloadType payloadAudio = params.getUsedAudioCodec();
								PayloadType payloadVideo = params.getUsedVideoCodec();

								displayMediaStats(params, audioStats, payloadAudio, audioLayout
										, titleAudio, codecAudio, dlAudio, ulAudio, null, iceAudio
										, ipAudio, senderLossRateAudio, receiverLossRateAudio
										, encoderAudio, decoderAudio, null, null, null, null
										, false, jitterBufferAudio);

								displayMediaStats(params, videoStats, payloadVideo, videoLayout
										, titleVideo, codecVideo, dlVideo, ulVideo, edlVideo, iceVideo
										, ipVideo, senderLossRateVideo, receiverLossRateVideo
										, encoderVideo, decoderVideo
										, videoResolutionSent, videoResolutionReceived
										,videoFpsSent, videoFpsReceived
										, true, null);
							}
						//}
					}
				});
			}
		};
		mTimer.scheduleAtFixedRate(mTask, 0, 1000);
	}*/

	public class HeadsetReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
				if (intent.hasExtra("state")) {
					switch (intent.getIntExtra("state", 0)) {
						case 0:
							if (oldIsSpeakerEnabled) {
								LinphoneManager.getInstance().routeAudioToSpeaker();
								isSpeakerEnabled = true;
								mBinding.speaker.setEnabled(true);
							}
							break;
						case 1:
							LinphoneManager.getInstance().routeAudioToReceiver();
							oldIsSpeakerEnabled = isSpeakerEnabled;
							isSpeakerEnabled = false;
							mBinding.speaker.setEnabled(false);
							break;
					}
					refreshInCallActions();
				}
			}
		}
	}
}
