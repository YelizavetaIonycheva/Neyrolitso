package org.pniei.portal.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

<<<<<<< HEAD
=======
import org.pniei.dwface.biometry.DWFace;
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
import org.pniei.portal.R;
import org.pniei.portal.utils.ConfigLoader;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;

public class LauncherActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {

        Utils.initTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);
        initApp();
    }

    private void initApp() {
        new Thread(() -> {
            PrefsUtils.ins().load(this);
<<<<<<< HEAD
=======
            DWFace.Init(getApplicationContext());
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
            // Автоматическая загрузка конфига
            if (!ConfigLoader.loadConfigFromDownloads(this)) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Конфигурационный файл не найден в папке Downloads", Toast.LENGTH_LONG).show();
                    finish();
                });
                return;
            }
            runOnUiThread(() -> {
                Intent newIntent = new Intent(LauncherActivity.this, MainActivity.class);
                startActivity(newIntent);
                finish();
            });
        }).start();
    }
}
