package org.pniei.portal.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public class ConfigLoader {
    private static final String TAG = "ConfigLoader";

    /**
     * Ищет файл конфигурации в папке Downloads, парсит и сохраняет в PrefsUtils.
     * @return true если конфиг успешно загружен, false в противном случае.
     */
    public static boolean loadConfigFromDownloads(Context context) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir == null || !downloadsDir.exists()) {
            Log.e(TAG, "Downloads directory not found");
            return false;
        }

        File[] files = downloadsDir.listFiles((dir, name) ->
                name.startsWith("spomp") && name.endsWith(".cfg") && name.length() == 12);
        if (files == null || files.length == 0) {
            Log.e(TAG, "No config file found in Downloads");
            return false;
        }

        File configFile = files[0];
        Log.i(TAG, "Found config file: " + configFile.getName());

        try (FileInputStream fis = new FileInputStream(configFile)) {
            byte[] data = new byte[(int) configFile.length()];
            int read = fis.read(data);
            if (read != configFile.length()) {
                Log.e(TAG, "Failed to read full config file");
                return false;
            }

            String json = new String(data, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);

            // Парсим параметры
            String id = obj.getString("id");
            String signature = obj.getString("signature");
            String phone = obj.getString("phone");
            String ip_skzi = obj.optString("ip_skzi", ""); // не используется, но может присутствовать
            String ip_ats = obj.getString("ip_ats");
            String ip_dns = obj.getString("ip_dns");
            String ks = obj.getString("ks");

            // Проверка CRC32: склеиваем все параметры без разделителей
            String dataForCrc = id + signature + phone + ip_skzi + ip_ats + ip_dns;
            CRC32 crc = new CRC32();
            crc.update(dataForCrc.getBytes());
            String computed = Long.toHexString(crc.getValue()).toUpperCase();
            if (!computed.equals(ks.toUpperCase())) {
                Log.e(TAG, "CRC mismatch. Expected: " + ks + ", computed: " + computed);
                return false;
            }

            PrefsUtils prefs = PrefsUtils.ins();
            prefs.saveConfig(id, signature, phone, ip_ats, ip_dns, "");

            Log.i(TAG, "Config loaded successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error loading config", e);
            return false;
        }
    }
}