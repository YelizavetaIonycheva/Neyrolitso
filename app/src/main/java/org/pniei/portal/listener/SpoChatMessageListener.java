package org.pniei.portal.listener;

public interface SpoChatMessageListener {

    void onSpoChatMessageStateChanged(long idMessage, int status);

    void onSpoChatMessageReceived(long idMessage);

    void onSpoLastChatMessageReceived(long idMessage);

    void onSpoFileStateChanged(long idFile, int status);

    void onSpoFileSendingStatus(long idFile, int percentSending);
}
