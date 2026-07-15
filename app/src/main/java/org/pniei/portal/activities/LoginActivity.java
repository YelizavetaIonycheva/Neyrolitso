package org.pniei.portal.activities;

import static android.content.Intent.ACTION_MAIN;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.PayloadType;
import org.pniei.portal.R;
import org.pniei.portal.fragments.AuthenticationFragment;
import org.pniei.portal.fragments.RegistrationFragment;
import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private Handler mHandler;
    private ServiceWaitThread mServiceThread;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Utils.initTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        mHandler = new Handler(Looper.getMainLooper());

        if (checkAndRequestNotifyPermission()) {
            fragmentShow();
        }
    }

    void fragmentShow() {
        boolean isRegistered = PrefsUtils.ins().getHashPass() != null;
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = isRegistered ? AuthenticationFragment.newInstance(this) : RegistrationFragment.newInstance(this);
        fm.beginTransaction().add(R.id.fragmentContainer, fragment).commit();
    }

    public void displayFragment(Fragment fragment, boolean toBackStack) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        if (toBackStack) transaction.addToBackStack(null);

        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }

    public void loginOk() {
        PrefsUtils.ins().setAuth(true);

        if (LinphoneService.isReady()) {
            onServicesReady();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));
            } else {
                startService(new Intent(ACTION_MAIN).setClass(this, LinphoneService.class));
            }

            SpoMessagesService.start(getApplicationContext(), PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P ? PrefsUtils.ins().getIdP() : PrefsUtils.ins().getIdTT(), PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P ? PrefsUtils.ins().getSignatureP() : PrefsUtils.ins().getSignatureTT());

            mServiceThread = new ServiceWaitThread();
            mServiceThread.start();
        }
    }

    protected void onServicesReady() {
        mHandler.post(() -> {
            Intent newIntent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(newIntent);
            finish();
        });
    }

    private class ServiceWaitThread extends Thread {
        public void run() {
            while (!(LinphoneService.isReady() && SpoMessagesService.isReady())) {
                try {
                    sleep(30);
                } catch (InterruptedException e) {
                    throw new RuntimeException("waiting thread sleep() has been interrupted");
                }
            }

            boolean isSelected = PrefsUtils.ins().isSelectCodecs();

            LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            if (!isSelected) {
                assert lc != null;
                for (PayloadType pt : lc.getAudioCodecs()) {
                    try {
                        lc.enablePayloadType(pt, false);
                    } catch (LinphoneCoreException ignored) {
                    }
                }

                PayloadType pt3 = lc.getAudioCodecs()[0];
                try {
                    lc.enablePayloadType(pt3, true);
                } catch (LinphoneCoreException ignored) {
                }

                for (PayloadType pt : lc.getVideoCodecs()) {
                    try {
                        lc.enablePayloadType(pt, false);
                    } catch (LinphoneCoreException ignored) {
                    }
                }
                PayloadType pt4 = lc.getVideoCodecs()[0];
                try {
                    lc.enablePayloadType(pt4, true);
                } catch (LinphoneCoreException ignored) {
                }
                PrefsUtils.ins().setSelectCodecs(true);
            }

            onServicesReady();
            mServiceThread = null;
        }
    }

    private boolean checkAndRequestNotifyPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsResultCallback.launch(Manifest.permission.POST_NOTIFICATIONS);
                return false;
            } else return true;
        } else {
            return true;
        }
    }

    private final ActivityResultLauncher<String> permissionsResultCallback = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (isGranted) {
            fragmentShow();
        } else {
            new MaterialAlertDialogBuilder(this).setTitle("Внимание").setMessage("Для работы приложения необходимо разрешение на отправку уведомлений.").setNegativeButton("Отмена", (dialog, which) -> {
            }).setPositiveButton("Разрешить", (dialog, which) -> checkAndRequestNotifyPermission()).show();
        }
    });
}