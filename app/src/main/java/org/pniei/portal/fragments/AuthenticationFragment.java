package org.pniei.portal.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputLayout;

import org.pniei.portal.R;
import org.pniei.portal.activities.LoginActivity;
import org.pniei.portal.databinding.AuthenticationFragmentBinding;
import org.pniei.portal.utils.CryptUtils;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;

import java.util.Objects;

public class AuthenticationFragment extends Fragment {
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;
    private AuthenticationFragmentBinding mBinding;
    private Handler mHandler;

    public static AuthenticationFragment newInstance(Context context) {
        mContext = context;
        return new AuthenticationFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.authentication_fragment, container, false);
        mHandler = new Handler(Looper.getMainLooper());

        mBinding.btnEnter.setOnClickListener(v -> {
            String enterPass = Objects.requireNonNull(mBinding.password.getText()).toString();
            String hashEnterPass = Utils.byteArrayToHexString(CryptUtils.getHash(enterPass.getBytes()));
            checkPass(hashEnterPass);
        });

        mBinding.password.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                mBinding.passwordLayout.setError(null);
            }
        });

        mBinding.password.setOnKeyListener((v, i, keyEvent) -> {
            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                switch (i) {
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER: {
                        String enterPass = Objects.requireNonNull(mBinding.password.getText()).toString();
                        String hashEnterPass = Utils.byteArrayToHexString(CryptUtils.getHash(enterPass.getBytes()));
                        checkPass(hashEnterPass);
                        return true;
                    }
                    default:
                        break;
                }
            }
            return false;
        });

        // Биометрия удалена
        mBinding.btnBiometry.setVisibility(View.GONE);

        return mBinding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        mBinding.password.setText("");
        PrefsUtils.ins().setAuth(false);
    }

    private void checkPass(String passHash) {
        mBinding.password.setEnabled(false);
        mBinding.btnEnter.setEnabled(false);
        mBinding.passwordLayout.setError(null);
        mBinding.progressPar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            if (!(passHash.equals(Utils.byteArrayToHexString(PrefsUtils.ins().getHashPass())))) {
                showError(mContext.getString(R.string.pass_error), mBinding.passwordLayout);
                return;
            }

            if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_NONE) {
                mHandler.post(() -> ((LoginActivity) requireActivity()).displayFragment(ModeSelectFragment.newInstance(mContext), true));
            } else {
                ((LoginActivity) requireActivity()).loginOk();
            }
        }).start();
    }

    private void showError(final String error, View view) {
        mHandler.post(() -> {
            if (view instanceof TextInputLayout) {
                ((TextInputLayout) view).setError(error);
            }
            mBinding.password.setEnabled(true);
            mBinding.btnEnter.setEnabled(true);
            mBinding.progressPar.setVisibility(View.GONE);
        });
    }
}