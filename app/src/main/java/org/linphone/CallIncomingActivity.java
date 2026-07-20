package org.linphone;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;
import org.pniei.portal.R;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.databinding.CallIncomingBinding;
import org.pniei.portal.database.SpoContact;

import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Toast;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.mediastream.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CallIncomingActivity extends AppCompatActivity {
	private static CallIncomingActivity instance;
	private CallIncomingBinding mBinding;
	private LinphoneCall mCall;
	private LinphoneCoreListenerBase mListener;
	private boolean alreadyAcceptedOrDeniedCall;

	public static CallIncomingActivity instance() {
		return instance;
	}

	public static boolean isInstanciated() {
		return instance != null;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Utils.initTheme(this);
		super.onCreate(savedInstanceState);

		if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		//setContentView(R.layout.call_incoming);
		mBinding = DataBindingUtil.setContentView(this, R.layout.call_incoming);

		// set this flag so this activity will stay in front of the keyguard
		int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
		getWindow().addFlags(flags);

		lookupCurrentCall();

		mBinding.accept.setOnClickListener(v -> answer());
		mBinding.decline.setOnClickListener(v -> decline());

		mListener = new LinphoneCoreListenerBase(){
			@Override
			public void callState(LinphoneCore lc, LinphoneCall call, State state, String message) {
				if (call == mCall && State.CallEnd == state) {
					finish();
				}
				if (state == State.StreamsRunning) {
					Log.e("CallIncommingActivity - onCreate -  State.StreamsRunning - speaker = "+LinphoneManager.getLc().isSpeakerEnabled());
					LinphoneManager.getLc().enableSpeaker(LinphoneManager.getLc().isSpeakerEnabled());
				}
			}
		};

		instance = this;
	}

	@Override
	protected void onResume() {
		super.onResume();
		instance = this;
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}

		alreadyAcceptedOrDeniedCall = false;
		mCall = null;

		lookupCurrentCall();
		if (mCall == null) {
			Log.d("Couldn't find incoming call");
			finish();
			return;
		}

		LinphoneAddress address = mCall.getRemoteAddress();
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
	}

	@Override
	protected void onStart() {
		super.onStart();
		checkAndRequestCallPermissions();
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
		super.onDestroy();
		instance = null;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneManager.isInstanciated() && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)) {
			LinphoneManager.getLc().terminateCall(mCall);
			finish();
		}
		return super.onKeyDown(keyCode, event);
	}

	private void lookupCurrentCall() {
		if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
			List<LinphoneCall> calls = LinphoneUtils.getLinphoneCalls(LinphoneManager.getLc());
			for (LinphoneCall call : calls) {
				if (State.IncomingReceived == call.getState()) {
					mCall = call;
					break;
				}
			}
		}
	}

	private void decline() {
		if (alreadyAcceptedOrDeniedCall) {
			return;
		}
		alreadyAcceptedOrDeniedCall = true;

		LinphoneManager.getLc().terminateCall(mCall);
		finish();
	}

	private void answer() {
		if (alreadyAcceptedOrDeniedCall) {
			return;
		}
		alreadyAcceptedOrDeniedCall = true;

		LinphoneCallParams params = LinphoneManager.getLc().createCallParams(mCall);

		boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(LinphoneService.instance().getApplicationContext());

		if (params != null) {
			params.enableLowBandwidth(isLowBandwidthConnection);
		}else {
			Log.e("Could not create call params for call");
		}

		if (params == null || !LinphoneManager.getInstance().acceptCallWithParams(mCall, params)) {
			// the above method takes care of Samsung Galaxy S
			Toast.makeText(this, R.string.couldnt_accept_call, Toast.LENGTH_LONG).show();
		} else {
			if (!MainActivity.isInstanciated()) {
				return;
			}
			LinphoneManager.getInstance().routeAudioToReceiver();

			if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
				LinphoneAddress address = mCall.getRemoteAddress();
				SpoContact contact = DBUtils.getContactForNumber(address.getUserName());
				if (contact != null)
					CryptUtils.initCryptSound(Integer.parseInt(PrefsUtils.ins().getIdTT()), Integer.parseInt(contact.getIdUser()), true);
				else
					CryptUtils.initCryptSound(0, 0, false);
			} else {
				CryptUtils.initCryptSound(0, 0, false);
			}
			MainActivity.instance().startIncallActivity(mCall);
		}
	}

	private void checkAndRequestCallPermissions() {
		ArrayList<String> permissionsList = new ArrayList<String>();

		int recordAudio = getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
		Log.i("[Permission] Record audio permission is " + (recordAudio == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
		int camera = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
		Log.i("[Permission] Camera permission is " + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

		if (recordAudio != PackageManager.PERMISSION_GRANTED) {
			if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.RECORD_AUDIO) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
				Log.i("[Permission] Asking for record audio");
				permissionsList.add(Manifest.permission.RECORD_AUDIO);
			}
		}
		if (LinphonePreferences.instance().shouldInitiateVideoCall() || LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests()) {
			if (camera != PackageManager.PERMISSION_GRANTED) {
				if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.CAMERA) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
					Log.i("[Permission] Asking for camera");
					permissionsList.add(Manifest.permission.CAMERA);
				}
			}
		}

		if (permissionsList.size() > 0) {
			String[] permissions = new String[permissionsList.size()];
			permissions = permissionsList.toArray(permissions);
			ActivityCompat.requestPermissions(this, permissions, 0);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		for (int i = 0; i < permissions.length; i++) {
			Log.i("[Permission] " + permissions[i] + " is " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
		}
	}
}