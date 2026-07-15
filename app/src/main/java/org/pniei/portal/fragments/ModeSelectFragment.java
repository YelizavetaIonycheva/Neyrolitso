package org.pniei.portal.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import org.json.JSONObject;
import org.pniei.portal.R;
import org.pniei.portal.activities.LoginActivity;
import org.pniei.portal.databinding.ModeSelectFragmentBinding;
import org.pniei.portal.utils.ConfigFileUtils;
import org.pniei.portal.utils.CryptUtils;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;

public class ModeSelectFragment extends Fragment {
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;

    public static ModeSelectFragment newInstance(Context context) {
        mContext = context;
        return new ModeSelectFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ModeSelectFragmentBinding mBinding = DataBindingUtil.inflate(inflater, R.layout.mode_select_fragment, container, false);

        mBinding.btnRegimeP.setOnClickListener(view -> {
            if (PrefsUtils.ins().isCfgEnterP()) {
                PrefsUtils.ins().setRegimeSelected(PrefsUtils.REGIME_P);
                ((LoginActivity) requireActivity()).loginOk();
                return;
            }
            loadConfig(PrefsUtils.REGIME_P);
        });

        mBinding.btnRegimeTT.setOnClickListener(view -> {
            if (PrefsUtils.ins().isCfgEnterTT()) {
                PrefsUtils.ins().setRegimeSelected(PrefsUtils.REGIME_TT);
                ((LoginActivity) requireActivity()).loginOk();
                return;
            }
            loadConfig(PrefsUtils.REGIME_TT);
        });

        return mBinding.getRoot();
    }

    private void loadConfig(int regime) {
        JSONObject config = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            config = ConfigFileUtils.loadConfigFromDownloads(mContext);
        }
        if (config == null) {
            Toast.makeText(mContext, "Не найден файл конфигурации в папке Загрузки. Пожалуйста, поместите файл spompXXX.cfg в папку Загрузки.", Toast.LENGTH_LONG).show();
            return;
        }

        if (parseConfig(config, regime)) {
            PrefsUtils.ins().setRegimeSelected(regime);
            ((LoginActivity) requireActivity()).loginOk();
        } else {
            Toast.makeText(mContext, "Ошибка обработки файла конфигурации.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean parseConfig(JSONObject jsonObject, int regime) {
        try {
            String id = jsonObject.getString(PrefsUtils.JsonKeysPrefs.ID);
            String signature = jsonObject.getString(PrefsUtils.JsonKeysPrefs.SIGNATURE);
            String phone = jsonObject.getString(PrefsUtils.JsonKeysPrefs.PHONE);
            String ipAts = jsonObject.getString(PrefsUtils.JsonKeysPrefs.IP_ATS);
            String ipDns = jsonObject.getString(PrefsUtils.JsonKeysPrefs.IP_DNS);
            String KS = jsonObject.getString("ks");

            if (regime == PrefsUtils.REGIME_P) {
                String ipSkzi = jsonObject.optString(PrefsUtils.JsonKeysPrefs.IP_SKZI, "");
                String ipMon = jsonObject.optString(PrefsUtils.JsonKeysPrefs.IP_MON, "");

                byte[] configs = (id + signature + phone + ipSkzi + ipMon + ipAts + ipDns).getBytes();
                int crc = CryptUtils.CRC32(configs, configs.length);
                if (!(KS.equalsIgnoreCase(Utils.intToHexString(crc)))) {
                    return false;
                }

                PrefsUtils.ins().setIdP(id);
                PrefsUtils.ins().setSignatureP(signature);
                PrefsUtils.ins().setPhoneP(phone);
                PrefsUtils.ins().setIpAtsP(ipAts + ":5070");
                PrefsUtils.ins().setIpDnsP(ipDns);
                PrefsUtils.ins().setCfgEnterP(true);
            } else if (regime == PrefsUtils.REGIME_TT) {
                byte[] configs = (id + signature + phone + ipAts + ipDns).getBytes();
                int crc = CryptUtils.CRC32(configs, configs.length);
                if (!(KS.equalsIgnoreCase(Utils.intToHexString(crc)))) {
                    return false;
                }

                PrefsUtils.ins().setIdTT(id);
                PrefsUtils.ins().setSignatureTT(signature);
                PrefsUtils.ins().setPhoneTT(phone);
                PrefsUtils.ins().setIpAtsTT(ipAts + ":5070");
                PrefsUtils.ins().setIpDnsTT(ipDns);
                PrefsUtils.ins().setCfgEnterTT(true);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}