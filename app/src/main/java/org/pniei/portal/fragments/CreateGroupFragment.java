package org.pniei.portal.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.pniei.portal.R;
import org.pniei.portal.activities.SecondaryActivity;
import org.pniei.portal.database.SpoChatRoom;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.databinding.CreateGroupCellBinding;
import org.pniei.portal.databinding.CreateGroupFragmentBinding;

public class CreateGroupFragment extends Fragment {
    private static final String ARG_CHAT_ROOM_ID = "id_chat_room";
    private static Context mContext;
    private static boolean isNewGroup;
    private SpoChatRoom mChatRoom;
    private CreateGroupFragmentBinding mBinding;
    private ContactListAdapter mAdapter;
    ArrayList<SpoContact> contacts;
    LinkedList<String> idUsers;

    public static CreateGroupFragment newInstance(Context context, long idChatRoom) {
        mContext = context;
        CreateGroupFragment fragment = new CreateGroupFragment();

        if (idChatRoom == 0) {
            isNewGroup = true;
        } else {
            isNewGroup = false;
            Bundle args = new Bundle();
            args.putLong(ARG_CHAT_ROOM_ID, idChatRoom);
            fragment.setArguments(args);
        }

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        idUsers = new LinkedList<>();

        if(!isNewGroup) {
            long idChatRoom = getArguments().getLong(ARG_CHAT_ROOM_ID);
            mChatRoom = DBUtils.getChatRoom(idChatRoom);
            idUsers.addAll(mChatRoom.getIdUsers());
        }

        contacts = new ArrayList<>(Arrays.asList(DBUtils.getContactList()));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.create_group_fragment, container, false);

        if (!isNewGroup) {
            mBinding.nameGroup.setText(mChatRoom.getNameChat());
            mBinding.label.setText(R.string.label_change_group);
        }

        mBinding.btnClose.setOnClickListener(v -> {
            backOrClose();
        });

        mBinding.listContact.setLayoutManager(new LinearLayoutManager(getContext()));
        mBinding.listContact.setHasFixedSize(true);

        mBinding.btnSave.setOnClickListener(v -> {
            String nameGroup = mBinding.nameGroup.getText().toString();
            if (nameGroup.equals("")) {
                Toast.makeText(mContext, "Введите наименование рассылки", Toast.LENGTH_SHORT).show();
                return;
            }

            // Проверка, что были выбраны контакты
            int count = 0;
            boolean [] selected = mAdapter.getChecked();
            for(int i = 0; i < selected.length; i++) {
                if (selected[i]) { count++;}
            }

            if (count == 0) {
                Toast.makeText(mContext, "Выберите контакты", Toast.LENGTH_SHORT).show();
                return;
            } else if (count == 1) {
                Toast.makeText(mContext, "Выберите больше одного контакта", Toast.LENGTH_SHORT).show();
                return;
            }


            if (isNewGroup) {
                mChatRoom = new SpoChatRoom();
            }
            mChatRoom.setNameChat(nameGroup);
            mChatRoom.setType(SpoChatRoom.MANY);
            ArrayList<String> idSelectedUsers = new ArrayList<>();
            idSelectedUsers.addAll(idUsers);
            mChatRoom.setIdUsers(idSelectedUsers);

            if (isNewGroup) {
                mChatRoom.setId(DBUtils.saveChatRoom(mChatRoom));
            } else {
                DBUtils.updateChatRoom(mChatRoom);
            }

            Fragment fragment = ChatFragment.newInstance(mContext, mChatRoom.getId());
            ((SecondaryActivity)getActivity()).displayFragment(fragment, true);
        });

        return mBinding.getRoot();
    }

    public void updateUI() {
        if (contacts.size() > 0) {
            if (mAdapter == null) {
                mAdapter = new ContactListAdapter(contacts);
            } else {
                mAdapter.setContacts(contacts);
            }
            mBinding.listContact.setAdapter(mAdapter);
        }
    }

    private void backOrClose() {
        if(getActivity().getSupportFragmentManager().getBackStackEntryCount() > 0)
            getActivity().getSupportFragmentManager().popBackStack();
        else
            getActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }


    private class ContactListAdapter extends RecyclerView.Adapter<ContactListAdapter.ChontactHolder> {
        List<SpoContact> mContacts;
        boolean[] checked;

        private class ChontactHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            public SpoContact mContact;
            public CreateGroupCellBinding mBinding;

            public ChontactHolder(View itemView) {
                super(itemView);
                mBinding = DataBindingUtil.bind(itemView);
            }

            public void bind(SpoContact contact ) {
                mContact = contact;
                if (mContact != null) {
                    mBinding.contactFullName.setText(mContact.getFullName());
                    mBinding.select.setChecked(checked[getAbsoluteAdapterPosition()]);
                    // Работа с изображением
                }
            }

            @Override
            public void onClick(View v) {
                int position = getAbsoluteAdapterPosition();
                checked[position] = !checked[getAbsoluteAdapterPosition()];
                mBinding.select.setChecked(checked[position]);
                if (checked[position]) {
                    idUsers.add(mContact.getIdUser());
                } else {
                    idUsers.remove(mContact.getIdUser());
                }
            }
        }

        public ContactListAdapter(List<SpoContact> contacts) {
            mContacts = contacts;
            checked = new boolean[mContacts.size()];

            if (!isNewGroup) {
                int i = 0;
                for(SpoContact contact: mContacts) {
                    for(String id : mChatRoom.getIdUsers()) {
                        if (contact.getIdUser().equals(id)) {
                            checked[i] = true;
                            break;
                        }
                    }
                    i++;
                }
            }
        }

        @NonNull
        @Override
        public ChontactHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.create_group_cell, parent, false);
            return new ChontactHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChontactHolder holder, int position) {
            SpoContact contact = mContacts.get(position);
            holder.itemView.setOnClickListener(holder);
            holder.bind(contact);
        }

        @Override
        public int getItemCount() {
            return mContacts.size();
        }

        @Override
        public void onViewRecycled(ChontactHolder holder) {
            holder.itemView.setOnClickListener(null);
            super.onViewRecycled(holder);
        }

        public void setContacts(List<SpoContact> contacts) {
            mContacts = contacts;
        }

        public boolean[] getChecked() {
            return checked;
        }

    }

}
