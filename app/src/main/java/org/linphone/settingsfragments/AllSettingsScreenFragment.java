package org.linphone.settingsfragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.pniei.portal.R;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.activities.SecondaryActivity;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.fragments.LoggerFragment;
import org.pniei.portal.fragments.UpdateFragment;
import org.pniei.portal.utils.Logger;
import org.pniei.portal.utils.PrefsUtils;

public class AllSettingsScreenFragment extends PreferenceFragmentCompat {
    private LinphonePreferences mPrefs;
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;

    public static AllSettingsScreenFragment newInstance(Context context) {
        mContext = context;
        return new AllSettingsScreenFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mPrefs = LinphonePreferences.instance();
        addPreferencesFromResource(R.xml.preferences);
        PreferenceScreen mPreferenceScreen = findPreference(rootKey);
        Handler mHandler = new Handler(Looper.getMainLooper());

        if (rootKey.equals(getString(R.string.pref_media_key))) {
            initMediaSettings();
            setMediaPreferencesListener();
        } else if (rootKey.equals(getString(R.string.pref_ip_address_key))) {
            initIpAddressSettings();
            setIpAddressPreferencesListener();
        } else if (rootKey.equals(getString(R.string.pref_advanced_key))) {
            initAdvancedSettings();
            setAdvancedPreferencesListener();
        }

        if (mPreferenceScreen != null)
            getPreferenceManager().setPreferences(mPreferenceScreen);
    }

    @Override
    public void onNavigateToScreen(PreferenceScreen preferenceScreen) {
        AdvancedSettingsSubScreenFragment fragment = new AdvancedSettingsSubScreenFragment();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.getKey());
        fragment.setArguments(args);
        getParentFragmentManager()
                .beginTransaction()
                .replace(getId(), fragment)
                .addToBackStack(null)
                .commit();
    }

    private void initIpAddressSettings() {
        EditTextPreference ipSkzi = findPreference(getString(R.string.pref_ip_skzi_key));
        if (ipSkzi != null) ipSkzi.setVisible(false);

        EditTextPreference ipMon = findPreference(getString(R.string.pref_ip_mon_key));
        if (ipMon != null) ipMon.setVisible(false);

        EditTextPreference domain = findPreference(getString(R.string.pref_domain_name_key));
        if (domain != null) {
            domain.setSummary(PrefsUtils.ins().getServerDomainName());
            domain.setText(PrefsUtils.ins().getServerDomainName());
        }

        EditTextPreference ipAts = findPreference(getString(R.string.pref_ip_ats_key));
        if (ipAts != null) {
            ipAts.setSummary(PrefsUtils.ins().getIpAtsP());
            ipAts.setText(PrefsUtils.ins().getIpAtsP());
        }

        EditTextPreference ipDns = findPreference(getString(R.string.pref_ip_dns_key));
        if (ipDns != null) {
            ipDns.setSummary(PrefsUtils.ins().getIpDnsP());
            ipDns.setText(PrefsUtils.ins().getIpDnsP());
        }

        EditTextPreference ipDnsSecond = findPreference(getString(R.string.pref_ip_dns_second_key));
        if (ipDnsSecond != null) {
            ipDnsSecond.setSummary(PrefsUtils.ins().getIpDnsSecondP());
            ipDnsSecond.setText(PrefsUtils.ins().getIpDnsSecondP());
        }
    }

    private void setIpAddressPreferencesListener() {
        Preference domainPref = findPreference(getString(R.string.pref_domain_name_key));
        if (domainPref != null) {
            domainPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = newValue.toString().isEmpty() ? "impulse.ru" : newValue.toString();
                PrefsUtils.ins().setServerDomainName(val);
                preference.setSummary(val);
                return true;
            });
        }

        Preference ipAtsPref = findPreference(getString(R.string.pref_ip_ats_key));
        if (ipAtsPref != null) {
            ipAtsPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (!PrefsUtils.ins().getIpAtsP().equals(newValue.toString())) {
                    PrefsUtils.ins().setIpAtsP(newValue.toString());
                    preference.setSummary(newValue.toString());
                    mPrefs.setAccountDomain(0, newValue.toString());
                    LinphoneManager.getLc().refreshRegisters();
                }
                return true;
            });
        }

        Preference ipDnsPref = findPreference(getString(R.string.pref_ip_dns_key));
        if (ipDnsPref != null) {
            ipDnsPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (!PrefsUtils.ins().getIpDnsP().equals(newValue.toString())) {
                    PrefsUtils.ins().setIpDnsP(newValue.toString());
                    preference.setSummary(newValue.toString());
                }
                return true;
            });
        }

        Preference ipDnsSecondPref = findPreference(getString(R.string.pref_ip_dns_second_key));
        if (ipDnsSecondPref != null) {
            ipDnsSecondPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (!PrefsUtils.ins().getIpDnsSecondP().equals(newValue.toString())) {
                    PrefsUtils.ins().setIpDnsSecondP(newValue.toString());
                    preference.setSummary(newValue.toString());
                }
                return true;
            });
        }
    }

    @SuppressLint("StringFormatMatches")
    private void initMediaSettings() {
        LinphoneCoreListenerBase mListener = new LinphoneCoreListenerBase() {
            @Override
            public void ecCalibrationStatus(LinphoneCore lc, final LinphoneCore.EcCalibratorStatus status, final int delayMs, Object data) {
                LinphoneManager.getInstance().routeAudioToReceiver();
            }
        };
    }

    private void setMediaPreferencesListener() {
    }

    private void initAdvancedSettings() {
        // All advanced settings are visible for Portal mode
    }

    @SuppressLint("BatteryLife")
    private void setAdvancedPreferencesListener() {
        Preference exportLogs = findPreference(getString(R.string.pref_export_logs_key));
        if (exportLogs != null) {
            exportLogs.setOnPreferenceClickListener(preference -> {
                ((SecondaryActivity) requireActivity()).displayFragment(LoggerFragment.newInstance(), true);
                return false;
            });
        }

        Preference updatePo = findPreference(getString(R.string.pref_update_po_key));
        if (updatePo != null) {
            updatePo.setOnPreferenceClickListener(preference -> {
                ((SecondaryActivity) requireActivity()).displayFragment(UpdateFragment.newInstance(mContext), true);
                return false;
            });
        }

        Preference resetSpomp = findPreference(getString(R.string.pref_reset_spomp_key));
        if (resetSpomp != null) {
            resetSpomp.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(mContext)
                        .setTitle(getString(R.string.pref_reset_config_title))
                        .setMessage(getString(R.string.reset_config_info))
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Сброc", (dialogInterface, i) -> {
                            PrefsUtils.ins().deleteConfig(getContext());
                            DBUtils.deleteDB(requireContext());
                            Logger.inc().clear();
                            ((SecondaryActivity) requireActivity()).quit(false);
                        })
                        .show();
                return false;
            });
        }

        Preference backgroundWork = findPreference(getString(R.string.pref_background_work_key));
        if (backgroundWork != null) {
            backgroundWork.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent();
                String packageName = mContext.getPackageName();
                PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                if (pm.isIgnoringBatteryOptimizations(packageName))
                    intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                else {
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                }
                startActivity(intent);
                return false;
            });
        }
    }
}