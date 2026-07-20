package org.pniei.portal.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.pniei.portal.R;
import org.pniei.portal.listener.OnBackClickListener;
import org.pniei.portal.listener.SpoContactListener;
import org.pniei.portal.listener.SpoListenerManager;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.databinding.ContactSyncCellBinding;
import org.pniei.portal.databinding.ContactSyncFragmentBinding;
import org.pniei.portal.services.SpoMessagesService;

public class ContactSyncFragment extends Fragment implements SpoContactListener, OnBackClickListener {
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;
    private ContactSyncFragmentBinding mBinding;
    private ArrayList<SpoContact> mAddContacts;
    private ArrayList<SpoContact> mChangedContacts;
    private ContactListAdapter mAddAdapter, mChangeAdapter;

    public static ContactSyncFragment newInstance(Context context) {
        mContext = context;
        return new ContactSyncFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.contact_sync_fragment, container, false);

        mBinding.listAddContact.setLayoutManager(new LinearLayoutManager(mContext));
        mBinding.listAddContact.setHasFixedSize(true);
        mBinding.listChangeContact.setLayoutManager(new LinearLayoutManager(mContext));
        mBinding.listChangeContact.setHasFixedSize(true);

        mBinding.btnSave.setOnClickListener(v -> {
            backOrClose();
        });

        mBinding.card1.setOnClickListener(v -> {
            if (mBinding.listAddContact.getVisibility() == View.GONE && mAddContacts != null && mAddContacts.size() > 0) {
                mBinding.listAddContact.setVisibility(View.VISIBLE);
            } else {
                mBinding.listAddContact.setVisibility(View.GONE);
            }
        });

        mBinding.card2.setOnClickListener(v -> {
            if (mBinding.listChangeContact.getVisibility() == View.GONE && mChangedContacts != null && mChangedContacts.size() > 0) {
                mBinding.listChangeContact.setVisibility(View.VISIBLE);
            } else {
                mBinding.listChangeContact.setVisibility(View.GONE);
            }
        });

        SpoListenerManager.addListener(this);
        Intent intent = new Intent(mContext, SpoMessagesService.class);
        intent.setAction(SpoMessagesService.ACTION_SYNC_CONTACT);
        mContext.startService(intent);

        return mBinding.getRoot();
    }

    public void updateUI() {
        if (mAddContacts != null && !mAddContacts.isEmpty()) {
            if (mAddAdapter == null) {
                mAddAdapter = new ContactListAdapter(mAddContacts);
            } else {
                mAddAdapter.setContacts(mAddContacts);
            }
            mBinding.listAddContact.setAdapter(mAddAdapter);
        }

        if (mChangedContacts != null && !mChangedContacts.isEmpty()) {
            if (mChangeAdapter == null) {
                mChangeAdapter = new ContactListAdapter(mChangedContacts);
            } else {
                mChangeAdapter.setContacts(mChangedContacts);
            }
            mBinding.listChangeContact.setAdapter(mChangeAdapter);
        }
    }

    private void backOrClose() {
        if (requireActivity().getSupportFragmentManager().getBackStackEntryCount() > 0)
            requireActivity().getSupportFragmentManager().popBackStack();
        else
            requireActivity().finish();
    }

    private static class ContactHolder extends RecyclerView.ViewHolder {
        private final ContactSyncCellBinding mBinding;

        public ContactHolder(View itemView) {
            super(itemView);
            mBinding = DataBindingUtil.bind(itemView);
        }

        public void bind(SpoContact contact) {
            if (contact != null) {
                mBinding.contactFullName.setText(contact.getFullName());
                mBinding.sipNumber.setText(contact.getSipNumber());
            }
        }
    }

    private static class ContactListAdapter extends RecyclerView.Adapter<ContactHolder> {
        List<SpoContact> mContacts;

        public ContactListAdapter(List<SpoContact> contacts) {
            mContacts = contacts;
        }

        @NonNull
        @Override
        public ContactHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_sync_cell, parent, false);
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
        super.onResume();
        updateUI();
    }

    @Override
    public boolean allowBackPressed() {
        return mBinding.loadingWindow.getVisibility() == View.GONE;
    }

    @Override
    public void onSpoContactSync(boolean result, String msg, ArrayList<SpoContact> addContacts, ArrayList<SpoContact> changedContacts) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                SpoListenerManager.removeListener(this);
                if (result) {
                    mBinding.loadingWindow.setVisibility(View.GONE);
                    mBinding.countAddContacts.setVisibility(View.VISIBLE);
                    mBinding.countChangeContacts.setVisibility(View.VISIBLE);

                    if (addContacts != null) {
                        mBinding.countAddContacts.setText(mContext.getResources().getQuantityString(R.plurals.info_num_add_contacts, addContacts.size(), addContacts.size()));
                        if (!addContacts.isEmpty()) {
                            //mBinding.listAddContact.setVisibility(View.VISIBLE);
                            mAddContacts = addContacts;
                        }
                    }

                    if (changedContacts != null) {
                        mBinding.countChangeContacts.setText(mContext.getResources().getQuantityString(R.plurals.info_num_change_contacts, changedContacts.size(), changedContacts.size()));
                        if (!changedContacts.isEmpty()) {
                            //mBinding.listChangeContact.setVisibility(View.VISIBLE);
                            mChangedContacts = changedContacts;
                        }
                    }
                    updateUI();
                } else {
                    backOrClose();
                    Toast.makeText(mContext, "Ошибка синхронизации. Error: " + msg, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void onSpoContactChanged(boolean result, String msg) {

    }

    @Override
    public void onSpoContactAdd(boolean result, String msg) {

    }

    @Override
    public void onSpoContactDelete(boolean result, String msg) {

    }
}
