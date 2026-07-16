package org.linphone;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAddress.TransportType;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.Transports;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneNatPolicy;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LpConfig;
import org.linphone.core.TunnelConfig;
import org.linphone.mediastream.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.pniei.portal.R;

public class LinphonePreferences {
	private static final int LINPHONE_CORE_RANDOM_PORT = -1;
	private static LinphonePreferences instance;
	private Context mContext;
	private String basePath;

	public static final synchronized LinphonePreferences instance() {
		if (instance == null) {
			instance = new LinphonePreferences();
		}
		return instance;
	}

	private LinphonePreferences() {

	}

	public void setContext(Context c) {
		mContext = c;
		basePath = mContext.getFilesDir().getAbsolutePath();
	}

	private String getString(int key) {
		if (mContext == null && LinphoneManager.isInstanciated()) {
			mContext = LinphoneManager.getInstance().getContext();
		}

		return mContext.getString(key);
	}

	private LinphoneCore getLc() {
		if (!LinphoneManager.isInstanciated())
			return null;

		return LinphoneManager.getLcIfManagerNotDestroyedOrNull();
	}

	public LpConfig getConfig() {
		LinphoneCore lc = getLc();
		if (lc != null) {
			return lc.getConfig();
		}

		if (!LinphoneManager.isInstanciated()) {
			File linphonerc = new File(basePath + "/.linphonerc");
			if (linphonerc.exists()) {
				return LinphoneCoreFactory.instance().createLpConfig(linphonerc.getAbsolutePath());
			} else if (mContext != null) {
				InputStream inputStream = mContext.getResources().openRawResource(R.raw.linphonerc_default);
			    InputStreamReader inputreader = new InputStreamReader(inputStream);
			    BufferedReader buffreader = new BufferedReader(inputreader);
			    StringBuilder text = new StringBuilder();
			    String line;
				try {
				    while ((line = buffreader.readLine()) != null) {
			            text.append(line);
			            text.append('\n');
			        }
				} catch (IOException ioe) {
					Log.e(ioe);
				}
			    return LinphoneCoreFactory.instance().createLpConfigFromString(text.toString());
			}
		} else {
			return LinphoneCoreFactory.instance().createLpConfig(LinphoneManager.getInstance().mLinphoneConfigFile);
		}
		return null;
	}

	public String getRingtone(String defaultRingtone) {
		String ringtone = getConfig().getString("app", "ringtone", defaultRingtone);
		if (ringtone == null || ringtone.length() == 0)
			ringtone = defaultRingtone;
		return ringtone;
	}

	// Accounts settings
	private LinphoneProxyConfig getProxyConfig(int n) {
		LinphoneProxyConfig[] prxCfgs = getLc().getProxyConfigList();
		if (n < 0 || n >= prxCfgs.length)
			return null;
		return prxCfgs[n];
	}

	private LinphoneAuthInfo getAuthInfo(int n) {
		LinphoneProxyConfig prxCfg = getProxyConfig(n);
		if (prxCfg == null) return null;
		try {
			LinphoneAddress addr = LinphoneCoreFactory.instance().createLinphoneAddress(prxCfg.getIdentity());
			LinphoneAuthInfo authInfo = getLc().findAuthInfo(addr.getUserName(), null, addr.getDomain());
			return authInfo;
		} catch (LinphoneCoreException e) {
			Log.e(e);
		}

		return null;
	}

	/**
	 * Removes a authInfo from the core and returns a copy of it.
	 * Useful to edit a authInfo (you should call saveAuthInfo after the modifications to save them).
	 */
	private LinphoneAuthInfo getClonedAuthInfo(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		if (authInfo == null)
			return null;

		LinphoneAuthInfo cloneAuthInfo = authInfo.clone();
		getLc().removeAuthInfo(authInfo);
		return cloneAuthInfo;
	}

	/**
	 * Saves a authInfo into the core.
	 * Useful to save the changes made to a cloned authInfo.
	 */
	private void saveAuthInfo(LinphoneAuthInfo authInfo) {
		getLc().addAuthInfo(authInfo);
	}

	public static class AccountBuilder {
		private LinphoneCore lc;
		private String tempUsername;
		private String tempDisplayName;
		private String tempUserId;
		private String tempPassword;
		private String tempHa1;
		private String tempDomain;
		private String tempProxy;
		private String tempRealm;
		private String tempPrefix;
		private boolean tempOutboundProxy;
		private String tempContactsParams;
		private String tempExpire;
		private TransportType tempTransport;
		private boolean tempAvpfEnabled = false;
		private int tempAvpfRRInterval = 0;
		private String tempQualityReportingCollector;
		private boolean tempQualityReportingEnabled = false;
		private int tempQualityReportingInterval = 0;
		private boolean tempEnabled = true;
		private boolean tempNoDefault = false;
		private boolean tempVPNAccount = false;


		public AccountBuilder(LinphoneCore lc) {
			this.lc = lc;
		}

		public AccountBuilder setTransport(TransportType transport) {
			tempTransport = transport;
			return this;
		}

		public AccountBuilder setUsername(String username) {
			tempUsername = username;
			return this;
		}

		public AccountBuilder setDisplayName(String displayName) {
			tempDisplayName = displayName;
			return this;
		}

		public AccountBuilder setPassword(String password) {
			tempPassword = password;
			return this;
		}

		public AccountBuilder setHa1(String ha1) {
			tempHa1 = ha1;
			return this;
		}

		public AccountBuilder setDomain(String domain) {
			tempDomain = domain;
			return this;
		}

		public AccountBuilder setProxy(String proxy) {
			tempProxy = proxy;
			return this;
		}

		public AccountBuilder setOutboundProxyEnabled(boolean enabled) {
			tempOutboundProxy = enabled;
			return this;
		}

		public AccountBuilder setContactParameters(String contactParams) {
			tempContactsParams = contactParams;
			return this;
		}

		public AccountBuilder setExpires(String expire) {
			 tempExpire = expire;
			return this;
		}

		public AccountBuilder setUserId(String userId) {
			tempUserId = userId;
			return this;
		}

		public AccountBuilder setAvpfEnabled(boolean enable) {
			tempAvpfEnabled = enable;
			return this;
		}

		public AccountBuilder setAvpfRRInterval(int interval) {
			tempAvpfRRInterval = interval;
			return this;
		}

		public AccountBuilder setRealm(String realm) {
			tempRealm = realm;
			return this;
		}

		public AccountBuilder setQualityReportingCollector(String collector) {
			tempQualityReportingCollector = collector;
			return this;
		}

		public AccountBuilder setPrefix(String prefix) {
			tempPrefix = prefix;
			return this;
		}

		public AccountBuilder setQualityReportingEnabled(boolean enable) {
			tempQualityReportingEnabled = enable;
			return this;
		}

		public AccountBuilder setQualityReportingInterval(int interval) {
			tempQualityReportingInterval = interval;
			return this;
		}

		public AccountBuilder setEnabled(boolean enable) {
			tempEnabled = enable;
			return this;
		}

		public AccountBuilder setNoDefault(boolean yesno) {
			tempNoDefault = yesno;
			return this;
		}

		/**
		 * Creates a new account
		 * @throws LinphoneCoreException
		 */
		public void saveNewAccount() throws LinphoneCoreException {

			if (tempUsername == null || tempUsername.length() < 1 || tempDomain == null || tempDomain.length() < 1) {
				Log.w("Skipping account save: username or domain not provided");
				return;
			}

			String identity = "sip:" + tempUsername + "@" + tempDomain;
			String proxy = "sip:";
			if (tempProxy == null) {
				proxy += tempDomain;
			} else {
				if (!tempProxy.startsWith("sip:") && !tempProxy.startsWith("<sip:")
					&& !tempProxy.startsWith("sips:") && !tempProxy.startsWith("<sips:")) {
					proxy += tempProxy;
				} else {
					proxy = tempProxy;
				}
			}
			LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(proxy);
			LinphoneAddress identityAddr = LinphoneCoreFactory.instance().createLinphoneAddress(identity);

			if (tempDisplayName != null) {
				identityAddr.setDisplayName(tempDisplayName);
			}

			if (tempTransport != null) {
				proxyAddr.setTransport(tempTransport);
			}

			String route = tempOutboundProxy ? proxyAddr.asStringUriOnly() : null;

			LinphoneProxyConfig prxCfg = lc.createProxyConfig(identityAddr.asString(), proxyAddr.asStringUriOnly(), route, tempEnabled);

			if (tempContactsParams != null)
				prxCfg.setContactUriParameters(tempContactsParams);
			if (tempExpire != null) {
				try {
					prxCfg.setExpires(Integer.parseInt(tempExpire));
				} catch (NumberFormatException nfe) {
					throw new LinphoneCoreException(nfe);
				}
			}

			prxCfg.enableAvpf(tempAvpfEnabled);
			prxCfg.setAvpfRRInterval(tempAvpfRRInterval);
			prxCfg.enableQualityReporting(tempQualityReportingEnabled);
			prxCfg.setQualityReportingCollector(tempQualityReportingCollector);
			prxCfg.setQualityReportingInterval(tempQualityReportingInterval);

			if(tempPrefix != null){
				prxCfg.setDialPrefix(tempPrefix);
			}

			if(tempRealm != null)
				prxCfg.setRealm(tempRealm);

			LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(tempUsername, tempUserId, tempPassword, tempHa1, tempRealm, tempDomain);

			lc.addProxyConfig(prxCfg);
			lc.addAuthInfo(authInfo);

			if (!tempNoDefault)
				lc.setDefaultProxyConfig(prxCfg);
		}
	}

	public TransportType getAccountTransport(int n) {
		TransportType transport = null;
		LinphoneProxyConfig proxyConfig = getProxyConfig(n);

		if (proxyConfig != null) {
			LinphoneAddress proxyAddr;
			try {
				proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(proxyConfig.getProxy());
				transport = proxyAddr.getTransport();
			} catch (LinphoneCoreException e) {
				Log.e(e);
			}
		}

		return transport;
	}

	public String getAccountUsername(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		return authInfo == null ? null : authInfo.getUsername();
	}

	public String getAccountUserId(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		return authInfo == null ? null : authInfo.getUserId();
	}

	public String getAccountRealm(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		return authInfo == null ? null : authInfo.getRealm();
	}

	private void setAccountPassword(int n, String password, String ha1) {
		String user = getAccountUsername(n);
		String domain = getAccountDomain(n);
		String userid = null;
		String realm = null;
		if(user != null && domain != null) {
			if (LinphoneManager.getLc().getAuthInfosList().length > n && LinphoneManager.getLc().getAuthInfosList()[n] != null) {
				userid = getAccountUserId(n);
				realm = getAccountRealm(n);
				LinphoneManager.getLc().removeAuthInfo(LinphoneManager.getLc().getAuthInfosList()[n]);
			}
			LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(
					user, userid, password, ha1, realm, domain);
			LinphoneManager.getLc().addAuthInfo(authInfo);
		}
	}

	public void setAccountDomain(int n, String domain) {
		String proxy = new String(domain);
		String identity = "sip:" + getAccountUsername(n) + "@" + domain;
		LinphoneAuthInfo old_info = getAuthInfo(n);
		try {
			if (!proxy.contains("sip:")) {
				proxy = "sip:" + proxy;
			}
			LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(proxy);
			if (!proxy.contains("transport=")) {
				proxyAddr.setTransport(getAccountTransport(n));
			}

			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			prxCfg.edit();
			prxCfg.setIdentity(identity);
			prxCfg.setProxy(proxyAddr.asStringUriOnly());
			prxCfg.enableRegister(true);
			prxCfg.done();

			if (old_info != null) {
				// We have to remove the previous auth info after otherwise we can't unregister the previous proxy config
				LinphoneAuthInfo new_info = old_info.clone();
				getLc().removeAuthInfo(old_info);
				new_info.setDomain(domain);
				saveAuthInfo(new_info);
			}
		} catch (LinphoneCoreException e) {
			Log.e(e);
		}
	}

	public String getAccountDomain(int n) {
		LinphoneProxyConfig proxyConf = getProxyConfig(n);
		return (proxyConf != null) ? proxyConf.getDomain() : "";
	}

	public void setAccountOutboundProxyEnabled(int n, boolean enabled) {
		try {
			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			prxCfg.edit();
			if (enabled) {
				String route = prxCfg.getProxy();
				prxCfg.setRoute(route);
			} else {
				prxCfg.setRoute(null);
			}
			prxCfg.done();
		} catch (LinphoneCoreException e) {
			Log.e(e);
		}
	}

	public boolean isAccountOutboundProxySet(int n) {
		return getProxyConfig(n).getRoute() != null;
	}

	public boolean isFriendlistsubscriptionEnabled() {
		return getConfig().getBool("app", "friendlist_subscription_enabled", false);
	}

	public int getDefaultAccountIndex() {
		if (getLc() == null)
			return -1;
		LinphoneProxyConfig defaultPrxCfg = getLc().getDefaultProxyConfig();
		if (defaultPrxCfg == null)
			return -1;

		LinphoneProxyConfig[] prxCfgs = getLc().getProxyConfigList();
		for (int i = 0; i < prxCfgs.length; i++) {
			if (defaultPrxCfg.getIdentity().equals(prxCfgs[i].getIdentity())) {
				return i;
			}
		}
		return -1;
	}

	public int getAccountCount() {
		if (getLc() == null || getLc().getProxyConfigList() == null)
			return 0;

		return getLc().getProxyConfigList().length;
	}

	public void setAccountEnabled(int n, boolean enabled) {
		LinphoneProxyConfig prxCfg = getProxyConfig(n);
		if (prxCfg == null) {
			LinphoneUtils.displayErrorAlert(getString(R.string.error), mContext);
			return;
		}
		prxCfg.edit();
		prxCfg.enableRegister(enabled);
		prxCfg.done();

		// If default proxy config is disabled, try to set another one as default proxy
		if (!enabled && getLc().getDefaultProxyConfig().getIdentity().equals(prxCfg.getIdentity())) {
			int count = getLc().getProxyConfigList().length;
			if (count > 1) {
				for (int i = 0; i < count; i++) {
					if (isAccountEnabled(i)) {
						getLc().setDefaultProxyConfig(getProxyConfig(i));
						break;
					}
				}
			}
		}
	}

	public boolean isAccountEnabled(int n) {
		return getProxyConfig(n).registerEnabled();
	}

	public void resetDefaultProxyConfig(){
		int count = getLc().getProxyConfigList().length;
		for (int i = 0; i < count; i++) {
			if (isAccountEnabled(i)) {
				getLc().setDefaultProxyConfig(getProxyConfig(i));
				break;
			}
		}

		if(getLc().getDefaultProxyConfig() == null){
			getLc().setDefaultProxyConfig(getProxyConfig(0));
		}
	}

	// Audio settings
	public void setEchoCancellation(boolean enable) {
		getLc().enableEchoCancellation(enable);
	}

	public boolean isEchoCancellationEnabled() {
		return getLc().isEchoCancellationEnabled();
	}

	public int getEchoCalibration() {
		return getConfig().getInt("sound", "ec_delay", -1);
	}

	public boolean isEchoConfigurationUpdated() {
		return getConfig().getBool("app", "ec_updated", false);
	}

	public void echoConfigurationUpdated() {
		getConfig().setBool("app", "ec_updated", true);
	}
	// End of audio settings


	// Video settings
	public boolean useFrontCam() {
		return getConfig().getBool("app", "front_camera_default", true);
	}


	public boolean isVideoEnabled() {
		return getLc().isVideoSupported() && getLc().isVideoEnabled();
	}

	public boolean shouldInitiateVideoCall() {
		return getLc().getVideoAutoInitiatePolicy();
	}

	public boolean shouldAutomaticallyAcceptVideoRequests() {
		return getLc().getVideoAutoAcceptPolicy();
	}

	public String getVideoPreset() {
		String preset = getLc().getVideoPreset();
		if (preset == null) preset = "default";
		return preset;
	}

	public void setVideoPreset(String preset) {
		if (preset.equals("default")) preset = null;
		getLc().setVideoPreset(preset);
		preset = getVideoPreset();
		if (!preset.equals("custom")) {
			getLc().setPreferredFramerate(0);
		}
		setPreferredVideoSize(getPreferredVideoSize()); // Apply the bandwidth limit
	}

	public String getPreferredVideoSize() {
		//LinphoneCore can only return video size (width and height), not the name
		return getConfig().getString("video", "size", "480p");
	}

	public void setPreferredVideoSize(String preferredVideoSize) {
		getLc().setPreferredVideoSizeByName(preferredVideoSize);
	}

	public int getPreferredVideoFps() {
		return (int)getLc().getPreferredFramerate();
	}

	public void setPreferredVideoFps(int fps) {
		getLc().setPreferredFramerate(fps);
	}

	public int getBandwidthLimit() {
		return getLc().getDownloadBandwidth();
	}

	public void setBandwidthLimit(int bandwidth) {
		getLc().setUploadBandwidth(bandwidth);
		getLc().setDownloadBandwidth(bandwidth);
	}

	public boolean getNativeDialerCall() {
		return getConfig().getBool("app", "native_dialer_call", false);
	}

	public boolean isWifiOnlyEnabled() {
		return getConfig().getBool("app", "wifi_only", false);
	}

	public void useRandomPort(boolean enabled, boolean apply) {
		getConfig().setBool("app", "random_port", enabled);
		if (apply) {
			if (enabled) {
				setSipPort(LINPHONE_CORE_RANDOM_PORT);
			} else {
				setSipPort(5060);
			}
		}
	}

	public void setSipPort(int port) {
		Transports transports = getLc().getSignalingTransportPorts();
		transports.udp = port;
		transports.tcp = port;
		transports.tls = LINPHONE_CORE_RANDOM_PORT;
		getLc().setSignalingTransportPorts(transports);
	}

	private LinphoneNatPolicy getOrCreateNatPolicy() {
		LinphoneNatPolicy nat = getLc().getNatPolicy();
		if (nat == null) {
			nat = getLc().createNatPolicy();
		}
		return nat;
	}

	public void setPushNotificationEnabled(boolean enable) {
		 getConfig().setBool("app", "push_notification", enable);

		 LinphoneCore lc = getLc();
		 if (lc == null) {
			 return;
		 }

		 if (enable) {
			 // Add push infos to exisiting proxy configs
			 String regId = getPushNotificationRegistrationID();
			 String appId = getString(R.string.push_sender_id);
			 if (regId != null && lc.getProxyConfigList().length > 0) {
				 for (LinphoneProxyConfig lpc : lc.getProxyConfigList()) {
					 String contactInfos = "app-id=" + appId + ";pn-type=" + getString(R.string.push_type) + ";pn-tok=" + regId + ";pn-silent=1";
					 lpc.edit();
					 lpc.setContactUriParameters(contactInfos);
					 lpc.done();
					 Log.d("Push notif infos added to proxy config " + lpc.getAddress().asStringUriOnly());
				 }
				 lc.refreshRegisters();
			 }
		 } else {
			 if (lc.getProxyConfigList().length > 0) {
				 for (LinphoneProxyConfig lpc : lc.getProxyConfigList()) {
					 lpc.edit();
					 lpc.setContactUriParameters(null);
					 lpc.done();
					 Log.d("Push notif infos removed from proxy config " + lpc.getAddress().asStringUriOnly());
				 }
				 lc.refreshRegisters();
			 }
		 }
	}

	public boolean isPushNotificationEnabled() {
		return getConfig().getBool("app", "push_notification", false);
	}

	public void setPushNotificationRegistrationID(String regId) {
		if (getConfig() == null) return;
		getConfig().setString("app", "push_notification_regid", (regId != null) ? regId: "");
		setPushNotificationEnabled(isPushNotificationEnabled());
	}

	public String getPushNotificationRegistrationID() {
		return getConfig().getString("app", "push_notification_regid", null);
	}

	public boolean isDebugEnabled() {
		return getConfig().getBool("app", "debug", false);
	}

	public void setBackgroundModeEnabled(boolean enabled) {
		getConfig().setBool("app", "background_mode", enabled);
	}

	public boolean isBackgroundModeEnabled() {
		return getConfig().getBool("app", "background_mode", true);
	}

	public boolean isConnectSkzi() {
		return getConfig().getBool("app", "connect_skzi", true);
	}

	public void setConnectSkzi(boolean enabled) {
		getConfig().setBool("app", "connect_skzi", enabled);
	}

	public String getDefaultDisplayName() {
		return getLc().getPrimaryContactDisplayName();
	}

	public void setDefaultUsername(String username) {
		getLc().setPrimaryContact(getDefaultDisplayName(), username);
	}

	public String getDefaultUsername() {
		return getLc().getPrimaryContactUsername();
	}
	// End of advanced settings

	// Tunnel settings
	private TunnelConfig tunnelConfig = null;

	public TunnelConfig getTunnelConfig() {
		if(getLc().isTunnelAvailable()) {
			if(tunnelConfig == null) {
				TunnelConfig servers[] = getLc().tunnelGetServers();
				if(servers.length > 0) {
					tunnelConfig = servers[0];
				} else {
					tunnelConfig = LinphoneCoreFactory.instance().createTunnelConfig();
				}
			}
			return tunnelConfig;
		} else {
			return null;
		}
	}

	public String getTunnelMode() {
		return getConfig().getString("app", "tunnel", null);
	}

	public boolean isProvisioningLoginViewEnabled() {

		return (getConfig() != null) ? getConfig().getBool("app", "show_login_view", false) : false;
	}

	public String getXmlrpcUrl(){
		return getConfig().getString("assistant", "xmlrpc_url", null);
	}


	public void setLinkPopupTime(String date){
		getConfig().setString("app", "link_popup_time", date);
	}

	public String getLinkPopupTime(){
		return getConfig().getString("app", "link_popup_time", null);
	}

	public String getActivityToLaunchOnIncomingReceived() {
		return getConfig().getString("app", "incoming_call_activity", "org.pniei.portal.activities.MainActivity");
	}

	public void setActivityToLaunchOnIncomingReceived(String name) {
		getConfig().setString("app", "incoming_call_activity", name);
	}

	public boolean getServiceNotificationVisibility() {
		return getConfig().getBool("app", "show_service_notification", true);
	}

	public void setServiceNotificationVisibility(boolean enable) {
		getConfig().setBool("app", "show_service_notification", enable);
	}

	public boolean isOverlayEnabled() {
		return getConfig().getBool("app", "display_overlay", false);
	}

	public void enableOverlay(boolean enable) {
		getConfig().setBool("app", "display_overlay", enable);
	}

	public boolean firstTimeAskingForPermission(String permission) {
		return firstTimeAskingForPermission(permission, true);
	}

	public boolean firstTimeAskingForPermission(String permission, boolean toggle) {
		boolean firstTime = getConfig().getBool("app", permission, true);
		if (toggle) {
			permissionHasBeenAsked(permission);
		}
		return firstTime;
	}

	public void permissionHasBeenAsked(String permission) {
		getConfig().setBool("app", permission, false);
	}

	public boolean isDeviceRingtoneEnabled() {
		int readExternalStorage = mContext.getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, mContext.getPackageName());
		return getConfig().getBool("app", "device_ringtone", true) && readExternalStorage == PackageManager.PERMISSION_GRANTED;
	}

	public void enableDeviceRingtone(boolean enable) {
		getConfig().setBool("app", "device_ringtone", enable);
	}

	public int getCodecBitrateLimit() {
		return getConfig().getInt("audio", "codec_bitrate_limit", 20);
	}

	public void setCodecBitrateLimit(int bitrate) {
		getConfig().setInt("audio", "codec_bitrate_limit", bitrate);
	}

	public boolean isAutoAnswerEnabled() {
		return getConfig().getBool("app", "auto_answer", false);
	}

	public int getAutoAnswerTime() {
		return getConfig().getInt("app", "auto_answer_delay", 0);
	}

	public void disableFriendsStorage() {
		getConfig().setBool("misc", "store_friends", false);
	}

}
