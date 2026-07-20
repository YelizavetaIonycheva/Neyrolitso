package org.pniei.portal.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;
import org.pniei.portal.R;
import org.pniei.portal.activities.LoginActivity;
import org.pniei.portal.databinding.EnterConfigFragmentBinding;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

public class EnterConfigFragment extends Fragment {
    private ActivityResultLauncher<Intent> selectConfigResultLauncher;
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;
    private Handler mHandler;
    private EnterConfigFragmentBinding mBinding;
    private DocumentFile selectedConfFile = null;
    private final boolean checkFileConfig = false;

    private static int selectedRegime;

    public static EnterConfigFragment newInstance(Context context, int regime) {
        mContext = context;
        selectedRegime = regime;
        return new EnterConfigFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.enter_config_fragment, container, false);
        mHandler = new Handler(Looper.getMainLooper());

        mBinding.confFile.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            selectConfigResultLauncher.launch(intent);
        });

        mBinding.btnEnter.setOnClickListener(view -> {
            mBinding.progressPar.setVisibility(View.VISIBLE);
            checkConfig();
        });
        registerForSelectFileResult();
        return mBinding.getRoot();
    }

    private void checkConfig() {
        new Thread(() -> {
            if (checkFileConfig && selectedConfFile == null) {
                showError(getString(R.string.err_file_not_select), mBinding.confFileLayout);
                return;
            }

            if (selectedRegime == PrefsUtils.REGIME_P) {
                //if (!PrefsUtils.ins().isCfgEnterP()) {
                byte[] bufConfig = Utils.readFileFromZip(mContext, selectedConfFile, ".cfgp");
                if (bufConfig == null) {
                    showError(getString(R.string.err_file_cfg_not_found), mBinding.confFileLayout);
                    return;
                }

                if (checkConfigFile(new String(bufConfig))) {
                    showError(getString(R.string.init_config_error), mBinding.confFileLayout);
                    return;
                }
                //}

                //if (!PrefsUtils.ins().isKeyEnter()) {
                byte[] keyData = Utils.readFileFromZip(mContext, selectedConfFile, ".key");

                if (keyData == null) {
                    showError(getString(R.string.err_file_key_not_found), mBinding.confFileLayout);
                    return;
                }

                PrefsUtils.ins().setCfgEnterP(true);
                PrefsUtils.ins().setKeyEnter(true);
                // }
            } else {
                //if (PrefsUtils.ins().isCfgEnterTT()) {
                byte[] bufConfig = Utils.readFileFromZip(mContext, selectedConfFile, ".cfgt");
                if (bufConfig == null) {
                    showError(getString(R.string.err_file_cfg_not_found), mBinding.confFileLayout);
                    return;
                }

                if (checkConfigFile(new String(bufConfig))) {
                    showError(getString(R.string.init_config_error), mBinding.confFileLayout);
                    return;
                }
                PrefsUtils.ins().setCfgEnterTT(true);
                // }
            }

            PrefsUtils.ins().setRegimeSelected(selectedRegime);
            ((LoginActivity) requireActivity()).loginOk();
        }).start();

    }

    private void showError(final String error, View view) {
        mHandler.post(() -> {
            mBinding.progressPar.setVisibility(View.INVISIBLE);
            if (view instanceof TextInputLayout) {
                ((TextInputLayout) view).setError(error);
            }
        });
    }

    private boolean checkConfigFile(String strConfig) {
        if (strConfig != null) {
            try {
                JSONObject jsonObject = new JSONObject(strConfig);
                String id = jsonObject.getString(PrefsUtils.JsonKeysPrefs.ID);
                String signature = jsonObject.getString(PrefsUtils.JsonKeysPrefs.SIGNATURE);
                String phone = jsonObject.getString(PrefsUtils.JsonKeysPrefs.PHONE);
                String ipSkzi = jsonObject.has(PrefsUtils.JsonKeysPrefs.IP_SKZI) ? jsonObject.getString(PrefsUtils.JsonKeysPrefs.IP_SKZI) : "";
                String ipMon = jsonObject.has(PrefsUtils.JsonKeysPrefs.IP_MON) ? jsonObject.getString(PrefsUtils.JsonKeysPrefs.IP_MON) : "";
                String ipAts = jsonObject.getString(PrefsUtils.JsonKeysPrefs.IP_ATS);
                String ipDns = jsonObject.getString(PrefsUtils.JsonKeysPrefs.IP_DNS);
                String KS = jsonObject.getString("ks");

                if (selectedRegime == PrefsUtils.REGIME_P) {
                    String sb = id +
                            signature +
                            phone +
                            ipSkzi +
                            ipMon +
                            ipAts +
                            ipDns;
                    byte[] configs = sb.getBytes();
                    int crc = CryptUtils.CRC32(configs, configs.length);
                    if (!(KS.equalsIgnoreCase(Utils.intToHexString(crc)))) {
                        return true;
                    }

                    PrefsUtils.ins().setIdP(id);
                    PrefsUtils.ins().setSignatureP(signature);
                    PrefsUtils.ins().setPhoneP(phone);
                    PrefsUtils.ins().setIpSkzi(ipSkzi);
                    PrefsUtils.ins().setIpMon(ipMon);
                    PrefsUtils.ins().setIpAtsP(ipAts + ":5070");
                    PrefsUtils.ins().setIpDnsP(ipDns);
                } else {
                    String sb = id +
                            signature +
                            phone +
                            ipAts +
                            ipDns;
                    byte[] configs = sb.getBytes();
                    int crc = CryptUtils.CRC32(configs, configs.length);
                    if (!(KS.equalsIgnoreCase(Utils.intToHexString(crc)))) {
                        return true;
                    }
                    PrefsUtils.ins().setIdTT(id);
                    PrefsUtils.ins().setSignatureTT(signature);
                    PrefsUtils.ins().setPhoneTT(phone);
                    PrefsUtils.ins().setIpAtsTT(ipAts + ":5070");
                    PrefsUtils.ins().setIpDnsTT(ipDns);
                }

                return false;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public void registerForSelectFileResult() {
        selectConfigResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent resultData = result.getData();
                        if (resultData != null) {
                            Uri fileUri = resultData.getData();
                            assert fileUri != null;
                            selectedConfFile = DocumentFile.fromSingleUri(mContext, fileUri);
                            mBinding.confFile.setText(selectedConfFile.getName());
                            mBinding.confFileLayout.setError(null);
                        }
                    }
                });
    }
}
