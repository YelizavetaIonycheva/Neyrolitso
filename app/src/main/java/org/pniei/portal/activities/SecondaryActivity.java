package org.pniei.portal.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import org.linphone.CallOutgoingActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.settingsfragments.SettingsFragment;
import org.pniei.portal.R;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.fragments.ChatFragment;
import org.pniei.portal.fragments.ContactChangeFragment;
import org.pniei.portal.fragments.ContactInfoFragment;
import org.pniei.portal.fragments.ContactSelectFragment;
import org.pniei.portal.fragments.ContactSyncFragment;
import org.pniei.portal.fragments.HistoryDetailFragment;
import org.pniei.portal.listener.OnBackClickListener;
import org.pniei.portal.services.MonitoringService;
import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.vpn.VpnClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public class SecondaryActivity extends AppCompatActivity  {
    // Ключи принимаемых параметров
    public static final String TYPE_FRAGMENT_KEY = "t_f_key";
    public static final String CHAT_ROOM_ID_KEY = "c_r_id_key";
    public static final String CONTACT_ID_KEY = "c_id_key";
    public static final String CONTACT_IS_NEW_KEY = "c_is_n_key";
    public static final String CONTACT_IS_PNONE_KEY = "c_is_p_key";
    public static final String HISTORY_NUMBER = "number";
    public static final String HISTORY_STATUS = "status";
    public static final String HISTORY_TIME = "time";
    public static final String HISTORY_DATE = "date";

    public static final int CHAT_FRAGMENT = 0;
    public static final int CONTACT_INFO_FRAGMENT = 1;
    public static final int CONTACT_CHANGE_FRAGMENT = 2;
    public static final int CONTACT_SELECT_FRAGMENT = 3;
    public static final int CONTACT_SYNC_FRAGMENT = 4;
    public static final int SETTINGS_FRAGMENT = 5;
    public static final int HISTORY_DETAIL_FRAGMENT = 6;

    private LinphoneCoreListenerBase mListener;
    private Intent mIntent;
    private Fragment currentFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Utils.initTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_secondary);

        mIntent = getIntent();
        if (mIntent != null && mIntent.hasExtra(TYPE_FRAGMENT_KEY)) {
            switch (mIntent.getIntExtra(TYPE_FRAGMENT_KEY, 0)) {
                case CHAT_FRAGMENT : {
                    currentFragment = ChatFragment.newInstance(this, mIntent.getLongExtra(CHAT_ROOM_ID_KEY, 0));
                    break;
                }
                case CONTACT_INFO_FRAGMENT : {
                    currentFragment = ContactInfoFragment.newInstance(this, mIntent.getLongExtra(CONTACT_ID_KEY, 0));
                    break;
                }
                case CONTACT_CHANGE_FRAGMENT : {
                    currentFragment = ContactChangeFragment.newInstance(this, mIntent.getBooleanExtra(CONTACT_IS_NEW_KEY, true), mIntent.getLongExtra(CONTACT_ID_KEY, 0));
                    break;
                }
                case CONTACT_SELECT_FRAGMENT : {
                    currentFragment = ContactSelectFragment.newInstance(this, mIntent.getBooleanExtra(CONTACT_IS_PNONE_KEY, true));
                    break;
                }
                case CONTACT_SYNC_FRAGMENT : {
                    currentFragment = ContactSyncFragment.newInstance(this);
                    break;
                }
                case SETTINGS_FRAGMENT : {
                    currentFragment = SettingsFragment.newInstance(this);
                    break;
                }
                case HISTORY_DETAIL_FRAGMENT : {
                    currentFragment = HistoryDetailFragment.newInstance(mIntent.getStringExtra(HISTORY_NUMBER),
                            mIntent.getStringExtra(HISTORY_STATUS),
                            mIntent.getStringExtra(HISTORY_TIME),
                            mIntent.getStringExtra(HISTORY_DATE));
                    break;
                }
            }
            displayFragment(currentFragment, false);
        } else {
            throw new RuntimeException("Activity intent not have Extra");
        }

        mListener = new LinphoneCoreListenerBase(){
            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                /*  if (state == LinphoneCall.State.IncomingReceived) {
                    startActivity(new Intent(MainActivity.instance(), CallIncomingActivity.class));
                } else*/ if (state == LinphoneCall.State.OutgoingInit || state == LinphoneCall.State.OutgoingProgress) {
                    startActivity(new Intent(MainActivity.instance(), CallOutgoingActivity.class));
                } else if (state == LinphoneCall.State.CallEnd || state == LinphoneCall.State.Error || state == LinphoneCall.State.CallReleased) {
                    //resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
                }
            }
        };

    }

    public void displayFragment(Fragment fragment, boolean toBackStack) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        if (fragment instanceof ChatFragment) {
            while(fm.getBackStackEntryCount() > 0) {
                fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
        } else {
            if (toBackStack)
                transaction.addToBackStack(null);
        }

        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commitAllowingStateLoss();
    }

    @Override
    public void onBackPressed() {
        if (currentFragment instanceof OnBackClickListener) {
            if (((OnBackClickListener)currentFragment).allowBackPressed()) {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    public void quit(boolean isSaveDB) {
        stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));

        if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P) {
            VpnClient.stopVpnService(this);
            MonitoringService.stopMonitoringService(this);
        }

        SpoMessagesService.stop(this);
        if (isSaveDB) {
            DBUtils.closeDB();
        }
        PrefsUtils.ins().setAuth(false);
        finishAffinity();
    }

    @Override
    protected void onPause() {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }

        PrefsUtils.ins().setAppBackground(true);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!LinphoneService.isReady()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
                startForegroundService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
            } else {
                startService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
            }
        }

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
            if (!LinphoneService.instance().displayServiceNotification()) {
                lc.refreshRegisters();
            }
        }

        PrefsUtils.ins().setAppBackground(false);
        SpoMessagesService.instance().removeMessageNotification();
    }

}
