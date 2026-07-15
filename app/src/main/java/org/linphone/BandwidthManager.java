package org.linphone;

import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;

public class BandwidthManager {

	public static final int HIGH_RESOLUTION = 0;
	public static final int LOW_RESOLUTION = 1;
	public static final int LOW_BANDWIDTH = 2;

	private static BandwidthManager instance;

	private int currentProfile = HIGH_RESOLUTION;
	public int getCurrentProfile() {return currentProfile;}

	public static final synchronized BandwidthManager getInstance() {
		if (instance == null) instance = new BandwidthManager();
		return instance;
	}


	private BandwidthManager() {
		// FIXME register a listener on NetworkManager to get notified of network state
		// FIXME register a listener on Preference to get notified of change in video enable value

		// FIXME initially get those values
	}


	public void updateWithProfileSettings(LinphoneCore lc, LinphoneCallParams callParams) {
		if (callParams != null) { // in call
			// Update video parm if
			if (!isVideoPossible()) { // NO VIDEO
				callParams.setVideoEnabled(false);
				callParams.setAudioBandwidth(40);
			} else {
				callParams.setVideoEnabled(true);
				callParams.setAudioBandwidth(0); // disable limitation
			}
		}
	}

	public boolean isVideoPossible() {
		return currentProfile != LOW_BANDWIDTH;
	}
}
