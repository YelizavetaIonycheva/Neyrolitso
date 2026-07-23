package org.pniei.portal.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;

import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.pniei.portal.R;
import org.pniei.portal.database.SpoFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {

    public static String getError(String error) {
        switch (error) {
            case "101":
                return "Данные не получены";
            case "102":
                return "Данные не являются JSON-объектом";
            case "103":
                return "Неполный набор данных";
            case "201":
                return "Ошибка авторизации";
            case "301":
                return "Попытка добавления собственного номера в список контактов";
            case "302":
                return "Пользователь не существует";
            case "303":
                return "Пользователь уже добавлен в ваш список контактов";
            case "304":
                return "Произошла ошибка при вставке данных в БД";
            case "401":
                return "Файл не найден";
            case "402":
                return "Не удалось удалить файл";
            case "403":
                return "Не удалось загрузить файл на сервер";
            case "404":
                return "Файл не получен";
            case "501":
                return "Нет адресатов для получения сообщения";
            case "502":
                return "Отсутствует содержимое сообщения";
        }
        return "Неизвестная ошибка";
    }

    public static String getContactPhotoDir(Context context) {
<<<<<<< HEAD
        return context.getApplicationInfo().dataDir + "/contact_photo_p/";
=======
        if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P) {
            return context.getApplicationInfo().dataDir + "/contact_photo_p/";
        } else {
            return context.getApplicationInfo().dataDir + "/contact_photo_tt/";
        }
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
    }

    public static String getAppDataDir(Context context) {
        return context.getApplicationInfo().dataDir;
    }

    public static String getFirmwareDir(Context context) {
        return context.getApplicationInfo().dataDir + "/firmware";
    }

    public static String getMimeType(String nameFile) {
        String type = null;
        if (nameFile.lastIndexOf(".") != -1) {
            String ext = nameFile.substring(nameFile.lastIndexOf(".") + 1);
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            type = mime.getMimeTypeFromExtension(ext);
        }

        return type;
    }

    public static int getTypeFile(String nameFile) {
        String[] parts = nameFile.split("\\.");
        if (parts.length <= 1)
            return SpoFile.TYPE_OTHER;

        String format = parts[parts.length - 1];
        if (format.equals("jpeg") || format.equals("jpg") || format.equals("png") || format.equals("bmp")) {
            return SpoFile.TYPE_IMAGE;
        } else if (format.equals("m4a")) {
            return SpoFile.TYPE_VOICE;
        } else {
            return SpoFile.TYPE_OTHER;
        }
    }

    private static final String[] months = {"января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    @SuppressLint("SimpleDateFormat")
    public static String timestampToHumanDate(Context context, long timestamp) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(timestamp);

            SimpleDateFormat dateFormat;
            if (isToday(cal)) {
                dateFormat = new SimpleDateFormat(context.getResources().getString(R.string.today_date_format));
            } else if (isYesterday(cal)) {
                return "Вчера";
            } else {
                dateFormat = new SimpleDateFormat(context.getResources().getString(R.string.messages_date_format));
            }

            return dateFormat.format(cal.getTime());
        } catch (NumberFormatException nfe) {
            return String.valueOf(timestamp);
        }
    }

    public static String timeDividerToHumanDate(long timestamp) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(timestamp);

            if (isToday(cal)) {
                return "сегодня";
            } else if (isYesterday(cal)) {
                return "вчера";
            } else {
                return cal.get(Calendar.DAY_OF_MONTH) +
                        " " +
                        months[cal.get(Calendar.MONTH)] +
                        " " +
                        cal.get(Calendar.YEAR) +
                        " Г.";
            }
        } catch (NumberFormatException nfe) {
            return String.valueOf(timestamp);
        }
    }

    @SuppressLint("SimpleDateFormat")
    public static String timeHistoryToHumanDate(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        StringBuilder sb = new StringBuilder();

        if (isToday(cal)) {
            sb.append("Сегодня, ");
        } else if (isYesterday(cal)) {
            sb.append("Вчера, ");
        } else {
            sb.append(cal.get(Calendar.DAY_OF_MONTH));
            sb.append(" ");
            sb.append(months[cal.get(Calendar.MONTH)]);
            sb.append(", ");
        }

        sb.append(new SimpleDateFormat("HH:mm").format(timestamp));
        return sb.toString();
    }

    public static String secondsToDisplayableString(int secs) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        cal.set(0, 0, 0, 0, 0, secs);
        return dateFormat.format(cal.getTime());
    }

    private static boolean isToday(Calendar cal) {
        return isSameDay(cal, Calendar.getInstance());
    }

    private static boolean isYesterday(Calendar cal1) {
        Calendar cal2 = Calendar.getInstance();

        return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) + 1 == cal2.get(Calendar.DAY_OF_YEAR));
    }

    private static boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) {
            return false;
        }

        return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
    }

    public static float dpToPx(float value, Context context) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics());
    }

    final protected static char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static String byteArrayToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;

        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }

    public static byte[] hexStringToByteArray(String s) {
        if (s == null) return null;

        int len = s.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }

        return data;
    }

    public static byte[] intToByteArray(int value) {
        byte[] buf = new byte[4];
        buf[0] = (byte) (value & 0x000000FF);
        buf[1] = (byte) (value >> 8 & 0x000000FF);
        buf[2] = (byte) (value >> 16 & 0x000000FF);
        buf[3] = (byte) (value >> 24 & 0x000000FF);

        return buf;
    }

    public static int byteArrayToInt(byte[] value) {
        if (value == null || value.length != 4)
            return 0;

        return (value[0] & 0x000000FF) |
                (value[1] << 8 & 0x0000FF00) |
                (value[2] << 16 & 0x00FF0000) |
                (value[3] << 24 & 0xFF000000);
    }

    public static String intToHexString(int a) {
        String sb = String.format("%02x", a >> 24 & 0xFF) +
                String.format("%02x", a >> 16 & 0xFF) +
                String.format("%02x", a >> 8 & 0xFF) +
                String.format("%02x", a & 0xFF);
        return sb.toUpperCase();
    }

    public static int serArrayToInt(byte[] value) {
        if (value == null || value.length != 8)
            return 0;

        return (value[4] & 0x000000FF) |
                (value[5] << 8 & 0x0000FF00) |
                (value[6] << 16 & 0x00FF0000) |
                (value[7] << 24 & 0xFF000000);
    }

    public static String convertSerToStr(int ser) {
        final int LEN_STR = 8;
        final String STR_ZERO = "00000000";
        String serStr = ser + "";

        if (serStr.length() < LEN_STR) {
            serStr = STR_ZERO.substring(0, (LEN_STR - serStr.length())) + serStr;
        }
        return serStr;
    }

    public static String convertComplToStr(int compl) {
        final int LEN_STR = 3;
        final String STR_ZERO = "000";
        String complStr = compl + "";

        if (complStr.length() < LEN_STR) {
            complStr = STR_ZERO.substring(0, (LEN_STR - complStr.length())) + complStr;
        }
        return complStr;
    }

    public static boolean checkIP(String ipAddress) {
        String[] fields = ipAddress.split("\\.");
        if ((fields.length == 1) && (fields[0].compareTo("") == 0))
            return true;
        if (fields.length != 4)
            return false;
        try {
            for (int i = 0; i < 4; i++) {
                if (Integer.parseInt(fields[i]) < 0 || Integer.parseInt(fields[i]) > 255)
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static String getFileName(Context context, Uri uriFile) {
        String path = FileUtils.getPath(context, uriFile);
        if (path != null) {
            final String[] split = path.split("/");
            return split[split.length - 1];
        } else {
            return null;
        }
    }

    public static String getFileName(String path) {
        if (path != null) {
            final String[] split = path.split("/");
            return split[split.length - 1];
        } else {
            return null;
        }
    }

    public static File createReceivedFile(Context context, String fileName) {
        File rootDir;
        if (android.os.Build.VERSION.SDK_INT < 29) {
            rootDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), context.getString(R.string.app_name) + "/Recieved");
            if (!rootDir.exists())
                rootDir.mkdirs();

        } else {
            rootDir = new File(context.getExternalMediaDirs()[0].getAbsolutePath(), "/Recieved");
            if (!rootDir.exists())
                rootDir.mkdirs();
        }
        return new File(rootDir.getAbsolutePath(), fileName);
    }

    public static File createSendedFile(Context context, String fileName) {
        File rootDir;
        if (android.os.Build.VERSION.SDK_INT < 29) {
            rootDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), context.getString(R.string.app_name) + "/Sended");
            //rootDir = new File(Environment.getExternalStorageDirectory(), context.getString(R.string.app_name) + "/Sended");
<<<<<<< HEAD
=======
            if (!rootDir.exists())
                rootDir.mkdirs();
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db

        } else {
            rootDir = new File(context.getExternalMediaDirs()[0].getAbsolutePath(), "/Sended");
            //rootDir = new File(context.getExternalFilesDir(null).getAbsolutePath(), context.getString(R.string.app_name) + "/Sended");
<<<<<<< HEAD
        }
        if (!rootDir.exists())
            rootDir.mkdirs();
=======
            if (!rootDir.exists())
                rootDir.mkdirs();
        }
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
        return new File(rootDir.getAbsolutePath(), fileName);
    }

    public static File getRootDir(Context context) {
        File rootDir;

        if (android.os.Build.VERSION.SDK_INT < 29) {
            rootDir = new File(Environment.getExternalStorageDirectory(), "/SPO_MP_Files");
            if (!rootDir.exists())
                rootDir.mkdirs();

        } else {
            rootDir = context.getExternalFilesDir(null);
        }
        return rootDir;
    }

    public static void hideKeyboardFrom(Context context, View view) {
        if (context != null && view != null) {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void hideKeyboard(Activity activity) {
        if (activity != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            View view = activity.getCurrentFocus();
            if (view == null) {
                view = new View(activity);
            }
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : Objects.requireNonNull(fileOrDirectory.listFiles()))
                deleteRecursive(child);

        fileOrDirectory.delete();
    }

    public static void deleteRecursive(String path) {
        File fileOrDirectory = new File(path);
        if (fileOrDirectory.isDirectory())
            for (File child : Objects.requireNonNull(fileOrDirectory.listFiles()))
                deleteRecursive(child);

        fileOrDirectory.delete();
    }

    public static String getUriPath(Context context, Uri uri) {
        String path = null;
        String strUri = uri.toString();

        if (strUri.startsWith("file://")) {
            return strUri.substring(7);
        }

        try {
            Uri docUriTree = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            if (docUriTree != null) {
                Cursor cursor = context.getContentResolver().query(docUriTree, null, null, null, null);
                if (cursor != null) {
                    cursor.moveToFirst();
                    path = cursor.getString(0);
                    path = path.substring(path.lastIndexOf(":") + 1);
                    cursor.close();
                }
            }
        } catch (IllegalArgumentException e) {
            return null;
        }

        return path;
    }

    public static void copyAssetsFiles(Context context, String copyDir, String dirIn) {
        if (context != null) {
            try {
<<<<<<< HEAD
                String[] assets = context.getAssets().list(copyDir);
                assert assets != null;
=======
                String assets[] = context.getAssets().list(copyDir);
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
                if (assets.length == 0) {
                    copyFile(context, copyDir, dirIn);
                } else {
                    File dir = new File(context.getApplicationInfo().dataDir + "/" + copyDir);
                    if (!dir.exists())
                        dir.mkdir();
                    for (String asset : assets)
                        copyAssetsFiles(context, copyDir + "/" + asset, dirIn);
                }
            } catch (IOException ex) {
                Log.e("", "I/O Exception", ex);
            }
        }
    }

    private static void copyFile(Context context, String fileName, String dirIn) {
        if (context != null) {
            Log.i("", "Copying file " + fileName);
            try {
                File file = new File(context.getApplicationInfo().dataDir + "/" + fileName);
                if (!file.exists()) {
                    InputStream in = context.getAssets().open(fileName);
                    OutputStream out = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        out = Files.newOutputStream(file.toPath());
                    }
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        assert out != null;
                        out.write(buffer, 0, read);
                    }
                }
            } catch (Exception e) {
                Log.e("", Objects.requireNonNull(e.getMessage()));
            }
        }
    }

    public static Bitmap getCroppedBitmap(Bitmap bitmap, Integer cx, Integer cy, Integer radius) {
        int diam = radius << 1;
        Bitmap targetBitmap = Bitmap.createBitmap(diam, diam, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(targetBitmap);
        final int color = 0xffFFFFFF;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawCircle(radius, radius, radius, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, -cx + radius, -cy + radius, paint);
        return targetBitmap;
    }

    public static byte[] idUserToByteArray(int value) {
        byte[] buf = new byte[10];
        buf[0] = (byte) (value & 0x000000FF);
        buf[1] = (byte) (value >> 8 & 0x000000FF);
        buf[2] = (byte) (value >> 16 & 0x000000FF);
        buf[3] = (byte) (value >> 24 & 0x000000FF);

        return buf;
    }

    public static String parsingDate(byte[] date) {
        if (date.length < 3)
            return "";

        if (date[1] == 0 || date[2] == 0)
            return "";

        StringBuilder sb = new StringBuilder();
        if (date[2] < 10)
            sb.append("0");
        sb.append(date[2]);
        sb.append(".");
        if (date[1] < 10)
            sb.append("0");
        sb.append(date[1]);
        sb.append(".");
        if (date[0] < 10)
            sb.append("0");
        sb.append(date[0]);
        return sb.toString();
    }

    public static void initTheme(Activity activity) {
<<<<<<< HEAD
        activity.setTheme(R.style.AppThemeP);
=======
        int regime = PrefsUtils.ins().getRegimeSelected();

        if (regime == PrefsUtils.REGIME_TT) {
            activity.setTheme(R.style.AppThemeTT);
        } else {
            activity.setTheme(R.style.AppThemeP);
        }
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
    }

    private static AlertDialog mWaitDialog = null;

    public static void showWaitDialog(Context context, String title) {
        closeWaitDialog();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        if (title != null)
            builder.setTitle(title);
        mWaitDialog = builder.setView(R.layout.dialog_wait)
                .setCancelable(false)
                .show();
    }

    public static void closeWaitDialog() {
        if (mWaitDialog != null) {
            mWaitDialog.dismiss();
            mWaitDialog = null;
        }
    }

    public static byte[] readFileFromZip(Context context, DocumentFile zipFile, String suffix) {
        byte[] buffer = new byte[1024];
        byte[] data;
        try {
            ZipInputStream zis = new ZipInputStream(context.getContentResolver().openInputStream(zipFile.getUri()));
            ZipEntry zipEntry = zis.getNextEntry();

            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {
                    if (zipEntry.getName().endsWith(suffix)) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            bos.write(buffer, 0, len);
                        }

                        data = bos.toByteArray();
                        bos.close();
                        zis.close();
                        return data;
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
<<<<<<< HEAD
}
=======

}
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
