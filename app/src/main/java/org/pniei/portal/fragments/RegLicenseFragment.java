package org.pniei.portal.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import org.json.JSONException;
import org.json.JSONObject;
import org.pniei.portal.R;
import org.pniei.portal.databinding.RegLicenseFragmentBinding;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

public class RegLicenseFragment extends Fragment {
    private static final String TAG = "RegLicenseFragment";
    //private ActivityResultLauncher<Intent> selectKeyResultLauncher;
    private ActivityResultLauncher<Intent> selectZipResultLauncher;
    /*private ActivityResultLauncher<Intent> qrcodeActivityResultLauncher;*/
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;
    private Handler mHandler;
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
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RegLicenseFragmentBinding mBinding = DataBindingUtil.inflate(inflater, R.layout.reg_license_fragment, container, false);
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

    private REG_LIC_STATUS checkRequestLicense(JSONObject result) {
        final String TYPE = "l_type", ID_DEV = "id_dev", LICENSE = "license";
        if (result != null) {
            try {
                if (result.has("error"))
                    return REG_LIC_STATUS.NOT_REG;
                @SuppressLint("HardwareIds") String deviceId = android.provider.Settings.Secure.getString(requireContext().getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
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
