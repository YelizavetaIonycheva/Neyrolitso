package org.pniei.portal.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.pniei.portal.R;
import org.pniei.portal.fragments.RegLicenseFragment;
import org.pniei.portal.vpn.VpnClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class RegLicenseActivity extends AppCompatActivity {
    private static final String KEY_FILE_FROM_APP = "key_file_from_app";
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reg_license);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, RegLicenseFragment.newInstance(this))
                .commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VpnClient.REQUST_VPN && resultCode == RESULT_OK) {
            if (!VpnClient.startVpnService(this)) {
                // Toast с сообщением о некорректных IP адресах
                Toast.makeText(this, R.string.bad_ip_skzi_address, Toast.LENGTH_SHORT);
            }
        }
    }
}
