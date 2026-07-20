package org.pniei.portal.fragments;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import android.view.animation.LinearInterpolator;
import org.linphone.LinphoneManager;
import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCallLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.pniei.portal.R;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.activities.SecondaryActivity;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.databinding.HistoryCellBinding;
import org.pniei.portal.databinding.HistoryListFragmentBinding;
import org.pniei.portal.database.SpoContact;

public class HistoryListFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "HistoryListFragment";

    private enum STATE {GONE, CLOSE, DELETE};
    private static final String LIST_STATE_KEY = "list_state_key";
    private ArrayList<LinphoneCallLog> mLogs;
    private HistoryListAdapter mAdapter;
    private Parcelable mListState;
    private HistoryListFragmentBinding mBinding;
    private Handler mHandler;
    private boolean stateEdit = false;                          // Состояние фрагмента (false - обычное, true - редактирование)
    private int iconResourceIdOld = R.drawable.ic_close;

    public static HistoryListFragment newInstance() {
        return new HistoryListFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.history_list_fragment, container, false);
        mHandler = new Handler(Looper.getMainLooper());

        mBinding.historyList.setLayoutManager(new LinearLayoutManager(getContext()));
        mBinding.historyList.setHasFixedSize(true);

        mBinding.btnAction.setOnClickListener(this);
        mBinding.selectAll.setOnClickListener(this);

        mBinding.btnSortByAll.setOnClickListener(this);
        mBinding.btnSortByMade.setOnClickListener(this);
        mBinding.btnSortByMissed.setOnClickListener(this);
        mBinding.btnSortByReceived.setOnClickListener(this);

        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(savedInstanceState != null)
            mListState = savedInstanceState.getParcelable(LIST_STATE_KEY);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnAction) {
            if (stateEdit) {
                boolean [] selected = mAdapter.getChecked();
                int countDeleted = 0;

                for (int i = 0; i < selected.length; i++) {
                    if (selected[i]) {
                        LinphoneManager.getLc().removeCallLog(mLogs.get(i-countDeleted));
                        mLogs.remove(i-countDeleted);
                        countDeleted++;
                    }
                }

                updateUI();
                setStateEdit(false);
            }
        } else if (id == R.id.selectAll) {
            if (mAdapter != null) {
                mBinding.checkBox.setChecked(!mBinding.checkBox.isChecked());
                mAdapter.setAllChecked(mBinding.checkBox.isChecked());

                if (mBinding.checkBox.isChecked()) {
                    setStateActionButton(STATE.DELETE, true);
                } else {
                    setStateActionButton(STATE.CLOSE, true);
                }
            }
        } else if (id == R.id.btnSortByMissed) {
                mBinding.btnMenu.close(true);
                mLogs = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getCallLogs(2)));
                updateUI();
        } else if (id == R.id.btnSortByReceived) {
            mBinding.btnMenu.close(true);
            mLogs = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getCallLogs(1)));
            updateUI();
        } else if (id == R.id.btnSortByMade) {
            mBinding.btnMenu.close(true);
            mLogs = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getCallLogs(0)));
            updateUI();
        } else if (id == R.id.btnSortByAll) {
            mBinding.btnMenu.close(true);
            updateHistory();
        }
    }

    public void updateHistory() {
        Log.d(TAG, "updateContacts");
        new Thread(() -> {
            mLogs = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getCallLogs()));
            mHandler.postDelayed(this::updateUI, 200);
            /*ArrayList<LinphoneCallLog> logs = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getCallLogs()));
            if (mLogs == null || (mLogs.size() != logs.size())) {
                mLogs = logs;
                mHandler.post(() -> updateUI());
            }*/
        }).start();
    }

    public void updateUI() {
        Log.d(TAG, "updateUI");

        if (!mLogs.isEmpty()) {
            mBinding.textEmptyHistoryList.setVisibility(View.GONE);
            mBinding.historyList.setVisibility(View.VISIBLE);
            if (mAdapter == null) {
                mAdapter = new HistoryListAdapter(mLogs);
            } else {
                mAdapter.setHistory(mLogs);
            }
            mBinding.historyList.setAdapter(mAdapter);
        } else {
            mBinding.textEmptyHistoryList.setVisibility(View.VISIBLE);
            mBinding.historyList.setVisibility(View.GONE);
        }
    }

    public void setStateEdit(boolean isSet) {
        setStateActionButton(isSet ? STATE.CLOSE : STATE.GONE, isSet);
        if (mAdapter != null) mAdapter.setShowCheckbox(isSet);
        stateEdit = isSet;
        mBinding.selectAll.setVisibility(isSet ? View.VISIBLE : View.GONE);
    }

    public void setStateActionButton(STATE state, boolean isAnimate) {
        int idImage;
        boolean isVisible;

        switch (state) {
            case CLOSE: {
                idImage = R.drawable.ic_close;
                isVisible = true;
                break;
            }
            case DELETE: {
                idImage = R.drawable.ic_delete;
                isVisible = true;
                break;
            }
            case GONE:
            default: {
                idImage = R.drawable.ic_close;
                isVisible = false;
                break;
            }
        }

        if (isAnimate) {
            animateChangeActionButton(idImage);
        } else {
            mBinding.btnAction.setImageResource(idImage);
        }

        mBinding.btnAction.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        //stateActionButton = state;
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

    private class HistoryListAdapter extends RecyclerView.Adapter<HistoryListAdapter.HistoryHolder> {
        ArrayList<LinphoneCallLog> mHistory;
        private boolean isShowCheckbox;
        private boolean[] checked;
        private int countSetChecked = 0;

        public HistoryListAdapter(ArrayList<LinphoneCallLog> history) {
            mHistory = history;
        }

        class HistoryHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
            private final HistoryCellBinding mBinding;
            LinphoneCallLog mLog;
            SpoContact mContact;
            private int mPosition;

            public HistoryHolder(View itemView) {
                super(itemView);
                mBinding = DataBindingUtil.bind(itemView);
                itemView.setOnClickListener(this);
                itemView.setOnLongClickListener(this);
            }

            public void bind(LinphoneCallLog log, boolean isLast, int position) {
                LinphoneAddress address;
                mPosition = position;

                mLog = log;
                if (log.getDirection() == CallDirection.Incoming) {
                    address = log.getFrom();
                    if (log.getStatus() == LinphoneCallLog.CallStatus.Missed) {
                        mBinding.icon.setImageResource(R.drawable.ic_call_missed);
                    } else {
                        mBinding.icon.setImageResource(R.drawable.ic_call_received);
                    }
                } else {
                    address = log.getTo();
                    mBinding.icon.setImageResource(R.drawable.ic_call_made);
                }

                mContact = DBUtils.getContactForNumber(address.getUserName());

                if (mContact != null) {
                    mBinding.numberOrContactName.setText(mContact.getFullName());
                } else {
                    mBinding.numberOrContactName.setText(address.getUserName());
                }

                mBinding.time.setText(Utils.timeHistoryToHumanDate(log.getTimestamp()));

                mBinding.divider.setVisibility(isLast ? View.GONE : View.VISIBLE);

                mBinding.select.setVisibility(isShowCheckbox ? View.VISIBLE : View.GONE);
                if (isShowCheckbox)
                    mBinding.select.setChecked(checked[mPosition]);

                // Работа с изображением
                if (mContact != null && mContact.getUriPhoto() != null) {
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
            }

            @Override
            public void onClick(View v) {
                if (stateEdit) {
                    checked[mPosition] = !checked[mPosition];
                    mBinding.select.setChecked(checked[mPosition]);

                    if (checked[mPosition]) {
                        countSetChecked++;
                    } else {
                        countSetChecked--;
                    }

                    if (countSetChecked == 0) {
                        setStateActionButton(STATE.CLOSE, true);
                    } else {
                        setStateActionButton(STATE.DELETE, true);
                    }
                } else {
                    Intent intent = new Intent(getContext(), SecondaryActivity.class);
                    intent.putExtra(SecondaryActivity.TYPE_FRAGMENT_KEY, SecondaryActivity.HISTORY_DETAIL_FRAGMENT);
                    if (mContact != null)
                        intent.putExtra(SecondaryActivity.HISTORY_NUMBER, mContact.getSipNumber());
                    else
                        intent.putExtra(SecondaryActivity.HISTORY_NUMBER,mBinding.numberOrContactName.getText().toString());

                    String status;
                    if (mLog.getDirection() == CallDirection.Outgoing) {
                        status = getString(R.string.outgoing);
                    } else {
                        if (mLog.getStatus() == LinphoneCallLog.CallStatus.Missed) {
                            status = getString(R.string.missed);
                        } else {
                            status = getString(R.string.incoming);
                        }
                    }

                    intent.putExtra(SecondaryActivity.HISTORY_STATUS, status);
                    intent.putExtra(SecondaryActivity.HISTORY_DATE, String.valueOf(mLog.getTimestamp()));
                    intent.putExtra(SecondaryActivity.HISTORY_TIME, Utils.secondsToDisplayableString(mLog.getCallDuration()));
                    startActivity(intent);
                }
            }



            @Override
            public boolean onLongClick(View v) {
                setStateEdit(true);
                return false;
            }
        }


        @NonNull
        @Override
        public HistoryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.history_cell, parent, false);
            return new HistoryHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryHolder holder, int position) {
            LinphoneCallLog log = mHistory.get(position);
            holder.bind(log, position == mLogs.size()-1, position);
        }

        @Override
        public int getItemCount() {
            return mHistory.size();
        }

        public void setHistory(ArrayList<LinphoneCallLog> history) {
            mHistory = history;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void setShowCheckbox(boolean show){
            isShowCheckbox = show;
            if (isShowCheckbox) {
                countSetChecked = 0;
                checked = new boolean[mHistory.size()];
            }
            notifyDataSetChanged();
        }

        public boolean[] getChecked() {
            return checked;
        }

        public void setAllChecked(boolean value) {
            Arrays.fill(checked, value);
            updateUI();
        }
    }

    @Override
    public void onResume() {
        updateHistory();
        LinphoneManager.getLc().resetMissedCallsCount();
        setStateEdit(false);

        //mBinding.historyList.scrollBy(0, 0);
        if (mListState != null) {
            assert mBinding.historyList.getLayoutManager() != null;
            mBinding.historyList.getLayoutManager().onRestoreInstanceState(mListState);
        }
        mListState = null;
        super.onResume();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        assert mBinding.historyList.getLayoutManager() != null;
        mListState = mBinding.historyList.getLayoutManager().onSaveInstanceState();
        outState.putParcelable(LIST_STATE_KEY, mListState);
    }

}
