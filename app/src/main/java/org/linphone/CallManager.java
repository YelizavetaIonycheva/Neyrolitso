package org.linphone;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.mediastream.Log;

/**
 * Управление вызовами (только аудио).
 * Все методы, связанные с видео, удалены.
 */
public class CallManager {
	private static CallManager instance;

	private CallManager() {}

	public static final synchronized CallManager getInstance() {
		if (instance == null) {
			instance = new CallManager();
		}
		return instance;
	}

	public void inviteAddress(LinphoneAddress lAddress, boolean videoEnabled, boolean lowBandwidth) throws LinphoneCoreException {
		LinphoneCore lc = LinphoneManager.getLc();
		LinphoneCallParams params = lc.createCallParams(null);

		// Видео всегда отключено
		params.setVideoEnabled(false);

		if (lowBandwidth) {
			params.enableLowBandwidth(true);
			Log.d("Low bandwidth enabled in call params");
		}

		lc.inviteAddressWithParams(lAddress, params);
	}
	public void updateCall() {
		LinphoneCore lc = LinphoneManager.getLc();
		LinphoneCall lCall = lc.getCurrentCall();
		if (lCall == null) {
			Log.e("Trying to updateCall while not in call: doing nothing");
			return;
		}
		LinphoneCallParams params = lc.createCallParams(lCall);
		params.setVideoEnabled(false);
		lc.updateCall(lCall, params);
	}
}