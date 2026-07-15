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
import org.pniei.portal.databinding.RegistrationFragmentBinding;
import org.pniei.portal.utils.CryptUtils;
import org.pniei.portal.utils.PrefsUtils;

import java.util.Objects;

public class RegistrationFragment extends Fragment {
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;
    private RegistrationFragmentBinding mBinding;
    private Handler mHandler;

    public static RegistrationFragment newInstance(Context context) {
        mContext = context;
        return new RegistrationFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler(Looper.getMainLooper());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.registration_fragment, container, false);

        mBinding.repeatPassword.setOnKeyListener((v, i, keyEvent) -> {
            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                switch (i) {
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER: {
                        checkPass();
                        return true;
                    }
                    default:
                        break;
                }
            }
            return false;
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

        mBinding.repeatPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                mBinding.repeatPasswordLayout.setError(null);
            }
        });

        mBinding.btnEnter.setOnClickListener(v -> checkPass());

        return mBinding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        mBinding.password.setText("");
    }

    private void checkPass() {
        mBinding.password.setEnabled(false);
        mBinding.repeatPassword.setEnabled(false);
        mBinding.btnEnter.setEnabled(false);
        mBinding.passwordLayout.setError(null);
        mBinding.repeatPasswordLayout.setError(null);

        String pas1 = Objects.requireNonNull(mBinding.password.getText()).toString();
        String pas2 = Objects.requireNonNull(mBinding.repeatPassword.getText()).toString();

        if (pas1.isEmpty()) {
            showError(mContext.getString(R.string.err_empty_password), mBinding.passwordLayout);
            return;
        } else if (pas1.length() < 4) {
            showError(mContext.getString(R.string.err_lenght_password), mBinding.passwordLayout);
            return;
        } else if (pas1.length() != pas2.length() || !pas1.equals(pas2)) {
            showError(mContext.getString(R.string.err_not_match_password), mBinding.repeatPasswordLayout);
            return;
        }

        PrefsUtils.ins().setHashPass(CryptUtils.getHash(pas1.getBytes()));
        ((LoginActivity) requireActivity()).displayFragment(ModeSelectFragment.newInstance(mContext), false);
    }

    private void showError(final String error, View view) {
        mHandler.post(() -> {
            if (view instanceof TextInputLayout) {
                ((TextInputLayout) view).setError(error);
            }
            mBinding.password.setEnabled(true);
            mBinding.repeatPassword.setEnabled(true);
            mBinding.btnEnter.setEnabled(true);
        });
    }
}