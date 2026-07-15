package org.pniei.portal.fragments;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.LinphoneManager;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import org.linphone.core.LinphoneCore;
import org.pniei.portal.R;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.activities.SecondaryActivity;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.databinding.HistoryInfoFragmentBinding;
import org.pniei.portal.database.SpoChatRoom;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.vpn.VpnConnection;

public class HistoryDetailFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "HistoryDetailFragment";
    private static final String ARG_NUMBER = "number";
    private static final String ARG_STATUS = "status";
    private static final String ARG_TIME = "time";
    private static final String ARG_DATE = "date";

    private String number, status, callDate, callTime;
    private HistoryInfoFragmentBinding mBinding;
    private SpoContact mContact;

    public static HistoryDetailFragment newInstance(String number, String status, String callDate, String callTime) {
        Bundle args = new Bundle();
        args.putString(ARG_NUMBER, number);
        args.putString(ARG_STATUS, status);
        args.putString(ARG_DATE, callDate);
        args.putString(ARG_TIME, callTime);

        HistoryDetailFragment fragment = new HistoryDetailFragment();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        number = getArguments().getString(ARG_NUMBER);
        status = getArguments().getString(ARG_STATUS);
        callDate = getArguments().getString(ARG_TIME);
        callTime = getArguments().getString(ARG_DATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.history_info_fragment, container, false);

        mContact = DBUtils.getContactForNumber(number);

        if (mContact == null) {
            mBinding.contactFullName.setText(number);
            mBinding.contactSipNumber.setVisibility(View.GONE);
            mBinding.btnAddContact.setOnClickListener(this);
            mBinding.btnMsg.setEnabled(false);
        } else {
            mBinding.contactFullName.setText(mContact.getFullName());
            mBinding.contactSipNumber.setText(mContact.getSipNumber());
            mBinding.btnAddContact.setVisibility(View.GONE);
            mBinding.btnMsg.setOnClickListener(this);
        }

        if (mContact != null && mContact.getUriPhoto() != null) {
            Bitmap bm = null;
            try {
                bm = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), Uri.parse(mContact.getUriPhoto()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (bm != null) {
                mBinding.contactPicture.setImageBitmap(Utils.getCroppedBitmap(bm, 256, 256, 256));
            } else {
                mBinding.contactPicture.setImageResource(R.drawable.img_contact_def);
            }

        } else {
            mBinding.contactPicture.setImageResource(R.drawable.img_contact_def);
        }

        if (status.equals(getResources().getString(R.string.missed))) {
            mBinding.directionCall.setText(getString(R.string.miss_call));
        } else if (status.equals(getResources().getString(R.string.incoming))) {
            mBinding.directionCall.setText(getString(R.string.in_call));
        } else if (status.equals(getResources().getString(R.string.outgoing))) {
            mBinding.directionCall.setText(getString(R.string.out_call));
        }

        mBinding.date.setText(new SimpleDateFormat("HH:mm  dd.MM.yyyy").format(Long.parseLong(callDate)));

        mBinding.durationCall.setText(callTime);

        mBinding.btnClose.setOnClickListener(this);
        mBinding.btnVideoCall.setOnClickListener(this);
        mBinding.btnCall.setOnClickListener(this);

        return mBinding.getRoot();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.btnClose) {
            backOrClose();
        } else if (id == R.id.btnMsg) {
            // Открытие чата с выбранным пользователем или создание нового
            SpoChatRoom chatRoom = DBUtils.getChatRoomForIdUser(mContact.getIdUser());

            if (chatRoom == null) {
                chatRoom = new SpoChatRoom();
                ArrayList<String> id_user = new ArrayList<>();
                id_user.add(mContact.getIdUser());
                chatRoom.setIdUsers(id_user);
                chatRoom.setNameChat(mContact.getFullName());
                chatRoom.setType(SpoChatRoom.ONE);
                chatRoom.setId(DBUtils.saveChatRoom(chatRoom));
            }

            Fragment fragment = ChatFragment.newInstance(getContext(), DBUtils.getChatRoomForIdUser(mContact.getIdUser()).getId());
            ((SecondaryActivity)getActivity()).displayFragment(fragment, true);
        } else if (id == R.id.btnCall) {
            if(LinphoneCore.RegistrationState.flagRegistrationOk == true) {
                if (mContact != null) {
                    LinphoneManager.getInstance().newOutgoingCall(mContact.getSipNumber(), "");

                } else {
                    LinphoneManager.getInstance().newOutgoingCall(number, "");
                }
            }else{
                Toast.makeText(getActivity(),"Нет соединения с СКЗИ",Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.btnAddContact) {
            Bundle args = new Bundle();
            args.putString(ContactChangeFragment.ARG_NUMBER, number);
            Fragment fragment = ContactChangeFragment.newInstance(getContext(), true, 0);
            fragment.setArguments(args);
            ((SecondaryActivity)getActivity()).displayFragment(fragment, false);
        }
    }




    private void backOrClose() {
        if(getActivity().getSupportFragmentManager().getBackStackEntryCount() > 0)
            getActivity().getSupportFragmentManager().popBackStack();
        else
            getActivity().finish();
    }
}
