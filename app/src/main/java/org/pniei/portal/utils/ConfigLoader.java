package org.pniei.portal.utils;

import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.util.zip.CRC32;

public class ConfigLoader {
    private static final String TAG = "ConfigLoader";

    public static class ConfigData {
        public String id;
        public String signature;
        public String phone;
        public String ip_skzi;
        public String ip_ats;
        public String ip_dns;
    }

    public static ConfigData loadConfig(File configFile) {
        try {
            String json = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                json = new String(Files.readAllBytes(configFile.toPath()));
            }
            JSONObject obj = new JSONObject(json);

            String id = obj.getString("id");
            String signature = obj.getString("signature");
            String phone = obj.getString("phone");
            String ip_skzi = obj.optString("ip_skzi", "");
            String ip_ats = obj.getString("ip_ats");
            String ip_dns = obj.getString("ip_dns");
            String ks = obj.getString("ks");

            // Проверка CRC32
            String data = id + signature + phone + ip_skzi + ip_ats + ip_dns;
            CRC32 crc = new CRC32();
            crc.update(data.getBytes());
            String computed = Long.toHexString(crc.getValue()).toUpperCase();
            if (!computed.equals(ks.toUpperCase())) {
                Log.e(TAG, "CRC32 mismatch. Expected: " + ks + ", computed: " + computed);
                return null;
            }

            ConfigData config = new ConfigData();
            config.id = id;
            config.signature = signature;
            config.phone = phone;
            config.ip_skzi = ip_skzi;
            config.ip_ats = ip_ats;
            config.ip_dns = ip_dns;
            return config;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config", e);
            return null;
        }
    }
}