package org.pniei.portal.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;
import org.pniei.portal.liveData.ManagerLiveData;
import org.pniei.portal.utils.PrefsUtils;
import org.pniei.portal.vpn.VpnClient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class GPSService extends Service {
    private static final String TAG = "GPSService";
    public static final String ACTION_START = "GPSService.START";
    public static final String ACTION_STOP = "GPSService.STOP";
    private GPSConnection mGPSConnection;
    private LocationManager mLocationManager;
    private ScheduledExecutorService processSendGPS = null;
    private boolean isRunning;

    static public void startGPSService(Context context) {
        if (!PrefsUtils.ins().getIpGps().equals(""))
            context.startService((new Intent(context, GPSService.class)).setAction(GPSService.ACTION_START));
    }

    static public void stopGPSService(Context context) {
        context.startService((new Intent(context, GPSService.class)).setAction(GPSService.ACTION_STOP));
    }

    public static class GPSConnection implements Runnable {
        private static final String TAG = "GPSService";
        private final String URL_GPS = "/maps/api/addmarker/";
        private final String NAME = "n";
        private final String DATE = "d";
        private final String LONGITUDE = "x";
        private final String LANTITUDE = "y";
        private final String CODE = "c";
        private final String ACCURACY = "a";
        private final String mConnectingAddress;
        private boolean isGPSGetting = false;
        private double mLatitude = 0, mLongitude = 0, mAccuracy = 0;
        private Date mDate;

        public GPSConnection(final String monitoringAddress) {
            mConnectingAddress = monitoringAddress;
        }

        @Override
        public void run() {
            if (PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P) {
                if (VpnClient.ins().isConnected() && isGPSGetting) {
                    communicateGPS();
                } else {
                    ManagerLiveData.ins().setGpsEvent(ManagerLiveData.GpsEvent.LOCATION_NOT_FIXED);
                }
            } else {
                if (isGPSGetting) {
                    communicateGPS();
                } else {
                    ManagerLiveData.ins().setGpsEvent(ManagerLiveData.GpsEvent.LOCATION_NOT_FIXED);
                }
            }
        }

        private void communicateGPS() {
            URL url;
            boolean fixed = false;
            HttpURLConnection conn = null;
            int responseCode;
            try {
                url = new URL("http://" + mConnectingAddress + URL_GPS);
                Log.i(TAG, "communicateGPS Старт url=" + url.toString());

                JSONObject jsonParam = new JSONObject();
                jsonParam.put(NAME, PrefsUtils.ins().getRegimeSelected() == PrefsUtils.REGIME_P ? PrefsUtils.ins().getIdP() : PrefsUtils.ins().getIdTT());
                jsonParam.put(DATE, String.format("%1$tF %1$tT", mDate));
                jsonParam.put(LANTITUDE, String.format(Locale.US, "%.8f", mLatitude));
                jsonParam.put(LONGITUDE, String.format(Locale.US, "%.8f", mLongitude));
                jsonParam.put(ACCURACY, (int) mAccuracy);
                Log.i(TAG, "send : " + jsonParam.toString());

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Referer", "http:/" + URL_GPS + "/");
                conn.setRequestProperty("Host", mConnectingAddress);
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setConnectTimeout(5000);
                conn.connect();

                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(jsonParam.toString());
                os.flush();
                os.close();

                responseCode = conn.getResponseCode();
                Log.i(TAG, "communicateGPS responseCode = " + responseCode);
                DataInputStream is = new DataInputStream(conn.getInputStream());
                byte[] dataBuf = new byte[3000];
                if (responseCode == 200) {
                    String dataStr;
                    int len = is.read(dataBuf);

                    if (len > 0) {
                        dataStr = new String(dataBuf, 0, len);
                        Log.i(TAG, "communicatAuth принятые данные :" + dataStr);
                        JSONObject jsonData = new JSONObject(dataStr);
                        if (jsonData.has(CODE) && jsonData.getInt(CODE) == 1) {
                            fixed = true;
                        }
                    }
                } else {
                    String dataStr;
                    int len = is.read(dataBuf);

                    if (len > 0) {
                        dataStr = new String(dataBuf, 0, len);
                        Log.i(TAG, "communicatAuth принятые данные :" + dataStr);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (conn != null)
                    conn.disconnect();
                Log.i(TAG, "communicateGPS Стоп");
                ManagerLiveData.ins().setGpsEvent(fixed ? ManagerLiveData.GpsEvent.LOCATION_FIXED : ManagerLiveData.GpsEvent.LOCATION_NOT_FIXED);
            }
        }

        public void setGPSAndDate(double latitude, double longitude, double accuracy, Date date) {
            if (date == null)
                return;

            mLatitude = latitude;
            mLongitude = longitude;
            mAccuracy = accuracy;
            mDate = date;
            isGPSGetting = true;
        }

        public void gpsProviderDisable() {
            isGPSGetting = false;
        }
    }


    @Override
    public void onCreate() {
        Log.i(TAG, "GPSService Create");
        isRunning = false;
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START.equals(intent.getAction())) {
            Log.i(TAG, "START GPS SERVICE");
            if (!isRunning) {
                if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, locationListener);
                    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, locationListener);
                    startGpsSend();
                }
                isRunning = true;
            }

            return START_NOT_STICKY;
        } else {
            Log.i(TAG, "STOP GPS SERVICE");
            stopGpsSend();
            mLocationManager.removeUpdates(locationListener);
            isRunning = false;
            return START_STICKY;
        }
    }

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.i(TAG, "onLocationChanged");
            setLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.i(TAG, "onStatusChanged");
            ManagerLiveData.ins().setGpsEvent(ManagerLiveData.GpsEvent.PROVIDER_ENABLE);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.i(TAG, "onProviderEnabled");
            if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                setLocation(mLocationManager.getLastKnownLocation(provider));
                ManagerLiveData.ins().setGpsEvent(ManagerLiveData.GpsEvent.PROVIDER_ENABLE);
            }
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.i(TAG, "onProviderDisabled");
            notGettingGPS();
            ManagerLiveData.ins().setGpsEvent(ManagerLiveData.GpsEvent.PROVIDER_DISABLE);
        }
    };

    private void setLocation(Location location) {
        if (location == null)
            return;

        if (location.getProvider().equals(LocationManager.GPS_PROVIDER) || location.getProvider().equals(LocationManager.NETWORK_PROVIDER)) {
            synchronized (TAG) {
                mGPSConnection.setGPSAndDate(location.getLatitude(), location.getLongitude(), location.getAccuracy(), new Date(location.getTime()));
                Log.e(TAG, formatLocation(location));
            }
        }
    }

    private void notGettingGPS() {
        mGPSConnection.gpsProviderDisable();
    }

    private String formatLocation(Location location) {
        if (location == null)
            return "";
        if (location.getProvider().equals(LocationManager.GPS_PROVIDER))
            return String.format("GPS_PROVIDER lat = %1$.8f, lon = %2$.8f, time = %3$tF %3$tT, acur = %4$.8f", location.getLatitude(), location.getLongitude(), new Date(location.getTime()), location.getAccuracy());
        else
            return String.format("NETWORK_PROVIDER lat = %1$.8f, lon = %2$.8f, time = %3$tF %3$tT, acur = %4$.8f", location.getLatitude(), location.getLongitude(), new Date(location.getTime()), location.getAccuracy());
    }

    private void startGpsSend() {
        Log.i(TAG, "START GPS SEND +++++++++++++++++++++++");
        if (processSendGPS != null) {
            processSendGPS.shutdown();
            processSendGPS = null;
        }
        mGPSConnection = new GPSConnection(PrefsUtils.ins().getIpGps());
        processSendGPS = Executors.newSingleThreadScheduledExecutor();
        processSendGPS.scheduleWithFixedDelay(mGPSConnection, 3, PrefsUtils.ins().getTimeIntervalSendGPS(), TimeUnit.SECONDS);
    }

    private void stopGpsSend() {
        Log.i(TAG, "STOP GPS SEND -----------------------------");
        if (processSendGPS != null) {
            processSendGPS.shutdown();
            processSendGPS = null;
        }
        mGPSConnection = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy() ");
        mLocationManager.removeUpdates(locationListener);
        mLocationManager = null;
        stopGpsSend();
    }
}
