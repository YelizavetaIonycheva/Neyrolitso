package org.pniei.portal.fragments;

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
import org.pniei.portal.utils.CryptUtils;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.vpn.VpnClient;
import java.io.File;
import java.io.FileOutputStream;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.databinding.DataBindingUtil;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

public class EnterConfigFragment extends Fragment {
    private ActivityResultLauncher<Intent> selectConfigResultLauncher;
    private static Context mContext;
    private Handler mHandler;
    private EnterConfigFragmentBinding mBinding;
    private DocumentFile selectedKeyFile = null;
    private DocumentFile selectedConfFile = null;
    private boolean checkFileConfig = false;

    private static int selectedRegime;

    public static EnterConfigFragment newInstance(Context context, int regime) {
        mContext = context;
        selectedRegime = regime;
        return new EnterConfigFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
                    byte [] bufConfig = Utils.readFileFromZip(mContext, selectedConfFile, ".cfgp");
                    if (bufConfig == null) {
                        showError(getString(R.string.err_file_cfg_not_found), mBinding.confFileLayout);
                        return;
                    }

                    if (!checkConfigFile(new String(bufConfig))) {
                        showError(getString(R.string.init_config_error), mBinding.confFileLayout);
                        return;
                    }
                //}

                //if (!PrefsUtils.ins().isKeyEnter()) {
                    byte [] keyData = Utils.readFileFromZip(mContext, selectedConfFile, ".key");

                    if (keyData == null) {
                        showError(getString(R.string.err_file_key_not_found), mBinding.confFileLayout);
                        return;
                    }

                    if (!saveVpnKey(keyData)) {
                        showError(getString(R.string.err_key_save), mBinding.confFileLayout);
                        return;
                    }
                    PrefsUtils.ins().setCfgEnterP(true);
                    PrefsUtils.ins().setKeyEnter(true);
               // }
            } else {
                //if (PrefsUtils.ins().isCfgEnterTT()) {
                    byte [] bufConfig = Utils.readFileFromZip(mContext, selectedConfFile, ".cfgt");
                    if (bufConfig == null) {
                        showError(getString(R.string.err_file_cfg_not_found), mBinding.confFileLayout);
                        return;
                    }

                    if (!checkConfigFile(new String(bufConfig))) {
                        showError(getString(R.string.init_config_error), mBinding.confFileLayout);
                        return;
                    }
                    PrefsUtils.ins().setCfgEnterTT(true);
               // }
            }

            PrefsUtils.ins().setRegimeSelected(selectedRegime);
            ((LoginActivity)getActivity()).loginOk();
        }).start();

    }

    private void showError(final String error, View view) {
        mHandler.post(() -> {
            mBinding.progressPar.setVisibility(View.INVISIBLE);
            if (view instanceof TextInputLayout) {
                ((TextInputLayout)view).setError(error);
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
                    StringBuilder sb = new StringBuilder();
                    sb.append(id)
                            .append(signature)
                            .append(phone)
                            .append(ipSkzi)
                            .append(ipMon)
                            .append(ipAts)
                            .append(ipDns);
                    byte [] configs = sb.toString().getBytes();
                    int crc = CryptUtils.CRC32(configs, configs.length);
                    if(!(KS.equalsIgnoreCase(Utils.intToHexString(crc)))) {
                        return false;
                    }

                    PrefsUtils.ins().setIdP(id);
                    PrefsUtils.ins().setSignatureP(signature);
                    PrefsUtils.ins().setPhoneP(phone);
                    PrefsUtils.ins().setIpSkzi(ipSkzi);
                    PrefsUtils.ins().setIpMon(ipMon);
                    PrefsUtils.ins().setIpAtsP(ipAts + ":5070");
                    PrefsUtils.ins().setIpDnsP(ipDns);
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(id)
                            .append(signature)
                            .append(phone)
                            .append(ipAts)
                            .append(ipDns);
                    byte [] configs = sb.toString().getBytes();
                    int crc = CryptUtils.CRC32(configs, configs.length);
                    if(!(KS.equalsIgnoreCase(Utils.intToHexString(crc)))) {
                        return false;
                    }
                    PrefsUtils.ins().setIdTT(id);
                    PrefsUtils.ins().setSignatureTT(signature);
                    PrefsUtils.ins().setPhoneTT(phone);
                    PrefsUtils.ins().setIpAtsTT(ipAts + ":5070");
                    PrefsUtils.ins().setIpDnsTT(ipDns);
                }

                return true;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private boolean saveVpnKey(byte [] keyData) {
        byte [] cryptKeyData;

        cryptKeyData = CryptUtils.cryptData(keyData, PrefsUtils.ins().getHashPass());
        if(cryptKeyData == null)
            return true;

        try {
            File fileKey = VpnClient.getFileKey(mContext);
            FileOutputStream fout = new FileOutputStream(fileKey);
            fout.write(cryptKeyData);
            fout.flush();
            fout.close();
            return VpnClient.ins().loadAndRefreshKeys(mContext);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void registerForSelectFileResult() {
        selectConfigResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent resultData = result.getData();
                        if (resultData != null) {
                            Uri fileUri = resultData.getData();
                            selectedConfFile = DocumentFile.fromSingleUri(mContext, fileUri);
                            if (selectedConfFile != null) {
                                mBinding.confFile.setText(selectedConfFile.getName());
                                mBinding.confFileLayout.setError(null);
                            }
                        }
                    }
                });
    }
}
