package org.linphone;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LpConfig;
import org.pniei.portal.R;

import java.io.File;
import java.io.InputStream;

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
			File linphonec = new File(basePath + "/.linphonec");
			if (linphonec.exists()) {
				return LinphoneCoreFactory.instance().createLpConfig(linphonec.getAbsolutePath());
			} else if (mContext != null) {
				InputStream inputStream = mContext.getResources().openRawResource(R.raw.linphonec_default);
				return LinphoneCoreFactory.instance().createLpConfigFromStream(inputStream);
			}
		}
		return null;
	}

	public String getRingtone(String defaultRingtone) {
		String ringtone = getConfig().getString("app", "ringtone", defaultRingtone);
		if (ringtone == null || ringtone.length() == 0)
			ringtone = defaultRingtone;
		return ringtone;
	}

	// Управление аккаунтами

	private LinphoneProxyConfig getProxyConfig(int n) {
		LinphoneProxyConfig[] prxcfg = getLc().getProxyConfigList();
		if (n < 0 || n >= prxcfg.length)
			return null;
		return prxcfg[n];
	}

	private LinphoneAuthInfo getAuthInfo(int n) {
		LinphoneProxyConfig prxcfg = getProxyConfig(n);
		if (prxcfg == null) return null;
		try {
			LinphoneAddress addr = LinphoneCoreFactory.instance().createLinphoneAddress(prxcfg.getIdentity());
			return getLc().findAuthInfo(addr.getUserName(), null, addr.getDomain());
		} catch (LinphoneCoreException e) {
			Log.e(e);
		}
		return null;
	}

	public int getAccountCount() {
		if (getLc() == null || getLc().getProxyConfigList() == null)
			return 0;
		return getLc().getProxyConfigList().length;
	}

	public int getDefaultAccountIndex() {
		if (getLc() == null)
			return -1;
		LinphoneProxyConfig defaultPrxcfg = getLc().getDefaultProxyConfig();
		if (defaultPrxcfg == null)
			return -1;
		LinphoneProxyConfig[] prxcfgs = getLc().getProxyConfigList();
		for (int i = 0; i < prxcfgs.length; i++) {
			if (defaultPrxcfg.getIdentity().equals(prxcfgs[i].getIdentity())) {
				return i;
			}
		}
		return -1;
	}

	public void setAccountEnabled(int n, boolean enabled) {
		LinphoneProxyConfig prxcfg = getProxyConfig(n);
		if (prxcfg == null) {
			LinphoneUtils.displayErrorAlert(getString(R.string.error), mContext);
			return;
		}
		prxcfg.edit();
		prxcfg.enableRegister(enabled);
		prxcfg.done();
		if (!enabled && getLc().getDefaultProxyConfig().getIdentity().equals(prxcfg.getIdentity())) {
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

	public String getAccountUsername(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		return authInfo == null ? null : authInfo.getUsername();
	}

	public String getAccountDomain(int n) {
		LinphoneProxyConfig proxyConf = getProxyConfig(n);
		return (proxyConf != null) ? proxyConf.getDomain() : "";
	}

	public void setAccountDomain(int n, String domain) {
		String proxy = domain;
		String identity = "sip:" + getAccountUsername(n) + "@" + domain;
		try {
			if (!proxy.contains("sip:")) {
				proxy = "sip:" + proxy;
			}
			LinphoneProxyConfig prxcfg = getProxyConfig(n);
			prxcfg.edit();
			prxcfg.setIdentity(identity);
			prxcfg.setProxy(proxy);
			prxcfg.enableRegister(true);
			prxcfg.done();
		} catch (LinphoneCoreException e) {
			Log.e(e);
		}
	}

	// Создание аккаунта

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
		private boolean tempOutboundProxy = false;
		private String tempContactsParams;
		private String tempExpire;
		private boolean tempAvpfEnabled = false;
		private int tempAvpfRRInterval = 0;
		private String tempQualityReportingCollector;
		private boolean tempQualityReportingEnabled = false;
		private int tempQualityReportingInterval = 0;
		private boolean tempEnabled = true;
		private boolean tempNoDefault = false;

		public AccountBuilder(LinphoneCore lc) {
			this.lc = lc;
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

		public void saveNewAccount() throws LinphoneCoreException {
			if (tempUsername == null || tempUsername.isEmpty() || tempDomain == null || tempDomain.isEmpty()) {
				Log.w("Skipping account save: username or domain not provided");
				return;
			}
			String identity = "sip:" + tempUsername + "@" + tempDomain;
			String proxy = "sip:";
			if (tempProxy == null) {
				proxy += tempDomain;
			} else {
				if (!tempProxy.startsWith("sip:")) {
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
			String route = tempOutboundProxy ? proxyAddr.asStringUriOnly() : null;
			LinphoneProxyConfig prxcfg = lc.createProxyConfig(identityAddr.asString(), proxyAddr.asStringUriOnly(), route, tempEnabled);
			if (tempContactsParams != null) prxcfg.setContactUriParameters(tempContactsParams);
			if (tempExpire != null) {
				try {
					prxcfg.setExpires(Integer.parseInt(tempExpire));
				} catch (NumberFormatException nfe) {
					throw new LinphoneCoreException(nfe);
				}
			}
			prxcfg.enableAvpf(tempAvpfEnabled);
			prxcfg.setAvpfRRInterval(tempAvpfRRInterval);
			prxcfg.enableQualityReporting(tempQualityReportingEnabled);
			prxcfg.setQualityReportingCollector(tempQualityReportingCollector);
			prxcfg.setQualityReportingInterval(tempQualityReportingInterval);
			if (tempPrefix != null) {
				prxcfg.setDialPrefix(tempPrefix);
			}
			if (tempRealm != null) {
				prxcfg.setRealm(tempRealm);
			}
			LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(tempUsername, tempUserId, tempPassword, tempHa1, tempRealm, tempDomain);
			lc.addProxyConfig(prxcfg);
			lc.addAuthInfo(authInfo);
			if (!tempNoDefault) {
				lc.setDefaultProxyConfig(prxcfg);
			}
		}
	}

	// Аудио настройки

	public int getCodecBitrateLimit() {
		return getConfig().getInt("audio", "codec_bitrate_limit", 20);
	}

	public void setCodecBitrateLimit(int bitrate) {
		getConfig().setInt("audio", "codec_bitrate_limit", bitrate);
	}

	// Настройки звонков

	public boolean isAutoAnswerEnabled() {
		return getConfig().getBool("app", "auto_answer", false);
	}

	public int getAutoAnswerTime() {
		return getConfig().getInt("app", "auto_answer_delay", 0);
	}

	public boolean isDeviceRingtoneEnabled() {
		int readExternalStorage = mContext.getPackageManager().checkPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, mContext.getPackageName());
		return getConfig().getBool("app", "device_ringtone", true) && readExternalStorage == PackageManager.PERMISSION_GRANTED;
	}

	public void enableDeviceRingtone(boolean enable) {
		getConfig().setBool("app", "device_ringtone", enable);
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
		LinphoneCore.Transports transports = getLc().getSignalingTransportPorts();
		transports.udp = port;
		transports.tcp = port;
		transports.tls = LINPHONE_CORE_RANDOM_PORT;
		getLc().setSignalingTransportPorts(transports);
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

	public boolean isConnectSki() {
		return getConfig().getBool("app", "connect_ski", true);
	}

	public void setConnectSki(boolean enabled) {
		getConfig().setBool("app", "connect_ski", enabled);
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

	public void setPushNotificationEnabled(boolean enable) {
		getConfig().setBool("app", "push_notification", enable);
		LinphoneCore lc = getLc();
		if (lc == null) return;
		if (enable) {
			String regId = getPushNotificationRegistrationID();
			String appId = getString(R.string.push_sender_id);
			if (regId != null && lc.getProxyConfigList().length > 0) {
				for (LinphoneProxyConfig lpc : lc.getProxyConfigList()) {
					String contactInfos = "app-id=" + appId + ";pn-type=" + getString(R.string.push_type) + ";pn-tok=" + regId + ";pn-silent=1";
					lpc.edit();
					lpc.setContactUriParameters(contactInfos);
					lpc.done();
				}
			}
		} else {
			if (lc.getProxyConfigList().length > 0) {
				for (LinphoneProxyConfig lpc : lc.getProxyConfigList()) {
					lpc.edit();
					lpc.setContactUriParameters(null);
					lpc.done();
				}
			}
			lc.refreshRegisters();
		}
	}

	public boolean isPushNotificationEnabled() {
		return getConfig().getBool("app", "push_notification", false);
	}

	public void setPushNotificationRegistrationID(String regId) {
		if (getConfig() == null) return;
		getConfig().setString("app", "push_notification_regid", (regId != null) ? regId : "");
		setPushNotificationEnabled(isPushNotificationEnabled());
	}

	public String getPushNotificationRegistrationID() {
		return getConfig().getString("app", "push_notification_regid", null);
	}

	private void setAccountPassword(int n, String password, String ha1) {
		String user = getAccountUsername(n);
		String domain = getAccountDomain(n);
		if (user != null && domain != null) {
			String userid = null;
			String realm = null;
			if (LinphoneManager.getLc().getAuthInfosList().length > n &&
					LinphoneManager.getLc().getAuthInfosList()[n] != null) {
				userid = getAuthInfo(n).getUserID();
				realm = getAuthInfo(n).getRealm();
				LinphoneManager.getLc().removeAuthInfo(LinphoneManager.getLc().getAuthInfosList()[n]);
			}
			LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(user, userid, password, ha1, realm, domain);
			LinphoneManager.getLc().addAuthInfo(authInfo);
		}
	}

	public void resetDefaultProxyConfig() {
		int count = getLc().getProxyConfigList().length;
		for (int i = 0; i < count; i++) {
			if (isAccountEnabled(i)) {
				getLc().setDefaultProxyConfig(getProxyConfig(i));
				break;
			}
		}
		if (getLc().getDefaultProxyConfig() == null) {
			getLc().setDefaultProxyConfig(getProxyConfig(0));
		}
	}

	public boolean isFriendlistSubscriptionEnabled() {
		return getConfig().getBool("app", "friendlist_subscription_enabled", false);
	}
}