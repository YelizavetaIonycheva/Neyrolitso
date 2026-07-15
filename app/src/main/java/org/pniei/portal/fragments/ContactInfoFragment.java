package org.pniei.portal.fragments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.linphone.LinphoneManager;

import java.io.IOException;
import java.util.ArrayList;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import org.linphone.core.LinphoneCore;
import org.pniei.portal.R;
import org.pniei.portal.listener.OnBackClickListener;
import org.pniei.portal.listener.SpoContactListener;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.activities.SecondaryActivity;
import org.pniei.portal.database.SpoChatRoom;
import org.pniei.portal.listener.SpoListenerManager;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.databinding.ContactInfoFragmentBinding;
import org.pniei.portal.services.SpoMessagesService;
import  org.pniei.portal.vpn.VpnConnection;

public class ContactInfoFragment extends Fragment implements SpoContactListener, OnBackClickListener  {
    private static final String ARG_CONTACT_ID = "id_contact";
    private static Context mContext;
    private SpoContact mContact;
    private ContactInfoFragmentBinding mBinding;
    private long mIdContact;

    public static ContactInfoFragment newInstance(Context context, long idContact) {
        mContext = context;
        Bundle args = new Bundle();
        args.putLong(ARG_CONTACT_ID, idContact);

        ContactInfoFragment fragment = new ContactInfoFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIdContact = getArguments().getLong(ARG_CONTACT_ID);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContact = DBUtils.getContact(mIdContact);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.contact_info_fragment, container, false);

        mBinding.contactFullName.setText(mContact.getFullName());
        mBinding.contactSipNumber.setText(mContact.getSipNumber());

        mBinding.btnEdit.setOnClickListener(v -> {
            if (mContact != null) {
                Fragment fragment = ContactChangeFragment.newInstance(mContext, false, mContact.getId());
                ((SecondaryActivity)getActivity()).displayFragment(fragment, true);
            }
        });

        mBinding.btnClose.setOnClickListener(v -> {
            backOrClose();
        });

        mBinding.btnDelete.setOnClickListener(v -> {
            // открыть диалоговое окно
            new MaterialAlertDialogBuilder(mContext)
                    .setTitle(R.string.delete_contact_title)
                    //.setMessage(R.string.delete_contact_title)
                    .setNegativeButton("Отмена", (dialog, which) -> {

                    })
                    .setPositiveButton("Удалить", (dialog, which) -> {
                        mBinding.loadingWindow.setVisibility(View.VISIBLE);
                        Intent intent = new Intent(mContext, SpoMessagesService.class);
                        intent.setAction(SpoMessagesService.ACTION_DELETE_CONTACT);
                        intent.putExtra(SpoMessagesService.CONTACT_KEY, mContact);
                        SpoListenerManager.addListener(this);
                        mContext.startService(intent);
                    })
                    .show();
        });

        mBinding.btnMsg.setOnClickListener(v -> {
            // Открытие чата с выбранным пользователем или создание нового
            SpoChatRoom chatRoom = DBUtils.getChatRoomForIdUser(mContact.getIdUser());
            //SpoChatRoom chatRoom = mManagerQueryDB.getChatRoomForIdUser(mContact.getId().toString()); // DEBUG

            if (chatRoom == null) {
                chatRoom = new SpoChatRoom();
                ArrayList<String> id = new ArrayList<>();
                id.add(mContact.getIdUser());
                chatRoom.setIdUsers(id);
                chatRoom.setNameChat(mContact.getFullName());
                chatRoom.setType(SpoChatRoom.ONE);
                chatRoom.setId(DBUtils.saveChatRoom(chatRoom));
            }

            Fragment fragment = ChatFragment.newInstance(mContext, DBUtils.getChatRoomForIdUser(mContact.getIdUser()).getId());
            ((SecondaryActivity)getActivity()).displayFragment(fragment, false);
        });

        mBinding.btnCall.setOnClickListener(v -> {
            if(LinphoneCore.RegistrationState.flagRegistrationOk == true) {
                LinphoneManager.getInstance().newOutgoingCall(mContact.getSipNumber(), "");
            }else{
                Toast.makeText(getActivity(),"Нет соединения с СКЗИ",Toast.LENGTH_SHORT).show();
            }
        });

        if (mContact.getUriPhoto() != null) {
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

        return mBinding.getRoot();
    }

    private void backOrClose() {
        if(getActivity().getSupportFragmentManager().getBackStackEntryCount() > 0)
            getActivity().getSupportFragmentManager().popBackStack();
        else
            getActivity().finish();
    }

    @Override
    public void onSpoContactSync(boolean result, String msg, ArrayList<SpoContact> addContacts, ArrayList<SpoContact> changedContacts) {

    }

    @Override
    public void onSpoContactChanged(boolean result, String msg) {

    }

    @Override
    public void onSpoContactAdd(boolean result, String msg) { }

    @Override
    public void onSpoContactDelete(boolean result, String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                SpoListenerManager.removeListener(this);
                if (result) {
                    backOrClose();
                } else {
                    mBinding.loadingWindow.setVisibility(View.GONE);
                    Toast.makeText(mContext, "Контакт не удален. Error: " + msg, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public boolean allowBackPressed() {
        return mBinding.loadingWindow.getVisibility() == View.GONE;
    }

}
