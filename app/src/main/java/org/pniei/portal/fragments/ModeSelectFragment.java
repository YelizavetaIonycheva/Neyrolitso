package org.pniei.portal.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import org.pniei.portal.R;
import org.pniei.portal.activities.LoginActivity;
import org.pniei.portal.databinding.ModeSelectFragmentBinding;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.vpn.VpnClient;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

public class ModeSelectFragment extends Fragment {
    private static Context mContext;
    private ModeSelectFragmentBinding mBinding;


    public static ModeSelectFragment newInstance(Context context) {
        mContext = context;
        return new ModeSelectFragment();
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.mode_select_fragment, container, false);

        mBinding.btnRegimeP.setOnClickListener(view -> {
            if (PrefsUtils.ins().isKeyEnter() && PrefsUtils.ins().isCfgEnterP()) {
                PrefsUtils.ins().setRegimeSelected(PrefsUtils.REGIME_P);
                VpnClient.ins().loadAndRefreshKeys(mContext);
                ((LoginActivity) requireActivity()).loginOk();
                return;
            }
            ((LoginActivity) requireActivity()).displayFragment(EnterConfigFragment.newInstance(mContext, PrefsUtils.REGIME_P), true);
        });

        mBinding.btnRegimeTT.setOnClickListener(view -> {
             if (PrefsUtils.ins().isCfgEnterTT()) {
                PrefsUtils.ins().setRegimeSelected(PrefsUtils.REGIME_TT);
                ((LoginActivity) requireActivity()).loginOk();
                return;
            }
            ((LoginActivity) requireActivity()).displayFragment(EnterConfigFragment.newInstance(mContext, PrefsUtils.REGIME_TT), true);
        });

        return mBinding.getRoot();
    }
}
