package org.pniei.portal.fragments;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.linphone.LinphoneManager;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.PresenceModel;
import org.pniei.portal.R;
import org.pniei.portal.listener.SpoChatMessageListener;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.activities.SecondaryActivity;
import org.pniei.portal.databinding.ChatListFragmentBinding;
import org.pniei.portal.database.SpoChatMessage;
import org.pniei.portal.database.SpoChatRoom;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.database.SpoFile;
import org.pniei.portal.listener.SpoListenerManager;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.databinding.ChatCellBinding;
import org.pniei.portal.notification.SpoNotificationsManager;
import org.pniei.portal.services.SpoMessagesService;

public class ChatListFragment extends Fragment implements SpoChatMessageListener, View.OnClickListener {
    private static final String TAG = "ChatListFragment";

    private enum STATE {NEW_CHAT, CLOSE, DELETE};
    private ChatListAdapter mAdapter;
    private boolean stateEdit = false;                          // Состояние фрагмента (false - обычное, true - редактирование)
    private STATE stateActionButton = STATE.NEW_CHAT;           // Состояние Action кнопки (0 - новый час, 1 - кнопка выхода из состояния редактирования чатов, 2 - кнопка удаления чатов)
    ArrayList<SpoChatRoom> mChatRooms = null;
    private int iconResourceIdOld = R.drawable.ic_new_chat;
    private ChatListFragmentBinding mBinding;
    private Handler mHandler;
    private SpoNotificationsManager mSpoNotificationsManager;
    private LinphoneCoreListenerBase mListener;

    public static ChatListFragment newInstance() {
        ChatListFragment fragment = new ChatListFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.inflate(inflater, R.layout.chat_list_fragment, container, false);
        mHandler = new Handler(Looper.getMainLooper());

        mBinding.chatList.setLayoutManager(new LinearLayoutManager(getContext()));
        mBinding.chatList.setHasFixedSize(true);

        mBinding.btnAction.setOnClickListener(this);
        mBinding.selectAll.setOnClickListener(this);

        setStateActionButton(stateActionButton, false);
        mSpoNotificationsManager = SpoNotificationsManager.ins(getContext());

        ////////////
        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
                for(SpoChatRoom chat : mChatRooms) {
                    List<String> id_users = chat.getIdUsers();
                    if (id_users.size() == 1) {
                        SpoContact contact = DBUtils.getContactForIdUser(id_users.get(0));
                        if (lf.getAddress() != null && lf.getAddress().getUserName() != null && contact!= null && lf.getAddress().getUserName().equals(contact.getSipNumber())) {
                            PresenceModel presenceModel = lf.getPresenceModelForUri(lf.getAddress().asStringUriOnly());
                            if (presenceModel != null) {
                                chat.setStatusNote(presenceModel.getNote("EN") == null? "" : presenceModel.getNote("EN").getContent());
                                chat.setStatusInt(presenceModel.getBasicStatus().toInt());
                            }
                            break;
                        }
                    }
                }

                mHandler.post(() -> updateUI());
            }
        };
        ////////////

        return mBinding.getRoot();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_action) {
            if (stateEdit) {
                boolean [] selected = mAdapter.getChecked();
                int countDeleted = 0;

                for (int i = 0; i < selected.length; i++) {
                    if (selected[i]) {
                        // Сначала удаление сообщений ожидающих отправки
                        List<SpoChatMessage> waitingMessages = Arrays.asList(DBUtils.getWaitingSpoChatMessagesForCharRoom(mChatRooms.get(i-countDeleted).getId()));
                        if (waitingMessages != null && waitingMessages.size() > 0) {
                            for (SpoChatMessage message: waitingMessages) {
                                Intent intent = new Intent(getContext(), SpoMessagesService.class);
                                intent.setAction(SpoMessagesService.ACTION_STOP_SENDING_MESSAGE);
                                intent.putExtra(SpoMessagesService.MESSAGE_ID_KEY, message.getId());
                                getContext().startService(intent);
                                DBUtils.deleteMessage(message);
                            }
                        }

                        DBUtils.deleteChatRoom(mChatRooms.get(i-countDeleted));
                        mChatRooms.remove(i-countDeleted);
                        countDeleted++;
                    }
                }

                updateUI();
                setStateEdit(false);
            } else {
                Intent intent = new Intent(getContext(), SecondaryActivity.class);
                intent.putExtra(SecondaryActivity.TYPE_FRAGMENT_KEY, SecondaryActivity.CONTACT_SELECT_FRAGMENT);
                intent.putExtra(SecondaryActivity.CONTACT_IS_PNONE_KEY, false);
                startActivity(intent);
            }
        } else if (id == R.id.selectAll) {
            if (mAdapter != null) {
                mBinding.checkBox.setChecked(!mBinding.checkBox.isChecked());
                mAdapter.setAllCheked(mBinding.checkBox.isChecked());

                if (mBinding.checkBox.isChecked()) {
                    setStateActionButton(STATE.DELETE, true);
                } else {
                    setStateActionButton(STATE.CLOSE, true);
                }
            }
        }
    }

    public void updateChatRooms() {
        Log.d(TAG, "updateChatRooms");

        new Thread(() -> {
            mChatRooms = new ArrayList<>(Arrays.asList(DBUtils.getChatList()));
            if (mChatRooms.size() > 0)
                Collections.sort(mChatRooms);

            /////////////
            updateStatusContactChat(mChatRooms);
            /////////////

            mHandler.postDelayed(() -> updateUI(), 200);
        }).start();
    }

    public void updateUI() {
        Log.d(TAG, "updateUI");
        if (mChatRooms.size() > 0) {
            mBinding.textEmptyChatList.setVisibility(View.GONE);
            if (mAdapter == null) {
                mAdapter = new ChatListAdapter(mChatRooms);
            } else {
                mAdapter.setChatRooms(mChatRooms);
            }
            mBinding.chatList.setAdapter(mAdapter);
            mBinding.chatList.scrollBy(0, 0);
        } else {
            mBinding.textEmptyChatList.setVisibility(View.VISIBLE);
        }
    }

    private static void updateStatusContactChat(List<SpoChatRoom> mChatRooms) {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            LinphoneFriend [] lfs = lc.getFriendList();
            if (lfs.length > 0 && mChatRooms.size() > 0) {
                List<SpoChatRoom> tmpList = new ArrayList<>(mChatRooms);
                if (tmpList.size() == 0)
                    return;
                for(SpoChatRoom chat : tmpList) {
                    List<String> id_users = chat.getIdUsers();
                    if (id_users.size() == 1) {
                        SpoContact contact = DBUtils.getContactForIdUser(id_users.get(0));
                        for (LinphoneFriend lf : lfs) {
                            if (lf.getAddress() != null && lf.getAddress().getUserName() != null && contact!= null && lf.getAddress().getUserName().equals(contact.getSipNumber())) {
                                PresenceModel presenceModel = lf.getPresenceModelForUri(lf.getAddress().asStringUriOnly());
                                if (presenceModel != null) {
                                    chat.setStatusNote(presenceModel.getNote("EN") == null? "" : presenceModel.getNote("EN").getContent());
                                    chat.setStatusInt(presenceModel.getBasicStatus().toInt());
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    public void setStateEdit(boolean isSet) {
        setStateActionButton(isSet ? STATE.CLOSE : STATE.NEW_CHAT, isSet);
        if (mAdapter != null) mAdapter.setShowCheckbox(isSet);
        stateEdit = isSet;
        mBinding.selectAll.setVisibility(isSet ? View.VISIBLE : View.GONE);
    }

    public void setStateActionButton(STATE state, boolean isAnimate) {
        int idImage;

        switch (state) {
            case NEW_CHAT:
            default: {
                idImage = R.drawable.ic_new_chat;
                break;
            }
            case CLOSE: {
                idImage = R.drawable.ic_close;
                break;
            }
            case DELETE: {
                idImage = R.drawable.ic_delete;
                break;
            }
        }

        if (isAnimate) {
            animateChangeActionButton(idImage);
        } else {
            mBinding.btnAction.setImageResource(idImage);
        }

        stateActionButton = state;
    }

    public void animateChangeActionButton(@DrawableRes int iconResourceId) {
        AnimatorSet animatorSet = new AnimatorSet();
        ObjectAnimator animatorRotate1 = ObjectAnimator
                .ofFloat(mBinding.btnAction,  "rotation",  0, 180)
                .setDuration(250);
        animatorRotate1.setInterpolator(new LinearInterpolator());

        ObjectAnimator animatorRotate2 = ObjectAnimator
                .ofFloat(mBinding.btnAction,  "rotation",  180 , 360)
                .setDuration(250);
        animatorRotate2.setInterpolator(new LinearInterpolator());

        ObjectAnimator animatorRotate3 = ObjectAnimator
                .ofInt(mBinding.btnAction, "imageResource", iconResourceIdOld, iconResourceId)
                .setDuration(1);
        iconResourceIdOld = iconResourceId;
        animatorSet.play(animatorRotate1).before(animatorRotate3).before(animatorRotate2);
        animatorSet.start();
    }

    @Override
    public void onSpoChatMessageStateChanged(long idMessage, int status) { }

    @Override
    public void onSpoChatMessageReceived(long idMessage) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                mChatRooms =  new ArrayList<>(Arrays.asList(DBUtils.getChatList()));
                updateChatRooms();
            });
        }
    }

    @Override
    public void onSpoLastChatMessageReceived(long idMessage) {
        SpoChatMessage msg = DBUtils.getChatMessageById(idMessage);
        SpoChatRoom chatRoom = DBUtils.getChatRoom(msg.getIdChatRoom());
        mSpoNotificationsManager.displayMessageNotification(chatRoom, msg);
    }

    @Override
    public void onSpoFileStateChanged(long idFile, int status) { }

    @Override
    public void onSpoFileSendingStatus(long idFile, int percentSending) { }

    private class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatRoomHolder> {
        private List<SpoChatRoom> mChatRooms;
        private boolean isShowCheckbox;
        private boolean[] checked;
        private int countSetChecked = 0;

        public ChatListAdapter(List<SpoChatRoom> chatRooms) {
            mChatRooms = chatRooms;
            isShowCheckbox = false;
        }

        class ChatRoomHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
            private SpoChatRoom mChatRoom;
            private ChatCellBinding mBinding;
            private int mPosition;

            public ChatRoomHolder(View itemView) {
                super(itemView);
                mBinding = DataBindingUtil.bind(itemView);
                itemView.setOnClickListener(this);
                itemView.setOnLongClickListener(this);
            }

            public void bind(SpoChatRoom chatRoom, boolean isLast, int position) {
                mChatRoom = chatRoom;
                mPosition = position;

                mBinding.chatLabel.setSelected(true);
                mBinding.chatLabel.setText(mChatRoom.getNameChat());

                int unreadMessagesCount = DBUtils.getUnreadMessagesCount(mChatRoom.getId());
                if (unreadMessagesCount > 0) {
                    mBinding.unreadLayout.setVisibility(View.VISIBLE);
                    if(unreadMessagesCount > 100) {
                        mBinding.unreadNumMessage.setTextSize(8);
                        if (unreadMessagesCount > 999) {
                            mBinding.unreadNumMessage.setTextSize(6);
                            mBinding.unreadNumMessage.setText("999+");
                        } else {
                            mBinding.unreadNumMessage.setText(String.valueOf(unreadMessagesCount));
                        }
                    } else {
                        mBinding.unreadNumMessage.setTextSize(10);
                        mBinding.unreadNumMessage.setText(String.valueOf(unreadMessagesCount));
                    }
                } else {
                    mBinding.unreadLayout.setVisibility(View.GONE);
                }

                List<SpoChatMessage> messages = Arrays.asList(DBUtils.getSpoChatMessagesRange(mChatRoom.getId(), 1, true));

                if(messages.size() > 0) {
                    SpoChatMessage message = messages.get(0);

                    switch (message.getTypeContent()) {
                        case SpoChatMessage.TEXT: {
                            if (message.getMessage() != null && message.getMessage().length() > 0) {
                                mBinding.lastMessage.setText(message.getMessage());
                            } else {
                                mBinding.lastMessage.setText("");
                            }
                            break;
                        }
                        case SpoChatMessage.FILE: {
                            if (message.getMessage() != null && message.getMessage().length() > 0) {
                                mBinding.lastMessage.setText(message.getMessage());
                            } else {
                                mBinding.lastMessage.setText("");
                                List<SpoFile> files = Arrays.asList(DBUtils.getSpoFiles(message.getId()));
                                if (files != null && files.size() > 0) {
                                    mBinding.lastMessage.setText(Utils.getFileName(files.get(0).getName()));
                                }
                            }
                            break;
                        }
                        default: {
                            mBinding.lastMessage.setText("");
                        }
                    }
                    mBinding.date.setText(Utils.timestampToHumanDate(getActivity(), message.getDate()));
                } else {
                    mBinding.lastMessage.setText("");
                    mBinding.date.setText("");
                }

                if (mChatRoom.getType() == SpoChatRoom.ONE) {
                    // Работа с изображением
                    SpoContact mContact = DBUtils.getContactForIdUser(mChatRoom.getIdUsers().get(0));
                    if (mContact != null && mContact.getUriPhoto() != null) {
                        Bitmap bm = null;
                        try {
                            bm = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), Uri.parse(mContact.getUriPhoto()));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (bm != null) {
                            mBinding.chatPicture.setImageBitmap(Utils.getCroppedBitmap(bm, 256, 256, 256));
                        } else {
                            mBinding.chatPicture.setImageResource(R.drawable.ic_avatar_one);
                        }
                    } else {
                        mBinding.chatPicture.setImageResource(R.drawable.ic_avatar_one);
                    }
                    switch (mChatRoom.getStatusInt()) {
                        case 2:
                        case 1:
                            mBinding.contactStatus.setImageResource(R.drawable.ic_state_offline);
                            break;
                        case 0:
                            if (mChatRoom.getStatusNote().equals("Ready")) {
                                mBinding.contactStatus.setImageResource(R.drawable.ic_state_online);
                            } else {
                                mBinding.contactStatus.setImageResource(R.drawable.ic_state_busy);
                            }
                            break;
                    }
                } else {
                    mBinding.chatPicture.setImageDrawable(getContext().getDrawable(R.drawable.ic_avatar_many));
                    mBinding.contactStatus.setVisibility(View.GONE);
                }

                mBinding.divider.setVisibility(isLast ? View.GONE : View.VISIBLE);

                mBinding.select.setVisibility(isShowCheckbox ? View.VISIBLE : View.GONE);
                if (isShowCheckbox)
                    mBinding.select.setChecked(checked[mPosition]);


            }

            @Override
            public void onClick(View v) {
                if (stateEdit) {
                    checked[mPosition] = !checked[mPosition];
                    mBinding.select.setChecked(checked[mPosition]);

                    if (checked[mPosition]) {
                        countSetChecked++;
                        if (countSetChecked == 1)
                            setStateActionButton(STATE.DELETE, true);
                    } else {
                        countSetChecked--;
                        if (countSetChecked == 0)
                        setStateActionButton(STATE.CLOSE, true);
                    
                    }
                } else {
                    SpoListenerManager.removeListener(ChatListFragment.this);
                    Intent intent = new Intent(getContext(), SecondaryActivity.class);
                    intent.putExtra(SecondaryActivity.TYPE_FRAGMENT_KEY, SecondaryActivity.CHAT_FRAGMENT);
                    intent.putExtra(SecondaryActivity.CHAT_ROOM_ID_KEY, mChatRoom.getId());
                    startActivity(intent);
                }
            }

            @Override
            public boolean onLongClick(View v) {
                setStateEdit(true);
                checked[mPosition] = true;
                countSetChecked++;
                setStateActionButton(STATE.DELETE, true);
                return false;
            }
        }

        @NonNull
        @Override
        public ChatRoomHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_cell, parent, false);
            return new ChatRoomHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatRoomHolder holder, int position) {
            SpoChatRoom chatRoom = mChatRooms.get(position);
            holder.bind(chatRoom, position == mChatRooms.size()-1, position);
        }

        @Override
        public int getItemCount() {
            return mChatRooms.size();
        }

        public void setChatRooms(List<SpoChatRoom> chatRooms) {
            mChatRooms = chatRooms;
        }

        public void setShowCheckbox(boolean show){
            isShowCheckbox = show;
            if (isShowCheckbox) {
                countSetChecked = 0;
                checked = new boolean[mChatRooms.size()];
            }
            notifyDataSetChanged();
        }

        public boolean[] getChecked() {
            return checked;
        }

        public void setAllCheked(boolean value) {
            for (int i = 0; i < checked.length; i++)
                checked[i] = value;
            updateUI();
        }
    }

    @Override
    public void onResume() {
        updateChatRooms();
        setStateEdit(false);

        setStateActionButton(STATE.NEW_CHAT, false);
        SpoListenerManager.removeAllListener();
        SpoListenerManager.addListener(this);

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }

        super.onResume();
    }

    @Override
    public void onPause() {
        SpoListenerManager.removeListener(this);

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }

        super.onPause();    
    }

}
