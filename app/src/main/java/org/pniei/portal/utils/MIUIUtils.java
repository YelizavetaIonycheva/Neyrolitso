package org.pniei.portal.utils;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

public class MIUIUtils {


    public static boolean canDrawOverlayViews(Context context) {
        try {
            return Settings.canDrawOverlays(context) && isFloatWindowOptionAllowed(context) && canBackgroundStart(context) && canShowWhenLocked(context);
        } catch (NoSuchMethodError e) {
            return canDrawOverlaysUsingReflection(context);
        }
    }

    private static boolean isFloatWindowOptionAllowed(Context context) {
        AppOpsManager manager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int m;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            m = manager.checkOp(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, Integer.valueOf(Binder.getCallingUid()), context.getPackageName());
        } else {
            m = manager.unsafeCheckOp(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, Integer.valueOf(Binder.getCallingUid()), context.getPackageName());
        }

        return m == AppOpsManager.MODE_ALLOWED;
    }

    public static boolean canBackgroundStart(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        try {
            int op = 10021; // >= 23
            // ops.checkOpNoThrow(op, uid, packageName)
            Method method = ops.getClass().getMethod("checkOpNoThrow", new Class[]
                    {int.class, int.class, String.class}
            );
            Integer result = (Integer) method.invoke(ops, op, Binder.getCallingUid(), context.getPackageName());
            return result == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            //Log.e(TAG, "not support", e);
        }
        return false;
    }

    public static boolean canShowWhenLocked(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        try {
            int op = 10020; // >= 23
            // ops.checkOpNoThrow(op, uid, packageName)
            Method method = ops.getClass().getMethod("checkOpNoThrow", new Class[]
                    {int.class, int.class, String.class}
            );
            Integer result = (Integer) method.invoke(ops, op, Integer.valueOf(Binder.getCallingUid()), context.getPackageName());
            return result == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            //Log.e(TAG, "not support", e);
        }
        return false;
    }

    public static boolean isMIUI() {
        return !TextUtils.isEmpty(getSystemProperty());
    }

    public static boolean isXiaomi() {
        return "xiaomi".equalsIgnoreCase(Build.MANUFACTURER);
    }

    public static void onDisplayPopupPermission(Context context) {
        if (!isMIUI()) {
            return;
        }

        try {
            // MIUI 8
            Intent localIntent = new Intent("miui.intent.action.APP_PERM_EDITOR");
            localIntent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
            localIntent.putExtra("extra_pkgname", context.getPackageName());
            context.startActivity(localIntent);
            return;
        } catch (Exception ignore) {
        }
        try {
            // MIUI 5/6/7
            Intent localIntent = new Intent("miui.intent.action.APP_PERM_EDITOR");
            localIntent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity");
            localIntent.putExtra("extra_pkgname", context.getPackageName());
            context.startActivity(localIntent);
            return;
        } catch (Exception ignore) {
        }
        // Otherwise jump to application details
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        context.startActivity(intent);
    }

    private static String getSystemProperty() {
        String line;
        BufferedReader input = null;
        try {
            java.lang.Process p = Runtime.getRuntime().exec("get prop " + "ro.miui.ui.version.name");
            input = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);
            line = input.readLine();
            input.close();
        } catch (IOException ex) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return line;
    }

    private static boolean canDrawOverlaysUsingReflection(Context context) {
        try {
            AppOpsManager manager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            Class<AppOpsManager> clazz = AppOpsManager.class;
            Method dispatchMethod = clazz.getMethod("checkOp", new Class[] { int.class, int.class, String.class });
            int mode = (Integer) dispatchMethod.invoke(manager, new Object[] { 24, Binder.getCallingUid(), context.getApplicationContext().getPackageName() });

            return AppOpsManager.MODE_ALLOWED == mode;

        } catch (Exception e) {  return false;  }
    }
}
