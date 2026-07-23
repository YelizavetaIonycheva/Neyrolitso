package org.pniei.portal.listener;

import android.util.Log;

import org.pniei.portal.database.SpoChatMessage;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.database.SpoFile;

import java.util.ArrayList;

public class SpoListenerManager {
    private static final String TAG = "SpoListenerManager";
    private static final ArrayList<SpoChatMessageListener> mChatListeners = new ArrayList<>();
    private static final ArrayList<SpoContactListener> mContactListeners = new ArrayList<>();
    private static final Object syncChat = new Object();
    private static final Object syncContact = new Object();

    public static void addListener(SpoChatMessageListener listener) {
        synchronized (syncChat) {
            if (!mChatListeners.contains(listener)) {
                mChatListeners.add(listener);
            }
            Log.d(TAG, "addListener size " + mChatListeners.size());
        }
    }

    public static void removeListener(SpoChatMessageListener listener) {
        synchronized (syncChat) {
            mChatListeners.remove(listener);
            Log.d(TAG, "removeListener size " + mChatListeners.size());
        }
    }

    public static void removeAllListener() {
        synchronized (syncChat) {
            mChatListeners.clear();
            Log.d(TAG, "removeAllListener size " + mChatListeners.size());
        }
    }

    public static boolean isAddChatListeners() {
        synchronized (syncChat) {
            return !mChatListeners.isEmpty();
        }
    }

    public static void callChatMessageStateChanged(SpoChatMessage msg) {
        synchronized (syncChat) {
            for (SpoChatMessageListener listener : mChatListeners) {
                listener.onSpoChatMessageStateChanged(msg.getId(), SpoChatMessage.SENT);
            }
        }
    }

    public static void callChatMessageReceived(SpoChatMessage msg) {
        synchronized (syncChat) {
            for (SpoChatMessageListener listener : mChatListeners) {
                listener.onSpoChatMessageReceived(msg.getId());
            }
        }
    }

    public static void callLastChatMessageReceived(SpoChatMessage msg) {
        synchronized (syncChat) {
            for (SpoChatMessageListener listener : mChatListeners) {
                listener.onSpoLastChatMessageReceived(msg.getId());
            }
        }
    }

    public static void callFileStateChanged(SpoFile file) {
        synchronized (syncChat) {
            for (SpoChatMessageListener listener : mChatListeners) {
                listener.onSpoFileStateChanged(file.getId(), file.getStatus());
            }
        }
    }

    public static void callFileSendingStatus(long id, int percent) {
        synchronized (syncChat) {
            for (SpoChatMessageListener listener : mChatListeners) {
                listener.onSpoFileSendingStatus(id, percent);
            }
        }
    }

    public static void addListener(SpoContactListener listener) {
        synchronized (syncContact) {
            if (!mContactListeners.contains(listener))
                mContactListeners.add(listener);
        }
    }

    public static void removeListener(SpoContactListener listener) {
        synchronized (syncContact) {
            mContactListeners.remove(listener);
        }
    }

    public static void callContactSync(boolean b, String msg, ArrayList<SpoContact> addContacts, ArrayList<SpoContact> changedContacts) {
        synchronized (syncContact) {
            for (SpoContactListener listener : mContactListeners) {
                listener.onSpoContactSync(b, msg, addContacts, changedContacts);
            }
        }
    }

    public static void callContactChanged(boolean b, String msg) {
        synchronized (syncContact) {
            for (SpoContactListener listener : mContactListeners) {
                listener.onSpoContactChanged(b, msg);
            }
        }
    }

    public static void callContactAdd(boolean b, String msg) {
        synchronized (syncContact) {
            for (SpoContactListener listener : mContactListeners) {
                listener.onSpoContactAdd(b, msg);
            }
        }
    }

    public static void callContactDelete(boolean b, String msg) {
        synchronized (syncContact) {
            for (SpoContactListener listener : mContactListeners) {
                listener.onSpoContactDelete(b, msg);
            }
        }
    }
}
