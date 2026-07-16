package org.pniei.portal.qrcode;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.pniei.dwface.biometry.BiometryUtils;
import org.pniei.dwface.biometry.CameraFragment;
import org.pniei.portal.R;

public class QRCodeScanFragment extends Fragment implements  ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = "QRCodeScaneFragment";
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";
    private BarcodeScannerOptions mBarcodeOptions;
    private PreviewView mPreviewView;
    private Executor cameraExecutor = Executors.newSingleThreadExecutor();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        mBarcodeOptions = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        return inflater.inflate(R.layout.qrcode_scane_fragment, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mPreviewView = view.findViewById(R.id.texture);
        startCamera();
    }


    private void startCamera() {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
            }
        }, ContextCompat.getMainExecutor(getContext()));
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                int rotationDegrees = image.getImageInfo().getRotationDegrees();
                Bitmap bitmap = imageToBitmap(image, rotationDegrees);
                InputImage inputImage = InputImage.fromBitmap(bitmap, Surface.ROTATION_0);

                BarcodeScanner scanner = BarcodeScanning.getClient(mBarcodeOptions);
                Task<List<Barcode>> scanResult = scanner.process(inputImage)
                        .addOnSuccessListener(barcodes -> {
                            for (Barcode barcode: barcodes) {
                                String rawValue = barcode.getRawValue();
                                ((QRCodeScanActivity) getActivity()).returnData(rawValue);
                                return;
                            }
                            image.close();
                        })
                        .addOnFailureListener(e -> {
                            e.printStackTrace();
                            image.close();
                        });
        });

        cameraProvider.unbindAll();

        if (getView() != null)
            cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview, imageAnalysis);
    }

    public static Bitmap imageToBitmap(ImageProxy image, int rotation) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer buffer0 = planes[0].getBuffer();
        ByteBuffer buffer1 = planes[1].getBuffer();
        ByteBuffer buffer2 = planes[2].getBuffer();

        int ySize = buffer0.remaining();
        int uSize = buffer1.remaining();
        int vSize = buffer2.remaining();

        byte [] nv21 = new byte[ySize + uSize + vSize];

        buffer0.get(nv21, 0, ySize);
        buffer2.get(nv21, ySize, vSize);
        buffer1.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
        byte [] imageBytes = out.toByteArray();
        Bitmap img = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        return Bitmap.createBitmap (img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
    }

    public static QRCodeScanFragment newInstance() {
        return new QRCodeScanFragment();
    }

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            showConfirmDialog();
        } else {
            permissionsResultCallback.launch(Manifest.permission.CAMERA);
        }
    }

    private ActivityResultLauncher<String> permissionsResultCallback =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    CameraFragment.ErrorDialog.newInstance(getString(org.pniei.dwface.R.string.request_permission))
                            .show(getChildFragmentManager(), FRAGMENT_DIALOG);
                }
            });

    public static class ErrorDialog extends DialogFragment {
        private static final String ARG_MESSAGE = "message";
        public static CameraFragment.ErrorDialog newInstance(String message) {
            CameraFragment.ErrorDialog dialog = new CameraFragment.ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> activity.finish())
                    .create();
        }
    }

    public void showConfirmDialog() {
        new MaterialAlertDialogBuilder(getContext())
                .setMessage(org.pniei.dwface.R.string.request_permission)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    permissionsResultCallback.launch(Manifest.permission.CAMERA);
                })
                .setNegativeButton(android.R.string.cancel,
                        (dialog, which) -> {
                            Activity activity = getActivity();
                            if (activity != null) {
                                activity.finish();
                            }
                        })
                .show();
    }

}