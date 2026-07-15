package org.pniei.portal.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.pniei.portal.R;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.activities.SecondaryActivity;
import org.pniei.portal.database.SpoChatRoom;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.databinding.ContactSelectCellBinding;
import org.pniei.portal.databinding.ContactSelectFragmentBinding;

public class ContactSelectFragment extends Fragment {
    private static final String ARG_IS_PNONE_CONT = "is_phone_contacts";
    private ActivityResultLauncher<String> permissionReadContactsResult;
    private static Context mContext;
    private boolean isPhoneContacts;
    private ContactSelectFragmentBinding mBinding;
    private ContactListAdapter mAdapter;
    private PhoneContactListAdapter mPhoneAdapter;
    private ArrayList<String> listPhoneContactName = null;
    private Cursor phones;

    public static ContactSelectFragment newInstance(Context context, boolean value) {
        mContext = context;
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_PNONE_CONT, value);

        ContactSelectFragment fragment = new ContactSelectFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isPhoneContacts = getArguments().getBoolean(ARG_IS_PNONE_CONT);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.contact_select_fragment, container, false);

        mBinding.listContact.setLayoutManager(new LinearLayoutManager(mContext));
        mBinding.listContact.setHasFixedSize(true);

        mBinding.close.setOnClickListener(v -> {
            backOrClose();
        });

        registerForPermissionReadContacts();
        return mBinding.getRoot();
    }

    public void updateUI() {
        if (isPhoneContacts) {
            showPhoneContacts();
        } else {
            List<SpoContact> contacts = Arrays.asList(DBUtils.getContactList());
            if (contacts.size() > 0) {
                if (mAdapter == null) {
                    mAdapter = new ContactListAdapter(contacts);
                } else {
                    mAdapter.setContacts(contacts);
                }
                mBinding.listContact.setAdapter(mAdapter);
            }
        }
    }

    private void showPhoneContacts() {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestReadContactsPermission();
        } else {
            if (listPhoneContactName == null) {
                phones = mContext.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                        null, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
                listPhoneContactName = new ArrayList<>();

                List<SpoContact> contacts = Arrays.asList(DBUtils.getContactList());
                boolean flag;

                if (phones.getCount() > 0) {
                    phones.moveToFirst();
                    while (!phones.isAfterLast()) {
                        String nameCPhoneContact = phones.getString(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                        flag = false;
                        for (SpoContact contact: contacts) {
                            if (contact.getFullName().equals(nameCPhoneContact)) {
                                flag = true;
                                break;
                            }
                        }

                        if (!flag) {
                            listPhoneContactName.add(nameCPhoneContact);
                        }

                        phones.moveToNext();
                    }
                }
            }

            if (mPhoneAdapter == null) {
                mPhoneAdapter = new PhoneContactListAdapter(listPhoneContactName);
            } else {
                mPhoneAdapter.setContacts(listPhoneContactName);
            }
            mBinding.listContact.setAdapter(mPhoneAdapter);
        }
    }

    private class PhoneContactHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        private String mNameContact;
        private ContactSelectCellBinding mBinding;

        public PhoneContactHolder(View itemView) {
            super(itemView);
            mBinding = DataBindingUtil.bind(itemView);
            itemView.setOnClickListener(this);
        }

        public void bind(String nameContact) {
            mNameContact = nameContact;
            mBinding.contactFullName.setText(mNameContact);
        }

        @Override
        public void onClick(View v) {
            Fragment fragment;
            fragment = ContactChangeFragment.newInstance(mContext, true, 0);
            Bundle args = new Bundle();
            args.putString(ContactChangeFragment.ARG_FULL_NAME, mNameContact);
            fragment.setArguments(args);
            if (getActivity() != null)
                ((SecondaryActivity)getActivity()).displayFragment(fragment, false);
        }
    }

    private class PhoneContactListAdapter extends RecyclerView.Adapter<PhoneContactHolder> {
        List<String> mContactNamelist;

        public PhoneContactListAdapter(List<String> names) {
            mContactNamelist = names;
        }

        @NonNull
        @Override
        public PhoneContactHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_select_cell, parent, false);
            return new PhoneContactHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhoneContactHolder holder, int position) {
            holder.bind(mContactNamelist.get(position));
        }

        @Override
        public int getItemCount() {
            return mContactNamelist.size();
        }

        public void setContacts(List<String> names) {
            mContactNamelist = names;
        }

    }

    private class ContactHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        private SpoContact mContact;
        private ContactSelectCellBinding mBinding;

        public ContactHolder(View itemView, boolean isContact) {
            super(itemView);
            if (isContact)
                mBinding = DataBindingUtil.bind(itemView);
            itemView.setOnClickListener(this);
        }

        public void bind(SpoContact contact ) {
            mContact = contact;
            if (mContact != null) {
                mBinding.contactFullName.setText(mContact.getFullName());

                // Работа с изображением
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
                        mBinding.contactPicture.setImageResource(R.drawable.ic_avatar_one);
                    }

                } else {
                    mBinding.contactPicture.setImageResource(R.drawable.ic_avatar_one);
                }
            }
        }

        @Override
        public void onClick(View v) {
            if (mContact != null) {
                // Открытие чата с выбранным пользователем или создание нового
                Fragment fragment;

                SpoChatRoom chatRoom = DBUtils.getChatRoomForIdUser(mContact.getIdUser());
                if (chatRoom == null) {
                    chatRoom = new SpoChatRoom();
                    ArrayList<String> id = new ArrayList<>();
                    id.add(mContact.getIdUser());
                    chatRoom.setIdUsers(id);
                    chatRoom.setNameChat(mContact.getFullName());
                    chatRoom.setType(SpoChatRoom.ONE);
                    chatRoom.setId(DBUtils.saveChatRoom(chatRoom));
                }
                fragment = ChatFragment.newInstance(mContext, DBUtils.getChatRoomForIdUser(mContact.getIdUser()).getId());

                ((SecondaryActivity)getActivity()).displayFragment(fragment, false);
            } else {
                // Открытие окна создания рассылки
                Fragment fragment = CreateGroupFragment.newInstance(mContext, 0);
                ((SecondaryActivity)getActivity()).displayFragment(fragment, false);
            }
        }
    }

    private class ContactListAdapter extends RecyclerView.Adapter<ContactHolder> {
        List<SpoContact> mContacts;

        public ContactListAdapter(List<SpoContact> contacts) {
            mContacts = contacts;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 100;
            } else {
                return 1;
            }
        }

        @NonNull
        @Override
        public ContactHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 1) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_select_cell, parent, false);
                return new ContactHolder(view, true);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.group_cell, parent, false);
                return new ContactHolder(view, false);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ContactHolder holder, int position) {
            if (position == 0) {
                holder.bind(null);
            } else {
                SpoContact contact = mContacts.get(position - 1);
                holder.bind(contact);
            }
        }

        @Override
        public int getItemCount() {
            return mContacts.size() + 1;
        }

        public void setContacts(List<SpoContact> contacts) {
            mContacts = contacts;
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }

    private void backOrClose() {
        if(getActivity().getSupportFragmentManager().getBackStackEntryCount() > 0)
            getActivity().getSupportFragmentManager().popBackStack();
        else
            getActivity().finish();
    }

    private void requestReadContactsPermission() {
        permissionReadContactsResult.launch(Manifest.permission.READ_CONTACTS);
    }

    public void registerForPermissionReadContacts() {
        permissionReadContactsResult = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                showPhoneContacts();
            } else {
                Toast.makeText(mContext, "Нет прав для чтения контактов", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
