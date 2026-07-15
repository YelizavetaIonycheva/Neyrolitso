package org.pniei.portal.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import org.pniei.portal.R;
import org.pniei.portal.utils.Logger;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;

public class LauncherActivity extends AppCompatActivity {
    private final String TAG = "LauncherActivity";
    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Utils.initTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);
        mHandler = new Handler(Looper.getMainLooper());

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        initApp();
    }

    private void initApp() {
        new Thread(() -> {
            if (PrefsUtils.ins().isAuth()) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Intent newIntent = new Intent(LauncherActivity.this, MainActivity.class);
                startActivity(newIntent);
                finish();
                return;
            }

            PrefsUtils.ins().load(this);
            Utils.copyAssetsFiles(this, "sound", "");
            Logger.inc().init(this);

            mHandler.post(() -> {
                Intent newIntent = new Intent(LauncherActivity.this, LoginActivity.class);
                startActivity(newIntent);
                finish();
            });
        }).start();
    }
}