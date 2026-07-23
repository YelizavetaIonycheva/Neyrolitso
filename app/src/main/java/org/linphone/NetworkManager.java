package org.linphone;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.PrefsUtils;


public class NetworkManager extends BroadcastReceiver {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
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
            SpoMessagesService.start(context,
                    PrefsUtils.ins().getIdP(),
                    PrefsUtils.ins().getSignatureP());
        }
    }

}