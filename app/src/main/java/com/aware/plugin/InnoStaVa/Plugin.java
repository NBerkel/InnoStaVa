package com.aware.plugin.InnoStaVa;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.aware.Applications;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Aware_Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

public class Plugin extends Aware_Plugin {
    public static String broadcast_receiver = "ACTION_INNOSTAVA_ESM";
    MyReceiver myReceiver;

    private static final int ESM_TRIGGER_THRESHOLD_MILLIS = 3000;

    private final int ACTIVITY_STILL = 3;
    private final ArrayList<Integer> stillActivities = new ArrayList<>(Arrays.asList(3,5));

    private static String location = "unknown";
    private static String previousSentLocation = "unknown";
    private static long location_changed = System.currentTimeMillis();
    private int activity = -1;
    private long activity_changed = System.currentTimeMillis();

    private static boolean checkOngoing = false;

    private EsmContextReceiver esmContextReceiver;
    private class EsmContextReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // nearest beacon
            if (intent.getAction().equals(com.aware.plugin.bluetooth_beacon_detect.Plugin.ACTION_AWARE_PLUGIN_BT_BEACON_NEAREST)) {
                String new_location = intent.getStringExtra(com.aware.plugin.bluetooth_beacon_detect.Provider.BluetoothBeacon_Data.MAC_ADDRESS);
                if (location.equals("unknown")) {
                    location = new_location;
                    location_changed = System.currentTimeMillis();
                    return;
                }
                // if a new location and not the same as where previous was sent
                if (!new_location.equals(previousSentLocation)
                        && !new_location.equals(location)) {
                    new Handler().postDelayed(new ESMCheckRunnable(activity, new_location), ESM_TRIGGER_THRESHOLD_MILLIS);
                }
                if (!new_location.equals(location)) {
                    location = new_location;
                    location_changed = System.currentTimeMillis();
                }
            }
//            else if (intent.getAction().equals(com.aware.plugin.google.activity_recognition.Plugin.ACTION_AWARE_GOOGLE_ACTIVITY_RECOGNITION)) {
//                Log.d(TAG, "checked_activity: " + intent.getIntExtra(com.aware.plugin.google.activity_recognition.Plugin.EXTRA_ACTIVITY, -1));
//                if (activity < 0) {
//                    activity = intent.getIntExtra(com.aware.plugin.google.activity_recognition.Plugin.EXTRA_ACTIVITY, -1);
//                    activity_changed = System.currentTimeMillis();
//                    return;
//                }
//                if (intent.getIntExtra(com.aware.plugin.google.activity_recognition.Plugin.EXTRA_CONFIDENCE, 100) > 50
//                        && !(intent.getIntExtra(com.aware.plugin.google.activity_recognition.Plugin.EXTRA_ACTIVITY, -1) == activity)) {
//                    // if both activities are considered "still", dont log activity changing
//                    if (stillActivities.contains(activity) && stillActivities.contains(intent.getIntExtra(com.aware.plugin.google.activity_recognition.Plugin.EXTRA_ACTIVITY, -1))) {
//                        return;
//                    }
//                    activity = intent.getIntExtra(com.aware.plugin.google.activity_recognition.Plugin.EXTRA_ACTIVITY, -1);
//                    activity_changed = System.currentTimeMillis();
//                }
//                // else if user is still and location unchanged for ESM_TRIGGER_THRESHOLD_MILLIS, send esm
//                else if (System.currentTimeMillis() - ESM_TRIGGER_THRESHOLD_MILLIS > location_changed &&
//                        System.currentTimeMillis() - ESM_TRIGGER_THRESHOLD_MILLIS > activity_changed
//                        && stillActivities.contains(activity)) {
//                    sendESM();
//                }
//            }
        }
    }

    private class ESMCheckRunnable implements Runnable {
        public ESMCheckRunnable(final int activity, final String location) {
            this.checked_activity = activity;
            this.checked_location = location;
        }
        private int checked_activity;
        private String checked_location;

        @Override
        public void run() {
            // if for some reason check done for non-still activity dont do anything
            checkOngoing = false;
            if (this.checked_location.equals(location) &&
                System.currentTimeMillis() - ESM_TRIGGER_THRESHOLD_MILLIS > location_changed
                    && !previousSentLocation.equals(checked_location)) {
                // if all conditions match, send esm
                previousSentLocation = checked_location;
                sendESM();
            }
        }
    }

    private void sendESM() {
        Calendar c = Calendar.getInstance();

        NotificationCompat.Builder notification2 = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_stat_communication_live_help)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setAutoCancel(true)
                .setContentTitle("Sent ESM")
                .setContentText("" + c.get(Calendar.HOUR_OF_DAY) + ":" + c.get(Calendar.MINUTE));

        NotificationManager notificationManager2 = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        // Will display the notification in the notification bar
        notificationManager2.notify((int) System.currentTimeMillis(), notification2.build());

        c = Calendar.getInstance();
        Calendar prev_c = Calendar.getInstance();

        Cursor last_two = getContentResolver().query(Provider.InnoStaVa_data.CONTENT_URI, null, Provider.InnoStaVa_data.QUESTION_ID + "=?", new String[]{"V6"}, "TIMESTAMP DESC");

        if (last_two != null && last_two.moveToFirst()) {
            // move to the second entry if able
            if (!last_two.moveToNext()) {
                // not enough data so always send
                Toast.makeText(context, "sending esm since not enough data", Toast.LENGTH_SHORT).show();
                last_two.close();
            }
            else {
                prev_c.setTimeInMillis(last_two.getLong(last_two.getColumnIndex(Provider.InnoStaVa_data.TIMESTAMP)));
                Toast.makeText(this, "Last ESM " + prev_c.get(Calendar.HOUR_OF_DAY) + ":" + prev_c.get(Calendar.MINUTE), Toast.LENGTH_LONG).show();
                // no ESMS outside 8-16
                if (8 < c.get(Calendar.HOUR_OF_DAY) && c.get(Calendar.HOUR_OF_DAY) > 17) {
                    last_two.close();
                    Toast.makeText(context, "no esm outside 8-17", Toast.LENGTH_SHORT).show();
                    return;
                }
                // if last entry from yesterday
                else if (c.get(Calendar.DAY_OF_YEAR) > prev_c.get(Calendar.DAY_OF_YEAR)) {
                    last_two.close();
                }
                // if current time is morning and second to last entry was during afternoon
                else if (c.get(Calendar.HOUR_OF_DAY) <= 12 && prev_c.get(Calendar.HOUR_OF_DAY) > 12) {
                    // everything ok dont need to do anything
                    Toast.makeText(context, "sending esm in the morning", Toast.LENGTH_SHORT).show();
                    last_two.close();
                }
                // currently afternoon and last entry morning OR different day as last entry
                else if (c.get(Calendar.HOUR_OF_DAY) > 12 && prev_c.get(Calendar.HOUR_OF_DAY) <= 12
                        || !(c.get(Calendar.DAY_OF_YEAR) == prev_c.get(Calendar.DAY_OF_YEAR))) {
                    // everything ok dont need to do anything
                    Toast.makeText(context, "sending esm in the afternoon", Toast.LENGTH_SHORT).show();
                    last_two.close();
                }
                // if no conditions match dont send
                else {
                    Toast.makeText(context, "no esm because too many already", Toast.LENGTH_SHORT).show();
                    last_two.close();
                    return;
                }
            }
        }
        else {
            Log.d(TAG, "last two was null");
        }
        // check current limits

        Intent resultIntent = new Intent(getApplicationContext(), InnoStaVaESM.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, resultIntent, 0);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_stat_communication_live_help)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setContentTitle("Questionnaire waiting")
                .setContentText("Open notification to answer questionnaire.");

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        // Will display the notification in the notification bar
        notificationManager.notify(123, notification.build());

        ContentValues vals = new ContentValues();
        vals.put(Provider.ESM_data.LOCATION, location);
        vals.put(Provider.ESM_data.TIMESTAMP, System.currentTimeMillis());
        vals.put(Provider.ESM_data.DEVICE_ID, Aware.getSetting(this, Aware_Preferences.DEVICE_ID));
        getContentResolver().insert(Provider.ESM_data.CONTENT_URI, vals);

        // cancel notification after 5 mins
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
                // Will display the notification in the notification bar
                notificationManager.cancel(123);
            }
        },300000);
    }

    private Context context;
    @Override
    public void onCreate() {
        super.onCreate();

        TAG = "AWARE::" + getResources().getString(R.string.app_name);

        context = this;
//        Aware.setSetting(this, Aware_Preferences.DEBUG_FLAG, false);

        //Any active plugin/sensor shares its overall context using broadcasts
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                //Broadcast your context here
            }
        };

        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_WIFI_STATE);
        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH);
        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH_ADMIN);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_FINE_LOCATION);

        boolean permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (!permissions_ok) {
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            permissions.putExtra(PermissionsHandler.EXTRA_REDIRECT_ACTIVITY,
                    getPackageName() + "/" + InnoStaVa.class.getName());

            startActivity(permissions);
        } else {
            Applications.isAccessibilityServiceActive(getApplicationContext());
            Aware.joinStudy(getApplicationContext(), "https://api.awareframework.com/index.php/webservice/index/989/73JTLkqEcwWZ");

            DATABASE_TABLES = Provider.DATABASE_TABLES;
            TABLES_FIELDS = Provider.TABLES_FIELDS;
            CONTEXT_URIS = new Uri[]{
                    Provider.InnoStaVa_data.CONTENT_URI,
                    Provider.ESM_data.CONTENT_URI
            };

            myReceiver = new MyReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("ACTION_INNOSTAVA_ESM");
            registerReceiver(myReceiver, intentFilter);

            esmContextReceiver = new EsmContextReceiver();
            IntentFilter contextFilter = new IntentFilter();
            contextFilter.addAction(com.aware.plugin.google.activity_recognition.Plugin.ACTION_AWARE_GOOGLE_ACTIVITY_RECOGNITION);
            contextFilter.addAction(com.aware.plugin.bluetooth_beacon_detect.Plugin.ACTION_AWARE_PLUGIN_BT_BEACON_NEAREST);
            registerReceiver(esmContextReceiver, contextFilter);

            // start aware and plugins
            Aware.startAWARE();

            Aware.setSetting(this, "frequency_plugin_google_activity_recognition", 60);
            Aware.startPlugin(this, "com.aware.plugin.google.activity_recognition");
            Aware.setSetting(this, "frequency_plugin_bluetooth_beacon_detect", 30000);
            Aware.startPlugin(this, "com.aware.plugin.bluetooth_beacon_detect");

            //Activate plugin -- do this ALWAYS as the last thing (this will restart your own plugin and apply the settings)
            Aware.startPlugin(this, "com.aware.plugin.InnoStaVa");
        }
    }

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        boolean permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (permissions_ok) {
            //Check if the user has toggled the debug messages
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            //Initialize our plugin's settings
            Aware.setSetting(this, Settings.STATUS_PLUGIN_INNOSTAVA, true);

        } else {
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permissions);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    public class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Intent resultIntent = new Intent(getApplicationContext(), InnoStaVaESM.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, resultIntent, 0);

                NotificationCompat.Builder notification = new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_stat_communication_live_help)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setContentTitle("Questionnaire waiting")
                        .setContentText("Open notification to answer questionnaire.");

                NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
                // Will display the notification in the notification bar
                notificationManager.notify(123, notification.build());
                // The subtext, which appears under the text on newer devices. This will show-up in the devices with Android 4.2 and above only
                // notification.setSubText("Tap to view documentation about notifications.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(esmContextReceiver);
        unregisterReceiver(myReceiver);
        Aware.stopPlugin(this, "com.aware.plugin.google.activity_recognition");
        Aware.stopPlugin(this, "com.aware.plugin.bluetooth_beacon_detect");
        //Stop AWARE
        Aware.stopAWARE();
    }
}