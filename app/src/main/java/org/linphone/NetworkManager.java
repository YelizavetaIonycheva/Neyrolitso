package org.linphone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.vpn.VpnClient;

/**
 *
 * Intercept network state changes and update linphone core through LinphoneManager.
 *
 */
public class NetworkManager extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		Boolean lNoConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY,false);
		if (LinphoneManager.isInstanciated()) {
			LinphoneManager.getInstance().connectivityChanged(cm, lNoConnectivity);
		}
		if (lNoConnectivity) {
			VpnClient.stopVpnService(context);
			SpoMessagesService.stop(context);
		} else {
			if (PrefsUtils.ins().isVpnEnable() && PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P) {
				VpnClient.startVpnServiceWithContext(context);
			}
			SpoMessagesService.start(context,
					PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P ? PrefsUtils.ins().getIdP() : PrefsUtils.ins().getIdTT(),
					PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P ? PrefsUtils.ins().getSignatureP() : PrefsUtils.ins().getSignatureTT());

		}
	}

}
