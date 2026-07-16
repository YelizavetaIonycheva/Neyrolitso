package org.pniei.portal.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.Reason;
import org.pniei.portal.R;
import org.pniei.portal.services.GPSService;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.fragments.ChatListFragment;
import org.pniei.portal.fragments.ContactsListFragment;
import org.pniei.portal.fragments.HistoryListFragment;
import org.pniei.portal.listener.SpoListenerManager;
import org.pniei.portal.services.MonitoringService;
import org.pniei.portal.services.SpoMessagesService;
import org.pniei.portal.utils.MIUIUtils;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.utils.Utils;
import org.pniei.portal.vpn.VpnClient;
import org.pniei.portal.web.CustomTabActivityHelper;
import org.pniei.portal.web.WebviewActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener ,CustomTabActivityHelper.CustomTabFallback{
    private static final String TAG = "MainActivity";
    private ActivityResultLauncher<Intent> requestOverlayResultLauncher;
    private ActivityResultLauncher<Intent> calResultLauncher;
    private static final int CALL_ACTIVITY = 19;
    private static final int PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER = 209;
    private static final int PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE = 210;
    private static final int PERMISSIONS_RECORD_AUDIO_ECHO_TESTER = 211;
    private static final int PERMISSIONS_ACCESS_FINE_LOCATION = 212;

    private static MainActivity instance;
    private StatusFragment statusFragment;
    private DrawerLayout sideMenu;
    private ImageView menu;
    private LinphoneCoreListenerBase mListener;

    public static boolean isInstanciated() {
        return instance != null;
    }

    public static MainActivity instance() {
        if (instance != null)
            return instance;
        throw new RuntimeException("MainActivity not instantiated yet");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.initTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        instance = this;

        initTabLayout();

        if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P) {
            // Запуск службы VPN и Смены ключей
            ArrayList<String> dnsAddress = new ArrayList<>();
            dnsAddress.add(PrefsUtils.ins().getIpDnsP());

            if (PrefsUtils.ins().isVpnEnable()) {
                if (!VpnClient.startVpnService(this)) {
                    // Toast с сообщением о некорректных IP адресах
                    Toast.makeText(this, R.string.bad_ip_skzi_address, Toast.LENGTH_SHORT);
                } else {
                    MonitoringService.startMonitoringService(this);
                }
            }
        }

        sideMenu = (DrawerLayout) findViewById(R.id.drawer_layout);
        menu = (ImageView) findViewById(R.id.btnMenu);
        menu.setOnClickListener(view -> {
            if (sideMenu.isDrawerOpen(GravityCompat.START)) {
                sideMenu.closeDrawer(GravityCompat.START);
            } else {
                sideMenu.openDrawer(GravityCompat.START);
            }
        });

        mListener = new LinphoneCoreListenerBase(){
            @Override
            public void registrationState(LinphoneCore lc, LinphoneProxyConfig proxy, LinphoneCore.RegistrationState state, String smessage) {
                LinphoneAuthInfo authInfo = lc.findAuthInfo(proxy.getIdentity(), proxy.getRealm(), proxy.getDomain());
                if (state.equals(LinphoneCore.RegistrationState.RegistrationCleared)) {
                    if (lc != null) {
                        if (authInfo != null)
                            lc.removeAuthInfo(authInfo);
                    }
                }

                if(state.equals(LinphoneCore.RegistrationState.RegistrationFailed)) {
                    if (proxy.getError() == Reason.BadCredentials) {
                        displayCustomToast(getString(R.string.error_bad_credentials), Toast.LENGTH_LONG);
                    }
                    /*if (proxy.getError() == Reason.Unauthorized) {
                        displayCustomToast(getString(R.string.error_unauthorized), Toast.LENGTH_LONG);
                    }
                    if (proxy.getError() == Reason.IOError) {
                        displayCustomToast(getString(R.string.error_io_error), Toast.LENGTH_LONG);
                    }*/
                }
            }

            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                /*if (state == LinphoneCall.State.IncomingReceived) {
                    startActivity(new Intent(MainActivity.instance(), CallIncomingActivity.class));
                } else */if (state == LinphoneCall.State.OutgoingInit || state == LinphoneCall.State.OutgoingProgress) {
                    startActivity(new Intent(MainActivity.instance(), CallOutgoingActivity.class));
                } else if (state == LinphoneCall.State.CallEnd || state == LinphoneCall.State.Error || state == LinphoneCall.State.CallReleased) {
                    resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
                }
            }
        };
        ArrayList<String> permissionsList = new ArrayList<String>();

        int recordAudio = getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
        int camera = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
        int readContacts = getPackageManager().checkPermission(Manifest.permission.READ_CONTACTS, getPackageName());
        int readStorage = getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, getPackageName());

        if (recordAudio != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.RECORD_AUDIO);
        }
        if (camera != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.CAMERA);
        }
        if (readContacts != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.READ_CONTACTS);
        }
        if (readStorage != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (permissionsList.size() > 0) {
            String[] permissions = new String[permissionsList.size()];
            int requestCode = 0;
            permissions = permissionsList.toArray(permissions);
            if (permissions.equals("READ_EXTERNAL_STORAGE")) {
                requestCode = PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE;
            }
            ActivityCompat.requestPermissions(this, permissions, requestCode);
        }
        //checkAndRequestReadExternalStoragePermissionForDeviceRingtone();

        if (!MIUIUtils.canDrawOverlayViews(this) && MIUIUtils.isXiaomi()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Запрос разрешения")
                    .setMessage("Для работы приложения необходимо предоставить разрешения: " +
                            "\n\"Всплывающие окна\"" +
                            "\n\"Отображать всплывающие окона, когда запущено в фоновом режиме\"" +
                            "\n\"Экран блокировки\"")
                    .setNegativeButton("Отмена", (dialog, which) -> {

                    })
                    .setPositiveButton("Предоставить", (dialog, which) -> {
                        MIUIUtils.onDisplayPopupPermission(this);
                    })
                    .show();
        }
        registerForRequestOverlayResult();
        registerForCallResult();

         if (PrefsUtils.ins().isSendGPS()) {
            if(getPackageManager().checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, getPackageName()) == PackageManager.PERMISSION_GRANTED) {
                GPSService.startGPSService(MainActivity.this);
            } else {
                checkAndRequestAccessFineLocationPermission();
            }
        }
        
    }

    public void createAccount(String username, String userid, String password, String displayname, String ha1, String prefix, String domain, LinphoneAddress.TransportType transport) {
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
        if (transport != null) {
            builder.setTransport(transport);
        }

        try {
            builder.saveNewAccount();
        } catch (LinphoneCoreException e) {
            e.printStackTrace();
        }
    }

    private void initSideMenu() {
        NavigationView navigationView = (NavigationView) findViewById(R.id.navigation);
        navigationView.setNavigationItemSelectedListener(this);
        TextView myNumber = (TextView) navigationView.getHeaderView(0).findViewById(R.id.myNumber);
        myNumber.setText(getString(R.string.my_number, (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P ? PrefsUtils.ins().getPhoneP() : PrefsUtils.ins().getPhoneTT()))); //"Мой номер: " + (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P ? PrefsUtils.ins().getPhoneP() : PrefsUtils.ins().getPhoneTT()));
    }

    private void initTabLayout() {
        ViewPager2 viewPager = findViewById(R.id.viewpager);
        viewPager.setAdapter(new SampleFragmentPagerAdapter(getSupportFragmentManager(), getLifecycle()));
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        TabLayoutMediator tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setIcon(R.drawable.ic_tab_chat); break;
                case 1: tab.setIcon(R.drawable.ic_tab_contacts); break;
                case 2: tab.setIcon(R.drawable.ic_tab_history); break;
            }
        });
        tabLayoutMediator.attach();
    }

    public static class SampleFragmentPagerAdapter extends FragmentStateAdapter {
        final int PAGE_COUNT = 3;

        public SampleFragmentPagerAdapter(FragmentManager fm, Lifecycle lc) {
            super(fm, lc);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            Fragment fragment = null;

            switch (position) {
                case 0: {
                    fragment = ChatListFragment.newInstance();
                    break;
                }
                case 1: {
                    fragment = ContactsListFragment.newInstance();
                    break;
                }
                case 2: {
                    fragment = HistoryListFragment.newInstance();
                    break;
                }
            }

            return fragment;
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

    public boolean checkAndRequestOverlayPermission() {
        if (!Compatibility.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            requestOverlayResultLauncher.launch(intent);
            return false;
        }
        return true;
    }

    public void checkAndRequestRecordAudioPermissionForEchoCanceller() {
        checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER);
    }

    public void checkAndRequestRecordAudioPermissionsForEchoTester() {
        checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_RECORD_AUDIO_ECHO_TESTER);
    }

    
    public void checkAndRequestAccessFineLocationPermission() {
        if ((getPackageManager().checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, getPackageName()) != PackageManager.PERMISSION_GRANTED) ||
                (getPackageManager().checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, getPackageName()) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_ACCESS_FINE_LOCATION);
        }
    }

    public void displayCustomToast(final String message, final int duration) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

        TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
        toastText.setText(message);

        final Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    public void checkAndRequestCameraPermission() {
        checkAndRequestPermission(Manifest.permission.CAMERA, 0);
    }

    public void checkAndRequestPermission(String permission, int result) {
        int permissionGranted = getPackageManager().checkPermission(permission, getPackageName());
        if (permissionGranted != PackageManager.PERMISSION_GRANTED) {
            //ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
            ActivityCompat.requestPermissions(this, new String[] { permission }, result);
        }
    }

    public void quit(boolean isSaveDB) {
        stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));

        if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P) {
            VpnClient.stopVpnService(this);
            MonitoringService.stopMonitoringService(this);
        }

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
                startActivity(new Intent(MainActivity.this, CallIncomingActivity.class));
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

    /*private void openWeb(String strUrl) {
        Uri url = Uri.parse(strUrl);
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = builder.build();
        CustomTabActivityHelper.openCustomTab(this, customTabsIntent, url, this);
    }*/

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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (permissions.length <= 0)
            return;

        switch (requestCode) {
            case PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE:
                if (permissions[0].compareTo(Manifest.permission.READ_EXTERNAL_STORAGE) != 0)
                    break;
                boolean enableRingtone = (grantResults[0] == PackageManager.PERMISSION_GRANTED);
                LinphonePreferences.instance().enableDeviceRingtone(enableRingtone);
                LinphoneManager.getInstance().enableDeviceRingtone(enableRingtone);
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VpnClient.REQUST_VPN && resultCode == RESULT_OK) {
            if (!VpnClient.startVpnService(this)) {
                // Toast с сообщением о некорректных IP адресах
                Toast.makeText(this, R.string.bad_ip_skzi_address, Toast.LENGTH_SHORT);
            } else {
                MonitoringService.startMonitoringService(this);
            }
        }
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
            // Автоматическое создание аккаунта
            if(LinphonePreferences.instance().getAccountCount() == 0) {
                if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
                    createAccount(PrefsUtils.ins().getPhoneTT(), "", "1234", "", null, null, PrefsUtils.ins().getIpAtsTT(), LinphoneAddress.TransportType.LinphoneTransportUdp);
                } else {
                    createAccount(PrefsUtils.ins().getPhoneP(), "", "1234", "", null, null, PrefsUtils.ins().getIpAtsP(), LinphoneAddress.TransportType.LinphoneTransportUdp);
                }

                try {
                    lc.createLinphoneFriendList();
                    List<SpoContact> contacts = Arrays.asList(DBUtils.getContactList());

                    LinphoneFriend [] lfs = lc.getFriendList();
                    if (lfs.length > 0) {
                        for (LinphoneFriend lf : lfs) {
                            lc.removeFriend(lf);
                        }
                    }

                    for(SpoContact contact : contacts) {
                        LinphoneFriend lf = lc.createFriend();
                        if (lf != null) {
                            if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
                                lf.addAddress(LinphoneCoreFactory.instance().createLinphoneAddress("sip:" + contact.getSipNumber() + "@" + PrefsUtils.ins().getIpAtsTT()));
                            } else {
                                lf.addAddress(LinphoneCoreFactory.instance().createLinphoneAddress("sip:" + contact.getSipNumber() + "@" + PrefsUtils.ins().getIpAtsP()));
                            }
                            //lf.addPhoneNumber(contact.getSipNumber());
                            lc.addFriend(lf);
                        }
                    }

                    LinphoneManager.getInstance().subscribeFriendList(true);
                } catch (LinphoneCoreException e) {
                    e.printStackTrace();
                }
            }

            lc.addListener(mListener);
            if (!LinphoneService.instance().displayServiceNotification()) {
                lc.refreshRegisters();
            }

            initSideMenu();
        }

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

        if (getIntent().getBooleanExtra("GoToChat", false)) {
            SpoListenerManager.removeAllListener();
            long idChatRoom = getIntent().getLongExtra("IdChatRoom", 0);
            getIntent().removeExtra("GoToChat");
            Intent newIntent = new Intent(this, SecondaryActivity.class);
            newIntent.putExtra(SecondaryActivity.TYPE_FRAGMENT_KEY, SecondaryActivity.CHAT_FRAGMENT);
            newIntent.putExtra(SecondaryActivity.CHAT_ROOM_ID_KEY, idChatRoom);
            startActivity(newIntent);
        } else {
            Log.e(TAG, "idChatRoom = " + null);
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

    public void registerForRequestOverlayResult() {
        requestOverlayResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {});
    }

    public void registerForCallResult() {
        calResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    int resultCode = result.getResultCode();
                    if (resultCode == Activity.RESULT_FIRST_USER)
                        getIntent().putExtra("PreviousActivity", CALL_ACTIVITY);
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        calResultLauncher = null;
    }
}