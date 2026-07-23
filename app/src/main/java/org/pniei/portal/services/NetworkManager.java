package org.pniei.portal.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import org.linphone.LinphoneManager;
import org.pniei.portal.utils.PrefsUtils;

public class NetworkManager extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean lNoConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
        if (LinphoneManager.isInstanciated()) {
            LinphoneManager.getInstance().connectivityChanged(cm, lNoConnectivity);
        }
        if (lNoConnectivity) {
            SpoMessagesService.stop(context);
        } else {
            SpoMessagesService.start(context, PrefsUtils.ins().getIdP(), PrefsUtils.ins().getSignatureP());
        }
    }
}