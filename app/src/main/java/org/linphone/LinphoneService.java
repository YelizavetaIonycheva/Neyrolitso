package org.linphone;

import org.linphone.compatibility.Compatibility;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallLog.CallStatus;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Version;
import org.linphone.notifications.NotificationsManager;
import org.linphone.ui.LinphoneOverlay;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;
import org.pniei.portal.R;
import org.pniei.portal.activities.MainActivity;
import org.pniei.portal.database.DBUtils;
import org.pniei.portal.database.SpoContact;
import org.pniei.portal.notification.SpoNotificationsManager;
import org.pniei.portal.utils.PrefsUtils;

public final class LinphoneService extends Service {
	public static final String TAG = "LinphoneService";
	public static final int IC_LEVEL_ORANGE=0;
	private static LinphoneService instance = null;
	private final static int INCALL_NOTIF_ID=2;
	private final static int MESSAGE_NOTIF_ID=3;
	private final static int CUSTOM_NOTIF_ID=4;
	private final static int MISSED_NOTIF_ID=5;
	private final static int SAS_NOTIF_ID=6;

	public static boolean isReady() {
		return instance != null;
	}

	/**
	 * @throws RuntimeException service not instantiated
	 */
	public static LinphoneService instance()  {
		if (isReady()) return instance;

		throw new RuntimeException("LinphoneService not instantiated yet");
	}

	public Handler mHandler = new Handler();
	private NotificationManager mNM;
	private NotificationsManager mNotificationManager;
	private Notification mNotif;
	private Notification mIncallNotif;
	private Notification mMsgNotif;
	private int mMsgNotifCount;
	private PendingIntent mNotifContentIntent;
	private String mNotificationTitle;
	private LinphoneCoreListenerBase mListener;
	public static int notifcationsPriority = (Version.sdkAboveOrEqual(Version.API16_JELLY_BEAN_41) ? Notification.PRIORITY_MIN : 0);
	private WindowManager mWindowManager;
	private LinphoneOverlay mOverlay;
	private Application.ActivityLifecycleCallbacks activityCallbacks;

	public boolean displayServiceNotification() {
		return LinphonePreferences.instance().getServiceNotificationVisibility();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate() {
		Log.i(TAG, "CREATE");
		super.onCreate();

		mNotificationTitle = getString(R.string.service_name);

		// Needed in order for the two next calls to succeed, libraries must have been loaded first
		LinphonePreferences.instance().setContext(getBaseContext());
		LinphoneCoreFactory.instance().setLogCollectionPath(getFilesDir().getAbsolutePath());
		boolean isDebugEnabled = LinphonePreferences.instance().isDebugEnabled();
		LinphoneCoreFactory.instance().enableLogCollection(isDebugEnabled);
		LinphoneCoreFactory.instance().setDebugMode(isDebugEnabled, getString(R.string.app_name));

		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mNM.cancel(INCALL_NOTIF_ID); // in case of crash the icon is not removed

		mNotificationManager = new NotificationsManager(this);
		SpoNotificationsManager.CreateChannel(this);

		Intent notifIntent = new Intent(this, incomingReceivedActivity);
		notifIntent.putExtra("Notification", true);

		LinphoneManager.createAndStart(LinphoneService.this);

		instance = this; // instance is ready once linphone manager has been created
		incomingReceivedActivityName = LinphonePreferences.instance().getActivityToLaunchOnIncomingReceived();
		try {
			incomingReceivedActivity = (Class<? extends Activity>) Class.forName(incomingReceivedActivityName);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		LinphoneManager.getLc().addListener(mListener = new LinphoneCoreListenerBase() {
			@Override
			public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
				if (instance == null) {
					Log.i(TAG,"Service not ready, discarding call state change to " + state.toString());
					return;
				}

				LinphoneAddress address = call.getRemoteAddress();
				SpoContact contact = DBUtils.getContactForNumber(address.getUserName());
				if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_TT) {
					if (contact == null) {
						LinphoneManager.getLc().terminateCall(call);
						return;
					}
				}

				mNotificationManager.displayCallNotification(call, state);

				if (state == LinphoneCall.State.IncomingReceived) {
					if(!LinphoneManager.getInstance().getCallGsmON()) {
						onIncomingReceived();
					}
				}

				if (state == State.CallEnd || state == State.CallReleased || state == State.Error) {
					if (LinphoneManager.isInstanciated() && LinphoneManager.getLc() != null && LinphoneManager.getLc().getCallsNb() == 0) {
						if (MainActivity.isInstantiated() && MainActivity.instance().getStatusFragment() != null) {
							removeSasNotification();
						}
					}
					destroyOverlay();
				}

				if (state == State.CallEnd && call.getCallLog().getStatus() == CallStatus.Missed) {
					int missedCallCount = LinphoneManager.getLcIfManagerNotDestroyedOrNull().getMissedCallsCount();
					String body;
					if (missedCallCount > 1) {
						body = getString(R.string.missed_calls_notif_body).replace("%i", String.valueOf(missedCallCount));
					} else {
						if (contact != null) {
							body = contact.getFullName();
						} else {
							body = address.getDisplayName();
							if (body == null) {
								body = address.asStringUriOnly();
							}
						}
					}

					Intent missedCallNotifIntent = new Intent(LinphoneService.this, incomingReceivedActivity);
					missedCallNotifIntent.putExtra("GoToHistory", true);
					PendingIntent intent;
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
						intent = PendingIntent.getActivity(LinphoneService.this, 0, missedCallNotifIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
					} else {
						intent = PendingIntent.getActivity(LinphoneService.this, 0, missedCallNotifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
					}

					Notification notif = Compatibility.createMissedCallNotification(instance, getString(R.string.missed_calls_notif_title), body, intent);
					notifyWrapper(MISSED_NOTIF_ID, notif);
				}
			}

			@Override
			public void globalState(LinphoneCore lc,LinphoneCore.GlobalState state, String message) {
				/*if (state == GlobalState.GlobalOn && displayServiceNotification()) {
					sendNotification(IC_LEVEL_ORANGE, R.string.notification_started);
				}*/
			}

			@Override
			public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState state, String smessage) {
				/*if (displayServiceNotification() && state == RegistrationState.RegistrationOk && LinphoneManager.getLc().getDefaultProxyConfig() != null && LinphoneManager.getLc().getDefaultProxyConfig().isRegistered()) {
					sendNotification(IC_LEVEL_ORANGE, R.string.notification_registered);
				}

				if (displayServiceNotification() && (state == RegistrationState.RegistrationFailed || state == RegistrationState.RegistrationCleared) && (LinphoneManager.getLc().getDefaultProxyConfig() == null || !LinphoneManager.getLc().getDefaultProxyConfig().isRegistered())) {
					sendNotification(IC_LEVEL_ORANGE, R.string.notification_register_failure);
				}

				if (displayServiceNotification() && state == RegistrationState.RegistrationNone) {
					sendNotification(IC_LEVEL_ORANGE, R.string.notification_started);
				}
				if (displayServiceNotification() && state == RegistrationState.RegistrationProgress) {
					sendNotification(IC_LEVEL_ORANGE, R.string.notification_register_in_progress);
				}*/
			}
		});

		if (displayServiceNotification()) {
			SpoNotificationsManager.ins(this).startForeground(this);
		}

		Intent intent = new Intent(this, KeepAliveReceiver.class);
		PendingIntent keepAlivePendingIntent = null;
		keepAlivePendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_MUTABLE);
		AlarmManager alarmManager = ((AlarmManager) this.getSystemService(Context.ALARM_SERVICE));
		Compatibility.scheduleAlarm(alarmManager, AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 600000, keepAlivePendingIntent);

		mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
	}

	public void createOverlay() {
		if (mOverlay != null) destroyOverlay();

		LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if (call == null || !call.getCurrentParams().getVideoEnabled()) return;

		mOverlay = new LinphoneOverlay(this);
		WindowManager.LayoutParams params = mOverlay.getWindowManagerLayoutParams();
		params.x = 0;
		params.y = 0;
		mWindowManager.addView(mOverlay, params);
	}

	public void destroyOverlay() {
		if (mOverlay != null) {
			mWindowManager.removeViewImmediate(mOverlay);
			mOverlay.destroy();
		}
		mOverlay = null;
	}

	public void removeSasNotification() {
		mNM.cancel(SAS_NOTIF_ID);
	}

	private String incomingReceivedActivityName;
	private Class<? extends Activity> incomingReceivedActivity = MainActivity.class;

	void stopForegroundCompat() {
		SpoNotificationsManager.ins(this).stopForeground(this);
	}

	/**
	 * Wrap notifier to avoid setting the linphone icons while the service
	 * is stopping. When the (rare) bug is triggered, the linphone icon is
	 * present despite the service is not running. To trigger it one could
	 * stop linphone as soon as it is started. Transport configured with TLS.
	 */
	private synchronized void notifyWrapper(int id, Notification notification) {
		if (instance != null && notification != null) {
			mNM.notify(id, notification);
		} else {
			Log.i(TAG,"Service not ready, discarding notification");
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onTaskRemoved(Intent rootIntent) {
		if (getResources().getBoolean(R.bool.kill_service_with_task_manager)) {
			Log.d(TAG,"Task removed, stop service");

			// If push is enabled, don't unregister account, otherwise do unregister
			if (LinphonePreferences.instance().isPushNotificationEnabled()) {
				LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
				if (lc != null) lc.setNetworkReachable(false);
			}
			stopSelf();
		}
		super.onTaskRemoved(rootIntent);
	}

	@Override
	public synchronized void onDestroy() {
        Log.i(TAG, "onDestroy");
		if (activityCallbacks != null){
			getApplication().unregisterActivityLifecycleCallbacks(activityCallbacks);
			activityCallbacks = null;
		}

		destroyOverlay();
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}

		instance = null;
		LinphoneManager.destroy();

	    // Make sure our notification is gone.
	    stopForegroundCompat();
	    mNM.cancel(INCALL_NOTIF_ID);
	    mNM.cancel(MESSAGE_NOTIF_ID);

		super.onDestroy();
	}

	@SuppressWarnings("unchecked")
	public void setActivityToLaunchOnIncomingReceived(String activityName) {
		try {
			incomingReceivedActivity = (Class<? extends Activity>) Class.forName(activityName);
			incomingReceivedActivityName = activityName;
			LinphonePreferences.instance().setActivityToLaunchOnIncomingReceived(incomingReceivedActivityName);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		resetIntentLaunchedOnNotificationClick();
	}

	private void resetIntentLaunchedOnNotificationClick() {
		Intent notifIntent = new Intent(this, incomingReceivedActivity);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			mNotifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
		} else {
		    mNotifContentIntent = PendingIntent.getActivity(this, 0, notifIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		}
	}

	protected void onIncomingReceived() {
		//wakeup linphone
		Log.e(TAG, "onIncomingReceived");

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			startActivity(new Intent()
					.setClass(this, incomingReceivedActivity)
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		}
	}

}

