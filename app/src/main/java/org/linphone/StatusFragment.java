package org.linphone;

import android.app.Activity;
import android.content.Context;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneProxyConfig;
import org.pniei.portal.R;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.databinding.StatusBinding;
import org.pniei.portal.liveData.ManagerLiveData;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.vpn.VpnClient;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import static org.pniei.portal.liveData.ManagerLiveData.GpsEvent.LOCATION_FIXED;
import static org.pniei.portal.liveData.ManagerLiveData.GpsEvent.LOCATION_NOT_FIXED;
import static org.pniei.portal.liveData.ManagerLiveData.GpsEvent.NOT_USE;
import static org.pniei.portal.liveData.ManagerLiveData.GpsEvent.PROVIDER_DISABLE;
import static org.pniei.portal.liveData.ManagerLiveData.GpsEvent.PROVIDER_ENABLE;
import static org.pniei.portal.liveData.ManagerLiveData.VpnQuality.QUALITY_HIGH;
import static org.pniei.portal.liveData.ManagerLiveData.VpnQuality.QUALITY_LOW;
import static org.pniei.portal.liveData.ManagerLiveData.VpnQuality.QUALITY_MIDL;
import static org.pniei.portal.liveData.ManagerLiveData.VpnQuality.VPN_DISABLE;
import static org.pniei.portal.liveData.ManagerLiveData.VpnQuality.VPN_ENABLE;

public class StatusFragment extends Fragment {
    private StatusBinding mBinding;
    private Handler refreshHandler = new Handler();
    private Runnable mCallQualityUpdater;
    private boolean isInCall, isAttached = false;
    private LinphoneCoreListenerBase mListener;
    private int mDisplayedQuality = -1;
    private Integer vpnQuality = 0;
    private final int GPS_STATUS_NOT_USE = -1;
    private final int GPS_STATUS_OFF = 0;
    private final int GPS_STATUS_NOT_FIXED = 1;
    private final int GPS_STATUS_FIXED = 2;
    private int gpsStatus;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.status, container, false);

        mListener = new LinphoneCoreListenerBase(){
            @Override
            public void registrationState(final LinphoneCore lc, final LinphoneProxyConfig proxy, final LinphoneCore.RegistrationState state, String smessage) {
                if (!isAttached || !LinphoneService.isReady()) {
                    return;
                }

                if(lc.getProxyConfigList() == null){
                    mBinding.statusLed.setImageResource(R.drawable.led_disconnected);
                    LinphoneCore.RegistrationState.flagRegistrationOk = false;
                    mBinding.statusText.setText(getString(R.string.no_account));
                } else {
                    mBinding.statusLed.setVisibility(View.VISIBLE);
                }

                if (lc.getDefaultProxyConfig() != null && lc.getDefaultProxyConfig().equals(proxy)) {
                    mBinding.statusLed.setImageResource(getStatusIconResource(state, true));
                    mBinding.statusText.setText(getStatusIconText(state));
                } else if(lc.getDefaultProxyConfig() == null) {
                    mBinding.statusLed.setImageResource(getStatusIconResource(state, true));
                    mBinding.statusText.setText(getStatusIconText(state));
                }

                try {
                    mBinding.statusText.setOnClickListener(v -> lc.refreshRegisters());
                } catch (IllegalStateException ise) {}
            }

            @Override
            public void notifyReceived(LinphoneCore lc, LinphoneEvent ev, String eventName, LinphoneContent content) {
                if(!content.getType().equals("application")) return;
                if(!content.getSubtype().equals("simple-message-summary")) return;

                if (content.getData() == null) return;

                int unreadCount = -1;
                String data = content.getDataAsString();
                String[] voiceMail = data.split("Voice-Message: ");
                final String[] intToParse = voiceMail[1].split("/",0);

                unreadCount = Integer.parseInt(intToParse[0]);
                if (unreadCount > 0) {
                    if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P) {
                        mBinding.voicemailCount.setText("" + unreadCount);
                        mBinding.voicemail.setVisibility(View.VISIBLE);
                        mBinding.voicemailCount.setVisibility(View.VISIBLE);
                    }
                } else {
                    mBinding.voicemail.setVisibility(View.GONE);
                    mBinding.voicemailCount.setVisibility(View.GONE);
                }
            }

        };

        isAttached = true;
        Activity activity = getActivity();

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).updateStatusFragment(this);
            isInCall = false;
        } else if (activity instanceof CallActivity) {
            ((CallActivity) activity).updateStatusFragment(this);
            isInCall = true;
        }

        if (PrefsUtils.ins().isSendGPS()) {
            LocationManager locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
            boolean gpsEnable = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            updateGpsStatusIcon(gpsEnable ? GPS_STATUS_NOT_FIXED : GPS_STATUS_OFF);
        } else {
            updateGpsStatusIcon(GPS_STATUS_NOT_USE);
        }

        return mBinding.getRoot();
    }


    @Override
    public void onDetach() {
        super.onDetach();
        isAttached = false;
    }

    public void resetAccountStatus(){
        if(LinphoneManager.getLc().getProxyConfigList().length == 0){
            LinphoneCore.RegistrationState.flagRegistrationOk = false;
            mBinding.statusLed.setImageResource(R.drawable.led_disconnected);
            mBinding.statusText.setText(getString(R.string.no_account));
        }
    }

    public void enableSideMenu(boolean enabled) {
        mBinding.btnMenu.setEnabled(enabled);
    }

    private int getStatusIconResource(LinphoneCore.RegistrationState state, boolean isDefaultAccount) {
        try {
            LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            boolean defaultAccountConnected = (isDefaultAccount && lc != null && lc.getDefaultProxyConfig() != null && lc.getDefaultProxyConfig().isRegistered()) || !isDefaultAccount;
            if (state == RegistrationState.RegistrationOk && defaultAccountConnected) {
                LinphoneCore.RegistrationState.flagRegistrationOk = true;
                return R.drawable.led_connected;
            } else if (state == RegistrationState.RegistrationProgress) {
                LinphoneCore.RegistrationState.flagRegistrationOk = false;
                return R.drawable.led_inprogress;
            } else if (state == RegistrationState.RegistrationFailed) {
                LinphoneCore.RegistrationState.flagRegistrationOk = false;
                return R.drawable.led_error;
            } else {
                LinphoneCore.RegistrationState.flagRegistrationOk = false;
                return R.drawable.led_disconnected;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        LinphoneCore.RegistrationState.flagRegistrationOk = false;
        return R.drawable.led_disconnected;
    }

    public String getStatusIconText(LinphoneCore.RegistrationState state) {
        Context context = getContext();
        if (!isAttached && MainActivity.isInstanciated())
            context = MainActivity.instance();
        else if (!isAttached && LinphoneService.isReady())
            context = LinphoneService.instance();

        try {
            if (state == RegistrationState.RegistrationOk && LinphoneManager.getLcIfManagerNotDestroyedOrNull().getDefaultProxyConfig().isRegistered()) {
                return context.getString(R.string.status_connected);
            } else if (state == RegistrationState.RegistrationProgress) {
                return context.getString(R.string.status_in_progress);
            } else if (state == RegistrationState.RegistrationFailed) {
                return context.getString(R.string.status_error);
            } else {
                return context.getString(R.string.status_not_connected);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return context.getString(R.string.status_not_connected);
    }


    //INCALL STATUS BAR
    private void startCallQuality() {
        mBinding.callQuality.setVisibility(View.VISIBLE);
        refreshHandler.postDelayed(mCallQualityUpdater = new Runnable() {
            LinphoneCall mCurrentCall = LinphoneManager.getLc()
                    .getCurrentCall();

            public void run() {
                if (mCurrentCall == null) {
                    mCallQualityUpdater = null;
                    return;
                }
                float newQuality = mCurrentCall.getCurrentQuality();
                updateQualityOfSignalIcon(newQuality);

                if (isInCall) {
                    refreshHandler.postDelayed(this, 1000);
                } else
                    mCallQualityUpdater = null;
            }
        }, 1000);
    }

    private void updateGpsStatusIcon(int event) {
        int gpsStatus = GPS_STATUS_NOT_USE;
        switch (event) {
            case NOT_USE :
                gpsStatus = GPS_STATUS_NOT_USE;
                break;
            case PROVIDER_DISABLE :
                gpsStatus = GPS_STATUS_OFF;
                break;

            case PROVIDER_ENABLE:
                if (gpsStatus != GPS_STATUS_FIXED)
                    gpsStatus = GPS_STATUS_NOT_FIXED;
                break;

            case LOCATION_NOT_FIXED:
                gpsStatus = GPS_STATUS_NOT_FIXED;
                break;

            case LOCATION_FIXED:
                gpsStatus = GPS_STATUS_FIXED;
                break;
        }

        switch(gpsStatus) {
            case GPS_STATUS_NOT_USE     : mBinding.gpsStatus.setVisibility(View.GONE); break;
            case GPS_STATUS_OFF         : mBinding.gpsStatus.setVisibility(View.VISIBLE); mBinding.gpsStatus.setImageResource(R.drawable.ic_gps_off); break;
            case GPS_STATUS_NOT_FIXED   : mBinding.gpsStatus.setVisibility(View.VISIBLE); mBinding.gpsStatus.setImageResource(R.drawable.ic_gps_not_fixed); break;
            case GPS_STATUS_FIXED       : mBinding.gpsStatus.setVisibility(View.VISIBLE); mBinding.gpsStatus.setImageResource(R.drawable.ic_gps_fixed); break;
        }
    }

    void updateQualityOfSignalIcon(float quality) {
        int iQuality = (int) quality;

        if (iQuality == mDisplayedQuality) return;
        if (quality >= 4) // Good Quality
        {
            mBinding.callQuality.setImageResource(
                    R.drawable.call_quality_indicator_4);
        } else if (quality >= 3) // Average quality
        {
            mBinding.callQuality.setImageResource(
                    R.drawable.call_quality_indicator_3);
        } else if (quality >= 2) // Low quality
        {
            mBinding.callQuality.setImageResource(
                    R.drawable.call_quality_indicator_2);
        } else if (quality >= 1) // Very low quality
        {
            mBinding.callQuality.setImageResource(
                    R.drawable.call_quality_indicator_1);
        } else // Worst quality
        {
            mBinding.callQuality.setImageResource(
                    R.drawable.call_quality_indicator_0);
        }
        mDisplayedQuality = iQuality;
    }

    @Override
    public void onResume() {
        super.onResume();

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
            LinphoneProxyConfig lpc = lc.getDefaultProxyConfig();
            if (lpc != null) {
                mListener.registrationState(lc, lpc, lpc.getState(), null);
            }

            LinphoneCall call = lc.getCurrentCall();
            if (isInCall && (call != null || lc.getConferenceSize() > 1 || lc.getCallsNb() > 0)) {
                if (call != null) {
                    startCallQuality();
                    //refreshStatusItems(call, call.getCurrentParams().getVideoEnabled());
                }
                mBinding.btnMenu.setVisibility(View.INVISIBLE);
                mBinding.callQuality.setVisibility(View.VISIBLE);

                // We are obviously connected
                if(lc.getDefaultProxyConfig() == null){
                    mBinding.statusLed.setImageResource(R.drawable.led_disconnected);
                    LinphoneCore.RegistrationState.flagRegistrationOk = false;
                    mBinding.statusText.setText(getString(R.string.no_account));
                } else {
                    mBinding.statusLed.setImageResource(getStatusIconResource(lc.getDefaultProxyConfig().getState(),true));
                    mBinding.statusText.setText(getStatusIconText(lc.getDefaultProxyConfig().getState()));
                }
            }
        } else {
            mBinding.statusText.setVisibility(View.VISIBLE);
        }

        if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P) {
            if (VpnClient.ins().isConnected()) {
                //mBinding.speed.setVisibility(View.VISIBLE);
                updateVpnQualityIcon(vpnQuality);
            }
            ManagerLiveData.ins().getVpnQuality().observe(getViewLifecycleOwner(), integer -> updateVpnQualityIcon(integer));
            //ManagerLiveData.ins().getVpnInfo().observe(getViewLifecycleOwner(), vpnInfo -> updateVpnInfo(vpnInfo));
        } else {
            //mBinding.speed.setVisibility(View.GONE);
            mBinding.vpnStatus.setVisibility(View.INVISIBLE);
        }

        ManagerLiveData.ins().getGpsEvent().observe(getViewLifecycleOwner(), integer -> updateGpsStatusIcon(integer));
    }

    @Override
    public void onPause() {
        super.onPause();

        /*LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }*/

        if (mCallQualityUpdater != null) {
            refreshHandler.removeCallbacks(mCallQualityUpdater);
            mCallQualityUpdater = null;
        }
        if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P) {
            ManagerLiveData.ins().getVpnQuality().removeObservers(getViewLifecycleOwner());
            ManagerLiveData.ins().getVpnInfo().removeObservers(getViewLifecycleOwner());
        }

        ManagerLiveData.ins().getGpsEvent().removeObservers(getViewLifecycleOwner());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
    }

    private void updateVpnQualityIcon(int quality) {
        vpnQuality = quality;
        switch (quality) {
			case VPN_DISABLE 	:
				mBinding.vpnStatus.setVisibility(View.INVISIBLE);
                //mBinding.speed.setVisibility(View.INVISIBLE);
				break;
            case VPN_ENABLE 	:
                mBinding.vpnStatus.setVisibility(View.VISIBLE);
                //mBinding.speed.setVisibility(View.VISIBLE);
                mBinding.vpnStatus.setImageResource(R.drawable.ic_vpn_quality_off);
                break;
            case QUALITY_HIGH 	:
                mBinding.vpnStatus.setVisibility(View.VISIBLE);
                //mBinding.speed.setVisibility(View.VISIBLE);
                mBinding.vpnStatus.setImageResource(R.drawable.ic_vpn_quality_high);
                break;
            case QUALITY_MIDL 	:
                mBinding.vpnStatus.setVisibility(View.VISIBLE);
                //mBinding.speed.setVisibility(View.VISIBLE);
                mBinding.vpnStatus.setImageResource(R.drawable.ic_vpn_quality_midle);
                break;
            case QUALITY_LOW 	:
                mBinding.vpnStatus.setVisibility(View.VISIBLE);
                //mBinding.speed.setVisibility(View.VISIBLE);
                mBinding.vpnStatus.setImageResource(R.drawable.ic_vpn_quality_low);
                break;
        }
    }

    /*private void updateVpnInfo(@NotNull ManagerLiveData.VpnInfo vpnInfo) {
        mBinding.speedIn.setText(String.format("%.2f kB/s", vpnInfo.getSpeedIN()));
        mBinding.speedOut.setText(String.format("%.2f kB/s", vpnInfo.getSpeedOUT()));
    }*/

}
