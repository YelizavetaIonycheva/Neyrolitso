package org.pniei.portal.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.linphone.CallActivity;
import org.linphone.CallIncomingActivity;
import org.linphone.CallOutgoingActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.StatusFragment;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.Reason;
import org.pniei.portal.R;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.fragments.ChatListFragment;
import org.pniei.portal.fragments.ContactsListFragment;
import org.pniei.portal.fragments.HistoryListFragment;
import org.pniei.portal.listener.SpoListenerManager;
import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.web.CustomTabActivityHelper;
import org.pniei.portal.web.WebviewActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, CustomTabActivityHelper.CustomTabFallback {

    private static final int PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE = 210;
    private static final int CALL_ACTIVITY = 19;

    private static MainActivity instance;
    private StatusFragment statusFragment;
    private DrawerLayout sideMenu;
    private LinphoneCoreListenerBase mListener;

    private ActivityResultLauncher<Intent> calResultLauncher;

    public static boolean isInstanciated() {
        return instance != null;
    }

    public static MainActivity instance() {
        if (instance != null) return instance;
        throw new RuntimeException("MainActivity not instantiated yet");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.initTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        instance = this;

        initTabLayout();

        sideMenu = findViewById(R.id.drawer_layout);
        ImageView menu = findViewById(R.id.btnMenu);
        menu.setOnClickListener(view -> {
            if (sideMenu.isDrawerOpen(GravityCompat.START)) {
                sideMenu.closeDrawer(GravityCompat.START);
            } else {
                sideMenu.openDrawer(GravityCompat.START);
            }
        });

        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void registrationState(LinphoneCore lc, LinphoneProxyConfig proxy, LinphoneCore.RegistrationState state, String message) {
                if (state == LinphoneCore.RegistrationState.RegistrationCleared) {
                    // Если аккаунт очищен, можно удалить authInfo при необходимости
                }
                if (state == LinphoneCore.RegistrationState.RegistrationFailed) {
                    if (proxy.getError() == Reason.BadCredentials) {
                        displayCustomToast(getString(R.string.error_bad_credentials), Toast.LENGTH_LONG);
                    }
                }
            }

            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                if (state == LinphoneCall.State.OutgoingInit || state == LinphoneCall.State.OutgoingProgress) {
                    startActivity(new Intent(MainActivity.this, CallOutgoingActivity.class));
                } else if (state == LinphoneCall.State.CallEnd || state == LinphoneCall.State.Error || state == LinphoneCall.State.CallReleased) {
                    resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
                }
            }
        };

        // Запрос разрешений
        ArrayList<String> permissionsList = new ArrayList<>();

        int recordAudio = getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
        int readContacts = getPackageManager().checkPermission(Manifest.permission.READ_CONTACTS, getPackageName());
        int readStorage = getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, getPackageName());

        if (recordAudio != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.RECORD_AUDIO);
        }
        if (readContacts != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.READ_CONTACTS);
        }
        if (readStorage != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!permissionsList.isEmpty()) {
            String[] permissions = permissionsList.toArray(new String[0]);
            int requestCode = 0;
            // Для READ_EXTERNAL_STORAGE используем специальный код
            for (String perm : permissions) {
                if (Manifest.permission.READ_EXTERNAL_STORAGE.equals(perm)) {
                    requestCode = PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE;
                    break;
                }
            }
            ActivityCompat.requestPermissions(this, permissions, requestCode);
        }

        registerForCallResult();
    }

    /**
     * Создание SIP-аккаунта
     */
    public void createAccount(String username, String userid, String password, String displayname, String ha1, String prefix, String domain) {
        username = LinphoneUtils.getDisplayableUsernameFromAddress(username);
        domain = LinphoneUtils.getDisplayableUsernameFromAddress(domain);

        LinphonePreferences.AccountBuilder builder = new LinphonePreferences.AccountBuilder(LinphoneManager.getLc())
                .setUsername(username)
                .setDomain(domain)
                .setHa1(ha1)
                .setUserId(userid)
                .setDisplayName(displayname)
                .setPassword(password);

        if (prefix != null) {
            builder.setPrefix(prefix);
        }

        String forcedProxy = "";
        if (!TextUtils.isEmpty(forcedProxy)) {
            builder.setProxy(forcedProxy)
                    .setOutboundProxyEnabled(true)
                    .setAvpfRRInterval(5);
        }

        try {
            builder.saveNewAccount();
        } catch (LinphoneCoreException e) {
            e.printStackTrace();
        }
    }

    private void initSideMenu() {
        NavigationView navigationView = findViewById(R.id.navigation);
        navigationView.setNavigationItemSelectedListener(this);
        TextView myNumber = navigationView.getHeaderView(0).findViewById(R.id.myNumber);
        myNumber.setText(getString(R.string.my_number, PrefsUtils.ins().getPhoneP()));
    }

    private void initTabLayout() {
        ViewPager2 viewPager = findViewById(R.id.viewpager);
        viewPager.setAdapter(new SampleFragmentPagerAdapter(getSupportFragmentManager(), getLifecycle()));
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setIcon(R.drawable.ic_tab_chat);
                    break;
                case 1:
                    tab.setIcon(R.drawable.ic_tab_contacts);
                    break;
                case 2:
                    tab.setIcon(R.drawable.ic_tab_history);
                    break;
            }
        }).attach();
    }

    public static class SampleFragmentPagerAdapter extends FragmentStateAdapter {
        private static final int PAGE_COUNT = 3;

        public SampleFragmentPagerAdapter(FragmentManager fm, Lifecycle lc) {
            super(fm, lc);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return ChatListFragment.newInstance();
                case 1:
                    return ContactsListFragment.newInstance();
                case 2:
                    return HistoryListFragment.newInstance();
                default:
                    throw new IllegalStateException("Unexpected position: " + position);
            }
        }

        @Override
        public int getItemCount() {
            return PAGE_COUNT;
        }
    }

    public StatusFragment getStatusFragment() {
        return statusFragment;
    }

    public void updateStatusFragment(StatusFragment fragment) {
        statusFragment = fragment;
    }

    public void displayCustomToast(String message, int duration) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));
        TextView toastText = layout.findViewById(R.id.toastMessage);
        toastText.setText(message);

        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    public void quit(boolean isSaveDB) {
        stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
        SpoMessagesService.stop(this);
        if (isSaveDB) {
            DBUtils.closeDB();
        }
        PrefsUtils.ins().setAuth(false);
        finishAffinity();
    }

    public void resetClassicMenuLayoutAndGoBackToCallIfStillRunning() {
        if (LinphoneManager.isInstanciated() && LinphoneManager.getLc().getCallsNb() > 0) {
            LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
            if (call.getState() == LinphoneCall.State.IncomingReceived) {
                startActivity(new Intent(this, CallIncomingActivity.class));
            } else {
                startIncallActivity(call);
            }
        }
    }

    public void startIncallActivity(LinphoneCall currentCall) {
        Intent intent = new Intent(this, CallActivity.class);
        if (calResultLauncher == null) {
            registerForCallResult();
        }
        calResultLauncher.launch(intent);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_settings) {
            Intent intent = new Intent(this, SecondaryActivity.class);
            intent.putExtra(SecondaryActivity.TYPE_FRAGMENT_KEY, SecondaryActivity.SETTINGS_FRAGMENT);
            startActivity(intent);
        } else if (id == R.id.nav_exit) {
            quit(true);
        }
        sideMenu.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (sideMenu.isDrawerOpen(GravityCompat.START)) {
            sideMenu.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE) {
            if (permissions.length > 0 && Manifest.permission.READ_EXTERNAL_STORAGE.equals(permissions[0])) {
                boolean enableRingtone = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                LinphonePreferences.instance().enableDeviceRingtone(enableRingtone);
                LinphoneManager.getInstance().enableDeviceRingtone(enableRingtone);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Здесь можно обработать результаты других Activity, если нужно
    }

    @Override
    protected void onPause() {
        getIntent().putExtra("PreviousActivity", 0);
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
        PrefsUtils.ins().setAppBackground(true);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!LinphoneService.isReady()) {
            startService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
        }

        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            // Создаём аккаунт, если его ещё нет
            if (LinphonePreferences.instance().getAccountCount() == 0) {
                createAccount(PrefsUtils.ins().getPhoneP(), "", "1234", "", null, null, PrefsUtils.ins().getIpAtsP());
            }

            // Инициализируем список контактов (друзей)
            try {
                lc.createLinphoneFriendList();
                SpoContact[] contacts = DBUtils.getContactList();
                LinphoneFriend[] lfs = lc.getFriendList();
                for (LinphoneFriend lf : lfs) {
                    lc.removeFriend(lf);
                }
                for (SpoContact contact : contacts) {
                    LinphoneFriend lf = lc.createFriend();
                    if (lf != null) {
                        lf.addAddress(LinphoneCoreFactory.instance().createLinphoneAddress(
                                "sip:" + contact.getSipNumber() + "@" + PrefsUtils.ins().getIpAtsP()));
                        lc.addFriend(lf);
                    }
                }
                LinphoneManager.getInstance().subscribeFriendList(true);
            } catch (LinphoneCoreException e) {
                e.printStackTrace();
            }

            lc.addListener(mListener);
            if (!LinphoneService.instance().displayServiceNotification()) {
                lc.refreshRegisters();
            }
            initSideMenu();
        }

        // Если возвращаемся из звонка — не обрабатываем повторно
        if (getIntent().getIntExtra("PreviousActivity", 0) != CALL_ACTIVITY) {
            if (LinphoneManager.getLc().getCalls().length > 0) {
                LinphoneCall call = LinphoneManager.getLc().getCalls()[0];
                LinphoneCall.State callState = call.getState();
                if (callState == LinphoneCall.State.IncomingReceived) {
                    startActivity(new Intent(this, CallIncomingActivity.class));
                } else if (callState == LinphoneCall.State.OutgoingInit || callState == LinphoneCall.State.OutgoingProgress || callState == LinphoneCall.State.OutgoingRinging) {
                    startActivity(new Intent(this, CallOutgoingActivity.class));
                } else {
                    startIncallActivity(call);
                }
            }
        }

        // Переход в чат по уведомлению
        if (getIntent().getBooleanExtra("GoToChat", false)) {
            SpoListenerManager.removeAllListener();
            long idChatRoom = getIntent().getLongExtra("IdChatRoom", 0);
            getIntent().removeExtra("GoToChat");
            Intent newIntent = new Intent(this, SecondaryActivity.class);
            newIntent.putExtra(SecondaryActivity.TYPE_FRAGMENT_KEY, SecondaryActivity.CHAT_FRAGMENT);
            newIntent.putExtra(SecondaryActivity.CHAT_ROOM_ID_KEY, idChatRoom);
            startActivity(newIntent);
        }

        PrefsUtils.ins().setAppBackground(false);
        SpoMessagesService.instance().removeMessageNotification();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("GoToChat", false)) {
            SpoListenerManager.removeAllListener();
            long idChatRoom = intent.getLongExtra("IdChatRoom", 0);
            Intent newIntent = new Intent(this, SecondaryActivity.class);
            newIntent.putExtra(SecondaryActivity.TYPE_FRAGMENT_KEY, SecondaryActivity.CHAT_FRAGMENT);
            newIntent.putExtra(SecondaryActivity.CHAT_ROOM_ID_KEY, idChatRoom);
            startActivity(newIntent);
        }
    }

    @Override
    public void openUri(Context context, Uri uri) {
        Intent intent = new Intent(context, WebviewActivity.class);
        intent.putExtra(WebviewActivity.EXTRA_URL, uri.toString());
        context.startActivity(intent);
    }

    public void registerForCallResult() {
        calResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_FIRST_USER) {
                        getIntent().putExtra("PreviousActivity", CALL_ACTIVITY);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        calResultLauncher = null;
    }
}