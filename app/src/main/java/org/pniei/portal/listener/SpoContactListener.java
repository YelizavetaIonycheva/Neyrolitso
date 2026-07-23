package org.pniei.portal.listener;

import java.util.ArrayList;
import org.pniei.portal.database.SpoContact;

public interface SpoContactListener {

    void onSpoContactSync(boolean result, String msg, ArrayList<SpoContact> addContacts, ArrayList<SpoContact> changedContacts);
    void onSpoContactChanged(boolean result, String msg);
    void onSpoContactAdd(boolean result, String msg);
    void onSpoContactDelete(boolean result, String msg);

}
