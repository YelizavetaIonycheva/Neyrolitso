package org.linphone;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.AuthMethod;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.LogCollectionUploadState;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCore.RemoteProvisioningState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PresenceActivityType;
import org.linphone.core.PresenceModel;
import org.linphone.core.PublishState;
import org.linphone.core.Reason;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.Hacks;
import org.linphone.tools.H264Helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.pniei.portal.R;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.utils.PrefsUtils;

import static android.media.AudioManager.MODE_RINGTONE;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_VOICE_CALL;

/**
 * Manager of the low level LibLinphone stuff.
 */
public class LinphoneManager implements LinphoneCoreListener, SensorEventListener {

	private static LinphoneManager instance;
	private Context mServiceContext;
	private AudioManager mAudioManager;
	private PowerManager mPowerManager;
	private Resources mR;
	private LinphonePreferences mPrefs;
	private LinphoneCore mLc;
	private String basePath;
	private static boolean sExited;
	private boolean mAudioFocused;
	private boolean echoTesterIsRunning;
	private boolean dozeModeEnabled;
	private boolean callGsmON;
	private int mLastNetworkType = -1;
	private ConnectivityManager mConnectivityManager;

	private BroadcastReceiver mKeepAliveReceiver;
	private BroadcastReceiver mDozeReceiver;
	private BroadcastReceiver mHookReceiver;
	private BroadcastReceiver mCallReceiver;
	private BroadcastReceiver mNetworkReceiver;
	private IntentFilter mKeepAliveIntentFilter;
	private IntentFilter mDozeIntentFilter;
	private IntentFilter mHookIntentFilter;
	private IntentFilter mCallIntentFilter;
	private IntentFilter mNetworkIntentFilter;

	private WakeLock mProxyWakeLock;
	private WakeLock mProximityWakeLock;

	private SensorManager mSensorManager;
	private Sensor mProximity;
	private boolean mProximitySensingEnabled;
	private boolean handsetON = false;

	private final String mLPConfigXsd;
	private final String mLinphoneFactoryConfigFile;
	private final String mLinphoneConfigFile;
	private final String mRingSoundFile;
	private final String mRingbackSoundFile;
	private final String mPauseSoundFile;
	private final String mChatDatabaseFile;
	private final String mCallLogDatabaseFilePortal;  // только Portal
	private final String mErrorToneFile;
	private final String mUserCertificatePath;

	private Timer mTimer;
	private MediaPlayer mRingerPlayer;
	private Vibrator mVibrator;
	private LinphoneCall ringingCall;

	private static final int LINPHONE_VOLUME_STREAM = STREAM_VOICE_CALL;
	private static final int dbStep = 4;

	protected LinphoneManager(final Context c) {
		sExited = false;
		echoTesterIsRunning = false;
		mServiceContext = c;
		basePath = c.getFilesDir().getAbsolutePath();
		mLPConfigXsd = basePath + "/lpcconfig.xsd";
		mLinphoneFactoryConfigFile = basePath + "/linphoneconfig";
		mLinphoneConfigFile = basePath + "/.linphoneconfig";
		mRingSoundFile = basePath + "/ringtone.mkv";
		mRingbackSoundFile = basePath + "/ringback.wav";
		mPauseSoundFile = basePath + "/hold.mkv";
		mChatDatabaseFile = basePath + "/linphone-history.db";
		mCallLogDatabaseFilePortal = basePath + DBUtils.DB_HISTORY_FILE_P;
		mErrorToneFile = basePath + "/error.wav";
		mUserCertificatePath = basePath;
		mPrefs = LinphonePreferences.instance();
		mAudioManager = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
		mVibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
		mPowerManager = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
		mConnectivityManager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
		mSensorManager = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
		mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		mR = c.getResources();
	}

	public static synchronized final LinphoneManager getInstance() {
		if (instance != null) return instance;
		if (sExited) {
			throw new RuntimeException("Linphone Manager was already destroyed. Better use getLcIfManagerNotDestroyedOrNull and check returned value");
		}
		throw new RuntimeException("Linphone Manager should be created before accessed");
	}

	public static synchronized final LinphoneCore getLc() {
		return getInstance().mLc;
	}

	public static synchronized final LinphoneCore getLcIfManagerNotDestroyedOrNull() {
		if (sExited || instance == null) {
			return null;
		}
		return getLc();
	}

	public static final boolean isInstanciated() {
		return instance != null;
	}

	public static synchronized void destroy() {
		if (instance == null) return;
		getInstance().changeStatusToOffline();
		sExited = true;
		instance.doDestroy();
	}

	public static void BluetoothManagerDestroy() {
		BluetoothManager.getInstance().destroy();
	}

	// --- Инициализация ---

	public static LinphoneManager createAndStart(final Context c) {
		if (instance != null)
			throw new RuntimeException("Linphone Manager is already created");
		instance = new LinphoneManager(c);
		instance.startLibLinphone(c);
		H264Helper.setH264Mode(H264Helper.MODE_AUTO, getLc()); // оставляем, но видео не используется
		return instance;
	}

	private synchronized void startLibLinphone(Context c) {
		try {
			copyAssetsFromPackage();
			mLc = LinphoneCoreFactory.instance().createLinphoneCore(this, mLinphoneConfigFile,
					mLinphoneFactoryConfigFile, null, c);

			TimerTask lTask = new TimerTask() {
				@Override
				public void run() {
					if (mLc != null) {
						mLc.iterate();
					}
				}
			};
			mTimer = new Timer("Linphone scheduler");
			mTimer.schedule(lTask, 0, 20);

			initLiblinphone(mLc);
			updateNetworkReachability();
			// Убираем resetCameraFromPreferences() – видео не нужно

		} catch (Exception e) {
			Log.e(e, "Cannot start linphone");
		}
	}

	private synchronized void initLiblinphone(LinphoneCore lc) throws LinphoneCoreException {
		mLc = lc;
		try {
			String versionName = mServiceContext.getPackageManager().getPackageInfo(mServiceContext.getPackageName(), 0).versionName;
			if (versionName == null) {
				versionName = String.valueOf(mServiceContext.getPackageManager().getPackageInfo(mServiceContext.getPackageName(), 0).versionCode);
			}
			mLc.setUserAgent("SipClient", versionName);
		} catch (NameNotFoundException e) {
			Log.e(e, "cannot get version name");
		}

		mLc.setRingback(mRingbackSoundFile);
		mLc.setPlayFile(mPauseSoundFile);
		// Всегда используем базу Portal
		mLc.setCallLogsDatabasePath(mCallLogDatabaseFilePortal);
		mLc.setUserCertificatesPath(mUserCertificatePath);
		enableDeviceRingtone(mPrefs.isDeviceRingtoneEnabled());

		int availableCores = Runtime.getRuntime().availableProcessors();
		Log.w("MediaStreamer : " + availableCores + " cores detected and configured");
		mLc.setCpuCount(availableCores);
		mLc.migrateCallLogs();

		// Регистрируем ресиверы
		mKeepAliveIntentFilter = new IntentFilter();
		mKeepAliveIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
		mKeepAliveReceiver = new KeepAliveReceiver();
		mServiceContext.registerReceiver(mKeepAliveReceiver, mKeepAliveIntentFilter);

		mHookIntentFilter = new IntentFilter();
		mHookIntentFilter.setPriority(999);
		mHookIntentFilter.addAction("com.base.module.phone.HOOKEVENT");
		mHookReceiver = new HookReceiver();
		mServiceContext.registerReceiver(mHookReceiver, mHookIntentFilter);

		mDozeIntentFilter = new IntentFilter();
		mDozeIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
		mDozeReceiver = new DozeReceiver();
		mServiceContext.registerReceiver(mDozeReceiver, mDozeIntentFilter);

		mCallIntentFilter = new IntentFilter();
		mCallIntentFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
		mCallReceiver = new OutgoingCallReceiver();
		mServiceContext.registerReceiver(mCallReceiver, mCallIntentFilter);

		mNetworkIntentFilter = new IntentFilter();
		mNetworkIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		mNetworkReceiver = new NetworkManager();
		mServiceContext.registerReceiver(mNetworkReceiver, mNetworkIntentFilter);

		mProximityWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "LinphoneManager");
		mProximityWakeLock.setReferenceCounted(false);

	}

	private void copyAssetsFromPackage() throws IOException {
		copyIfNotExist(R.raw.notes_of_the_optimistic, mRingSoundFile);
		copyIfNotExist(R.raw.ringback, mRingbackSoundFile);
		copyIfNotExist(R.raw.hold, mPauseSoundFile);
		copyIfNotExist(R.raw.incoming_chat, mErrorToneFile);
		copyIfNotExist(R.raw.linphonecore_default, mLinphoneConfigFile);
		copyFromPackage(R.raw.linphonecore_factory, new File(mLinphoneFactoryConfigFile).getName());
		copyIfNotExist(R.raw.lpconfig, mLPConfigXsd);
		copyFromPackage(R.raw.assistant_create, new File(mDynamicConfigFile).getName());
	}

	private void copyIfNotExist(int resourceId, String target) throws IOException {
		File file = new File(target);
		if (!file.exists()) {
			copyFromPackage(resourceId, file.getName());
		}
	}

	private void copyFromPackage(int resourceId, String fileName) throws IOException {
		InputStream in = mServiceContext.getResources().openRawResource(resourceId);
		File outFile = new File(basePath, fileName);
		FileOutputStream out = new FileOutputStream(outFile);
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		out.close();
		in.close();
	}

	// Методы, связанные с сетью

	public void updateNetworkReachability() {
		if (mConnectivityManager == null) return;
		boolean connected = false;
		NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
		connected = networkInfo != null && networkInfo.isConnected();
		if (networkInfo == null && Version.sdkAboveOrEqual(Version.API21_LOLLIPOP_50)) {
			for (Network network : mConnectivityManager.getAllNetworks()) {
				if (network != null) {
					networkInfo = mConnectivityManager.getNetworkInfo(network);
					if (networkInfo != null && networkInfo.isConnected()) {
						connected = true;
						break;
					}
				}
			}
		}
		if (networkInfo == null || !connected) {
			Log.i("No connectivity: setting network unreachable");
			mLc.setNetworkReachable(false);
		} else if (dozeModeEnabled) {
			Log.i("Doze Mode enabled: shutting down network");
			mLc.setNetworkReachable(false);
		} else if (connected) {
			boolean wifiOnly = LinphonePreferences.instance().isWifiOnlyEnabled();
			if (wifiOnly) {
				if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
					mLc.setNetworkReachable(true);
				} else {
					Log.i("Wifi-only mode, setting network not reachable");
					mLc.setNetworkReachable(false);
				}
			} else {
				int curType = networkInfo.getType();
				if (curType != mLastNetworkType) {
					Log.i("Connectivity has changed.");
					mLc.setNetworkReachable(false);
				}
				mLc.setNetworkReachable(true);
				mLastNetworkType = curType;
			}
		}
	}

	public void connectivityChanged(ConnectivityManager cm, boolean noConnectivity) {
		updateNetworkReachability();
	}

	// Управление аудио

	private void routeAudioToSpeakerHelper(boolean speakerOn) {
		Log.w("Routing audio to " + (speakerOn ? "speaker" : "earpiece") + ", disabling bluetooth audio route");
		BluetoothManager.getInstance().disableBluetoothSCO();
		mLc.enableSpeaker(speakerOn);
	}

	public void routeAudioToSpeaker() {
		routeAudioToSpeakerHelper(true);
	}

	public void routeAudioToReceiver() {
		routeAudioToSpeakerHelper(false);
	}

	public void enableProximitySensing(boolean enable) {
		if (enable) {
			if (!mProximitySensingEnabled) {
				mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
				mProximitySensingEnabled = true;
			}
		} else {
			if (mProximitySensingEnabled) {
				mSensorManager.unregisterListener(this);
				mProximitySensingEnabled = false;
				if (mProximityWakeLock.isHeld()) {
					mProximityWakeLock.release();
				}
			}
		}
	}

	public void enableDeviceRingtone(boolean use) {
		if (use) {
			mLc.setRing(null);
		} else {
			mLc.setRing(mRingSoundFile);
		}
	}

	private void requestAudioFocus(int stream) {
		if (!mAudioFocused) {
			int res = mAudioManager.requestAudioFocus(null, stream, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
			Log.d("Audio focus requested: " + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "Granted" : "Denied"));
			if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) mAudioFocused = true;
		}
	}

	public void setAudioManagerInCallMode() {
		if (mAudioManager.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
			Log.w("[AudioManager] already in MODE_IN_COMMUNICATION, return");
			return;
		}
		Log.d("[AudioManager] Mode: MODE_IN_COMMUNICATION");
		mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
	}

	// Управление звонками

	public void newOutgoingCall(String to, String displayName) {
		if (to == null) return;
		LinphoneProxyConfig lpc = getLc().getDefaultProxyConfig();
		if (lpc != null) {
			to = lpc.normalizePhoneNumber(to);
		}
		LinphoneAddress lAddress;
		try {
			lAddress = mLc.interpretUrl(to);
			if (mR.getBoolean(R.bool.forbid_self_call) && lpc != null && lAddress.asStringUriOnly().equals(lpc.getIdentity())) {
				return;
			}
		} catch (LinphoneCoreException e) {
			Log.e(e);
			return;
		}
		lAddress.setDisplayName(displayName);
		boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(LinphoneService.instance().getApplicationContext());
		if (mLc.isNetworkReachable()) {
			try {
				LinphoneCallParams params = mLc.createCallParams(null);
				params.enableLowBandwidth(isLowBandwidthConnection);
				mLc.inviteAddressWithParams(lAddress, params);
			} catch (LinphoneCoreException e) {
				Log.e(e);
			}
		}
	}

	public void terminateCall() {
		if (mLc.isInCall()) {
			mLc.terminateCall(mLc.getCurrentCall());
		}
	}

	public boolean acceptCallWithParams(LinphoneCall call, LinphoneCallParams params) {
		try {
			mLc.acceptCallWithParams(call, params);
			return true;
		} catch (LinphoneCoreException e) {
			Log.i(e, "Accept call failed");
			return false;
		}
	}

	public void changeStatusToOnline() {
		LinphoneCore lc = getLcIfManagerNotDestroyedOrNull();
		if (isInstanciated() && lc != null) {
			PresenceModel model = lc.getPresenceModel();
			if (model != null && model.getActivity().getType() != PresenceActivityType.TV) {
				// уже online
			} else {
				// установить online
			}
		}
	}

	public void changeStatusToOnThePhone() {
		LinphoneCore lc = getLcIfManagerNotDestroyedOrNull();
		if (isInstanciated() && lc != null) {
			PresenceModel model = lc.getPresenceModel();
			if (model == null) {
				model = LinphoneCoreFactory.instance().createPresenceModel();
				lc.setPresenceModel(model);
			}
			// Установить статус "в разговоре"
		}
	}

	public void changeStatusToOffline() {
		LinphoneCore lc = getLcIfManagerNotDestroyedOrNull();
		if (isInstanciated() && lc != null) {
			// Установить offline
		}
	}

	public void subscribeFriendList(boolean enabled) {
		LinphoneCore lc = getLcIfManagerNotDestroyedOrNull();
		if (lc != null && lc.getFriendLists() != null && lc.getFriendLists().length > 0) {
			LinphoneFriendList friendList = lc.getFriendLists()[0];
			Log.i("Presence list subscription is " + (enabled ? "enabled" : "disabled"));
			friendList.enableSubscriptions(enabled);
		}
	}

	// Обработчики звонков

	@SuppressLint("WakeLock")
	@Override
	public void callState(final LinphoneCore lc, final LinphoneCall call, final State state, final String message) {
		Log.i("New call state [" + state + "]");
		if (state == State.IncomingReceived && !call.equals(lc.getCurrentCall())) {
			if (call.getReplacedCall() != null) {
				// attended transfer – будет принят автоматически
				return;
			}
			if (state == State.IncomingReceived && getCallGsmON()) {
				if (mLc != null) {
					mLc.declineCall(call, Reason.Busy);
				}
			} else if (state == State.IncomingReceived) {
				TimerTask lTask = new TimerTask() {
					@Override
					public void run() {
						if (mLc != null) {
							try {
								if (mLc.getCallsNb() > 0) {
									mLc.acceptCall(call);
								}
							} catch (LinphoneCoreException e) {
								Log.e(e);
							}
						}
					}
				};
				mTimer = new Timer("Auto answer");
				mTimer.schedule(lTask, mPrefs.getAutoAnswerTime());
			} else if (state == State.IncomingReceived || state == State.CallIncomingEarlyMedia) {
				if (mLc.getCallsNb() == 1) {
					requestAudioFocus(STREAM_RING);
					ringingCall = call;
					startRinging();
				}
			}
		} else if (call == ringingCall && isRinging) {
			stopRinging();
		}

		if (state == State.Connected) {
			if (mLc.getCallsNb() == 1) {
				if (call.getDirection() == CallDirection.Incoming) {
					setAudioManagerInCallMode();
					requestAudioFocus(STREAM_VOICE_CALL);
				}
			}
			if (Hacks.needSoftvolume()) {
				Log.w("Using soft volume audio hack");
				adjustVolume(0);
			}
		}

		if (state == State.CallEnd || state == State.Error) {
			if (mLc.getCallsNb() == 0) {
				enableProximitySensing(false);
				Context activity = getContext();
				if (mAudioFocused) {
					int res = mAudioManager.abandonAudioFocus(null);
					Log.d("Audio focus released: " + (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? "Granted" : "Denied"));
					mAudioFocused = false;
				}
				if (activity != null) {
					TelephonyManager tm = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
					if (tm.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
						Log.d("---AudioManager: back to MODE_NORMAL");
						mAudioManager.setMode(AudioManager.MODE_NORMAL);
						Log.d("All call terminated, routing back to earpiece");
						routeAudioToReceiver();
					}
				}
			}
		}

		if (state == State.OutgoingInit) {
			setAudioManagerInCallMode();
			requestAudioFocus(STREAM_VOICE_CALL);
			startBluetooth();
		}

		if (state == State.StreamsRunning) {
			startBluetooth();
			setAudioManagerInCallMode();
		}
	}

	public void startBluetooth() {
		if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
			BluetoothManager.getInstance().routeAudioToBluetooth();
		}
	}

	// Звонок (рингтон)

	private boolean isRinging;

	private synchronized void startRinging() {
		if (!LinphonePreferences.instance().isDeviceRingtoneEnabled()) {
			routeAudioToSpeaker();
			return;
		}
		if (mR.getBoolean(R.bool.allow_ringing_while_early_media)) {
			routeAudioToSpeaker();
		}
		mAudioManager.setMode(MODE_RINGTONE);
		try {
			if (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE ||
					mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
				long[] pattern = {0, 1000, 1000};
				mVibrator.vibrate(pattern, 1);
			}
			if (mRingerPlayer == null) {
				requestAudioFocus(STREAM_RING);
				mRingerPlayer = new MediaPlayer();
				mRingerPlayer.setAudioStreamType(STREAM_RING);
				String ringtone = LinphonePreferences.instance().getRingtone(Settings.System.DEFAULT_RINGTONE_URI.toString());
				try {
					if (ringtone.startsWith("content://")) {
						mRingerPlayer.setDataSource(mServiceContext, Uri.parse(ringtone));
					} else {
						FileInputStream fis = new FileInputStream(ringtone);
						mRingerPlayer.setDataSource(fis.getFD());
						fis.close();
					}
				} catch (IOException e) {
					Log.e(e, "Cannot set ringtone");
				}
				mRingerPlayer.prepare();
				mRingerPlayer.setLooping(true);
				mRingerPlayer.start();
			} else {
				Log.w("already ringing");
			}
		} catch (Exception e) {
			Log.e(e, "cannot handle incoming call");
		}
		isRinging = true;
	}

	private synchronized void stopRinging() {
		if (mRingerPlayer != null) {
			mRingerPlayer.stop();
			mRingerPlayer.release();
			mRingerPlayer = null;
		}
		if (mVibrator != null) {
			mVibrator.cancel();
		}
		if (Hacks.needGalaxySAudioHack()) {
			mAudioManager.setMode(AudioManager.MODE_NORMAL);
		}
		isRinging = false;
		if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
			if (mServiceContext.getResources().getBoolean(R.bool.isTablet)) {
				Log.d("Stopped ringing, routing back to speaker");
				routeAudioToSpeaker();
			} else {
				Log.d("Stopped ringing, routing back to earpiece");
				routeAudioToReceiver();
			}
		}
	}

	// Регулировка громкости

	public void adjustVolume(int i) {
		if (Build.VERSION.SDK_INT < 15) {
			int oldVolume = mAudioManager.getStreamVolume(LINPHONE_VOLUME_STREAM);
			int maxVolume = mAudioManager.getStreamMaxVolume(LINPHONE_VOLUME_STREAM);
			int nextVolume = oldVolume + i;
			if (nextVolume > maxVolume) nextVolume = maxVolume;
			if (nextVolume < 0) nextVolume = 0;
			mLc.setPlaybackGain((nextVolume - maxVolume) * dbStep);
		} else {
			mAudioManager.adjustStreamVolume(LINPHONE_VOLUME_STREAM,
					i < 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE,
					AudioManager.FLAG_SHOW_UI);
		}
	}

	// Doze

	public void setDozeModeEnabled(boolean b) {
		dozeModeEnabled = b;
	}

	public void dozeManager(boolean enable) {
		if (enable) {
			Log.i("[Doze Mode]: register");
			mServiceContext.registerReceiver(mDozeReceiver, mDozeIntentFilter);
			dozeModeEnabled = true;
		} else {
			Log.i("[Doze Mode]: unregister");
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				try {
					if (mDozeReceiver != null) {
						mServiceContext.unregisterReceiver(mDozeReceiver);
					}
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				}
			}
			dozeModeEnabled = false;
		}
	}
	private void doDestroy() {
		BluetoothManager.destroy();
		try {
			mTimer.cancel();
			mLc.destroy();
		} catch (RuntimeException e) {
			Log.e(e);
		} finally {
			try {
				if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
					mServiceContext.unregisterReceiver(mNetworkReceiver);
				}
			} catch (Exception e) {
				Log.e(e);
			}
			try {
				mServiceContext.unregisterReceiver(mHookReceiver);
			} catch (Exception e) {
				Log.e(e);
			}
			try {
				mServiceContext.unregisterReceiver(mKeepAliveReceiver);
			} catch (Exception e) {
				Log.e(e);
			}
			try {
				mServiceContext.unregisterReceiver(mCallReceiver);
			} catch (Exception e) {
				Log.e(e);
			}
			try {
				dozeManager(false);
			} catch (Exception iae) {
				Log.e(iae);
			}
			mLc = null;
			instance = null;
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.timestamp == 0) return;
		if (isProximitySensorNearby(event)) {
			if (!mProximityWakeLock.isHeld()) {
				mProximityWakeLock.acquire();
			}
		} else {
			if (mProximityWakeLock.isHeld()) {
				mProximityWakeLock.release();
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	private static boolean isProximitySensorNearby(final SensorEvent event) {
		float threshold = 4.001f;
		final float distanceInCm = event.values[0];
		final float maxDistance = event.sensor.getMaximumRange();
		Log.d("Proximity sensor report [" + distanceInCm + "], for max range [" + maxDistance + "])");
		if (maxDistance <= threshold) {
			threshold = maxDistance;
		}
		return distanceInCm <= threshold;
	}

	public Context getContext() {
		try {
			if (MainActivity.isInstanciated())
				return MainActivity.instance();
			else if (CallActivity.isInstanciated())
				return CallActivity.instance();
			else if (CallIncomingActivity.isInstanciated())
				return CallIncomingActivity.instance();
			else if (mServiceContext != null)
				return mServiceContext;
			else if (LinphoneService.isReady())
				return LinphoneService.instance().getApplicationContext();
		} catch (Exception e) {
			Log.e(e);
		}
		return null;
	}

	public void setCallGsmON(boolean on) {
		callGsmON = on;
	}

	public boolean getCallGsmON() {
		return callGsmON;
	}

	public void setHandsetMode(Boolean on) {
		handsetON = on;
	}

	public boolean isHandsetModeOn() {
		return handsetON;
	}

	public void isAccountWithAlias() {
	}

	@Override
	public void displayStatus(LinphoneCore lc, String message) {
		Log.i(message);
	}

	@Override
	public void displayMessage(LinphoneCore lc, String message) {
		Log.i(message);
	}

	@Override
	public void displayWarning(LinphoneCore lc, String message) {
		Log.w(message);
	}

	@Override
	public void show(LinphoneCore lc) {
	}

	@Override
	public void globalState(LinphoneCore lc, GlobalState state, String message) {
		Log.i("New global state [" + state + "]");
		if (state == GlobalState.GlobalOn) {
			try {
				Log.e("LinphoneManager", " globalState ON");
				initLiblinphone(lc);
			} catch (IllegalArgumentException | LinphoneCoreException e) {
				Log.e(e);
			}
		}
	}

	@Override
	public void registrationState(LinphoneCore lc, LinphoneProxyConfig proxy, RegistrationState state, String message) {
		Log.i("New registration state [" + state + "]");
		if (LinphoneManager.getLc().getDefaultProxyConfig() == null) {
			subscribeFriendList(false);
		}
	}

	@Override
	public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf, String url) {
	}

	@Override
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
	}

	@Override
	public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {
		Log.d("DTMF received: " + dtmf);
	}

	@Override
	public void callStatsUpdated(LinphoneCore lc, LinphoneCall call, LinphoneCallStats stats) {
	}

	@Override
	public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call, boolean encrypted, String authenticationToken) {
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneCall call, LinphoneAddress from, byte[] event) {
	}

	@Override
	public void transferState(LinphoneCore lc, LinphoneCall call, State new_call_state) {
	}

	@Override
	public void infoReceived(LinphoneCore lc, LinphoneCall call, LinphoneInfoMessage info) {
		Log.d("Info message received from " + call.getRemoteAddress().asString());
	}

	@Override
	public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev, SubscriptionState state) {
		Log.d("Subscription state changed to " + state);
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneEvent ev, String eventName, LinphoneContent content) {
		Log.d("Notify received for event " + eventName);
	}

	@Override
	public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev, PublishState state) {
		Log.d("Publish state changed to " + state);
	}

	@Override
	public void configuringStatus(LinphoneCore lc, RemoteProvisioningState state, String message) {
		Log.d("Remote provisioning status = " + state.toString() + " (" + message + ")");
	}

	@Override
	public void fileTransferProgressIndication(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, int progress) {
	}

	@Override
	public void fileTransferRecv(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, byte[] buffer, int size) {
	}

	@Override
	public int fileTransferSend(LinphoneCore lc, LinphoneChatMessage message, LinphoneContent content, ByteBuffer buffer, int size) {
		return 0;
	}

	@Override
	public void uploadProgressIndication(LinphoneCore linphoneCore, int offset, int total) {
		if (total > 0)
			Log.d("Log upload progress: " + offset + "/" + total);
	}

	@Override
	public void uploadStateChanged(LinphoneCore linphoneCore, LogCollectionUploadState state, String info) {
		Log.d("Log upload state: " + state);
	}

	@Override
	public void ecCalibrationStatus(LinphoneCore lc, EcCalibratorStatus status, int delay_ms, Object data) {
		((AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);
		mAudioManager.abandonAudioFocus(null);
		Log.i("Set audio mode on 'Normal'");
	}

	@Override
	public void friendListCreated(LinphoneCore lc, LinphoneFriendList list) {
	}

	@Override
	public void friendListRemoved(LinphoneCore lc, LinphoneFriendList list) {
	}

	@Override
	public void networkReachableChanged(LinphoneCore lc, boolean enable) {
	}

	@Override
	public void authInfoRequested(LinphoneCore lc, String realm, String username, String domain) {
	}

	@Override
	public void authenticationRequested(LinphoneCore lc, LinphoneAuthInfo authInfo, AuthMethod method) {
	}
}