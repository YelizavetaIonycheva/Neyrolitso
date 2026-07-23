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
<<<<<<< HEAD
            SpoMessagesService.start(context, PrefsUtils.ins().getIdP(), PrefsUtils.ins().getSignatureP());
=======
            SpoMessagesService.start(context,
                    PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P ? PrefsUtils.ins().getIdP() : PrefsUtils.ins().getIdTT(),
                    PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P ? PrefsUtils.ins().getSignatureP() : PrefsUtils.ins().getSignatureTT());
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
        }
    }
}