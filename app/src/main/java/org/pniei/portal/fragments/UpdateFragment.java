package org.pniei.portal.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import org.pniei.portal.R;
import org.pniei.portal.databinding.UpdateFragmentBinding;
import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.Utils;

public class UpdateFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "UpdateFragment";
    private UpdateFragmentBinding mBinding;
    private Handler mHandler;
    private String linkUpdateApp = null;
    private boolean isDownloadingFile = false;
    private boolean isCheckUpdate = false;
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;
    private boolean isShowBtnUpdate = false;

    public static UpdateFragment newInstance(Context context) {
        mContext = context;
        return new UpdateFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.update_fragment, container, false);

        mBinding.btnCheckUpdate.setOnClickListener(this);
        mBinding.btnDownloadUpdate.setOnClickListener(this);
        mBinding.btnUpdate.setOnClickListener(this);

        checkFileUpdate();

        return mBinding.getRoot();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.btnCheckUpdate) {
            checkUpdate();
        } else if (id == R.id.btnDownloadUpdate) {
            downloadUpdate();
        } else if (id == R.id.btnUpdate) {
            updatePo();
        }
    }

    private void checkUpdate() {
        new Thread(() -> {
            isCheckUpdate = true;
            showProgressInfo(true, getString(R.string.progress_check_update_po));
            showNewVersionInfo(false, null);

            showNewVersionInfo(true, null);
            linkUpdateApp = null;
            showProgressInfo(false, null);
        }
                start();
    }

    private void downloadUpdate() {
        if (linkUpdateApp == null)
            return;

        mBinding.fileVersionInfoLayout.setVisibility(View.GONE);
        new Thread(() -> {
            showProgressInfo(true, getString(R.string.progress_download_file_po));
            String firmwareDir = Utils.getFirmwareDir(mContext);
            File file = new File(firmwareDir);
            file.mkdirs();
            File outputFile = new File(file, "app.apk");
            try {
                FileOutputStream fos = new FileOutputStream(outputFile);
                if (!downloadFile(linkUpdateApp, fos)) {
                    mHandler.post(() -> {
                        if (getContext() != null)
                            Toast.makeText(getContext(), "Не удалось загрузить файл ПО", Toast.LENGTH_LONG).show();
                    });
                    fos.flush();
                    fos.close();
                    return;
                }
                fos.flush();
                fos.close();
                checkFileUpdate();
            } catch (IOException e) {
                e.printStackTrace();
            }
            showProgressInfo(false, null);
        }).start();
    }

    private void updatePo() {
        String firmwareDir = Utils.getFirmwareDir(mContext);
        File file = new File(firmwareDir, "app.apk");
        Uri fileURI = FileProvider.getUriForFile(mContext, mContext.getApplicationContext().getPackageName() + ".provider", file);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileURI, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void checkFileUpdate() {
        new Thread(() -> {
            String firmwareDir = Utils.getFirmwareDir(mContext);
            File file = new File(firmwareDir, "app.apk");
            if (file.exists()) {
                final PackageManager pm = mContext.getPackageManager();
                PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);

                if (info == null || Objects.equals(info.versionName, BuildConfig.VERSION_NAME)) {
                    file.delete();
                    isShowBtnUpdate = false;
                    mHandler.post(() -> {
                        if (mContext != null) {
                            mBinding.btnUpdate.setVisibility(View.GONE);
                            mBinding.fileVersionInfoLayout.setVisibility(View.GONE);
                        }
                    });
                } else {
                    isShowBtnUpdate = true;
                    mHandler.post(() -> {
                        if (mContext != null) {
                            mBinding.btnUpdate.setVisibility(View.VISIBLE);
                            mBinding.btnDownloadUpdate.setVisibility(View.GONE);
                            mBinding.fileVersionInfoLayout.setVisibility(View.VISIBLE);
                            mBinding.newVersionInfoLayout.setVisibility(View.GONE);
                            mBinding.fileVersion.setText(info.versionName);
                        }
                    });
                }
            } else {
                isShowBtnUpdate = false;
                mHandler.post(() -> {
                    if (mContext != null) {
                        mBinding.btnUpdate.setVisibility(View.GONE);
                        mBinding.fileVersionInfoLayout.setVisibility(View.GONE);
                    }
                });
            }
        }).start();
    }

    private void showProgressInfo(boolean show, String info) {
        mHandler.post(() -> {
            mBinding.progressInfoLayout.setVisibility(show ? View.VISIBLE : View.GONE);
            mBinding.progressInfo.setText(info != null ? info : "");
            if (show) {
                mBinding.btnCheckUpdate.setVisibility(View.GONE);
                mBinding.btnDownloadUpdate.setVisibility(View.GONE);
                mBinding.btnUpdate.setVisibility(View.GONE);
            } else {
                mBinding.btnUpdate.setVisibility(isShowBtnUpdate ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void showNewVersionInfo(boolean show, String version) {
        mHandler.post(() -> {
            mBinding.newVersionInfoLayout.setVisibility(show ? View.VISIBLE : View.GONE);
            if (version != null) {
                mBinding.availableVersion.setText(version);
                mBinding.btnDownloadUpdate.setVisibility(View.VISIBLE);
                mBinding.btnCheckUpdate.setVisibility(View.GONE);
            } else {
                mBinding.availableVersion.setText("Нет обновления");
                mBinding.btnDownloadUpdate.setVisibility(View.GONE);
                mBinding.btnCheckUpdate.setVisibility(View.VISIBLE);
            }
        });
    }

    public boolean downloadFile(String urlDownload, OutputStream fileStream) {
        HttpURLConnection c = null;
        final int bufSize = 10240;
        int needRead, alreadyRead;
        byte[] buf = new byte[bufSize];
        int len, temp, perc = -1;
        double count = 0, fileSize;
        StringBuilder sb = new StringBuilder();
        isDownloadingFile = true;

        try {
            URL url = new URL(urlDownload);
            c = (HttpURLConnection) url.openConnection();
            c.setDoInput(true);
            c.connect();

            int status = c.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                fileSize = c.getContentLength();

                DataInputStream is = new DataInputStream(c.getInputStream());
                needRead = bufSize;
                alreadyRead = 0;
                while ((len = is.read(buf, alreadyRead, needRead)) > 0 && isDownloadingFile) {
                    if (len < bufSize) {    // Из сети принято данных меньше выделенного размера буфура
                        if ((alreadyRead + len) < bufSize) { // Если размер ранее принятых данных и только что принятых меньше выделенного размера буфура
                            if ((count + alreadyRead + len) != (int) fileSize) { // Если еще не все данные приняты
                                alreadyRead += len;
                                needRead = bufSize - alreadyRead;
                                if (!isDownloadingFile)
                                    break;
                                continue;
                            }
                        }
                    }
                    alreadyRead += len;
                    fileStream.write(buf, 0, alreadyRead);
                    count += alreadyRead;
                    temp = (int) (count * 100.0 / fileSize);
                    if (temp != perc) {
                        perc = temp;
                        sb.append(getString(R.string.progress_download_file_po)).append(" ").append(perc).append(" %");
                        showProgressInfo(true, sb.toString());
                        sb.delete(0, sb.length());
                    }
                    alreadyRead = 0;
                    needRead = bufSize;
                }

                return isDownloadingFile;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        return false;
    }

    @Override
    public void onDestroy() {
        isDownloadingFile = false;
        isCheckUpdate = false;
        super.onDestroy();
    }
}
