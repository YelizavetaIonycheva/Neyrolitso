package org.linphone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.View;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.Hacks;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.pniei.portal.R;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Helpers.
 */
public final class LinphoneUtils {

	private LinphoneUtils(){}

	public static boolean onKeyBackGoHome(Activity activity, int keyCode, KeyEvent event) {
		if (!(keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0)) {
			return false; // continue
		}

		return true;
	}

	public static boolean onKeyVolumeAdjust(int keyCode) {
		if (!((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
				&& (Hacks.needSoftvolume())|| Build.VERSION.SDK_INT >= 15)) {
			return false; // continue
		}

		if (!LinphoneService.isReady()) {
			Log.i("Couldn't change softvolume has service is not running");
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			LinphoneManager.getInstance().adjustVolume(1);
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
			LinphoneManager.getInstance().adjustVolume(-1);
		}
		return true;
	}

	public static final List<LinphoneCall> getLinphoneCalls(LinphoneCore lc) {
		return new ArrayList<LinphoneCall>(Arrays.asList(lc.getCalls()));
	}

	public static final List<LinphoneCall> getCallsInState(LinphoneCore lc, Collection<State> states) {
		List<LinphoneCall> foundCalls = new ArrayList<LinphoneCall>();
		for (LinphoneCall call : getLinphoneCalls(lc)) {
			if (states.contains(call.getState())) {
				foundCalls.add(call);
			}
		}
		return foundCalls;
	}

	public static void setVisibility(View v, int id, boolean visible) {
		v.findViewById(id).setVisibility(visible ? VISIBLE : GONE);
	}
	public static void setVisibility(View v, boolean visible) {
		v.setVisibility(visible ? VISIBLE : GONE);
	}

	public static boolean isCallRunning(LinphoneCall call) {
		if (call == null) {
			return false;
		}

		LinphoneCall.State state = call.getState();

		return state == LinphoneCall.State.Connected ||
				state == LinphoneCall.State.CallUpdating ||
				state == LinphoneCall.State.CallUpdatedByRemote ||
				state == LinphoneCall.State.StreamsRunning ||
				state == LinphoneCall.State.Resuming;
	}

	public static boolean isCallEstablished(LinphoneCall call) {
		if (call == null) {
			return false;
		}

		LinphoneCall.State state = call.getState();

		return isCallRunning(call) ||
				state == LinphoneCall.State.Paused ||
				state == LinphoneCall.State.PausedByRemote ||
				state == LinphoneCall.State.Pausing;
	}

	public static boolean isHighBandwidthConnection(Context context){
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null && info.isConnected() && isConnectionFast(info.getType(),info.getSubtype()));
    }

	private static boolean isConnectionFast(int type, int subType){
		if (type == ConnectivityManager.TYPE_MOBILE) {
            switch (subType) {
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_IDEN:
            	return false;
            }
		}
        //in doubt, assume connection is good.
        return true;
    }

	public static void recursiveFileRemoval(File root) {
		if (!root.delete()) {
			if (root.isDirectory()) {
				File[] files = root.listFiles();
		        if (files != null) {
		            for (File f : files) {
		            	recursiveFileRemoval(f);
		            }
		        }
			}
		}
	}

	public static String getDisplayableUsernameFromAddress(String sipAddress) {
		if (sipAddress == null)
			return null;

		String username = sipAddress;
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc == null) return username;

		if (username.startsWith("sip:")) {
			username = username.substring(4);
		}

		if (username.contains("@")) {
			String domain = username.split("@")[1];
			LinphoneProxyConfig lpc = lc.getDefaultProxyConfig();
			if (lpc != null) {
				if (domain.equals(lpc.getDomain())) {
					return username.split("@")[0];
				}
			} else {
				if (domain.equals(LinphoneManager.getInstance().getContext().getString(R.string.default_domain))) {
					return username.split("@")[0];
				}
			}
		}
		return username;
	}

	public static void storeImage(Context context, LinphoneChatMessage msg) {
		if (msg == null || msg.getFileTransferInformation() == null || msg.getAppData() == null) return;
		File file = new File(Environment.getExternalStorageDirectory(), msg.getAppData());
		Bitmap bm = BitmapFactory.decodeFile(file.getPath());
		if (bm == null) return;

		ContentValues values = new ContentValues();
        values.put(Images.Media.TITLE, file.getName());
        String extension = msg.getFileTransferInformation().getSubtype();
        values.put(Images.Media.MIME_TYPE, "image/" + extension);
        ContentResolver cr = context.getContentResolver();
        Uri path = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        OutputStream stream;
		try {
			stream = cr.openOutputStream(path);
			if (extension != null && extension.toLowerCase(Locale.getDefault()).equals("png")) {
				bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
			} else {
				bm.compress(Bitmap.CompressFormat.JPEG, 100, stream);
			}

			stream.close();
			file.delete();
	        bm.recycle();

	        msg.setAppData(path.toString());
		} catch (FileNotFoundException e) {
			Log.e(e);
		} catch (IOException e) {
			Log.e(e);
		}
	}

	public static void displayErrorAlert(String msg, Context ctxt) {
		if (ctxt != null && msg != null) {
			AlertDialog.Builder builder = new AlertDialog.Builder(ctxt);
			builder.setMessage(msg)
					.setCancelable(false)
					.setNeutralButton(ctxt.getString(R.string.ok), null)
					.show();
		}
	}


	/************************************************************************************************
	 *							Picasa/Photos management workaround									*
	 ************************************************************************************************/

	public static String getTimestamp() {
		try {
			return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
		} catch (RuntimeException e) {
			return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
		}
	}

}

