package org.linphone.settingsfragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.ArraySet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.PayloadType;
import org.linphone.mediastream.Log;
import org.pniei.dwface.biometry.BiometryActivity;
import org.pniei.dwface.biometry.BiometryPrefs;
import org.pniei.dwface.biometry.BiometryUtils;
import org.pniei.portal.R;
import org.pniei.portal.activities.LoginActivity;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.activities.SecondaryActivity;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.fragments.LoggerFragment;
import org.pniei.portal.fragments.UpdateFragment;

import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.Logger;
import org.pniei.portal.utils.NetworkRequestUtils;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import static org.pniei.portal.utils.PrefsUtils.ExportImportPrefs;
import static org.pniei.portal.utils.PrefsUtils.REGIME_P;

public static class AllSettingsScreenFragment extends PreferenceFragmentCompat {
    private LinphonePreferences mPrefs;
    private ActivityResultLauncher<Intent> biometryResultLauncher;
    private ActivityResultLauncher<Intent> selectDirResultLauncher;
    private ActivityResultLauncher<Intent> selectFileResultLauncher;
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;
    private DocumentFile pickedDir = null;
    private DocumentFile pickedFile = null;
    private TextView selectedDir = null;
    private Handler mHandler;
    /*private AlertDialog waitDialog;*/
    private String mRootKey;
    private LinphoneCoreListenerBase mListener;

    public static AllSettingsScreenFragment newInstance(Context context) {
        mContext = context;
        return new AllSettingsScreenFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mPrefs = LinphonePreferences.instance();
        addPreferencesFromResource(R.xml.preferences);
        PreferenceScreen mPreferenceScreen = findPreference(rootKey);
        mHandler = new Handler(Looper.getMainLooper());
        mRootKey = rootKey;
        /*if(rootKey.equals(getString(R.string.pref_codecs_key))) {
            initCodecsSettings();
        } else*/
        if (rootKey.equals(getString(R.string.pref_media_key))) {
            initMediaSettings();
            setMediaPreferencesListener();
        } else if (rootKey.equals(getString(R.string.pref_ip_address_key))) {
            initIpAddressSettings();
            setIpAddressPreferencesListener();
        } else if (rootKey.equals(getString(R.string.pref_vpn_info_key))) {
            initKeyInfo();
        } else if (rootKey.equals(getString(R.string.pref_biometry_settings_key))) {
            registerForBiometryResult();
            registerForSelectDirResult();
            registerForSelectFileResult();
            initBiometrySettings();
        } else if (rootKey.equals(getString(R.string.pref_gps_key))) {
            initGpsSettings();
            setGpsPreferencesListener();
        } else if (rootKey.equals(getString(R.string.pref_advanced_key))) {
            registerForSelectDirResult();
            registerForSelectFileResult();
            initAdvancedSettings();
            setAdvancedPreferencesListener();
        }

        //hideSettings();
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

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (PrefsUtils.ins().getRegimeSelected() == REGIME_P && mRootKey.equals(getString(R.string.pref_vpn_info_key))) {
            ManagerLiveData.ins().getSkziTimeError().observe(getViewLifecycleOwner(), s -> {
                if (!s.isEmpty()) {
                    findPreference(getString(R.string.pref_connect_skzi_time_error_key)).setVisible(true);
                    findPreference(getString(R.string.pref_connect_skzi_time_error_key)).setOnPreferenceClickListener(preference -> {
                        new MaterialAlertDialogBuilder(mContext)
                                .setTitle(getString(R.string.pref_connect_skzi_time_error))
                                .setMessage(s)
                                .setPositiveButton("OK", null)
                                .show();
                        return true;
                    });
                } else {
                    findPreference(getString(R.string.pref_connect_skzi_time_error_key)).setVisible(false);
                }
            });
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    private void initCodecsSettings() {
        PreferenceCategory codecs = findPreference(getString(R.string.pref_audio_codecs_key));
        assert codecs != null;
        codecs.removeAll();

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        assert lc != null;
        for (final PayloadType pt : lc.getAudioCodecs()) {
            if (!pt.getMime().equals("L16") && !pt.getMime().equals("opus")) {
                CheckBoxPreference codec = new CheckBoxPreference(requireActivity());
                codec.setTitle(pt.getMime());
                codec.setSummary(pt.getRate() + " Hz");
                codec.setChecked(lc.isPayloadTypeEnabled(pt));

                codec.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enable = (Boolean) newValue;
                    try {
                        LinphoneManager.getLcIfManagerNotDestroyedOrNull().enablePayloadType(pt, enable);
                    } catch (LinphoneCoreException e) {
                        Log.e(e);
                    }
                    return true;
                });
                codecs.addPreference(codec);
            }
        }

        codecs = findPreference(getString(R.string.pref_video_codecs_key));
        assert codecs != null;
        codecs.removeAll();
        for (final PayloadType pt : lc.getVideoCodecs()) {
            if (pt.getMime().equals("H264")) {
                final CheckBoxPreference codec = new CheckBoxPreference(Objects.requireNonNull(getActivity()));
                codec.setTitle(pt.getMime());
                codec.setChecked(lc.isPayloadTypeEnabled(pt));
                codec.setEnabled(false);
                /*codec.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enable = (Boolean) newValue;
                    try {
                        LinphoneManager.getLcIfManagerNotDestroyedOrNull().enablePayloadType(pt, enable);
                    } catch (LinphoneCoreException e) {
                        Log.e(e);
                    }
                    return true;
                });*/

                codecs.addPreference(codec);
            }
        }

    }

    private void initIpAddressSettings() {
        EditTextPreference domain = findPreference(getString(R.string.pref_domain_name_key));
        assert domain != null;
        domain.setSummary(PrefsUtils.ins().getServerDomainName());
        domain.setText(PrefsUtils.ins().getServerDomainName());

        if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
            EditTextPreference ipSkzi = findPreference(getString(R.string.pref_ip_skzi_key));
            assert ipSkzi != null;
            ipSkzi.setVisible(false);

            EditTextPreference ipMon = findPreference(getString(R.string.pref_ip_mon_key));
            assert ipMon != null;
            ipMon.setVisible(false);

            EditTextPreference ipAts = findPreference(getString(R.string.pref_ip_ats_key));
            assert ipAts != null;
            ipAts.setSummary(PrefsUtils.ins().getIpAtsTT());
            ipAts.setText(PrefsUtils.ins().getIpAtsTT());

            EditTextPreference ipDns = findPreference(getString(R.string.pref_ip_dns_key));
            assert ipDns != null;
            ipDns.setSummary(PrefsUtils.ins().getIpDnsTT());
            ipDns.setText(PrefsUtils.ins().getIpDnsTT());

            EditTextPreference ipDnsSecond = findPreference(getString(R.string.pref_ip_dns_second_key));
            assert ipDnsSecond != null;
            ipDnsSecond.setVisible(false);
        } else {
            EditTextPreference ipSkzi = findPreference(getString(R.string.pref_ip_skzi_key));
            assert ipSkzi != null;
            ipSkzi.setSummary(PrefsUtils.ins().getIpSkzi());
            ipSkzi.setText(PrefsUtils.ins().getIpSkzi());

            EditTextPreference ipMon = findPreference(getString(R.string.pref_ip_mon_key));
            assert ipMon != null;
            ipMon.setSummary(PrefsUtils.ins().getIpMon());
            ipMon.setText(PrefsUtils.ins().getIpMon());

            EditTextPreference ipAts = findPreference(getString(R.string.pref_ip_ats_key));
            assert ipAts != null;
            ipAts.setSummary(PrefsUtils.ins().getIpAtsP());
            ipAts.setText(PrefsUtils.ins().getIpAtsP());

            EditTextPreference ipDns = findPreference(getString(R.string.pref_ip_dns_key));
            assert ipDns != null;
            ipDns.setSummary(PrefsUtils.ins().getIpDnsP());
            ipDns.setText(PrefsUtils.ins().getIpDnsP());

            EditTextPreference ipDnsSecond = findPreference(getString(R.string.pref_ip_dns_second_key));
            assert ipDnsSecond != null;
            ipDnsSecond.setSummary(PrefsUtils.ins().getIpDnsSecondP());
            ipDnsSecond.setText(PrefsUtils.ins().getIpDnsSecondP());
        }
    }

    private void setIpAddressPreferencesListener() {
        findPreference(getString(R.string.pref_domain_name_key)).setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue.toString().isEmpty()) {
                newValue = "impulse.ru";
            }
            PrefsUtils.ins().setServerDomainName(newValue.toString());
            preference.setSummary(newValue.toString());
            return true;
        });

        findPreference(getString(R.string.pref_ip_skzi_key)).setOnPreferenceChangeListener((preference, newValue) -> {
            if (!PrefsUtils.ins().getIpSkzi().equals(newValue.toString())) {
                PrefsUtils.ins().setIpSkzi(newValue.toString());
                preference.setSummary(newValue.toString());
            }
            return true;
        });

        findPreference(getString(R.string.pref_ip_mon_key)).setOnPreferenceChangeListener((preference, newValue) -> {
            if (!PrefsUtils.ins().getIpMon().equals(newValue.toString())) {
                PrefsUtils.ins().setIpMon(newValue.toString());
                preference.setSummary(newValue.toString());
            }
            return true;
        });

        findPreference(getString(R.string.pref_ip_ats_key)).setOnPreferenceChangeListener((preference, newValue) -> {
            if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
                if (!PrefsUtils.ins().getIpAtsTT().equals(newValue.toString())) {
                    PrefsUtils.ins().setIpAtsTT(newValue.toString());
                    preference.setSummary(newValue.toString());
                    mPrefs.setAccountDomain(0, newValue.toString());
                    preference.setSummary(newValue.toString());
                    LinphoneManager.getLc().refreshRegisters();
                }
            } else {
                if (!PrefsUtils.ins().getIpAtsP().equals(newValue.toString())) {
                    PrefsUtils.ins().setIpAtsP(newValue.toString());
                    preference.setSummary(newValue.toString());
                    mPrefs.setAccountDomain(0, newValue.toString());
                    preference.setSummary(newValue.toString());
                    LinphoneManager.getLc().refreshRegisters();
                }
            }
            return true;
        });

        findPreference(getString(R.string.pref_ip_dns_key)).setOnPreferenceChangeListener((preference, newValue) -> {
            if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
                if (!PrefsUtils.ins().getIpDnsTT().equals(newValue.toString())) {
                    PrefsUtils.ins().setIpDnsTT(newValue.toString());
                    preference.setSummary(newValue.toString());
                }
            } else {
                if (!PrefsUtils.ins().getIpDnsP().equals(newValue.toString())) {
                    PrefsUtils.ins().setIpDnsP(newValue.toString());
                    preference.setSummary(newValue.toString());
                }
            }
            return true;
        });

        findPreference(getString(R.string.pref_ip_dns_second_key)).setOnPreferenceChangeListener((preference, newValue) -> {
            if (PrefsUtils.ins().getRegimeSelected() == REGIME_P) {
                if (!PrefsUtils.ins().getIpDnsSecondP().equals(newValue.toString())) {
                    PrefsUtils.ins().setIpDnsSecondP(newValue.toString());
                    preference.setSummary(newValue.toString());
                }
            }
            return true;
        });
    }

    @SuppressLint("StringFormatMatches")
    private void initMediaSettings() {

        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void ecCalibrationStatus(LinphoneCore lc, final LinphoneCore.EcCalibratorStatus status, final int delayMs, Object data) {
                LinphoneManager.getInstance().routeAudioToReceiver();
            }
        };

        ((CheckBoxPreference) Objects.requireNonNull(findPreference(getString(R.string.pref_overlay_key)))).setChecked(mPrefs.isOverlayEnabled());
    }
    private static void setListPreferenceValues(ListPreference pref, List<CharSequence> entries, List<CharSequence> values) {
        CharSequence[] contents = new CharSequence[entries.size()];
        entries.toArray(contents);
        pref.setEntries(contents);
        contents = new CharSequence[values.size()];
        values.toArray(contents);
        pref.setEntryValues(contents);
    }

    private void setMediaPreferencesListener() {
        findPreference(getString(R.string.pref_echo_canceller_calibration_key)).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference) {
                synchronized (AllSettingsScreenFragment.this) {
                    preference.setSummary(R.string.ec_calibrating);

                    int recordAudio = requireActivity().getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, requireActivity().getPackageName());
                    if (recordAudio == PackageManager.PERMISSION_GRANTED) {
                    } else {
                        MainActivity.instance().checkAndRequestRecordAudioPermissionForEchoCanceller();
                    }
                }
                return true;
            }
        });

        findPreference(getString(R.string.pref_echo_tester_key)).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference) {
                synchronized (AllSettingsScreenFragment.this) {
                    int recordAudio = requireActivity().getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, requireActivity().getPackageName());
                    if (recordAudio == PackageManager.PERMISSION_GRANTED) {
                        if (LinphoneManager.getInstance().getEchoTesterStatus())
                            stopEchoTester();
                        else
                            startEchoTester();
                    } else {
                        MainActivity.instance().checkAndRequestRecordAudioPermissionsForEchoTester();
                    }
                }
                return true;
            }
        });

        findPreference(getString(R.string.pref_preferred_video_size_key)).setOnPreferenceChangeListener((preference, newValue) -> {
            mPrefs.setPreferredVideoSize(newValue.toString());
            preference.setSummary(mPrefs.getPreferredVideoSize());
            return true;
        });

        findPreference(getString(R.string.pref_overlay_key)).setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enable = (Boolean) newValue;
            if (enable) {
                if (MainActivity.instance().checkAndRequestOverlayPermission()) {
                    mPrefs.enableOverlay(true);
                }
            } else {
                mPrefs.enableOverlay(false);
            }
            return true;
        });
    }

    private void initAdvancedSettings() {
        if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
            findPreference(getString(R.string.pref_export_logs_key)).setVisible(false);
        }
    }

    @SuppressLint("BatteryLife")
    private void setAdvancedPreferencesListener() {
        findPreference(getString(R.string.pref_export_logs_key)).setOnPreferenceClickListener(preference -> {
            /*if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            }
            else {*/
            ((SecondaryActivity) requireActivity()).displayFragment(LoggerFragment.newInstance(), true);
            //}
            return false;
        });

        findPreference(getString(R.string.pref_update_po_key)).setOnPreferenceClickListener(preference -> {
            /*if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            }
            else {*/
            ((SecondaryActivity) requireActivity()).displayFragment(UpdateFragment.newInstance(mContext), true);
            // }
            return false;
        });


        findPreference(getString(R.string.pref_reset_spomp_key)).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(mContext)
                    .setTitle(getString(R.string.pref_reset_config_title))
                    .setMessage(getString(R.string.reset_config_info))
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Сброc", (dialogInterface, i) -> {
                        PrefsUtils.ins().deleteConfig(getContext());
                        DBUtils.deleteDB(requireContext());
                        Logger.inc().clear();
                        Utils.deleteRecursive(BiometryUtils.getBiometryDir(mContext));
                        ((SecondaryActivity) requireActivity()).quit(false);
                    })
                    .show();
            return false;
        });

        findPreference(getString(R.string.pref_change_regime_key)).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(mContext)
                    .setTitle(getString(R.string.pref_change_regime_title))
                    .setMessage(getString(R.string.change_regime_info))
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Сменить", (dialogInterface, i) -> {
                        Utils.showWaitDialog(mContext, getString(R.string.pref_change_regime_title));

                        /*waitDialog = new MaterialAlertDialogBuilder(mContext)
                                .setTitle("Смена режима работы")
                                .setView(R.layout.dialog_wait)
                                .setCancelable(false)
                                .show();*/

                        new Thread(() -> {
                            mContext.stopService(new Intent(Intent.ACTION_MAIN).setClass(mContext, LinphoneService.class));
                            if (PrefsUtils.ins().getRegimeSelected() == REGIME_P) {
                            }
                            SpoMessagesService.stop(mContext);
                            PrefsUtils.ins().setAuth(false);
                            PrefsUtils.ins().setRegimeSelected(PrefsUtils.REGIME_NONE);
                            DBUtils.closeDB();

                            mHandler.post(() -> {
                                Utils.closeWaitDialog();
                                /*waitDialog.dismiss();*/
                                Intent intent = new Intent(mContext, LoginActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                startActivity(intent);
                                requireActivity().finish();
                                MainActivity.instance().finish();
                            });
                        }).start();
                    })
                    .show();
            return false;
        });

        findPreference(getString(R.string.pref_background_work_key)).setOnPreferenceClickListener(preference -> {
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

        findPreference(getString(R.string.pref_export_settings_key)).setOnPreferenceClickListener(preference -> {
            AlertDialog alertDialog = new MaterialAlertDialogBuilder(mContext)
                    .setTitle(getString(R.string.pref_export_settings_title))
                    .setMessage(R.string.info_export_settings)
                    .setView(R.layout.dialog_select_dir)
                    .setNegativeButton(getString(R.string.cancel), null)
                    .setPositiveButton(getString(R.string.export), (dialog, which) -> {
                        if (pickedDir == null)
                            return;
                        Utils.showWaitDialog(mContext, getString(R.string.pref_export_settings_title));

                        new Thread(() -> {
                            DocumentFile fileCont = pickedDir.createFile("text", getString(R.string.file_export_prefs));

                            // Формирование экспортируемых настроек
                            JSONObject jsonObject = new JSONObject();
                            StringBuilder sb = new StringBuilder();

                            try {
                                if (PrefsUtils.ins().getRegimeSelected() == REGIME_P) {
                                    jsonObject.put(ExportImportPrefs.REGIME, ExportImportPrefs.REGIME_P);
                                    sb.append(ExportImportPrefs.REGIME_P);
                                    jsonObject.put(ExportImportPrefs.IP_ATS, PrefsUtils.ins().getIpAtsP());
                                    sb.append(PrefsUtils.ins().getIpAtsP());
                                    jsonObject.put(ExportImportPrefs.IP_SKZI, PrefsUtils.ins().getIpSkzi());
                                    sb.append(PrefsUtils.ins().getIpSkzi());
                                    jsonObject.put(ExportImportPrefs.IP_MON, PrefsUtils.ins().getIpMon());
                                    sb.append(PrefsUtils.ins().getIpMon());
                                    jsonObject.put(ExportImportPrefs.IP_DNS, PrefsUtils.ins().getIpDnsP());
                                    sb.append(PrefsUtils.ins().getIpDnsP());
                                    jsonObject.put(ExportImportPrefs.IP_DNS_SECOND, PrefsUtils.ins().getIpDnsSecondP());
                                    JSONArray apps = new JSONArray();
                                    for (String app : PrefsUtils.ins().getVpnApps()) {
                                        apps.put(app);
                                        sb.append(app);
                                    }
                                    jsonObject.put(ExportImportPrefs.VPN_APP_LIST, apps);
                                } else {
                                    jsonObject.put(ExportImportPrefs.REGIME, ExportImportPrefs.REGIME_TT);
                                    sb.append(ExportImportPrefs.REGIME_TT);
                                    jsonObject.put(ExportImportPrefs.IP_ATS, PrefsUtils.ins().getIpAtsTT());
                                    sb.append(PrefsUtils.ins().getIpAtsTT());
                                    jsonObject.put(ExportImportPrefs.IP_DNS, PrefsUtils.ins().getIpDnsTT());
                                    sb.append(PrefsUtils.ins().getIpDnsTT());
                                }

                                // Расчет контрольной суммы
                                byte[] configs = sb.toString().getBytes();
                                int crc = CryptUtils.CRC32(configs, configs.length);
                                jsonObject.put(ExportImportPrefs.PREF_CRC, Utils.intToHexString(crc));
                            } catch (JSONException jsonE) {
                                jsonE.printStackTrace();
                                mHandler.post(() -> {
                                    Utils.closeWaitDialog();
                                    Toast.makeText(mContext, getString(R.string.error_export_settings), Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }

                            try (OutputStream os = mContext.getContentResolver().openOutputStream(fileCont.getUri())) {
                                byte[] data = jsonObject.toString().getBytes(StandardCharsets.UTF_8);
                                assert os != null;
                                os.write(data);
                                os.flush();
                            } catch (Exception ex) {
                                mHandler.post(() -> {
                                    Utils.closeWaitDialog();
                                    Toast.makeText(mContext, getString(R.string.error_export_settings), Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }
                            mHandler.post(() -> {
                                Utils.closeWaitDialog();
                                Toast.makeText(mContext,getString(R.string.end_export_settings), Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    })
                    .show();
            ImageButton button = alertDialog.findViewById(R.id.btnSelectKeyDir);
            assert button != null;
            button.setOnClickListener(view -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                selectDirResultLauncher.launch(intent);
            });
            selectedDir = alertDialog.findViewById(R.id.nameDir);
            return true;
        });

        findPreference(getString(R.string.pref_import_settings_key)).setOnPreferenceClickListener(preference -> {
            AlertDialog alertDialog = new MaterialAlertDialogBuilder(mContext)
                    .setTitle(getString(R.string.pref_import_settings_title))
                    .setMessage(R.string.info_import_settings)
                    .setView(R.layout.dialog_select_file)
                    .setNegativeButton(getString(R.string.cancel), null)
                    .setPositiveButton(getString(R.string.import_str), (dialog, which) -> {
                        if (pickedFile == null)
                            return;
                        Utils.showWaitDialog(mContext, getString(R.string.pref_import_settings_title));

                        new Thread(() -> {
                            byte[] data = null;

                            try (InputStream is = mContext.getContentResolver().openInputStream(pickedFile.getUri())) {
                                assert is != null;
                                data = new byte[is.available()];
                                is.read(data);

                                JSONObject jsonObject = new JSONObject(new String(data));

                                /*if (!jsonObject.has(ExportImportPrefs.REGIME)) {
                                    throw new Exception("Отсутствует поле \"regime\"");
                                }*/
                                int regime;
                                String ipAts, ipSkzi = "", ipMon = "", ipDns, ipDnsSecond = "", prefCrc;
                                Set<String> vpnAppList = new ArraySet<>();
                                StringBuilder sb = new StringBuilder();

                                regime = jsonObject.getInt(ExportImportPrefs.REGIME);
                                if (regime != PrefsUtils.ins().getRegimeSelected()) {
                                    mHandler.post(() -> {
                                        Utils.closeWaitDialog();
                                        Toast.makeText(mContext, getString(R.string.error_import_settings_wrong_regime), Toast.LENGTH_SHORT).show();
                                    });
                                    return;
                                }

                                sb.append(jsonObject.getString(ExportImportPrefs.REGIME));
                                ipAts = jsonObject.getString(ExportImportPrefs.IP_ATS);
                                sb.append(ipAts);

                                if (regime == ExportImportPrefs.REGIME_P) {
                                    ipSkzi = jsonObject.getString(ExportImportPrefs.IP_SKZI);
                                    sb.append(ipSkzi);
                                    ipMon = jsonObject.getString(ExportImportPrefs.IP_MON);
                                    sb.append(ipMon);
                                    ipDns = jsonObject.getString(ExportImportPrefs.IP_DNS);
                                    sb.append(ipDns);
                                    ipDnsSecond = jsonObject.getString(ExportImportPrefs.IP_DNS_SECOND);
                                    sb.append(ipDnsSecond);
                                    JSONArray jsonArray = jsonObject.getJSONArray(ExportImportPrefs.VPN_APP_LIST);
                                    for (int i = 0; i < jsonArray.length(); i++) {
                                        String appName = jsonArray.getString(i);
                                        vpnAppList.add(appName);
                                        sb.append(appName);
                                    }
                                } else if (regime == ExportImportPrefs.REGIME_TT) {
                                    ipDns = jsonObject.getString(ExportImportPrefs.IP_DNS);
                                    sb.append(ipDns);
                                } else {
                                    throw new Exception("Некорректное содержание поля \"regime\"");
                                }

                                prefCrc = jsonObject.getString(ExportImportPrefs.PREF_CRC);

                                byte[] configs = sb.toString().getBytes();
                                if (!(prefCrc.equalsIgnoreCase(Utils.intToHexString(crc)))) {
                                    throw new Exception("Несовпала контрольная сумма");
                                }

                                // Применение настроек
                                PrefsUtils prefs = PrefsUtils.ins();
                                if (regime == ExportImportPrefs.REGIME_P) {
                                    prefs.setIpAtsP(ipAts);
                                    prefs.setIpSkzi(ipSkzi);
                                    prefs.setIpMon(ipMon);
                                    prefs.setIpDnsP(ipDns);
                                    prefs.setIpDnsSecondP(ipDnsSecond);
                                } else {
                                    prefs.setIpAtsTT(ipAts);
                                    prefs.setIpDnsTT(ipDns);
                                }

                                //initIpAddressSettings();  // Обвновление значений на экране

                                // Обновление параметров sip клиента
                                LinphonePreferences sipPrefs = LinphonePreferences.instance();
                                sipPrefs.setAccountDomain(0, ipAts);

                                // Переустановка соединения с СКЗИ для режима Портал
                                if (regime == ExportImportPrefs.REGIME_P) {
                                    MonitoringService.stopMonitoringService(mContext);

                                    if (PrefsUtils.ins().isVpnEnable()) {
                                        if (VpnClient.restartVpnService(getActivity()))
                                            MonitoringService.startMonitoringService(mContext);
                                    }
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                mHandler.post(() -> {
                                    Utils.closeWaitDialog();
                                    Toast.makeText(mContext, getString(R.string.error_import_settings), Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }

                            mHandler.post(() -> {
                                Utils.closeWaitDialog();
                                Toast.makeText(mContext, getString(R.string.end_import_settings), Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    })
                    .show();
            ImageButton button = alertDialog.findViewById(R.id.btnSelectFile);
            assert button != null;
            button.setOnClickListener(view -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                selectFileResultLauncher.launch(intent);
            });
            selectedDir = alertDialog.findViewById(R.id.nameFile);
            return true;
        });
    }

    private void initKeyInfo() {
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference(getString(R.string.pref_connect_skzi_key)))).setChecked(PrefsUtils.ins().isVpnEnable());
        findPreference(getString(R.string.pref_connect_skzi_key)).setOnPreferenceChangeListener((preference, newValue) -> {
            findPreference(getString(R.string.pref_connect_skzi_time_error_key)).setVisible(false);
            PrefsUtils.ins().setEnableVpn((boolean) newValue);
            return true;
        });
    }

    MultiSelectListPreference work_with_vpn = findPreference(getString(R.string.pref_work_with_vpn_key));
    PackageManager pm = mContext.getPackageManager();
    Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://"+NetworkRequestUtils.URL_SERVICES_ADDRESS +PrefsUtils.ins().

    getServerDomainName()));
    List<ResolveInfo> listBrowser = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
    String[] list_package_app = new String[listBrowser.size()];
    String[] list_name_app = new String[listBrowser.size()];

    int i = 0;
        for(
    ResolveInfo resolveInfo :listBrowser)

    {
        list_package_app[i] = resolveInfo.activityInfo.packageName;
        list_name_app[i] = resolveInfo.activityInfo.loadLabel(getContext().getPackageManager()).toString();
        i++;
    }

        work_with_vpn.setEntries(list_name_app);
        work_with_vpn.setEntryValues(list_package_app);
        work_with_vpn.setValues(PrefsUtils.ins().

    getVpnApps());

        work_with_vpn.setOnPreferenceChangeListener((preference,newValue)->

    {
        PrefsUtils.ins().setVpnApps((Set<String>) newValue);
        MonitoringService.stopMonitoringService(mContext);
        return true;
    });
}

private PreferenceScreen createPrefScreenNextKey() {
    PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(getContext());
    PreferenceCategory preferenceCategory = new PreferenceCategory(getContext());
    preferenceCategory.setTitle(R.string.key_next_settings_title);
    preferenceScreen.addPreference(preferenceCategory);
    return preferenceScreen;
}

private void initBiometrySettings() {
    PreferenceCategory category = findPreference(getString(R.string.pref_biometry_settings_cat_key));
    if (category == null)
        return;

    if (!BiometryPrefs.ins().isInitBiometryLib()) {
        category.setVisible(false);
        return;
    }

    Preference deleteBiometry = category.getPreference(0);
    Preference createBiometry = category.getPreference(1);
    Preference updateBiometry = category.getPreference(2);
    Preference exportBiometry = category.getPreference(3);
    Preference importBiometry = category.getPreference(4);

    if (BiometryPrefs.ins().isBiometryBind()) {
        deleteBiometry.setVisible(true);
        deleteBiometry.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Удалить биометрический контейнер ?")
                    .setNegativeButton("Нет", null)
                    .setPositiveButton("Да", (dialog, which) -> {
                        Utils.showWaitDialog(mContext, "Удаление контейнера");
                            /*waitDialog = new MaterialAlertDialogBuilder(mContext)
                                    .setTitle("Удаление контейнера")
                                    .setView(R.layout.dialog_wait)
                                    .setCancelable(false)
                                    .show();*/

                        new Thread(() -> {
                            BiometryPrefs.ins().setBiometryBind(false);
                            BiometryPrefs.ins().setP1(0);
                            BiometryPrefs.ins().setP2(0);
                            BiometryPrefs.ins().setP3(0);
                            Utils.deleteRecursive(BiometryUtils.getImagesDir(mContext));
                            Utils.deleteRecursive(BiometryUtils.getNbccDir(mContext));
                            Utils.deleteRecursive(BiometryUtils.getRectDir(mContext));

                            mHandler.post(() -> {
                                Utils.closeWaitDialog();
                                /* waitDialog.dismiss();*/
                                initBiometrySettings();
                                Toast.makeText(mContext, "Биометрический контейнер удален", Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    })
                    .show();
            return true;
        });
        createBiometry.setVisible(false);
        updateBiometry.setVisible(true);
        updateBiometry.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(mContext)
                    .setTitle("Переобучение биометрического контейнера")
                    .setMessage(R.string.info_update_biometry)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Переобучить", (dialog, which) -> {
                        ArrayList<byte[]> dataArray = new ArrayList<>();
                        dataArray.add(PrefsUtils.ins().getHashPass());
                        byte[] data = BiometryUtils.dataGeneration(dataArray);
                        BiometryPrefs.ins().setKeyDecryptImage(PrefsUtils.ins().getHashPass());
                        BiometryPrefs.ins().setKeyEncryptImage(PrefsUtils.ins().getHashPass());
                        Intent newIntent = new Intent(mContext, BiometryActivity.class);
                        newIntent.putExtra(BiometryActivity.REGIME, BiometryActivity.REGIME_UPDATE);
                        newIntent.putExtra(BiometryActivity.DATA, data);
                        biometryResultLauncher.launch(newIntent);
                    })
                    .show();
            return true;
        });

        exportBiometry.setVisible(true);
        exportBiometry.setOnPreferenceClickListener(preference -> {
            AlertDialog alertDialog = new MaterialAlertDialogBuilder(mContext)
                    .setTitle("Экспорт биометрического контейнера")
                    .setMessage(R.string.info_export_biometry)
                    .setView(R.layout.dialog_export_biometry)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Экспорт", (dialog, which) -> {
                        if (pickedDir == null)
                            return;

                        CheckBox isAddImagesCheckBox = ((AlertDialog) dialog).findViewById(R.id.isAddImages);
                        boolean isAddImages = isAddImagesCheckBox.isChecked();

                        Utils.showWaitDialog(mContext, "Экспорт контейнера");
                            /*waitDialog = new MaterialAlertDialogBuilder(mContext)
                                    .setTitle("Экспорт контейнера")
                                    .setView(R.layout.dialog_wait)
                                    .setCancelable(false)
                                    .show();*/

                        new Thread(() -> {
                            DocumentFile fileCont = pickedDir.createFile("text", "ImpulsBiom.bc");
                            // Создание архива
                            File archive = BiometryUtils.exportContainer(mContext, isAddImages);
                            if (archive == null) {
                                mHandler.post(() -> {
                                    Utils.closeWaitDialog();
                                    /* waitDialog.dismiss();*/
                                    Toast.makeText(mContext, "Ошибка экспорта контейнера", Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }

                            try (OutputStream os = mContext.getContentResolver().openOutputStream(fileCont.getUri())) {
                                byte[] data;
                                FileInputStream fis = new FileInputStream(archive);
                                data = new byte[fis.available()];
                                fis.read(data);
                                // Шифрование архива
                                data = CryptUtils.cryptData(data, PrefsUtils.ins().getHashPass());
                                os.write(data);
                                os.flush();
                                archive.delete();
                            } catch (Exception ex) {
                                archive.delete();
                                mHandler.post(() -> {
                                    Utils.closeWaitDialog();
                                    /*waitDialog.dismiss();*/
                                    Toast.makeText(mContext, "Ошибка экспорта контейнера", Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }
                            mHandler.post(() -> {
                                Utils.closeWaitDialog();
                                /*waitDialog.dismiss();*/
                                Toast.makeText(mContext, "Контейнер экспортирован", Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    })
                    .show();
            ImageButton button = alertDialog.findViewById(R.id.btnSelectKeyDir);
            button.setOnClickListener(view -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                selectDirResultLauncher.launch(intent);
            });
            selectedDir = alertDialog.findViewById(R.id.nameDir);
            return true;
        });
        importBiometry.setVisible(false);
    } else {
        deleteBiometry.setVisible(false);
        createBiometry.setVisible(true);
        createBiometry.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(mContext)
                    .setTitle("Создание биометрического контейнера")
                    .setMessage(R.string.info_create_biometry)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Создать", (dialog, which) -> {
                        ArrayList<byte[]> dataArray = new ArrayList<>();
                        dataArray.add(PrefsUtils.ins().getHashPass());
                        byte[] data = BiometryUtils.dataGeneration(dataArray);
                        BiometryUtils.createDirs(mContext);
                        BiometryPrefs.ins().setKeyDecryptImage(PrefsUtils.ins().getHashPass());
                        BiometryPrefs.ins().setKeyEncryptImage(PrefsUtils.ins().getHashPass());
                        Intent newIntent = new Intent(mContext, BiometryActivity.class);
                        newIntent.putExtra(BiometryActivity.REGIME, BiometryActivity.REGIME_COLLECT_ALL);
                        newIntent.putExtra(BiometryActivity.DATA, data);
                        biometryResultLauncher.launch(newIntent);
                    })
                    .show();
            return true;
        });
        updateBiometry.setVisible(false);
        exportBiometry.setVisible(false);
        importBiometry.setVisible(true);
        importBiometry.setOnPreferenceClickListener(preference -> {
            AlertDialog alertDialog = new MaterialAlertDialogBuilder(mContext)
                    .setTitle(getString(R.string.import_biometry_title))
                    .setMessage(R.string.info_import_biometry)
                    .setView(R.layout.dialog_import_biometry)
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Импорт", (dialog, which) -> {
                        EditText editText = ((AlertDialog) dialog).findViewById(R.id.pass);
                        if (editText == null)
                            return;

                        String passEnter = editText.getText().toString();
                        if (passEnter.length() == 0) {
                            Toast.makeText(mContext, "Пароль не введен", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (pickedFile == null)
                            return;
                        Utils.showWaitDialog(mContext, "Импорт контейнера");

                        new Thread(() -> {
                            File temp = new File(BiometryUtils.getBiometryDir(mContext), "BiomContArchive");
                            try (InputStream is = mContext.getContentResolver().openInputStream(pickedFile.getUri())) {
                                byte[] data = new byte[is.available()];
                                is.read(data);

                                data = CryptUtils.decryptData(data, CryptUtils.getHash(passEnter.getBytes()));
                                if (data == null) {
                                    mHandler.post(() -> {
                                        Utils.closeWaitDialog();
                                        Toast.makeText(mContext, "Неверный пароль", Toast.LENGTH_SHORT).show();
                                    });
                                    return;
                                }

                                FileOutputStream fos = new FileOutputStream(temp);
                                fos.write(data);
                                fos.flush();
                                fos.close();
                            } catch (Exception ex) {
                                temp.delete();
                                mHandler.post(() -> {
                                    Utils.closeWaitDialog();
                                    Toast.makeText(mContext, "Ошибка импорта контейнера", Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }

                            if (!BiometryUtils.importContainer(mContext, temp)) {
                                temp.delete();
                                mHandler.post(() -> {
                                    Utils.closeWaitDialog();
                                    /*waitDialog.dismiss();*/
                                    Toast.makeText(mContext, "Ошибка импорта контейнера", Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }

                            temp.delete();
                            BiometryPrefs.ins().setBiometryBind(true);
                            mHandler.post(() -> {
                                Utils.closeWaitDialog();
                                /*waitDialog.dismiss();*/
                                initBiometrySettings();
                                Toast.makeText(mContext, "Контейнер импортирован", Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    })
                    .show();
            ImageButton button = alertDialog.findViewById(R.id.btnSelectFile);
            button.setOnClickListener(view -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                selectFileResultLauncher.launch(intent);
            });
            selectedDir = alertDialog.findViewById(R.id.nameDir);

            return true;
        });
    }
}

public void registerForBiometryResult() {
    biometryResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent resultData = result.getData();
                    if (resultData != null) {
                        int res = resultData.getIntExtra(BiometryActivity.RESULT, BiometryActivity.ERROR);

                        if (res == BiometryActivity.REG_OK) {
                            byte[] nbcc = resultData.getByteArrayExtra(BiometryActivity.NBCC);
                            if (nbcc != null) {
                                BiometryUtils.saveNBCC(mContext, nbcc);
                                Toast.makeText(mContext, R.string.biometry_result_ok, Toast.LENGTH_SHORT).show();
                                BiometryPrefs.ins().setBiometryBind(true);
                                initBiometrySettings();
                            } else {
                                Toast.makeText(mContext, R.string.error_biometry_data, Toast.LENGTH_SHORT).show();
                            }
                        } else if (res == BiometryActivity.TIME_OUT) {
                            Toast.makeText(mContext, R.string.error_biometry_timeout, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(mContext, R.string.error_biometry, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
}

public void registerForSelectDirResult() {
    selectDirResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent resultData = result.getData();
                    Uri treeUri = resultData.getData();
                    pickedDir = DocumentFile.fromTreeUri(getContext(), treeUri);
                    mContext.getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    String dir = Utils.getUriPath(mContext, treeUri);
                    if (selectedDir != null)
                        selectedDir.setText(dir == null ? "" : ("/" + dir));
                }
            });
}

public void registerForSelectFileResult() {
    selectFileResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent resultData = result.getData();
                    Uri fileUri = resultData.getData();
                    pickedFile = DocumentFile.fromSingleUri(getContext(), fileUri);
                    selectedDir.setText(pickedFile.getName());
                }
            });
}

private void initGpsSettings() {
    EditTextPreference ip_db = findPreference(getString(R.string.pref_ip_db_key));
    ip_db.setSummary(PrefsUtils.ins().getIpGps());
    ip_db.setText(PrefsUtils.ins().getIpGps());
    ip_db.setVisible(PrefsUtils.ins().isSendGPS());

    CheckBoxPreference send_gps = (CheckBoxPreference) findPreference(getString(R.string.pref_send_gps_key));
    send_gps.setChecked(PrefsUtils.ins().isSendGPS());

    ListPreference gps_time = (ListPreference) findPreference(getString(R.string.pref_gps_time_key));
    gps_time.setSummary(String.valueOf(PrefsUtils.ins().getTimeIntervalSendGPS()));
    gps_time.setValue(String.valueOf(PrefsUtils.ins().getTimeIntervalSendGPS()));
    gps_time.setVisible(PrefsUtils.ins().isSendGPS());
}

private void setGpsPreferencesListener() {

    findPreference(getString(R.string.pref_ip_db_key)).setOnPreferenceChangeListener((preference, newValue) -> {
        if (!PrefsUtils.ins().getIpGps().equals(newValue.toString())) {
            PrefsUtils.ins().setIpGps(newValue.toString());
            preference.setSummary(newValue.toString());
            if (PrefsUtils.ins().isSendGPS()) {
                if (getActivity().getPackageManager().checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, getActivity().getPackageName()) == PackageManager.PERMISSION_GRANTED) {
                    GPSService.stopGPSService(getContext());
                    GPSService.startGPSService(getContext());
                } else {
                    MainActivity.instance().checkAndRequestAccessFineLocationPermission();
                }
            }
        }
        return true;
    });

    findPreference(getString(R.string.pref_send_gps_key)).setOnPreferenceChangeListener((preference, newValue) -> {
        boolean enabled = (Boolean) newValue;

        if (enabled) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                PrefsUtils.ins().setSendGPS(true);
                GPSService.startGPSService(getContext());
                findPreference(getString(R.string.pref_gps_time_key)).setVisible(enabled);
                findPreference(getString(R.string.pref_ip_db_key)).setVisible(enabled);
            } else {
                MainActivity.instance().checkAndRequestAccessFineLocationPermission();
            }
            ManagerLiveData.ins().setGpsEvent(ManagerLiveData.GpsEvent.LOCATION_NOT_FIXED);
        } else {
            GPSService.stopGPSService(mContext);
            findPreference(getString(R.string.pref_gps_time_key)).setVisible(enabled);
            findPreference(getString(R.string.pref_ip_db_key)).setVisible(enabled);
            PrefsUtils.ins().setSendGPS(false);
            ManagerLiveData.ins().setGpsEvent(ManagerLiveData.GpsEvent.NOT_USE);
        }

        return true;
    });

    findPreference(getString(R.string.pref_gps_time_key)).setOnPreferenceChangeListener((preference, newValue) -> {
        int time = Integer.parseInt(newValue.toString());
        PrefsUtils.ins().setTimeIntervalSendGPS(time);
        preference.setSummary(newValue.toString());
        if (PrefsUtils.ins().isSendGPS()) {
            if (getActivity().getPackageManager().checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, getActivity().getPackageName()) == PackageManager.PERMISSION_GRANTED) {
                GPSService.stopGPSService(getContext());
                GPSService.startGPSService(getContext());
            } else {
                MainActivity.instance().checkAndRequestAccessFineLocationPermission();
            }
        }
        return true;
    });
}

public void startEchoTester() {
    Preference preference = findPreference(getString(R.string.pref_echo_tester_key));
    try {
        if (LinphoneManager.getInstance().startEchoTester() > 0) {
            preference.setSummary("Is running");
        }
    } catch (LinphoneCoreException e) {
        e.printStackTrace();
    }
}

public void stopEchoTester() {
    Preference preference = findPreference(getString(R.string.pref_echo_tester_key));
    try {
        if (LinphoneManager.getInstance().stopEchoTester() > 0) {
            preference.setSummary("Is stopped");
        }
    } catch (LinphoneCoreException e) {
        e.printStackTrace();
    }
}

public void startEchoCancellerCalibration() {
    try {
        if (LinphoneManager.getInstance().getEchoTesterStatus())
            stopEchoTester();
        LinphoneManager.getInstance().startEcCalibration(mListener);
    } catch (LinphoneCoreException e) {
        Log.e(e);
    }
}

@Override
public void onDestroyView() {
    if (PrefsUtils.ins().getRegimeSelected() == REGIME_P && mRootKey.equals(getString(R.string.pref_vpn_info_key))) {
        ManagerLiveData.ins().getSkziTimeError().removeObservers(getViewLifecycleOwner());
    }
    super.onDestroyView();
}

}
