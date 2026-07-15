package org.pniei.portal.utils;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ConfigFileUtils {
    private static final String TAG = "ConfigFileUtils";
    private static final String CONFIG_FILE_PREFIX = "spomp";
    private static final String CONFIG_FILE_EXTENSION = ".cfg";

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static JSONObject loadConfigFromDownloads(Context context) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir == null || !downloadsDir.exists()) {
            Log.e(TAG, "Папка Downloads не найдена.");
            return null;
        }

        File[] files = downloadsDir.listFiles((dir, name) -> name.startsWith(CONFIG_FILE_PREFIX) && name.endsWith(CONFIG_FILE_EXTENSION));

        if (files == null || files.length == 0) {
            Log.e(TAG, "Файлы конфигурации не найдены в папке Downloads.");
            return null;
        }

        // Используем первый найденный файл
        // (spompXXX.cfg, где XXX - номер телефона)
        File configFile = files[0];
        Log.i(TAG, "Найден файл конфигурации: " + configFile.getName());

        try (InputStream inputStream = Files.newInputStream(configFile.toPath())) {
            byte[] data = new byte[(int) configFile.length()];
            int bytesRead = inputStream.read(data);
            if (bytesRead != configFile.length()) {
                Log.e(TAG, "Ошибка чтения файла конфигурации.");
                return null;
            }
            String jsonString = new String(data, StandardCharsets.UTF_8);
            return new JSONObject(jsonString);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Ошибка загрузки или парсинга файла конфигурации.", e);
            return null;
        }
    }
}