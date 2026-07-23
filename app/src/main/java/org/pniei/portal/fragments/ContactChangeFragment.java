package org.pniei.portal.fragments;

<<<<<<< HEAD
=======
import android.annotation.SuppressLint;
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
<<<<<<< HEAD
=======
import android.graphics.BitmapFactory;
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import org.linphone.LinphoneManager;
import org.pniei.portal.R;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.databinding.ContactChangeFragmentBinding;
import org.pniei.portal.listener.OnBackClickListener;
import org.pniei.portal.listener.SpoContactListener;
import org.pniei.portal.listener.SpoListenerManager;
import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
<<<<<<< HEAD
=======
import java.util.Objects;
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db

public class ContactChangeFragment extends Fragment implements SpoContactListener, OnBackClickListener {

    private static final String TAG = "ContactChangeFragment";
    private static final String ARG_IS_NEW_CONTACT = "is_new_contact";
    private static final String ARG_CONTACT_ID = "id_contact";
    public static final String ARG_FULL_NAME = "full_name";
    public static final String ARG_NUMBER = "number";

    private static final int PHOTO_SIZE = 512;
    private static final int PHOTO_DISPLAY_SIZE = 256;

    private ActivityResultLauncher<Intent> selectContactImageResultLauncher;
    private Context mContext;
    private SpoContact mContact;
    private ContactChangeFragmentBinding mBinding;
    private boolean isNewContact;
    private String mFullName;
    private String mTempFullName;
    private String mNumber;
    private byte[] photoToAdd;

    public static ContactChangeFragment newInstance(Context context, boolean isNewContact, long idContact) {
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_NEW_CONTACT, isNewContact);
        args.putLong(ARG_CONTACT_ID, idContact);

        ContactChangeFragment fragment = new ContactChangeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static ContactChangeFragment newInstance(Context context, boolean isNewContact, String fullName, String number) {
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_NEW_CONTACT, isNewContact);
        args.putString(ARG_FULL_NAME, fullName);
        args.putString(ARG_NUMBER, number);

        ContactChangeFragment fragment = new ContactChangeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = requireContext();

        assert getArguments() != null;
        isNewContact = getArguments().getBoolean(ARG_IS_NEW_CONTACT, true);

        if (isNewContact) {
            mFullName = getArguments().getString(ARG_FULL_NAME, "");
            mNumber = getArguments().getString(ARG_NUMBER, "");
        } else {
            long idContact = getArguments().getLong(ARG_CONTACT_ID);
            mContact = DBUtils.getContact(idContact);
        }

        registerForSelectContactImageResult();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.contact_change_fragment, container, false);

        initUI();
        setupListeners();
        loadContactPhoto();

        return mBinding.getRoot();
    }

    private void initUI() {
        if (isNewContact) {
            mBinding.label.setText(R.string.label_new_contact);
            mBinding.btnClearPhoto.setVisibility(View.GONE);
            mBinding.contactFullName.setText(mFullName);
            mBinding.contactSipNumber.setText(mNumber);
        } else {
            mBinding.label.setText(R.string.label_change_contact);
            mBinding.contactFullName.setText(mContact.getFullName());
            mBinding.contactSipNumber.setText(mContact.getSipNumber());
            mBinding.contactSipNumber.setEnabled(false);
            mBinding.contactSipNumber.setCursorVisible(false);
            mBinding.contactSipNumber.setBackgroundColor(Color.TRANSPARENT);
            mBinding.contactSipNumber.setKeyListener(null);
        }
    }

    private void setupListeners() {
        mBinding.btnSave.setOnClickListener(v -> onSaveClicked(v));
        mBinding.btnClose.setOnClickListener(v -> {
            Utils.hideKeyboardFrom(mContext, v);
            backOrClose();
        });
        mBinding.imageContact.setOnClickListener(v -> {
            MainActivity.instance().checkAndRequestCameraPermission();
            pickImage();
        });
        mBinding.btnClearPhoto.setOnClickListener(v -> onClearPhotoClicked());
    }

    // Загрузка фото

    private void loadContactPhoto() {
        if (isNewContact || mContact == null || mContact.getUriPhoto() == null) {
            setDefaultPhoto();
            return;
        }

        try {
            Bitmap bm = MediaStore.Images.Media.getBitmap(
                    requireContext().getContentResolver(),
                    Uri.parse(mContact.getUriPhoto())
            );

            if (bm != null) {
                Bitmap cropped = Utils.getCroppedBitmap(bm, PHOTO_DISPLAY_SIZE, PHOTO_DISPLAY_SIZE, PHOTO_DISPLAY_SIZE);
                mBinding.contactPicture.setImageBitmap(cropped);
                mBinding.btnClearPhoto.setVisibility(View.VISIBLE);
            } else {
                setDefaultPhoto();
            }
        } catch (IOException e) {
            Log.e(TAG, "Ошибка загрузки фото контакта", e);
            setDefaultPhoto();
        } catch (Exception e) {
            Log.e(TAG, "Неизвестная ошибка при загрузке фото", e);
            setDefaultPhoto();
        }
    }

    private void setDefaultPhoto() {
        mBinding.contactPicture.setImageResource(R.drawable.img_contact_def);
        mBinding.btnClearPhoto.setVisibility(View.GONE);
    }

    private void notifyPhotoChanged() {
        loadContactPhoto();
    }

    // Сохранение контакта

    private void onSaveClicked(View v) {
        String fullName = mBinding.contactFullName.getText().toString().trim();
        String number = mBinding.contactSipNumber.getText().toString().trim();

        // Валидация
        if (fullName.isEmpty()) {
            Toast.makeText(mContext, "Введите имя контакта", Toast.LENGTH_SHORT).show();
            return;
        }

        if (number.isEmpty()) {
            Toast.makeText(mContext, "Введите номер контакта", Toast.LENGTH_SHORT).show();
            return;
        }

        // Проверка дубликата для нового контакта
        if (isNewContact && DBUtils.getContactForNumber(number) != null) {
            Toast.makeText(mContext, "Контакт с номером " + number + " уже существует", Toast.LENGTH_SHORT).show();
            return;
        }

        // Создаем новый контакт если нужно
        if (isNewContact) {
            mContact = new SpoContact();
            mContact.setFullName(fullName);
            mContact.setSipNumber(number);
        }

        // Проверяем, были ли изменения
        boolean hasChanges = false;

        // Проверяем изменения в имени/номере
        if (!fullName.equals(mContact.getFullName()) || !number.equals(mContact.getSipNumber())) {
            if (!isNewContact) {
                mTempFullName = mContact.getFullName();
            }
            mContact.setFullName(fullName);
            mContact.setSipNumber(number);
            hasChanges = true;
        }

        // Проверяем изменения в фото
        if (photoToAdd != null) {
            hasChanges = true;
        }

        // Если нет изменений - просто закрываем
        if (!hasChanges) {
            Utils.hideKeyboardFrom(mContext, v);
            backOrClose();
            return;
        }

        // Сохраняем контакт в БД (для нового - чтобы получить ID)
        if (isNewContact) {
            long id = DBUtils.insertContact(mContact);
            if (id == -1) {
                Toast.makeText(mContext, "Ошибка сохранения контакта", Toast.LENGTH_SHORT).show();
                return;
            }
            mContact.setId(id);
        } else {
            DBUtils.updateContact(mContact);
        }

        // Сохраняем фото (если есть)
        if (photoToAdd != null) {
            saveContactPhoto();
        }

        // Отправляем на сервер
        sendContactToServer(v);
    }

    private void sendContactToServer(View v) {
        mBinding.loadingWindow.setVisibility(View.VISIBLE);

        Intent intent = new Intent(mContext, SpoMessagesService.class);
        intent.setAction(isNewContact ?
                SpoMessagesService.ACTION_ADD_CONTACT :
                SpoMessagesService.ACTION_CHANGE_CONTACT);
        intent.putExtra(SpoMessagesService.CONTACT_KEY, mContact);

        Utils.hideKeyboard(getActivity());
        SpoListenerManager.addListener(this);
        mContext.startService(intent);
    }

    private void saveContactPhoto() {
        if (photoToAdd == null || mContact == null) {
            return;
        }

        String oldPhotoUri = mContact.getUriPhoto();

        // Для нового контакта ID должен быть установлен
        if (mContact.getId() == 0) {
            Log.e(TAG, "ID контакта не установлен, невозможно сохранить фото");
            return;
        }

        try {
            String newPhotoUri = createFilePhoto(mContact.getId());

            if (newPhotoUri != null) {
                // Сначала сохраняем новое
                mContact.setUriPhoto(newPhotoUri);

                // Потом удаляем старое (если было)
                if (oldPhotoUri != null) {
                    deletePhotoFileSafely(oldPhotoUri);
                }

                // Обновляем в БД
                DBUtils.updateContact(mContact);
                notifyPhotoChanged();

                // Очищаем photoToAdd после успешного сохранения
                photoToAdd = null;
            } else {
                Log.e(TAG, "Не удалось создать файл фото");
                Toast.makeText(mContext, "Не удалось сохранить фото", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при сохранении фото", e);
            mContact.setUriPhoto(oldPhotoUri);
            Toast.makeText(mContext, "Ошибка при сохранении фото", Toast.LENGTH_SHORT).show();
        }
    }

    private String createFilePhoto(long idContact) {
        if (photoToAdd == null) {
            return null;
        }

        File dir = new File(Utils.getContactPhotoDir(mContext));
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Не удалось создать директорию для фото");
            return null;
        }

        File newPhoto = new File(dir, String.valueOf(idContact));
        try (FileOutputStream out = new FileOutputStream(newPhoto)) {
            out.write(photoToAdd);
            out.flush();
            return Uri.fromFile(newPhoto).toString();
        } catch (IOException e) {
            Log.e(TAG, "Ошибка записи файла фото", e);
            return null;
        }
    }

    private void deletePhotoFileSafely(String photoUri) {
        if (photoUri == null) {
            return;
        }

        try {
            Uri uri = Uri.parse(photoUri);
            String path = uri.getPath();
            if (path == null) {
                return;
            }

            File file = new File(path);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    Log.d(TAG, "Фото удалено: " + path);
                } else {
                    Log.w(TAG, "Не удалось удалить файл: " + path);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка удаления фото", e);
        }
    }

    private void onClearPhotoClicked() {
        if (isNewContact || mContact == null) {
            return;
        }

        String photoUri = mContact.getUriPhoto();
        if (photoUri == null) {
            return;
        }

        // Удаляем файл
        deletePhotoFileSafely(photoUri);

        // Обновляем контакт
        mContact.setUriPhoto(null);
        DBUtils.updateContact(mContact);

        // Обновляем UI
        setDefaultPhoto();

        Toast.makeText(mContext, "Фото удалено", Toast.LENGTH_SHORT).show();
    }

    // Выбор фото

    private void pickImage() {
        final List<Intent> cameraIntents = new ArrayList<>();
        final Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntents.add(captureIntent);

        final Intent galleryIntent = new Intent();
        galleryIntent.setType("image/*");
        galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
        galleryIntent.setAction(Intent.ACTION_GET_CONTENT);

        final Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.image_picker_title));
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));

        selectContactImageResultLauncher.launch(chooserIntent);
    }

    private void registerForSelectContactImageResult() {
        selectContactImageResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getExtras() != null && data.getExtras().get("data") != null) {
                            // Фото из камеры
                            Bitmap bm = (Bitmap) data.getExtras().get("data");
                            editContactPicture(bm);
                        } else if (data != null && data.getData() != null) {
                            // Фото из галереи
                            Uri selectedImageUri = data.getData();
                            try {
                                Bitmap selectedImage = MediaStore.Images.Media.getBitmap(
                                        LinphoneManager.getInstance().getContext().getContentResolver(),
                                        selectedImageUri
                                );
                                editContactPicture(selectedImage);
                            } catch (IOException e) {
                                Log.e(TAG, "Ошибка загрузки фото из галереи", e);
                                Toast.makeText(mContext, "Ошибка загрузки фото", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );
    }

    private void editContactPicture(Bitmap image) {
        if (image == null) {
            Toast.makeText(mContext, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Создаем квадратное изображение с центрированием
            Bitmap background = Bitmap.createBitmap(PHOTO_SIZE, PHOTO_SIZE, Bitmap.Config.ARGB_8888);

            float originalWidth = image.getWidth();
            float originalHeight = image.getHeight();
            Canvas canvas = new Canvas(background);

<<<<<<< HEAD
            Matrix transformation = getMatrix(originalHeight, originalWidth);
=======
            float scale;
            float xTranslation = 0.0f;
            float yTranslation = 0.0f;

            if (originalHeight > originalWidth) {
                scale = PHOTO_SIZE / originalWidth;
                yTranslation = (PHOTO_SIZE - originalHeight * scale) / 2.0f;
            } else {
                scale = PHOTO_SIZE / originalHeight;
                xTranslation = (PHOTO_SIZE - originalWidth * scale) / 2.0f;
            }

            Matrix transformation = new Matrix();
            transformation.postTranslate(xTranslation, yTranslation);
            transformation.preScale(scale, scale);
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db

            Paint paint = new Paint();
            paint.setFilterBitmap(true);
            canvas.drawBitmap(image, transformation, paint);

            // Конвертируем в byte array для сохранения
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            background.compress(Bitmap.CompressFormat.PNG, 100, stream);
            photoToAdd = stream.toByteArray();

            // Отображаем фото в UI
            Bitmap displayBitmap = Utils.getCroppedBitmap(background, PHOTO_DISPLAY_SIZE, PHOTO_DISPLAY_SIZE, PHOTO_DISPLAY_SIZE);
            mBinding.contactPicture.setImageBitmap(displayBitmap);

            // Показываем кнопку очистки (если контакт не новый)
            if (!isNewContact) {
                mBinding.btnClearPhoto.setVisibility(View.VISIBLE);
            }

            image.recycle();
            background.recycle();

        } catch (Exception e) {
            Log.e(TAG, "Ошибка обработки изображения", e);
            Toast.makeText(mContext, "Ошибка обработки изображения", Toast.LENGTH_SHORT).show();
        }
    }

<<<<<<< HEAD
    @NonNull
    private static Matrix getMatrix(float originalHeight, float originalWidth) {
        float scale;
        float xTranslation = 0.0f;
        float yTranslation = 0.0f;

        if (originalHeight > originalWidth) {
            scale = PHOTO_SIZE / originalWidth;
            yTranslation = (PHOTO_SIZE - originalHeight * scale) / 2.0f;
        } else {
            scale = PHOTO_SIZE / originalHeight;
            xTranslation = (PHOTO_SIZE - originalWidth * scale) / 2.0f;
        }

        Matrix transformation = new Matrix();
        transformation.postTranslate(xTranslation, yTranslation);
        transformation.preScale(scale, scale);
        return transformation;
    }

=======
>>>>>>> f1f0ba4992deebceefcbec824421c405340748db
    private void backOrClose() {
        if (getActivity() != null) {
            if (getActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getActivity().getSupportFragmentManager().popBackStack();
            } else {
                getActivity().finish();
            }
        }
    }

    @Override
    public boolean allowBackPressed() {
        return mBinding.loadingWindow.getVisibility() == View.GONE;
    }

    @Override
    public void onSpoContactSync(boolean result, String msg, ArrayList<SpoContact> addContacts, ArrayList<SpoContact> changedContacts) {
        // Не используется
    }

    @Override
    public void onSpoContactChanged(boolean result, String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                SpoListenerManager.removeListener(this);

                if (result) {
                    Toast.makeText(mContext, "Контакт обновлен", Toast.LENGTH_SHORT).show();
                    backOrClose();
                } else {
                    // Восстанавливаем данные при ошибке
                    if (mTempFullName != null) {
                        mContact.setFullName(mTempFullName);
                    }
                    mBinding.loadingWindow.setVisibility(View.GONE);
                    Toast.makeText(mContext, "Ошибка: " + msg, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void onSpoContactAdd(boolean result, String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                SpoListenerManager.removeListener(this);

                if (result) {
                    Toast.makeText(mContext, "Контакт добавлен", Toast.LENGTH_SHORT).show();
                    backOrClose();
                } else {
                    mBinding.loadingWindow.setVisibility(View.GONE);
                    Toast.makeText(mContext, "Ошибка: " + msg, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void onSpoContactDelete(boolean result, String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                SpoListenerManager.removeListener(this);

                if (result) {
                    backOrClose();
                } else {
                    mBinding.loadingWindow.setVisibility(View.GONE);
                    Toast.makeText(mContext, "Ошибка: " + msg, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Очищаем photoToAdd чтобы избежать утечек памяти
        photoToAdd = null;
    }
}