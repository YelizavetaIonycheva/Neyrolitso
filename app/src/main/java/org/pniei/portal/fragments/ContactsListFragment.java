package org.pniei.portal.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.PresenceModel;
import org.pniei.portal.R;
import org.pniei.portal.databinding.ContactCellBinding;
import org.pniei.portal.databinding.ContactListFragmentBinding;
import org.pniei.portal.utils.RxJavaUtils;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.activities.SecondaryActivity;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.database.DBUtils;

public class ContactsListFragment extends Fragment {
    private static final String TAG = "ContactsListFragment";
    private ContactListAdapter mAdapter;
    private static final String LIST_STATE_KEY = "list_state_key";
    private List<SpoContact> mContacts;
    private ContactListFragmentBinding mBinding;
    private Handler mHandler;
    private Parcelable mListState;
    private boolean isSearch = false;
    private LinphoneCoreListenerBase mListener;

    public static ContactsListFragment newInstance() {
        return new ContactsListFragment();
    }

    @SuppressLint("CheckResult")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.contact_list_fragment, container, false);
        mHandler = new Handler(Looper.getMainLooper());

        mBinding.contactList.setLayoutManager(new LinearLayoutManager(getContext()));
        mBinding.contactList.setHasFixedSize(true);

        mBinding.btnNewContact.setOnClickListener(v -> {
            mBinding.btnMenu.close(true);
            Intent intent = new Intent(getContext(), SecondaryActivity.class);
            intent.putExtra(SecondaryActivity.TYPE_FRAGMENT_KEY, SecondaryActivity.CONTACT_CHANGE_FRAGMENT);
            intent.putExtra(SecondaryActivity.CONTACT_IS_NEW_KEY, true);
            startActivity(intent);
        });

        mBinding.btnNewContactFromPhone.setOnClickListener(v -> {
            mBinding.btnMenu.close(true);
            Intent intent = new Intent(getContext(), SecondaryActivity.class);
            intent.putExtra(SecondaryActivity.TYPE_FRAGMENT_KEY, SecondaryActivity.CONTACT_SELECT_FRAGMENT);
            intent.putExtra(SecondaryActivity.CONTACT_IS_PNONE_KEY, true);
            startActivity(intent);
        });

        mBinding.btnSyncContacts.setOnClickListener(v -> {
            mBinding.btnMenu.close(true);
            Intent intent = new Intent(getContext(), SecondaryActivity.class);
            intent.putExtra(SecondaryActivity.TYPE_FRAGMENT_KEY, SecondaryActivity.CONTACT_SYNC_FRAGMENT);
            startActivity(intent);
        });

        mBinding.searchView.setOnCloseListener(() -> isSearch = false);

        RxJavaUtils.getObserverSearchContact(mBinding.searchView)
                .subscribe(spoContacts -> {
                    mContacts = spoContacts;
                    isSearch = true;
                    updateStatusContacts(mContacts);
                    updateUI();
                });

        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
                for (SpoContact contact : mContacts) {
                    if (lf.getAddress() != null && lf.getAddress().getUserName() != null && contact != null && lf.getAddress().getUserName().equals(contact.getSipNumber())) {
                        PresenceModel presenceModel = lf.getPresenceModelForUri(lf.getAddress().asStringUriOnly());
                        if (presenceModel != null) {
                            contact.setStatusNote(presenceModel.getNote("EN") == null ? "" : presenceModel.getNote("EN").getContent());
                            contact.setStatusInt(presenceModel.getBasicStatus().toInt());
                        }
                        break;
                    }
                }

                mHandler.post(() -> updateUI());
            }
        };

        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null)
            mListState = savedInstanceState.getParcelable(LIST_STATE_KEY);
    }

    public void updateContacts() {
        Log.d(TAG, "updateContacts");
        new Thread(() -> {
            mContacts = Arrays.asList(DBUtils.getContactList());
            updateStatusContacts(mContacts);
            mHandler.postDelayed(this::updateUI, 200);
        }).start();
    }

    private static void updateStatusContacts(List<SpoContact> contacts) {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            LinphoneFriend[] lfs = lc.getFriendList();
            if (lfs.length > 0 && !contacts.isEmpty()) {
                List<SpoContact> tmpList = new ArrayList<>(contacts);
                for (LinphoneFriend lf : lfs) {
                    if (tmpList.isEmpty())
                        break;
                    for (SpoContact contact : tmpList) {
                        if (lf.getAddress() != null && lf.getAddress().getUserName() != null && contact != null && lf.getAddress().getUserName().equals(contact.getSipNumber())) {
                            PresenceModel presenceModel = lf.getPresenceModelForUri(lf.getAddress().asStringUriOnly());
                            if (presenceModel != null) {
                                contact.setStatusNote(presenceModel.getNote("EN") == null ? "" : presenceModel.getNote("EN").getContent());
                                contact.setStatusInt(presenceModel.getBasicStatus().toInt());
                            }
                            tmpList.remove(contact);
                            break;
                        }
                    }
                }
            }
        }
    }

    public void updateUI() {
        Log.d(TAG, "updateUI");
        if (!mContacts.isEmpty()) {
            mBinding.textEmptyContactList.setVisibility(View.GONE);
            mBinding.searchView.setVisibility(View.VISIBLE);
            if (mAdapter == null) {
                mAdapter = new ContactListAdapter(mContacts);
            } else {
                mAdapter.setContacts(mContacts);
            }
            mBinding.contactList.setAdapter(mAdapter);
        } else {
            mBinding.contactList.setAdapter(null);
            if (!isSearch) {
                mBinding.searchView.setVisibility(View.GONE);
                mBinding.textEmptyContactList.setVisibility(View.VISIBLE);
            }
        }
    }

    private class ContactHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private SpoContact mContact;
        private final ContactCellBinding mBinding;

        public ContactHolder(View itemView) {
            super(itemView);
            mBinding = DataBindingUtil.bind(itemView);
            itemView.setOnClickListener(this);
        }

        public void bind(SpoContact contact) {
            mContact = contact;
            mBinding.contactFullName.setText(mContact.getFullName());
            mBinding.sipNumber.setText(mContact.getSipNumber());

            // Работа с изображением
            if (mContact.getUriPhoto() != null) {
                Bitmap bm = null;
                try {
                    bm = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), Uri.parse(mContact.getUriPhoto()));
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

            switch (mContact.getStatusInt()) {
                case 2:
                case 1:
                    mBinding.contactStatus.setImageResource(R.drawable.ic_state_offline);
                    break;
                case 0:
                    if (mContact.getStatusNote().equals("Ready")) {
                        mBinding.contactStatus.setImageResource(R.drawable.ic_state_online);
                    } else {
                        mBinding.contactStatus.setImageResource(R.drawable.ic_state_busy);
                    }
                    break;
            }
        }

        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getContext(), SecondaryActivity.class);
            intent.putExtra(SecondaryActivity.TYPE_FRAGMENT_KEY, SecondaryActivity.CONTACT_INFO_FRAGMENT);
            intent.putExtra(SecondaryActivity.CONTACT_ID_KEY, mContact.getId());
            startActivity(intent);
        }
    }

    private class ContactListAdapter extends RecyclerView.Adapter<ContactHolder> {
        List<SpoContact> mContacts;

        public ContactListAdapter(List<SpoContact> contacts) {
            mContacts = contacts;
        }

        @NonNull
        @Override
        public ContactHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_cell, parent, false);
            return new ContactHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ContactHolder holder, int position) {
            SpoContact contact = mContacts.get(position);
            holder.bind(contact);
        }

        @Override
        public int getItemCount() {
            return mContacts.size();
        }

        public void setContacts(List<SpoContact> contacts) {
            mContacts = contacts;
        }

    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");

        mBinding.searchView.onActionViewCollapsed();
        updateContacts();

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }

        // mBinding.contactList.scrollBy(0, 0);
        if (mListState != null) {
            assert mBinding.contactList.getLayoutManager() != null;
            mBinding.contactList.getLayoutManager().onRestoreInstanceState(mListState);
        }
        mListState = null;
        super.onResume();
    }

    @Override
    public void onPause() {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }

        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        assert mBinding.contactList.getLayoutManager() != null;
        mListState = mBinding.contactList.getLayoutManager().onSaveInstanceState();
        outState.putParcelable(LIST_STATE_KEY, mListState);
    }

}
