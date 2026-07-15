package org.pniei.portal.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.json.JSONException;
import org.json.JSONObject;
import org.pniei.portal.R;
import org.pniei.portal.activities.LoginActivity;
import org.pniei.portal.databinding.RegLicenseFragmentBinding;
import org.pniei.portal.utils.CryptUtils;
import org.pniei.portal.utils.NetworkRequestUtils;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.vpn.VpnClient;

import java.io.InputStream;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

public class RegLicenseFragment extends Fragment {
    private static final String TAG = "RegLicenseFragment";
    //private ActivityResultLauncher<Intent> selectKeyResultLauncher;
    private ActivityResultLauncher<Intent> selectZipResultLauncher;
    /*private ActivityResultLauncher<Intent> qrcodeActivityResultLauncher;*/
    private static Context mContext;
    private Handler mHandler;
    private RegLicenseFragmentBinding mBinding;
    //private DocumentFile selectedZipFile = null;
    //private AlertDialog dialogRegLicense;

    private enum REG_LIC_STATUS {
        OK,
        NOT_REG,
        ERROR
    }

    public static RegLicenseFragment newInstance(Context context) {
        mContext = context;
        return new RegLicenseFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.reg_license_fragment, container, false);
        mHandler = new Handler(Looper.getMainLooper());

        /*mBinding.btnScanQR.setOnClickListener(view -> {
            Intent newIntent = new Intent(mContext, QRCodeScanActivity.class);
            qrcodeActivityResultLauncher.launch(newIntent);
        });*/

        mBinding.btnSelectQR.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            selectZipResultLauncher.launch(intent);
        });

        /*registerQRCodeScanResult();*/
  /*      registerForSelectKeyResult();*/
        registerForSelectZipResult();
        return mBinding.getRoot();
    }

    /*
    public void showDialogRegLicense() {
        StringBuilder sb = new StringBuilder();
        sb.append("Тип: ").append(PrefsUtils.ins().getLicenseType()).append("\n")
                .append("Ключ: ").append(PrefsUtils.ins().getLicenseKey()).append("\n");

        dialogRegLicense = new MaterialAlertDialogBuilder(mContext)
                .setTitle(getString(R.string.dialog_reg_license_title))
                .setMessage(sb.toString())
                .setCancelable(false)
                .setView(R.layout.dialog_enter_zip)
                .setNegativeButton("Отмена", (dialog, which) -> Utils.hideKeyboardFrom(mContext, ((AlertDialog)dialog).findViewById(R.id.zipFile)))
                .setPositiveButton("Зарегистрировать", null)
                .show();

        dialogRegLicense.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            regLicenseProcess();
        });
        TextInputEditText zipDir = dialogRegLicense.findViewById(R.id.zipFile);
        zipDir.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);*/
            //intent.setType("*/*");
            /*selectKeyResultLauncher.launch(intent);
        });

    }
*/
    /*public void registerQRCodeScanResult() {
        qrcodeActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent resultData = result.getData();
                        if (resultData != null) {
                            int res = resultData.getIntExtra(QRCodeScanActivity.RESULT, QRCodeScanActivity.ERROR);

                            if (res == QRCodeScanActivity.OK) {
                                String qrData = resultData.getStringExtra(QRCodeScanActivity.DATA);
                                if (checkQRCodeData(qrData)) {
                                    showDialogRegLicense();
                                } else {
                                    Toast.makeText(mContext, "Некорректный QR-код", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(mContext, "Ошибка сканирования", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }*/

    private boolean checkQRCodeData(String qrData) {
        try {
            JSONObject jsonObject = new JSONObject(qrData);
            String licenseType = jsonObject.getString("l_type");
            String licenseKey = jsonObject.getString("l_key");
            String ipSKZI = jsonObject.getString("ip_skzi");
            String ipDNS = jsonObject.getString("ip_dns");
            String KS = jsonObject.getString("ks");

            StringBuilder sb = new StringBuilder();
            sb.append(licenseType)
                    .append(licenseKey)
                    .append(ipSKZI)
                    .append(ipDNS);
            byte [] license = sb.toString().getBytes();
            int crc = CryptUtils.CRC32(license, license.length);
            if(!(KS.equalsIgnoreCase(Utils.intToHexString(crc)))) {
                return false;
            }

            PrefsUtils.ins().setLicenseType(licenseType);
            PrefsUtils.ins().setLicenseKey(licenseKey);
            PrefsUtils.ins().setIpSkzi(ipSKZI);
            PrefsUtils.ins().setIpDnsP(ipDNS);
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    private REG_LIC_STATUS checkRequestLicense(JSONObject result) {
        final String TYPE = "l_type", ID_DEV = "id_dev", LICENSE = "license";
        if (result != null) {
            try {
                if (result.has("error"))
                    return REG_LIC_STATUS.NOT_REG;
                String deviceId = android.provider.Settings.Secure.getString(getContext().getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                if (result.getString(TYPE).equals(PrefsUtils.ins().getLicenseType())
                        && result.getString(ID_DEV).equals(deviceId)
                        && result.getString(LICENSE).equals("SUCCESS")) {
                    if (result.getString(TYPE).equals("custom")) {
                        PrefsUtils.ins().setIdP(result.getString(PrefsUtils.JsonKeysPrefs.ID));
                        PrefsUtils.ins().setSignatureP(result.getString(PrefsUtils.JsonKeysPrefs.SIGNATURE));
                        PrefsUtils.ins().setPhoneP(result.getString(PrefsUtils.JsonKeysPrefs.PHONE));
                        PrefsUtils.ins().setIpAtsP(result.getString(PrefsUtils.JsonKeysPrefs.IP_ATS) + ":5070");
                        if (result.has(PrefsUtils.JsonKeysPrefs.IP_SKZI))
                            PrefsUtils.ins().setIpSkzi(result.getString(PrefsUtils.JsonKeysPrefs.IP_SKZI));
                        if (result.has(PrefsUtils.JsonKeysPrefs.IP_MON))
                            PrefsUtils.ins().setIpMon(result.getString(PrefsUtils.JsonKeysPrefs.IP_MON));
                        PrefsUtils.ins().setCfgEnterP(true);
                    }
                    return REG_LIC_STATUS.OK;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return REG_LIC_STATUS.ERROR;
    }

    private void regLicenseProcess(DocumentFile selectedZipFile) {
        if (selectedZipFile == null)
            return;
        Utils.showWaitDialog(mContext, "Регистрация приложения");

        new Thread(() -> {
            // Анализ файла-картинки с QR-кодом

            byte[] fileImgQR = Utils.readFileFromZip(mContext, selectedZipFile, ".png");
            if (fileImgQR == null) {
                mHandler.post(() -> {
                    Utils.closeWaitDialog();
                    Toast.makeText(mContext, "Файл с QR-кодом не найден", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeByteArray(fileImgQR, 0, fileImgQR.length);
            if (bitmap == null) {
                mHandler.post(() -> {
                    Utils.closeWaitDialog();
                    Toast.makeText(mContext, "Некорректный файл", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            /*Bitmap bmOverlay = Bitmap.createBitmap(bitmap.getWidth() + 32, bitmap.getHeight() + 32, bitmap.getConfig());
            Canvas canvas = new Canvas(bmOverlay);
            canvas.drawRGB(0xFF, 0xFF, 0xFF);
            canvas.drawBitmap(bitmap, 16, 16, null);*/

            InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
            BarcodeScannerOptions barcodeOptions = new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build();

            BarcodeScanning.getClient(barcodeOptions)
                    .process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            String rawValue = barcode.getRawValue();
                            if (checkQRCodeData(rawValue)) {
                                byte[] keyData = Utils.readFileFromZip(mContext, selectedZipFile, ".keyreg");

                                if (keyData == null) {
                                    mHandler.post(() -> {
                                        Utils.closeWaitDialog();
                                        mHandler.post(() -> Toast.makeText(mContext, getString(R.string.err_file_key_reg_not_found), Toast.LENGTH_SHORT).show());
                                    });
                                    return;
                                }

                                if (!VpnClient.ins().loadAndRefreshKeys(keyData)) {
                                    mHandler.post(() -> {
                                        Utils.closeWaitDialog();
                                        mHandler.post(() -> Toast.makeText(mContext, getString(R.string.err_key_save), Toast.LENGTH_SHORT).show());
                                    });
                                    return;
                                }

                                if (!VpnClient.startVpnService(getActivity())) {
                                    mHandler.post(() -> {
                                        Utils.closeWaitDialog();
                                        mHandler.post(() -> Toast.makeText(mContext, getString(R.string.bad_ip_skzi_address), Toast.LENGTH_SHORT).show());
                                    });
                                    return;
                                }

                                new Thread(() -> {
                                    String deviceId = android.provider.Settings.Secure.getString(getContext().getContentResolver(), android.provider.Settings.Secure.ANDROID_ID); // DEBUG
                                    long startTime = System.currentTimeMillis();
                                    // 20 секунд на выполнение
                                    while (System.currentTimeMillis() - startTime < 20000) {
                                        if (VpnClient.ins().isConnected()) {
                                            JSONObject result = NetworkRequestUtils.checkLicense(PrefsUtils.ins().getLicenseType(), PrefsUtils.ins().getLicenseKey(), deviceId);
                                            /*JSONObject result = null;
                                            try {
                                                String res = "{" +
                                                        "\"l_type\": \"custom\"," +
                                                        "\"id_dev\": \"" + deviceId + "\"," +
                                                        "\"license\": \"SUCCESS\"," +
                                                        "\"id\": \"14\"," +
                                                        "\"signature\": \"TYq9SX0YZc\"," +
                                                        "\"phone\": \"235\"," +
                                                        "\"ip_ats\": \"172.16.10.223\"," +
                                                        "\"ip_skzi\": \"31.173.180.102\"," +
                                                        "\"error\": 200" +
                                                        "}";
                                                result = new JSONObject(res);
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }*/

                                            REG_LIC_STATUS status = checkRequestLicense(result);

                                            if (status == REG_LIC_STATUS.OK) {
                                                PrefsUtils.ins().setLicenseReg(true);
                                                mHandler.post(() -> {
                                                    Utils.closeWaitDialog();
                                                    Toast.makeText(mContext, "Лицензионный ключ зарегистрирован", Toast.LENGTH_SHORT).show();
                                                    Intent newIntent = new Intent(mContext, LoginActivity.class);
                                                    startActivity(newIntent);
                                                    getActivity().finish();
                                                });
                                                break;
                                            } else if (status == REG_LIC_STATUS.NOT_REG) {
                                                break;
                                            } else {
                                                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                                                continue;
                                            }
                                        }
                                        try {
                                            Thread.sleep(500);
                                        } catch (InterruptedException e) {
                                        }
                                    }
                                    if (!PrefsUtils.ins().isLicenseReg()) {
                                        mHandler.post(() -> {
                                            Utils.closeWaitDialog();
                                            Toast.makeText(mContext, "Ключ не зарегистрирован", Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                    VpnClient.stopVpnService(mContext);
                                    return;
                                }).start();
                                return;
                            }
                        }
                        Utils.closeWaitDialog();
                        mHandler.post(() -> Toast.makeText(mContext, "Некорректный QR-код", Toast.LENGTH_SHORT).show());
                    })
                    .addOnFailureListener(e -> {
                        e.printStackTrace();
                    });
        }).start();
    }

    public void registerForSelectZipResult() {
        selectZipResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent resultData = result.getData();
                        if (resultData != null) {
                            Uri fileUri = resultData.getData();
                            DocumentFile selectedZipFile = DocumentFile.fromSingleUri(mContext, fileUri);
                            if (selectedZipFile.exists()) {
                                regLicenseProcess(selectedZipFile);
                            }
                        }
                    }
                });
    }
/*
    public void registerForSelectKeyResult() {
        selectKeyResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent resultData = result.getData();
                        if (resultData != null) {
                            Uri fileUri = resultData.getData();
                            selectedZipFile = DocumentFile.fromSingleUri(mContext, fileUri);
                            if (dialogRegLicense != null) {
                                TextInputEditText zipFile = dialogRegLicense.findViewById(R.id.zipFile);
                                TextInputLayout zipFileLayout = dialogRegLicense.findViewById(R.id.zipFileLayout);
                                zipFile.setText(selectedZipFile.getName());
                                zipFileLayout.setError(null);
                            }
                        }
                    }
                });
    }

    private boolean readVpnKey() {
        byte [] keyData;
        if(selectedZipFile == null)
            return false;

        keyData = new byte [(int) selectedZipFile.length()];
        try {
            InputStream fin = mContext.getContentResolver().openInputStream(selectedZipFile.getUri());
            if (fin.read(keyData) != selectedZipFile.length())
                return false;
            fin.close();
            return VpnClient.ins().loadAndRefreshKeys(keyData);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }*/
}
