package org.pniei.portal.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.LinphoneManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableField;
import androidx.fragment.app.Fragment;

import org.linphone.StatusFragment;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.pniei.portal.R;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.databinding.DialerFragmentBinding;
import org.pniei.portal.vpn.VpnConnection;

public class DialerFragment extends Fragment {
    private static DialerFragment instance;
    private CallNumber callNumber;
    private DialerFragmentBinding mBinding;

    public class CallNumber {
        private ObservableField<String> number = new ObservableField<>();

        public CallNumber() {
            number.set("");
        }

        public void addDigit(String digit) {
            if(number.get().length() < 10)
                number.set(number.get()+digit);
        }

        public void eraseDigit() {
            if (number.get().length() > 0)
                number.set(number.get().substring(0, number.get().length()-1));
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
        DialerFragment fragment = new DialerFragment();
        return fragment;
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
            if(mBinding.inputNumber.getText().length() > 0) {
                callNumber.number.set("");
                if(RegistrationState.flagRegistrationOk == true) {
                    LinphoneManager.getInstance().newOutgoingCall(mBinding.inputNumber.getText().toString(), "");
                }else{
                    Toast.makeText(getActivity(),"Нет соединения с СКЗИ",Toast.LENGTH_SHORT).show();
                }
            }
        });

        instance = this;

        return mBinding.getRoot();
    }
}
