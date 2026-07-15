package org.pniei.portal.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import org.linphone.LinphoneManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import org.pniei.portal.R;
import org.pniei.portal.listener.OnBackClickListener;
import org.pniei.portal.listener.SpoContactListener;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.listener.SpoListenerManager;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.databinding.ContactChangeFragmentBinding;
import org.pniei.portal.services.SpoMessagesService;

public class ContactChangeFragment extends Fragment implements SpoContactListener, OnBackClickListener {
    private static final String ARG_IS_NEW_CONTACT = "is_new_contact";
    private static final String ARG_CONTACT_ID = "id_contact";
    public static final String ARG_FULL_NAME = "full_name";
    public static final String ARG_NUMBER = "number";

    private ActivityResultLauncher<Intent> selectContactImageResultLauncher;
    private static Context mContext;
    private SpoContact mContact;
    private ContactChangeFragmentBinding mBinding;
    private boolean isNewContact;
    private String mFullName, mTempFullName, mNumber;
    private File pickedPhotoForContactFile = null;
    private static final int PHOTO_SIZE = 512;
    private byte[] photoToAdd;

    public static ContactChangeFragment newInstance(Context context, boolean isNewContact, long idContact) {
        mContext = context;
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_NEW_CONTACT, isNewContact);
        args.putLong(ARG_CONTACT_ID, idContact);

        ContactChangeFragment fragment = new ContactChangeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.inflate(inflater, R.layout.contact_change_fragment, container, false);

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

        mBinding.btnSave.setOnClickListener(v -> {
            String fullName, number;
            fullName = mBinding.contactFullName.getText().toString();
            number = mBinding.contactSipNumber.getText().toString();

            if (fullName.length() > 0 && number.length() > 0) {
                if(DBUtils.getContactForNumber(number) != null && isNewContact) {
                    Toast.makeText(mContext, "Контакт с номером " + number + " уже существует", Toast.LENGTH_SHORT).show();
                } else {
                    if (isNewContact) {
                        mContact = new SpoContact();
                    }

                    if (photoToAdd != null) {
                        if (mContact.getUriPhoto() != null) {
                            File filePhoto = new File(Uri.parse(mContact.getUriPhoto()).getPath());
                            if (filePhoto.exists())
                                filePhoto.delete();
                            mContact.setUriPhoto(null);
                        }
                        mContact.setUriPhoto(createFilePhoto(mContact.getId()));
                    }

                    if (fullName.equals(mContact.getFullName()) && number.equals(mContact.getSipNumber())) {
                        DBUtils.updateContact(mContact);
                        Utils.hideKeyboardFrom(mContext, v);
                        backOrClose();
                        return;
                    }

                    mTempFullName = mContact.getFullName();
                    mContact.setFullName(fullName);
                    mContact.setSipNumber(number);

                    // Отправка контакта на сервер
                    mBinding.loadingWindow.setVisibility(View.VISIBLE);
                    Intent intent = new Intent(mContext, SpoMessagesService.class);
                    intent.setAction(isNewContact ? SpoMessagesService.ACTION_ADD_CONTACT : SpoMessagesService.ACTION_CHANGE_CONTACT);
                    intent.putExtra(SpoMessagesService.CONTACT_KEY, mContact);

                    Utils.hideKeyboard(getActivity());
                    SpoListenerManager.addListener(this);
                    mContext.startService(intent);
                }
            }
        });

        mBinding.btnClose.setOnClickListener(v -> {
            Utils.hideKeyboardFrom(mContext, v);
            backOrClose();
        });

        mBinding.imageContact.setOnClickListener(v -> {
            MainActivity.instance().checkAndRequestCameraPermission();
            pickImage();
        });

        mBinding.btnClearPhoto.setOnClickListener(v -> {
            if (!isNewContact && mContact.getUriPhoto() != null) {
                mBinding.contactPicture.setImageResource(R.drawable.img_contact_def);
                mBinding.btnClearPhoto.setVisibility(View.GONE);
                File filePhoto = new File(Uri.parse(mContact.getUriPhoto()).getPath());
                if (filePhoto.exists())
                    filePhoto.delete();
                mContact.setUriPhoto(null);
                DBUtils.updateContact(mContact);
            }
        });

        if (!isNewContact && mContact.getUriPhoto() != null) {
            Bitmap bm = null;
            try {
                bm = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), Uri.parse(mContact.getUriPhoto()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (bm != null) {
                mBinding.contactPicture.setImageBitmap(Utils.getCroppedBitmap(bm, 256, 256, 256));
                mBinding.btnClearPhoto.setVisibility(View.VISIBLE);
            } else {
                mBinding.contactPicture.setImageResource(R.drawable.img_contact_def);
                mBinding.btnClearPhoto.setVisibility(View.GONE);
            }

        } else {
            mBinding.contactPicture.setImageResource(R.drawable.img_contact_def);
            mBinding.btnClearPhoto.setVisibility(View.GONE);
        }

        return mBinding.getRoot();
    }

    private void backOrClose() {
        if(getActivity().getSupportFragmentManager().getBackStackEntryCount() > 0)
            getActivity().getSupportFragmentManager().popBackStack();
        else
            getActivity().finish();
    }

    private String createFilePhoto(long idContact) {
        File dir = new File(Utils.getContactPhotoDir(mContext));
        if (!dir.exists())
            if (!dir.mkdir())
                return null;

        if (photoToAdd != null) {
            File newPhoto = new File(Utils.getContactPhotoDir(mContext) + idContact);
            try {
                FileOutputStream out = new FileOutputStream(newPhoto);
                out.write(photoToAdd);
                out.flush();
                out.close();
                return Uri.fromFile(newPhoto).toString();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void pickImage() {
        final List<Intent> cameraIntents = new ArrayList<Intent>();
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

    @Override
    public void onSpoContactSync(boolean result, String msg, ArrayList<SpoContact> addContacts, ArrayList<SpoContact> changedContacts) {

    }

    @Override
    public void onSpoContactChanged(boolean result, String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                SpoListenerManager.removeListener(this);
                if (result) {
                    backOrClose();
                } else {
                    if (!isNewContact)
                        mContact.setFullName(mTempFullName);
                    mBinding.loadingWindow.setVisibility(View.GONE);
                    Toast.makeText(mContext, "Контакт не изменен. Error: " + msg, Toast.LENGTH_LONG).show();
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
                    backOrClose();
                } else {
                    mBinding.loadingWindow.setVisibility(View.GONE);
                    Toast.makeText(mContext, "Контакт не сохранен. Error: " + msg, Toast.LENGTH_LONG).show();
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
                    Toast.makeText(mContext, "Контакт не удален. Error: " + msg, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public boolean allowBackPressed() {
        return mBinding.loadingWindow.getVisibility() == View.GONE;
    }

    public void registerForSelectContactImageResult() {
        selectContactImageResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getExtras() != null && data.getExtras().get("data") != null) {
                            Bitmap bm = (Bitmap) data.getExtras().get("data");
                            editContactPicture(null, bm);
                        }
                        else if (data != null && data.getData() != null) {
                            Uri selectedImageUri = data.getData();
                            try {
                                Bitmap selectedImage = MediaStore.Images.Media.getBitmap(LinphoneManager.getInstance().getContext().getContentResolver(), selectedImageUri);
                                editContactPicture(null, selectedImage);
                            } catch (IOException e) { e.printStackTrace(); }
                        }
                        else {
                            editContactPicture(pickedPhotoForContactFile.getAbsolutePath(), null);
                        }
                    }
                });
    }

    private void editContactPicture(String filePath, Bitmap image) {
        if (image == null) {
            image = BitmapFactory.decodeFile(filePath);
            if (image == null)
                return;
        }

        Bitmap background = Bitmap.createBitmap(PHOTO_SIZE, PHOTO_SIZE, Bitmap.Config.ARGB_8888);

        float originalWidth = image.getWidth();
        float originalHeight = image.getHeight();
        Canvas canvas = new Canvas(background);
        float scale = 1.0f, xTranslation = 0.0f, yTranslation = 0.0f;

        if (originalHeight > originalWidth) {
            scale = PHOTO_SIZE / originalWidth;
            yTranslation = (PHOTO_SIZE - originalHeight * scale) / 2.0f;
        } else if(originalHeight < originalWidth) {
            scale = PHOTO_SIZE / originalHeight;
            xTranslation = (PHOTO_SIZE - originalWidth * scale) / 2.0f;
        } else {
            scale = PHOTO_SIZE / originalHeight;
            xTranslation = (PHOTO_SIZE - originalWidth * scale) / 2.0f;
            yTranslation = (PHOTO_SIZE - originalHeight * scale) / 2.0f;
        }

        Matrix transformation = new Matrix();
        transformation.postTranslate(xTranslation, yTranslation);
        transformation.preScale(scale, scale);

        Paint paint = new Paint();
        paint.setFilterBitmap(true);
        canvas.drawBitmap(image, transformation, paint);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        background.compress(Bitmap.CompressFormat.PNG, 0, stream);
        mBinding.contactPicture.setImageBitmap(Utils.getCroppedBitmap(background, 256, 256, 256));
        photoToAdd = stream.toByteArray();
        image.recycle();
    }

}
