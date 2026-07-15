package org.linphone;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

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
import org.pniei.portal.utils.PrefsUtils;

public class StatusFragment extends Fragment {
    private StatusBinding mBinding;
    private final Handler refreshHandler = new Handler();
    private Runnable mCallQualityUpdater;
    private boolean isInCall, isAttached = false;
    private LinphoneCoreListenerBase mListener;
    private int mDisplayedQuality = -1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
                    mBinding.statusLed.setImageResource(getStatusIconResource(state));
                    mBinding.statusText.setText(getStatusIconText(state));
                } else if(lc.getDefaultProxyConfig() == null) {
                    mBinding.statusLed.setImageResource(getStatusIconResource(state));
                    mBinding.statusText.setText(getStatusIconText(state));
                }

                try {
                    mBinding.statusText.setOnClickListener(v -> lc.refreshRegisters());
                } catch (IllegalStateException ignored) {}
            }

            @SuppressLint("SetTextI18n")
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

        // GPS удален
        mBinding.gpsStatus.setVisibility(View.GONE);

        // VPN удален
        mBinding.vpnStatus.setVisibility(View.GONE);

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

    private int getStatusIconResource(RegistrationState state) {
        try {
            LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            boolean defaultAccountConnected = (lc != null && lc.getDefaultProxyConfig() != null && lc.getDefaultProxyConfig().isRegistered());
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
                assert context != null;
                return context.getString(R.string.status_connected);
            } else if (state == RegistrationState.RegistrationProgress) {
                assert context != null;
                return context.getString(R.string.status_in_progress);
            } else if (state == RegistrationState.RegistrationFailed) {
                assert context != null;
                return context.getString(R.string.status_error);
            } else {
                assert context != null;
                return context.getString(R.string.status_not_connected);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        assert context != null;
        return context.getString(R.string.status_not_connected);
    }

    //INCALL STATUS BAR
    private void startCallQuality() {
        mBinding.callQuality.setVisibility(View.VISIBLE);
        refreshHandler.postDelayed(mCallQualityUpdater = new Runnable() {
            final LinphoneCall mCurrentCall = LinphoneManager.getLc().getCurrentCall();

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

    void updateQualityOfSignalIcon(float quality) {
        int iQuality = (int) quality;

        if (iQuality == mDisplayedQuality) return;
        if (quality >= 4) // Good Quality
        {
            mBinding.callQuality.setImageResource(R.drawable.call_quality_indicator_4);
        } else if (quality >= 3) // Average quality
        {
            mBinding.callQuality.setImageResource(R.drawable.call_quality_indicator_3);
        } else if (quality >= 2) // Low quality
        {
            mBinding.callQuality.setImageResource(R.drawable.call_quality_indicator_2);
        } else if (quality >= 1) // Very low quality
        {
            mBinding.callQuality.setImageResource(R.drawable.call_quality_indicator_1);
        } else // Worst quality
        {
            mBinding.callQuality.setImageResource(R.drawable.call_quality_indicator_0);
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
                }
                mBinding.btnMenu.setVisibility(View.INVISIBLE);
                mBinding.callQuality.setVisibility(View.VISIBLE);

                if(lc.getDefaultProxyConfig() == null){
                    mBinding.statusLed.setImageResource(R.drawable.led_disconnected);
                    LinphoneCore.RegistrationState.flagRegistrationOk = false;
                    mBinding.statusText.setText(getString(R.string.no_account));
                } else {
                    mBinding.statusLed.setImageResource(getStatusIconResource(lc.getDefaultProxyConfig().getState()));
                    mBinding.statusText.setText(getStatusIconText(lc.getDefaultProxyConfig().getState()));
                }
            }
        } else {
            mBinding.statusText.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mCallQualityUpdater != null) {
            refreshHandler.removeCallbacks(mCallQualityUpdater);
            mCallQualityUpdater = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
    }
}