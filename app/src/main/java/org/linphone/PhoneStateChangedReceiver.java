package org.linphone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

public class PhoneStateChangedReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		final String extraState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

		if (!LinphoneManager.isInstanciated())
			return;

		if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(extraState) || TelephonyManager.EXTRA_STATE_RINGING.equals(extraState)) {
			LinphoneManager.getInstance().setCallGsmON(true);
			LinphoneManager.getLc().pauseAllCalls();
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(extraState)) {
			LinphoneManager.getInstance().setCallGsmON(false);
        }
	}
}
