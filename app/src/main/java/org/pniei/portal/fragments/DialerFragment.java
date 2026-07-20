package org.pniei.portal.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableField;
import androidx.fragment.app.Fragment;

import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.pniei.portal.R;
import org.pniei.portal.databinding.DialerFragmentBinding;

public class DialerFragment extends Fragment {
    private CallNumber callNumber;
    private DialerFragmentBinding mBinding;

    public static class CallNumber {
        private static final int MAX_DIGITS = 10;
        private final ObservableField<String> number = new ObservableField<>();

        public CallNumber() {
            number.set("");
        }

        public void addDigit(String digit) {
            if (digit == null || digit.isEmpty()) {
                return;
            }

            String current = number.get();
            if (current != null && current.length() < MAX_DIGITS) {
                number.set(current + digit);
            }
        }

        public void eraseDigit() {
            String current = number.get();
            if (current != null && !current.isEmpty()) {
                number.set(current.substring(0, current.length() - 1));
            }
        }

        public boolean erase() {
            number.set("");
            return true;
        }

        public ObservableField<String> getNumber() {
            return number;
        }

    }

    public static DialerFragment newInstance() {
        return new DialerFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        callNumber = new CallNumber();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.dialer_fragment, container, false);
        mBinding.setCallnumber(callNumber);

        mBinding.btnAction.setOnClickListener(v -> {
            if (mBinding.inputNumber.getText().length() > 0) {
                callNumber.number.set("");
                if (RegistrationState.flagRegistrationOk) {
                    LinphoneManager.getInstance().newOutgoingCall(mBinding.inputNumber.getText().toString(), "");
                } else {
                    Toast.makeText(getActivity(), "Нет соединения с СКЗИ", Toast.LENGTH_SHORT).show();
                }
            }
        });

        DialerFragment instance = this;

        return mBinding.getRoot();
    }
}
