package org.linphone;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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

public class StatusFragment extends Fragment {
    private StatusBinding mBinding;
    private Handler refreshHandler = new Handler();
    private Runnable mCallQualityUpdater;
    private boolean isInCall, isAttached = false;
    private LinphoneCoreListenerBase mListener;
    private int mDisplayedQuality = -1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.status, container, false);

        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void registrationState(final LinphoneCore lc, final LinphoneProxyConfig proxy, final RegistrationState state, String message) {
                if (!isAttached || !LinphoneService.isReady()) {
                    return;
                }
                if (lc.getProxyConfigList() == null) {
                    mBinding.statusLed.setImageResource(R.drawable.led_disconnected);
                    RegistrationState.flagRegistrationOk = false;
                    mBinding.statusText.setText(getString(R.string.no_account));
                } else {
                    mBinding.statusLed.setVisibility(View.VISIBLE);
                }
                if (lc.getDefaultProxyConfig() != null && lc.getDefaultProxyConfig().equals(proxy)) {
                    mBinding.statusLed.setImageResource(getStatusIconResource(state, true));
                    mBinding.statusText.setText(getStatusIconText(state));
                } else if (lc.getDefaultProxyConfig() == null) {
                    mBinding.statusLed.setImageResource(getStatusIconResource(state, true));
                    mBinding.statusText.setText(getStatusIconText(state));
                }
                try {
                    mBinding.statusText.setOnClickListener(v -> {
                        // можно добавить действие при клике
                    });
                } catch (IllegalStateException ise) {
                    // ignore
                }
            }

            @Override
            public void notifyReceived(LinphoneCore lc, LinphoneEvent ev, String eventName, LinphoneContent content) {
                if (!content.getType().equals("application")) return;
                if (!content.getSubtype().equals("simple-message-summary")) return;
                if (content.getData() == null) return;
                String data = content.getDataAsString();
                String[] voiceMail = data.split("Voice-Message:");
                if (voiceMail.length > 1) {
                    final String[] intToParse = voiceMail[1].split("/", 0);
                    int unreadCount = Integer.parseInt(intToParse[0]);
                    if (unreadCount > 0) {
                        mBinding.voicemailCount.setText("" + unreadCount);
                        mBinding.voicemail.setVisibility(View.VISIBLE);
                        mBinding.voicemailCount.setVisibility(View.VISIBLE);
                    } else {
                        mBinding.voicemail.setVisibility(View.GONE);
                        mBinding.voicemailCount.setVisibility(View.GONE);
                    }
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

        return mBinding.getRoot();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        isAttached = false;
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
                if (lc.getDefaultProxyConfig() == null) {
                    mBinding.statusLed.setImageResource(R.drawable.led_disconnected);
                    RegistrationState.flagRegistrationOk = false;
                    mBinding.statusText.setText(getString(R.string.no_account));
                } else {
                    mBinding.statusLed.setImageResource(getStatusIconResource(lc.getDefaultProxyConfig().getState(), true));
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

    private int getStatusIconResource(RegistrationState state, boolean isDefaultAccount) {
        try {
            LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            boolean defaultAccountConnected = (isDefaultAccount && lc != null && lc.getDefaultProxyConfig() != null && lc.getDefaultProxyConfig().isRegistered()) || !isDefaultAccount;
            if (state == RegistrationState.RegistrationOk && defaultAccountConnected) {
                RegistrationState.flagRegistrationOk = true;
                return R.drawable.led_connected;
            } else if (state == RegistrationState.RegistrationProgress) {
                RegistrationState.flagRegistrationOk = false;
                return R.drawable.led_inprogress;
            } else if (state == RegistrationState.RegistrationFailed) {
                RegistrationState.flagRegistrationOk = false;
                return R.drawable.led_error;
            } else {
                RegistrationState.flagRegistrationOk = false;
                return R.drawable.led_disconnected;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        RegistrationState.flagRegistrationOk = false;
        return R.drawable.led_disconnected;
    }

    public String getStatusIconText(RegistrationState state) {
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

    // Качество звонка

    private void startCallQuality() {
        mBinding.callQuality.setVisibility(View.VISIBLE);
        refreshHandler.postDelayed(mCallQualityUpdater = new Runnable() {
            LinphoneCall mCurrentCall = LinphoneManager.getLc().getCurrentCall();

            public void run() {
                if (mCurrentCall == null) {
                    mCallQualityUpdater = null;
                    return;
                }
                float newQuality = mCurrentCall.getCurrentQuality();
                updateQualityOfSignalIcon(newQuality);
                if (isInCall) {
                    refreshHandler.postDelayed(this, 1000);
                } else {
                    mCallQualityUpdater = null;
                }
            }
        }, 1000);
    }

    void updateQualityOfSignalIcon(float quality) {
        int iQuality = (int) quality;
        if (iQuality == mDisplayedQuality) return;
        if (quality >= 4) {
            mBinding.callQuality.setImageResource(R.drawable.call_quality_indicator_4);
        } else if (quality >= 3) {
            mBinding.callQuality.setImageResource(R.drawable.call_quality_indicator_3);
        } else if (quality >= 2) {
            mBinding.callQuality.setImageResource(R.drawable.call_quality_indicator_2);
        } else if (quality >= 1) {
            mBinding.callQuality.setImageResource(R.drawable.call_quality_indicator_1);
        } else {
            mBinding.callQuality.setImageResource(R.drawable.call_quality_indicator_0);
        }
        mDisplayedQuality = iQuality;
    }

    public void enableSideMenu(boolean enabled) {
        mBinding.btnMenu.setEnabled(enabled);
    }
}