package org.linphone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AccountEnableReceiver extends BroadcastReceiver {
	private static final String TAG = "AccountEnableReceiver";
	private static final String FIELD_ID = "id";
	private static final String FIELD_ACTIVE = "active";

	@Override
	public void onReceive(Context context, Intent intent) {
		int prefsAccountIndex = (int)(long)intent.getLongExtra(FIELD_ID, -1);
		boolean enable = intent.getBooleanExtra(FIELD_ACTIVE, true);
		Log.i(TAG, "Received broadcast for index=" + Integer.toString(prefsAccountIndex) + ",enable=" + Boolean.toString(enable));
		if (prefsAccountIndex < 0 || prefsAccountIndex >= LinphonePreferences.instance().getAccountCount())
			return;
		LinphonePreferences.instance().setAccountEnabled(prefsAccountIndex, enable);
	}
}

