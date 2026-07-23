package org.pniei.portal.utils;

import android.content.Context;
import android.content.SharedPreferences;
<<<<<<< HEAD
=======
import android.util.Log;
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db

public class PrefsUtils {
    private static final String TAG = "PrefsUtils";

<<<<<<< HEAD
=======
    // Режимы (оставлен только Portal, но константа не используется)
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
    public static final int REGIME_P = 1;

    private static PrefsUtils instance;
    private SharedPreferences prefs;

<<<<<<< HEAD
    // Конфигурационные данные (из файла конфигурации)
=======
    // Конфигурационные данные SIP (из файла конфигурации)
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
    private String mIdP;
    private String mSignatureP;
    private String mPhoneP;
    private String mIpAtsP;
    private String mIpDnsP;
    private String mIpDnsSecondP;
    private String serverDomainName;

    // Состояние приложения
    private boolean isAuth = false;
    private boolean isAppBackground;
    private boolean isSelectCodecs;

    public interface Prefs {
        String NAME_FILE_PREFS = "smp_settings";
        String ID_P = "id_p";
        String SIGNATURE_P = "signature_p";
        String PHONE_P = "phone_p";
        String IP_ATS_P = "ip_ats_p";
        String IP_DNS_P = "ip_dns_p";
        String IP_DNS_SECOND_P = "ip_dns_second_p";
        String SERVER_DOMAIN = "server_domain";
        String IS_AUTH = "is_auth";
        String IS_APP_BACKGROUND = "is_app_background";
        String SELECT_CODEC = "select_codec";
    }

<<<<<<< HEAD
    private PrefsUtils() {
    }
=======
    private PrefsUtils() { }
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db

    public static synchronized PrefsUtils ins() {
        if (instance == null) {
            instance = new PrefsUtils();
        }
        return instance;
    }

    /**
     * Загрузка сохранённых настроек из SharedPreferences.
<<<<<<< HEAD
=======
     * Вызывается при запуске приложения (в LauncherActivity).
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
     */
    public void load(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(Prefs.NAME_FILE_PREFS, Context.MODE_PRIVATE);
        mIdP = prefs.getString(Prefs.ID_P, "");
        mSignatureP = prefs.getString(Prefs.SIGNATURE_P, "");
        mPhoneP = prefs.getString(Prefs.PHONE_P, "");
        mIpAtsP = prefs.getString(Prefs.IP_ATS_P, "");
        mIpDnsP = prefs.getString(Prefs.IP_DNS_P, "");
        mIpDnsSecondP = prefs.getString(Prefs.IP_DNS_SECOND_P, "");
        serverDomainName = prefs.getString(Prefs.SERVER_DOMAIN, "impulse.ru");
        isAuth = prefs.getBoolean(Prefs.IS_AUTH, false);
        isAppBackground = prefs.getBoolean(Prefs.IS_APP_BACKGROUND, false);
        isSelectCodecs = prefs.getBoolean(Prefs.SELECT_CODEC, false);
    }

    /**
<<<<<<< HEAD
     * Сохранение параметров конфигурации.
=======
     * Сохранение параметров конфигурации (после успешной загрузки из файла).
     * Автоматически устанавливает флаг isAuth = true.
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
     */
    public void saveConfig(String id, String signature, String phone, String ipAts, String ipDns, String ipDnsSecond) {
        prefs.edit()
                .putString(Prefs.ID_P, id)
                .putString(Prefs.SIGNATURE_P, signature)
                .putString(Prefs.PHONE_P, phone)
                .putString(Prefs.IP_ATS_P, ipAts)
                .putString(Prefs.IP_DNS_P, ipDns)
                .putString(Prefs.IP_DNS_SECOND_P, ipDnsSecond)
                .putBoolean(Prefs.IS_AUTH, true)
                .apply();
        mIdP = id;
        mSignatureP = signature;
        mPhoneP = phone;
        mIpAtsP = ipAts;
        mIpDnsP = ipDns;
        mIpDnsSecondP = ipDnsSecond;
        isAuth = true;
    }

    public String getIdP() {
        return mIdP;
    }

    public String getSignatureP() {
        return mSignatureP;
    }

    public String getPhoneP() {
        return mPhoneP;
    }

    public String getIpAtsP() {
        return mIpAtsP;
    }

    public String getIpDnsP() {
        return mIpDnsP;
    }

    public String getIpDnsSecondP() {
        return mIpDnsSecondP;
    }

    public String getServerDomainName() {
        return serverDomainName;
    }

    public boolean isAuth() {
        return isAuth;
    }

    public boolean isAppBackground() {
        return isAppBackground;
    }

    public boolean isSelectCodecs() {
        return isSelectCodecs;
    }

<<<<<<< HEAD
    public int getRegimeSelected() {
        return REGIME_P;
    }

    public void setIpAtsP(String ipAts) {
        mIpAtsP = ipAts;
        prefs.edit().putString(Prefs.IP_ATS_P, ipAts).apply();
    }

    public void setIpDnsP(String ipDns) {
        mIpDnsP = ipDns;
        prefs.edit().putString(Prefs.IP_DNS_P, ipDns).apply();
    }

    public void setIpDnsSecondP(String ipDnsSecond) {
        mIpDnsSecondP = ipDnsSecond;
        prefs.edit().putString(Prefs.IP_DNS_SECOND_P, ipDnsSecond).apply();
    }
=======
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db

    public void setAuth(boolean auth) {
        isAuth = auth;
        prefs.edit().putBoolean(Prefs.IS_AUTH, auth).apply();
    }

    public void setAppBackground(boolean value) {
        isAppBackground = value;
        prefs.edit().putBoolean(Prefs.IS_APP_BACKGROUND, value).apply();
    }

    public void setSelectCodecs(boolean selectCodecs) {
        isSelectCodecs = selectCodecs;
        prefs.edit().putBoolean(Prefs.SELECT_CODEC, selectCodecs).apply();
    }

    public void setServerDomainName(String serverDomainName) {
        this.serverDomainName = serverDomainName;
        prefs.edit().putString(Prefs.SERVER_DOMAIN, serverDomainName).apply();
    }

    public void deleteConfig(Context context) {
        prefs.edit().clear().apply();
        isAuth = false;
        mIdP = "";
        mSignatureP = "";
        mPhoneP = "";
        mIpAtsP = "";
        mIpDnsP = "";
        mIpDnsSecondP = "";
        serverDomainName = "impulse.ru";
    }
}