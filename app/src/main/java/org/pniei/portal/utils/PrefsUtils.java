package org.pniei.portal.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

public class PrefsUtils {
    private static final String TAG = "PrefsUtils";
    public static final int REGIME_NONE = 0;
    public static final int REGIME_P = 1;
    public static final int REGIME_TT = 2;
    public static final String LICENSE_FULL = "full";
    public static final String LICENSE_CUSTOM = "custom";

    private static PrefsUtils instance;
    private SharedPreferences prefs;

    /** Общие **/
    private int mRegimeSelected;        // Выбранный режим работы (0 - режим не выбран, 1 - Режим Портал, 2 - Режим Точка-Точка)
    private boolean isLicenseReg;       // Признак регистрации лицензионного ключа на сервере
    private String mLicenseType;        // Наименование типа лицензии ("full", "custom")
    private String mLicenseKey;         // Лицензионный ключ
    private String mHashPass;           // Хэш на пароль пользователя
    private String serverDomainName;
    private boolean isSelectCodecs;     // Признак был ли выполнен первоначальный выбор кодеков
    private boolean isAuth = false;     // Признак прохождения аутентификации (Не сохраняется в файл)
    private boolean isAppBackground;    // Признак работы в фоне (Не сохраняется в файл)
    private Set<String> app_vpn_list;   // Список браузеров работающих через VPN
    private String mIpGps;              // IP-адрес для записи координат
    private boolean isSendGPS;          // Отправлять ли координаты
    private int timeIntervalSendGPS;    // Интервал времени отправки координат на сервер

    /** Режим Портал **/
    private boolean isCfgEnterP;        // Признак ввода конфигурации для режима Портал
    private String mIdP;                // Уникальный идентификатор пользователя в режиме Портал
    private String mSignatureP;         // Подпись для доступа к системе в режиме Портал
    private String mPhoneP;             // Номер пользователя в режиме Портал
    private String mIpAtsP;             // IP-адрес АТС в режиме Портал
    private String mIpDnsP;             // IP-адрес DNS в режиме Портал
    private String mIpDnsSecondP;       // IP-адрес DNS дополнительный в режиме Портал
    private String mIpSkzi;             // IP-адрес СКЗИ
    private String mIpMon;              // IP-адрес сервера мониторинга ключей
    private boolean isVpnEnable;        // Признак работы VPN в режиме Портал
    private boolean isKeyEnter;         // Признак ввода ключей для режима Портал

    /** Режим Точка-Точка **/
    private boolean isCfgEnterTT;       // Признак ввода конфигурации для режима Точка-Точка
    private String mIdTT;               // Уникальный идентификатор пользователя в режиме Точка-Точка
    private String mSignatureTT;        // Подпись для доступа к системе в режиме Точка-Точка
    private String mPhoneTT;            // Номер пользователя в режиме Точка-Точка
    private String mIpAtsTT;            // IP-адрес АТС в режиме Точка-Точка
    private String mIpDnsTT;            // IP-адрес DNS в режиме Точка-Точка

    public interface JsonKeysPrefs {
        String ID =         "id";
        String SIGNATURE =  "signature";
        String PHONE =      "phone";
        String IP_ATS =     "ip_ats";
        String IP_DNS =     "ip_dns";
        String IP_SKZI =    "ip_skzi";
        String IP_MON =     "ip_mon";
    }

    public interface ExportImportPrefs {
        String REGIME =         "regime";
        int REGIME_P =          1;
        int REGIME_TT =         2;
        String IP_ATS =         "ip_ats";
        String IP_SKZI =        "ip_skzi";
        String IP_MON =         "ip_mon";
        String IP_DNS =         "ip_dns";
        String IP_DNS_SECOND =  "ip_dns_second";
        String VPN_APP_LIST =   "vpn_app_list";
        String PREF_CRC =       "pref_crc";
    }

    public interface Prefs {
        String NAME_FILE_PREFS =    "smp_settings";

        /** Общие **/
        String REGIME_SELECTED =    "regime_selected";
        String LICENSE_REG =        "license_reg";
        String LICENSE_TYPE =       "license_type";
        String LICENSE_KEY =        "license_key";
        String HASH_PASS =          "hash_pass";
        String SELECT_CODEC =       "select_codec";
        String IP_GPS =             "ip_gps";
        String SEND_GPS =           "send_gps";
        String TIME_GPS =           "time_gps";

        /** Режим Портал **/
        String CONFIG_ENTER_P =     "config_enter_p";
        String ID_P =               "id_p";
        String SIGNATURE_P =        "signature_p";
        String PHONE_P =            "phone_p";
        String IP_ATS_P =           "ip_ats_p";
        String IP_DNS_P =           "ip_dns_p";
        String IP_DNS_SECOND_P =    "ip_dns_second_p";
        String IP_SKZI =            "ip_skzi";
        String IP_MON =             "ip_mon";
        String VPN_ENABLE =         "vpn_enable";
        String KEY_ENTER =          "key_enter";
        String BROWSER_LIST =       "browser_list";

        /** Режим Точка-Точка **/
        String CONFIG_ENTER_TT =    "config_enter_tt";
        String ID_TT =              "id_tt";
        String SIGNATURE_TT =       "signature_tt";
        String PHONE_TT =           "phone_tt";
        String IP_ATS_TT =          "ip_ats_tt";
        String IP_DNS_TT =          "ip_dns_tt";
        String SERVER_DOMAIN =      "server_domain";
    }

    private PrefsUtils() { }

    public static synchronized PrefsUtils ins() {
        if (instance == null) {
            Log.e(TAG, "PrefsUtils Create");
            instance = new PrefsUtils();
        }
        return instance;
    }


    /** Общие **/
    public int getRegimeSelected() {
        return mRegimeSelected;
    }

    public void setRegimeSelected(int regimeSelected) {
        mRegimeSelected = regimeSelected;
        prefs.edit().putInt(Prefs.REGIME_SELECTED, regimeSelected).apply();
    }

    public boolean isLicenseReg() {
        return isLicenseReg;
    }

    public void setLicenseReg(boolean licenseReg) {
        isLicenseReg = licenseReg;
        prefs.edit().putBoolean(Prefs.LICENSE_REG, isLicenseReg).apply();
    }

    public String getLicenseType() {
        return mLicenseType;
    }

    public void setLicenseType(String licenseType) {
        mLicenseType = licenseType;
        prefs.edit().putString(Prefs.LICENSE_TYPE, mLicenseType).apply();
    }

    public String getLicenseKey() {
        return mLicenseKey;
    }

    public void setLicenseKey(String licenseKey) {
        mLicenseKey = licenseKey;
        prefs.edit().putString(Prefs.LICENSE_KEY, mLicenseKey).apply();
    }

    public byte[] getHashPass() {
        return Utils.hexStringToByteArray(mHashPass);
    }

    public void setHashPass(byte[] hashPass) {
        mHashPass = Utils.byteArrayToHexString(hashPass);
        prefs.edit().putString(Prefs.HASH_PASS, mHashPass).apply();
    }

    public boolean isSelectCodecs() {
        return isSelectCodecs;
    }

    public void setSelectCodecs(boolean selectCodecs) {
        isSelectCodecs = selectCodecs;
        prefs.edit().putBoolean(Prefs.SELECT_CODEC, isSelectCodecs).apply();
    }

    public boolean isAuth() {
        return isAuth;
    }

    public void setAuth(boolean auth) {
        isAuth = auth;
    }

    public void setAppBackground(boolean value) {
        isAppBackground = value;
    }

    public boolean isAppBackground() {
        return isAppBackground;
    }


    /** Режим Портал **/

    public boolean isCfgEnterP() {
        return isCfgEnterP;
    }

    public void setCfgEnterP(boolean cfgEnterP) {
        isCfgEnterP = cfgEnterP;
        prefs.edit().putBoolean(Prefs.CONFIG_ENTER_P, isCfgEnterP).apply();
    }

    public String getIdP() {
        return mIdP;
    }

    public void setIdP(String idP) {
        mIdP = idP;
        prefs.edit().putString(Prefs.ID_P, mIdP).apply();
    }

    public String getSignatureP() {
        return mSignatureP;
    }

    public void setSignatureP(String signatureP) {
        mSignatureP = signatureP;
        prefs.edit().putString(Prefs.SIGNATURE_P, mSignatureP).apply();
    }

    public String getPhoneP() {
        return mPhoneP;
    }

    public void setPhoneP(String phone) {
        mPhoneP = phone;
        prefs.edit().putString(Prefs.PHONE_P, mPhoneP).apply();
    }

    public String getIpAtsP() {
        return mIpAtsP;
    }

    public void setIpAtsP(String ipAts) {
        mIpAtsP = ipAts;
        prefs.edit().putString(Prefs.IP_ATS_P, mIpAtsP).apply();
    }

    public String getIpDnsP() {
        return mIpDnsP;
    }

    public void setIpDnsP(String ipDns) {
        mIpDnsP = ipDns;
        prefs.edit().putString(Prefs.IP_DNS_P, mIpDnsP).apply();
    }

    public String getIpDnsSecondP() {
        return mIpDnsSecondP;
    }

    public void setIpDnsSecondP(String ipDnsSecondP) {
        mIpDnsSecondP = ipDnsSecondP;
        prefs.edit().putString(Prefs.IP_DNS_SECOND_P, mIpDnsSecondP).apply();
    }

    public String getIpSkzi() {
        return mIpSkzi;
    }

    public void setIpSkzi(String ipSkzi) {
        mIpSkzi = ipSkzi;
        prefs.edit().putString(Prefs.IP_SKZI, mIpSkzi).apply();
    }

    public String getIpMon() {
        return mIpMon;
    }

    public void setIpMon(String ipMon) {
        mIpMon = ipMon;
        prefs.edit().putString(Prefs.IP_MON, mIpMon).apply();
    }

    public void setEnableVpn(boolean enable) {
        isVpnEnable = enable;
        prefs.edit().putBoolean(Prefs.VPN_ENABLE, isVpnEnable).apply();
    }
    public boolean isKeyEnter() {
        return isKeyEnter;
    }

    public void setKeyEnter(boolean keyEnter) {
        isKeyEnter = keyEnter;
        prefs.edit().putBoolean(Prefs.KEY_ENTER, isKeyEnter).apply();
    }


    /** Режим Точка-Точка **/

    public boolean isCfgEnterTT() {
        return isCfgEnterTT;
    }

    public void setCfgEnterTT(boolean cfgEnterTT) {
        isCfgEnterTT = cfgEnterTT;
        prefs.edit().putBoolean(Prefs.CONFIG_ENTER_TT, isCfgEnterTT).apply();
    }

    public String getIdTT() {
        return mIdTT;
    }

    public void setIdTT(String idTT) {
        mIdTT = idTT;
        prefs.edit().putString(Prefs.ID_TT, mIdTT).apply();
    }

    public String getSignatureTT() {
        return mSignatureTT;
    }

    public void setSignatureTT(String signatureTT) {
        mSignatureTT = signatureTT;
        prefs.edit().putString(Prefs.SIGNATURE_TT, mSignatureTT).apply();
    }

    public String getPhoneTT() {
        return mPhoneTT;
    }

    public void setPhoneTT(String phone) {
        mPhoneTT = phone;
        prefs.edit().putString(Prefs.PHONE_TT, mPhoneTT).apply();
    }

    public String getIpAtsTT() {
        return mIpAtsTT;
    }

    public void setIpAtsTT(String ipAts) {
        mIpAtsTT = ipAts;
        prefs.edit().putString(Prefs.IP_ATS_TT, mIpAtsTT).apply();
    }

    public String getIpDnsTT() {
        return mIpDnsTT;
    }

    public void setIpDnsTT(String ipDns) {
        mIpDnsTT = ipDns;
        prefs.edit().putString(Prefs.IP_DNS_TT, mIpDnsTT).apply();
    }

    public String getServerDomainName() {
        return serverDomainName;
    }

    public void setServerDomainName(String serverDomainName) {
        this.serverDomainName = serverDomainName;
        prefs.edit().putString(Prefs.SERVER_DOMAIN, serverDomainName).apply();
    }

    /** Вспомогательные методы **/

    public void load(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(Prefs.NAME_FILE_PREFS, Context.MODE_PRIVATE);

        /** Общие **/
        mRegimeSelected     = prefs.getInt(Prefs.REGIME_SELECTED, REGIME_NONE);
        isLicenseReg        = prefs.getBoolean(Prefs.LICENSE_REG, false);
        mLicenseType        = prefs.getString(Prefs.LICENSE_TYPE, "");
        mLicenseKey         = prefs.getString(Prefs.LICENSE_KEY, "");
        mHashPass           = prefs.getString(Prefs.HASH_PASS, null);
        isSelectCodecs      = prefs.getBoolean(Prefs.SELECT_CODEC, false);
        isAuth              = false;
        isAppBackground     = false;

        serverDomainName    = prefs.getString(Prefs.SERVER_DOMAIN, "impulse.ru");

        /** Режим Портал **/
        isCfgEnterP         = prefs.getBoolean(Prefs.CONFIG_ENTER_P, false);
        mIdP                = prefs.getString(Prefs.ID_P, "");
        mSignatureP         = prefs.getString(Prefs.SIGNATURE_P, "");
        mPhoneP             = prefs.getString(Prefs.PHONE_P, "");
        mIpAtsP             = prefs.getString(Prefs.IP_ATS_P, "");
        mIpDnsP             = prefs.getString(Prefs.IP_DNS_P, "");
        mIpDnsSecondP       = prefs.getString(Prefs.IP_DNS_SECOND_P, "");
        mIpSkzi             = prefs.getString(Prefs.IP_SKZI, "");
        mIpMon              = prefs.getString(Prefs.IP_MON, "");
        isKeyEnter          = prefs.getBoolean(Prefs.KEY_ENTER, false);

        /** Режим Точка-Точка **/
        isCfgEnterTT         = prefs.getBoolean(Prefs.CONFIG_ENTER_TT, false);
        mIdTT               = prefs.getString(Prefs.ID_TT, "");
        mSignatureTT        = prefs.getString(Prefs.SIGNATURE_TT, "");
        mPhoneTT            = prefs.getString(Prefs.PHONE_TT, "");
        mIpAtsTT            = prefs.getString(Prefs.IP_ATS_TT, "");
        mIpDnsTT            = prefs.getString(Prefs.IP_DNS_TT, "");
    }

    public void deleteConfig(Context context) {
        mHashPass = null;
        prefs.edit().clear().apply();
        /*File file = new File(context.getApplicationInfo().dataDir + "/shared_prefs/" + Prefs.NAME_FILE_PREFS + ".xml");
        file.delete();*/
    }
}
