package org.linphone.settingsfragments;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreListenerBase;
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
import org.pniei.portal.utils.CryptUtils;
import org.pniei.portal.utils.Logger;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import android.activity.result.ActivityResultLauncher;
import android.activity.result.contract.ActivityResultContracts;
import android.appcompat.app.AlertDialog;
import android.core.content.ContextCompat;
import android.documentfile.provider.DocumentFile;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragmentCompat;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreferenceCompat;

import static org.pniei.portal.utils.PrefsUtils.ExportImportPrefs;
import static org.pniei.portal.utils.PrefsUtils.REGIME_P;

public class AllSettingsScreenFragment extends PreferenceFragmentCompat {
    private LinphonePreferences mPrefs;
    private PreferenceScreen mPreferencesScreen;
    private ActivityResultLauncher<Intent> biometryResultLauncher;
    private ActivityResultLauncher<Intent> selectDirResultLauncher;
    private ActivityResultLauncher<Intent> selectFileResultLauncher;
    private static Context mContext;
    private DocumentFile pickedDir = null;
    private DocumentFile pickedFile = null;
    private TextView selectedDir = null;
    private Handler mHandler;
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
        mPreferenceScreen = findPreference(rootKey);
        mHandler = new Handler(Looper.getMainLooper());
        mRootKey = rootKey;

        if(rootKey.equals(getString(R.string.pref_media_key))) {
            initMediaSettings();
            setMediaPreferencesListener();
        } else if(rootKey.equals(getString(R.string.pref_ip_address_key))) {
            initIpAddressSettings();
            setIpAddressPreferencesListener();
        } else if(rootKey.equals(getString(R.string.pref_biometry_settings_key))) {
            registerForBiometryResult();
            initBiometrySettings();
        } else if(rootKey.equals(getString(R.string.pref_advanced_key))) {
            registerForSelectDirResult();
            registerForSelectFileResult();
            initAdvancedSettings();
            setAdvancedPreferencesListener();
        }

        if(mPreferenceScreen != null)
            getPreferenceManager().setPreferences(mPreferenceScreen);
    }

    @Override
    public void onNavigateToScreen(PreferenceScreen preferenceScreen){
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
        EditTextPreference domain = findPreference(getString(R.string.pref_domain_name_key));
        domain.setSummary(PrefsUtils.ins().getServerDomainName());
        domain.setText(PrefsUtils.ins().getServerDomainName());

        if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
            EditTextPreference ipSkzi = findPreference(getString(R.string.pref_ip_skzi_key));
            ipSkzi.setVisible(false);

            EditTextPreference ipMon = findPreference(getString(R.string.pref_ip_mon_key));
            ipMon.setVisible(false);

            EditTextPreference ipAts = findPreference(getString(R.string.pref_ip_ats_key));
            ipAts.setSummary(PrefsUtils.ins().getIpAtsTT());
            ipAts.setText(PrefsUtils.ins().getIpAtsTT());

            EditTextPreference ipDns = findPreference(getString(R.string.pref_ip_dns_key));
            ipDns.setSummary(PrefsUtils.ins().getIpDnsTT());
            ipDns.setText(PrefsUtils.ins().getIpDnsTT());

            EditTextPreference ipDnsSecond = findPreference(getString(R.string.pref_ip_dns_second_key));
            ipDnsSecond.setVisible(false);
        } else {
            EditTextPreference ipAts = findPreference(getString(R.string.pref_ip_ats_key));
            ipAts.setSummary(PrefsUtils.ins().getIpAtsP());
            ipAts.setText(PrefsUtils.ins().getIpAtsP());

            EditTextPreference ipDns = findPreference(getString(R.string.pref_ip_dns_key));
            ipDns.setSummary(PrefsUtils.ins().getIpDnsP());
            ipDns.setText(PrefsUtils.ins().getIpDnsP());
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

        findPreference(getString(R.string.pref_ip_ats_key)).setOnPreferenceChangeListener((preference, newValue) -> {
            if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
                if(!PrefsUtils.ins().getIpAtsTT().equals(newValue.toString())) {
                    PrefsUtils.ins().setIpAtsTT(newValue.toString());
                    preference.setSummary(newValue.toString());
                    mPrefs.setAccountDomain(0, newValue.toString());
                    LinphoneManager.getLc().refreshRegisters();
                }
            } else {
                if(!PrefsUtils.ins().getIpAtsP().equals(newValue.toString())) {
                    PrefsUtils.ins().setIpAtsP(newValue.toString());
                    preference.setSummary(newValue.toString());
                    mPrefs.setAccountDomain(0, newValue.toString());
                    LinphoneManager.getLc().refreshRegisters();
                }
            }
            return true;
        });

        findPreference(getString(R.string.pref_ip_dns_key)).setOnPreferenceChangeListener((preference, newValue) -> {
            if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
                if(!PrefsUtils.ins().getIpDnsTT().equals(newValue.toString())) {
                    PrefsUtils.ins().setIpDnsTT(newValue.toString());
                    preference.setSummary(newValue.toString());
                }
            } else {
                if(!PrefsUtils.ins().getIpDnsP().equals(newValue.toString())) {
                    PrefsUtils.ins().setIpDnsP(newValue.toString());
                    preference.setSummary(newValue.toString());
                }
            }
            return true;
        });
    }

    private void initMediaSettings() {
        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void ecCalibrationStatus(LinphoneCore lc, final LinphoneCore.EcCalibratorStatus status, final int delayMs, Object data) {
                LinphoneManager.getInstance().routeAudioToReceiver();

                CheckBoxPreference echoCancellation = (CheckBoxPreference) findPreference(getString(R.string.pref_echo_cancellation_key));
                Preference echoCancellerCalibration = findPreference(getString(R.string.pref_echo_canceller_calibration_key));

                if (status == LinphoneCore.EcCalibratorStatus.DoneNoEcho) {
                    echoCancellerCalibration.setSummary(R.string.no_echo);
                    echoCancellation.setChecked(false);
                    LinphonePreferences.instance().setEchoCancellation(false);
                    ((AudioManager)getActivity().getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);
                    Log.i("Set audio mode on 'Normal'");
                } else if (status == LinphoneCore.EcCalibratorStatus.Done) {
                    echoCancellerCalibration.setSummary(String.format(getString(R.string.ec_calibrated), delayMs));
                    echoCancellation.setChecked(true);
                    LinphonePreferences.instance().setEchoCancellation(true);
                    ((AudioManager)getActivity().getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);
                    Log.i("Set audio mode on 'Normal'");
                } else if (status == LinphoneCore.EcCalibratorStatus.Failed) {
                    echoCancellerCalibration.setSummary(R.string.failed);
                    echoCancellation.setChecked(true);
                    LinphonePreferences.instance().setEchoCancellation(true);
                    ((AudioManager)getActivity().getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);
                    Log.i("Set audio mode on 'Normal'");
                }
            }
        };

        CheckBoxPreference echoCancellation = (CheckBoxPreference) findPreference(getString(R.string.pref_echo_cancellation_key));
        echoCancellation.setChecked(mPrefs.isEchoCancellationEnabled());

        if (mPrefs.isEchoCancellationEnabled()) {
            Preference echoCalibration = findPreference(getString(R.string.pref_echo_canceller_calibration_key));
            echoCalibration.setSummary(String.format(getString(R.string.ec_calibrated), mPrefs.getEchoCalibration()));
        }

        initializePreferredVideoSizePreferences((ListPreference) findPreference(getString(R.string.pref_preferred_video_size_key)));
        updateVideoPreferencesAccordingToPreset();

        ((CheckBoxPreference) findPreference(getString(R.string.pref_overlay_key))).setChecked(mPrefs.isOverlayEnabled());
    }

    private void updateVideoPreferencesAccordingToPreset() {
    }

    private void initializePreferredVideoSizePreferences(ListPreference pref) {
        List<CharSequence> entries = new ArrayList<CharSequence>();
        List<CharSequence> values = new ArrayList<CharSequence>();
        for (String name : LinphoneManager.getLc().getSupportedVideoSizes()) {
            entries.add(name);
            values.add(name);
        }

        setListPreferenceValues(pref, entries, values);

        String value = mPrefs.getPreferredVideoSize();
        pref.setSummary(value);
        pref.setValue(value);
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
            public boolean onPreferenceClick(Preference preference) {
                synchronized (AllSettingsScreenFragment.this) {
                    preference.setSummary(R.string.ec_calibrating);

                    int recordAudio = getActivity().getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getActivity().getPackageName());
                    if (recordAudio == PackageManager.PERMISSION_GRANTED) {
                        startEchoCancellerCalibration();
                    } else {
                        MainActivity.instance().checkAndRequestRecordAudioPermissionForEchoCanceller();
                    }
                }
                return true;
            }
        });

        findPreference(getString(R.string.pref_echo_tester_key)).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                synchronized (AllSettingsScreenFragment.this) {
                    int recordAudio = getActivity().getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getActivity().getPackageName());
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
            updateVideoPreferencesAccordingToPreset();
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
        // Настройки для VPN, GPS, мониторинга и SKZI удалены
        if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
            findPreference(getString(R.string.pref_export_logs_key)).setVisible(false);
        }
    }

    private void setAdvancedPreferencesListener() {
        findPreference(getString(R.string.pref_export_logs_key)).setOnPreferenceClickListener(preference -> {
            ((SecondaryActivity)getActivity()).displayFragment(LoggerFragment.newInstance(), true);
            return false;
        });

        findPreference(getString(R.string.pref_update_po_key)).setOnPreferenceClickListener(preference -> {
            ((SecondaryActivity)getActivity()).displayFragment(UpdateFragment.newInstance(mContext), true);
            return false;
        });

        findPreference(getString(R.string.pref_reset_spomp_key)).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(mContext)
                    .setTitle(getString(R.string.pref_reset_config_title))
                    .setMessage(getString(R.string.reset_config_info))
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Сбросить", (dialogInterface, i) -> {
                        PrefsUtils.ins().deleteConfig(getContext());
                        DBUtils.deleteDB(getContext());
                        Logger.inc().clear();
                        Utils.deleteRecursive(BiometryUtils.getBiometryDir(mContext));
                        ((SecondaryActivity)getActivity()).quit(false);
                    })
                    .show();
            return false;
        });

        findPreference(getString(R.string.pref_change_regime_key)).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(mContext)
                    .setTitle(getString(R.string.pref_change_regime_title))
                    .setMessage(getString(R.string.change_regime_info))
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Сменить", (dialog, which) -> {
                        Utils.showWaitDialog(mContext, getString(R.string.pref_change_regime_title));

                        new Thread(() -> {
                            mContext.stopService(new Intent(Intent.ACTION_MAIN).setClass(mContext, LinphoneService.class));
                            SpoMessagesService.stop(mContext);
                            PrefsUtils.ins().setAuth(false);
                            PrefsUtils.ins().setRegimeSelected(PrefsUtils.REGIME_NONE);
                            DBUtils.closeDB();

                            mHandler.post(() -> {
                                Utils.closeWaitDialog();
                                Intent intent = new Intent(mContext, LoginActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
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

                            JSONObject jsonObject = new JSONObject();
                            StringBuilder sb = new StringBuilder();

                            try {
                                if (PrefsUtils.ins().getRegimeSelected() == REGIME_P) {
                                    jsonObject.put(ExportImportPrefs.REGIME, ExportImportPrefs.REGIME_P);
                                    sb.append(ExportImportPrefs.REGIME_P);
                                    jsonObject.put(ExportImportPrefs.IP_ATS, PrefsUtils.ins().getIpAtsP());
                                    sb.append(PrefsUtils.ins().getIpAtsP());
                                    jsonObject.put(ExportImportPrefs.IP_DNS, PrefsUtils.ins().getIpDnsP());
                                    sb.append(PrefsUtils.ins().getIpDnsP());
                                } else {
                                    jsonObject.put(ExportImportPrefs.REGIME, ExportImportPrefs.REGIME_TT);
                                    sb.append(ExportImportPrefs.REGIME_TT);
                                    jsonObject.put(ExportImportPrefs.IP_ATS, PrefsUtils.ins().getIpAtsTT());
                                    sb.append(PrefsUtils.ins().getIpAtsTT());
                                    jsonObject.put(ExportImportPrefs.IP_DNS, PrefsUtils.ins().getIpDnsTT());
                                    sb.append(PrefsUtils.ins().getIpDnsTT());
                                }

                                byte [] configs = sb.toString().getBytes();
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
                                byte [] data = jsonObject.toString().getBytes(StandardCharsets.UTF_8);
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
                                Toast.makeText(mContext, getString(R.string.end_export_settings), Toast.LENGTH_SHORT).show();
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
                            byte [] data = null;
                            try (InputStream is = mContext.getContentResolver().openInputStream(pickedFile.getUri())) {
                                data = new byte[is.available()];
                                is.read(data);

                                JSONObject jsonObject = new JSONObject(new String(data));

                                int regime;
                                String ipAts, ipDns, prefCrc;
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
                                    ipDns = jsonObject.getString(ExportImportPrefs.IP_DNS);
                                    sb.append(ipDns);
                                } else if (regime == ExportImportPrefs.REGIME_TT) {
                                    ipDns = jsonObject.getString(ExportImportPrefs.IP_DNS);
                                    sb.append(ipDns);
                                } else {
                                    throw new Exception("Неизвестный режим в файле конфигурации");
                                }

                                prefCrc = jsonObject.getString(ExportImportPrefs.PREF_CRC);

                                byte [] configs = sb.toString().getBytes();
                                int crc = CryptUtils.CRC32(configs, configs.length);
                                if(!(prefCrc.equalsIgnoreCase(Utils.intToHexString(crc)))) {
                                    throw new Exception("Контрольная сумма не совпадает");
                                }

                                PrefsUtils prefs = PrefsUtils.ins();
                                if (regime == ExportImportPrefs.REGIME_P) {
                                    prefs.setIpAtsP(ipAts);
                                    prefs.setIpDnsP(ipDns);
                                } else {
                                    prefs.setIpAtsTT(ipAts);
                                    prefs.setIpDnsTT(ipDns);
                                }

                                LinphonePreferences sipPrefs = LinphonePreferences.instance();
                                sipPrefs.setAccountDomain(0, ipAts);

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
                        .setTitle("Удаление биометрии")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Да", (dialog, which) -> {
                            Utils.showWaitDialog(mContext, "Удаление биометрии");

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
                                    initBiometrySettings();
                                    Toast.makeText(mContext, "Биометрия удалена", Toast.LENGTH_SHORT).show();
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
                        .setTitle("Обновление биометрии")
                        .setMessage(R.string.info_update_biometry)
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Обновить", (dialog, which) -> {
                            ArrayList<byte[]> dataArray = new ArrayList<>();
                            dataArray.add(PrefsUtils.ins().getHashPass());
                            byte [] data = BiometryUtils.dataGeneration(dataArray);
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
                        .setTitle("Экспорт биометрии")
                        .setMessage(R.string.info_export_biometry)
                        .setView(R.layout.dialog_export_biometry)
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Экспорт", (dialog, which) -> {
                            if (pickedDir == null)
                                return;

                            CheckBox isAddImagesCheckBox = ((AlertDialog)dialog).findViewById(R.id.isAddImages);
                            boolean isAddImages = isAddImagesCheckBox.isChecked();

                            Utils.showWaitDialog(mContext, "Экспорт биометрии");

                            new Thread(() -> {
                                DocumentFile fileCont = pickedDir.createFile("text", "ImpulsBiom.bc");
                                File archive = BiometryUtils.exportContainer(mContext, isAddImages);
                                if (archive == null) {
                                    mHandler.post(() -> {
                                        Utils.closeWaitDialog();
                                        Toast.makeText(mContext, "Ошибка экспорта биометрии", Toast.LENGTH_SHORT).show();
                                    });
                                    return;
                                }

                                try (OutputStream os = mContext.getContentResolver().openOutputStream(fileCont.getUri())) {
                                    byte [] data;
                                    FileInputStream fis = new FileInputStream(archive);
                                    data = new byte [fis.available()];
                                    fis.read(data);
                                    data = CryptUtils.cryptData(data, PrefsUtils.ins().getHashPass());
                                    os.write(data);
                                    os.flush();
                                    archive.delete();
                                } catch (Exception ex) {
                                    archive.delete();
                                    mHandler.post(() -> {
                                        Utils.closeWaitDialog();
                                        Toast.makeText(mContext, "Ошибка экспорта биометрии", Toast.LENGTH_SHORT).show();
                                    });
                                    return;
                                }
                                mHandler.post(() -> {
                                    Utils.closeWaitDialog();
                                    Toast.makeText(mContext, "Биометрия экспортирована", Toast.LENGTH_SHORT).show();
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
                        .setTitle("Создание биометрии")
                        .setMessage(R.string.info_create_biometry)
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Создать", (dialog, which) -> {
                            ArrayList<byte[]> dataArray = new ArrayList<>();
                            dataArray.add(PrefsUtils.ins().getHashPass());
                            byte [] data = BiometryUtils.dataGeneration(dataArray);
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
                            EditText editText = ((AlertDialog)dialog).findViewById(R.id.pass);
                            if (editText == null)
                                return;

                            String passEnter = editText.getText().toString();
                            if (passEnter.length() == 0) {
                                Toast.makeText(mContext, "Введите пароль", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            if (pickedFile == null)
                                return;
                            Utils.showWaitDialog(mContext, "Импорт биометрии");

                            new Thread(() -> {
                                File temp = new File(BiometryUtils.getBiometryDir(mContext), "BiomContArchive");
                                try (InputStream is = mContext.getContentResolver().openInputStream(pickedFile.getUri())) {
                                    byte [] data = new byte[is.available()];
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
                                        Toast.makeText(mContext, "Ошибка импорта биометрии", Toast.LENGTH_SHORT).show();
                                    });
                                    return;
                                }

                                if (!BiometryUtils.importContainer(mContext, temp)) {
                                    temp.delete();
                                    mHandler.post(() -> {
                                        Utils.closeWaitDialog();
                                        Toast.makeText(mContext, "Ошибка импорта биометрии", Toast.LENGTH_SHORT).show();
                                    });
                                    return;
                                }

                                temp.delete();
                                BiometryPrefs.ins().setBiometryBind(true);
                                mHandler.post(() -> {
                                    Utils.closeWaitDialog();
                                    initBiometrySettings();
                                    Toast.makeText(mContext, "Биометрия импортирована", Toast.LENGTH_SHORT).show();
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
                                byte [] nbcc = resultData.getByteArrayExtra(BiometryActivity.NBCC);
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
        super.onDestroyView();
    }
}