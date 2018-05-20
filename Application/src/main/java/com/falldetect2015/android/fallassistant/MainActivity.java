/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================================
 * (View) MainActivity
 * - Displays Home screen to users
 * - Calls to faModel object to start/stop sensor service
 * - <Todo>Calls to perform actual "Email"
 * http://examples.javacodegeeks.com/android/core/hardware/sensor/android-accelerometer-example/
 */

package com.falldetect2015.android.fallassistant;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.squareup.otto.ThreadEnforcer;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends Activity implements AdapterView.OnItemClickListener, TextToSpeech.OnInitListener, SensorEventListener {
    public static final boolean DEBUG = true;
    public static final String PREF_FILE = "myPrefs";
    public static final String LOG_TAG = "FallAssistant.";
    public static final String PREF_CONTACT_NUMBER = "contactNumber";
    public static final String PREF_HELP_MSG = "MESSAGE";
    public static final String PREF_SENSOR_MAX = "SENSORMAX";
    private static final String PREF_SERVICE_STATE = "serviceState";
    private static final String PREF_SAMPLING_SPEED = "samplingSpeed";
    private static final String PREF_WAIT_SECS = "waitSeconds";
    private static final String SERVICESTARTED_KEY = "serviceStarted";
    private static final long UPDATE_INTERVAL = 5000;
    public static String phoneNum = "5126269115";
    public static String helpMsg;
    public static float normalThreshold = 10,
            fallenThreshold = 5;
    public static Bus bus;
    public static SharedPreferences appPrefs;
    public static SharedPreferences.Editor ed;
    public static SharedPreferences mAppPrefs;
    private static Boolean svcRunning = false;
    private final int REQ_CODE_SPEECH_INPUT = 100;
    private int rate = SensorManager.SENSOR_DELAY_UI;
    private String contactNumber;
    private int defWaitSecs = 20;
    private int waitSeconds = defWaitSecs;
    private Sample[] mSamples;
    private GridView mGridView;
    private Boolean mSamplesSwitch = false;
    private PowerManager.WakeLock samplingWakeLock;
    private String sensorName = null;
    private boolean captureState = false;
    private SensorManager mSensorManager;
    private PrintWriter captureFile;
    private String captureStateText;
    private float[] lastSensorValues = new float[3];
    private float[] mGravity;
    private TextToSpeech tts;
    private double pitch = 1.0;
    private double speed = 1.0;
    private LocationManager mlocManager = null;
    private LocationListener mLocationListener = null;
    private String myGeocodeLocation = null;
    private Location currentLocation;
    private double currentLattitude;
    private double currentLongitude;
    private String response = "";
    private Boolean needhelp = false;
    private Boolean exittimer = false;
    private Boolean helpReq;
    private Boolean ttsReady = false;
    private Boolean noMovement = true;
    private Intent iSensorService = new Intent(this, faSensorService.class);
    private Date serviceStartedTimeStamp;
    private float mAccelLast, mAccel, mAccelCurrent, maxAccelSeen;
    private Boolean fallDetected = false;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAppPrefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        contactNumber = "5126269115";// appPrefs.getString(PREF_CONTACT_NUMBER, "5126269115");
        helpMsg = getString(R.string.fall_message);
        //ParseAnalytics.trackAppOpenedInBackground(getIntent());
        // Enable Local Datastore.
        //Parse.enableLocalDatastore(this);
        //Parse.initialize(this, "kB6TzT245Sz7l5bt22kmwzKty59EQSjWaGj34x6z", "hpUrd43JtBb79XZCRlKrG1DMY58TYNJx81L5G05k");
        //ParseObject faParseObject = new ParseObject("Fall Assistant");
        //faParseObject.put("foo", "bar");
        //faParseObject.saveInBackground();
        setContentView(com.falldetect2015.android.fallassistant.R.layout.activity_main);
        mSamplesSwitch = false;
        bus = new Bus(ThreadEnforcer.ANY);
        if (tts == null) {
            tts = new TextToSpeech(this, this);
        }
        stopSensorService();
        Log.d(LOG_TAG, "onCreate Fall Assistant");
        // Prepare list of samples in this dashboard.
        mSamples = new Sample[]{
                new Sample(com.falldetect2015.android.fallassistant.R.string.nav_1_titl, com.falldetect2015.android.fallassistant.R.string.nav_1_desc,
                        MainActivity.class),
                new Sample(com.falldetect2015.android.fallassistant.R.string.nav_2_titl, com.falldetect2015.android.fallassistant.R.string.nav_2_desc,
                        PrefsActivity.class),
                new Sample(com.falldetect2015.android.fallassistant.R.string.nav_3_titl, com.falldetect2015.android.fallassistant.R.string.nav_3_desc,
                        MainActivity.class),
        };

        // Prepare the GridView
        mGridView = (GridView) findViewById(android.R.id.list);
        mGridView.setAdapter(new SampleAdapter());
        mGridView.setOnItemClickListener(this);
        //String sensorName = sensor.getName();
        //Intent i = getIntent();
        /*if (i != null) {
            sensorName = i.getStringExtra("sensorname");
            if (DEBUG)
                Log.d(LOG_TAG, "sensorName: " + sensorName);
        }*/

        if (savedInstanceState != null) {
            svcRunning = false; // savedInstanceState.getBoolean( svcRunning, false );
            //sensorName = savedInstanceState.getString( SENSORNAME_KEY );
        }
        Boolean svcState = false;  //appPrefs.getBoolean(PREF_SERVICE_STATE, false);
        waitSeconds = defWaitSecs; //appPrefs.getInt(PREF_WAIT_SECS, defWaitSecs);
        String svcStateText = null;
        if (captureState == true) {
            //File captureFileName = new File("/sdcard", "capture.csv");
            //svcStateText = "Capture: " + captureFileName.getAbsolutePath();
        /* try {
        // if we are restarting (e.g. due to orientation change), we append to the log file instead of overwriting it
                //captureFile = new PrintWriter(new FileWriter(captureFileName, svcRunning));
            } catch (IOException ex) {
                Log.e(LOG_TAG, ex.getMessage(), ex);
                //captureStateText = "Capture: " + ex.getMessage();
            }*/
        } else { //captureStateText = "Capture: OFF";
            rate = SensorManager.SENSOR_DELAY_UI;//appPrefs.getInt(PREF_SAMPLING_SPEED, SensorManager.SENSOR_DELAY_UI);
        }
    }

    protected void onStart() {
        super.onStart();
        // if (DEBUG) Log.d(LOG_TAG, "onStart");
        try {
            if (svcRunning != null && svcRunning == true) {
                //if (DEBUG) Log.d(LOG_TAG, "onStart: svcRunning");
                startSensorService();
            }
            if (tts != null) {
                //if (DEBUG) Log.d(LOG_TAG, "onStart: ttsengine new");
                if (tts == null) tts = new TextToSpeech(this, this);
            }
        } catch (Exception e) {
            // Receiver was probably already stopped in onPause()
        }
    }

    protected void onResume() {
        super.onResume();
        if (DEBUG) {
            Log.d(LOG_TAG, "onResume");
        }
        //if (svcRunning == true) { reStartService(); }
        //registerReceiver(br, new IntentFilter(myReceiver.COUNTDOWN_BR)); Log.i(LOG_TAG, "Registered broacast receiver");
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (DEBUG) Log.d(LOG_TAG, "onSaveInstanceState");
        outState.putBoolean(SERVICESTARTED_KEY, svcRunning);
    }

    protected void onPause() {
        super.onPause();
        if (DEBUG) {
            Log.d(LOG_TAG, "onPause");
        }
        if ((svcRunning != null) && (svcRunning == true)) {
//            ed = appPrefs.edit();
            //ed.putBoolean(PREF_SERVICE_STATE, svcRunning);
            //ed.apply();
            reStartService();
        }
    }

    protected void onStop() {
        super.onStop();
        if (DEBUG)
            Log.d(LOG_TAG, "onStop");
        if (svcRunning == true) {
            ed = appPrefs.edit();
            ed.putBoolean(PREF_SERVICE_STATE, svcRunning);
            reStartService();
        }
        if (tts != null) {
            //Log.d(LOG_TAG, "onStart -- tts stop");
            tts.stop();
            tts.shutdown();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSensorService();
    }

    /**
     * Receiving speech input
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    //set string to text goes here ex. txtSpeechInput.setText(result.get(0));
                    response = result.get(0);
                    if ((response.contains("help")) || (response.contains("yes"))) {
                        needhelp = true;
                    }
                }
                break;
            }
        }
    }

    private void reStartService() {
        stopSensorService();
        startSensorService();
    }

    private void startSensorService() {
        try {
            if (tts == null) tts = new TextToSpeech(this, this);
            tts.setLanguage(Locale.getDefault());
            if (bus == null) bus = new Bus(ThreadEnforcer.ANY);
            bus.register(this);
            stopSensorService();
            // use this to start and trigger a service
            // potentially add data to the intent
            //i.putExtra("KEY1", "Value to be used by the service");
            //startService(new Intent(this, faSensorService.class));
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            mSensorManager.registerListener(this,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_UI);
            if (DEBUG) Log.d(LOG_TAG, "registerListener/faSensorService");
            serviceStartedTimeStamp = new Date();
            if (DEBUG) Log.d(LOG_TAG, "Sensor ServiceX: Started");
            svcRunning = true;
            //Log.d(LOG_TAG+"MainActivity", "MainActivity: Start sensor service");
        } catch (Exception e) {
            // Receiver was probably already stopped in onPause()
            Log.d(LOG_TAG + "MainActivity.startSensorService", "Exception: Start sensor service " + e.getMessage());
        }
        svcRunning = true;
        // ed = appPrefs.edit();
        // ed.putBoolean(PREF_SERVICE_STATE, svcRunning);
    }

    private void stopSensorService() {
        if ((svcRunning != null) && (svcRunning != true))
            return;
        Log.d(MainActivity.LOG_TAG, "faSensorService stopSensorService");
        bus.unregister(this);

        if (mSensorManager != null) {
            if (DEBUG) Log.d(LOG_TAG, "unregisterListener/faSensorService");
            mSensorManager.unregisterListener(this);
        }

        svcRunning = false;
        Date serviceStoppedTimeStamp = new Date();
        long secondsEllapsed =
                (serviceStoppedTimeStamp.getTime() -
                        serviceStartedTimeStamp.getTime()) / 1000L;
        bus.post("Sensor ServiceX: Stopped");
        Log.d(LOG_TAG, "Service ServiceX: " +
                serviceStartedTimeStamp.toString() +
                "; Service stopped: " +
                serviceStoppedTimeStamp.toString() +
                " (" + secondsEllapsed + " seconds) " +
                "; samples collected: ");
        // ed = appPrefs.edit();
        // ed.putBoolean( PREF_SERVICE_STATE, svcRunning );
    }

    @Subscribe
    public void onTestEvent(TestData event) {
        Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show();
    }

    // SensorEventListener
    public void onSensorChanged(SensorEvent sensorEvent) {
        Log.d(LOG_TAG, "Sensor ServiceX: onSensorChanged");
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double threshold = (fallDetected == true) ? fallenThreshold : normalThreshold;
            mGravity = sensorEvent.values.clone();
            // fall / cant get up detection
            /*lastSensorValues[0] = mTestData.x;
            mTestData.y = mGravity[1];
            lastSensorValues[1] = mTestData.y;
            mTestData.z = mGravity[2];
            lastSensorValues[2] = mTestData.z;*/
            mAccelLast = mAccelCurrent;
            mAccelCurrent = (float) Math.sqrt(mGravity[0] * mGravity[0] + mGravity[1] * mGravity[1] + mGravity[2] * mGravity[2]);
            float delta = mAccelCurrent - mAccelLast;
            mAccel = Math.abs(mAccel * 0.9f) + delta;
            if (mAccel > maxAccelSeen) {
                maxAccelSeen = mAccel;
                //string message = "Increased";
            }


            if (DEBUG)
                Log.d(LOG_TAG, "Sensor ServiceX: onChange mAccel=" + mAccel + " maxAccelSeen=" + maxAccelSeen + " threshold=" + threshold);
            if (mAccel > threshold) {
                maxAccelSeen = 0;
                if ((fallDetected == true) && (mAccel > fallenThreshold)) {
                    Log.i("Fall", "Fall detected");
                    stopSensorService();
                    help();
                } else {
                    if ((fallDetected == false) && (mAccel > normalThreshold)) {
                        fallDetected = true;
                        help();
                    }
                }
            }
            //bus.post(mTestData);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void help() {
        speech();
        if (helpReq == null) helpReq = false;
        if ((helpReq == false)) {
            helpReq = true;
            helpalert("Are you ok?", "Are you ok? Do you need help?");
        }
        helpReq = true;

        new CountDownTimer(waitSeconds, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Display Timer Secs Left
                Toast.makeText(getApplicationContext(),
                        Long.toString(millisUntilFinished).concat(" seconds left on response timer"),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFinish() {
                needhelp = false;
                promptSpeechInput();
                if (needhelp) {
                    sendSmsByManager();
                } else {
                    exittimer = false;
                    new CountDownTimer(20000, 1000) {

                        @Override
                        public void onTick(long millisUntilFinished) {
                            // do something after 1s
                            if (exittimer == true) {
                                cancel();
                            }
                            if (needhelp == true) {
                                sendSmsByManager();
                                cancel();
                            }
                        }

                        @Override
                        public void onFinish() {
                            // do something end times 5s
                            if (exittimer == false) {
                                sendSmsByManager();
                            }
                        }
                    }.start();
                }
            }
        }.start();
        noMovement = false;
    }

    private void speech() {
        tts.speak("Do you Need help?", TextToSpeech.QUEUE_FLUSH, null);
    }

    public void helpalert(String title, String alertMessage) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                this);
        // set title
        alertDialogBuilder.setTitle("Fall Alert " + title);
        // set dialog message
        alertDialogBuilder
                .setMessage(alertMessage)
                .setCancelable(false)
                .setPositiveButton("Yes I need help", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // if this button is clicked, close
                        // current activity
                        needhelp = true;
                    }
                })
                .setNegativeButton("No I don't need help", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // if this button is clicked, close
                        // current activity
                        exittimer = true;
                    }
                });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void sendSmsByManager() {
        try {
            helpReq = false;
            // Get the default instance of the SmsManager
            /*if (mlocManager == null) {
                mlocManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            }
            mLocationListener = new myLocationListener();
            mlocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
            //getAddressFromLocation(mLastKownLocation, this, new GeocoderHandler());

            Geocoder gc = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> addresses = gc.getFromLocation(currentLattitude,
                        currentLongitude, 1);
                StringBuilder sb = new StringBuilder();
                if (addresses.size() > 0) {
                    Address address = addresses.get(0);
                    for (int i = 0; i < address.getMaxAddressLineIndex(); i++)
                        sb.append(address.getAddressLine(i)).append("\n");
                    sb.append(address.getLocality()).append("\n");
                    sb.append(address.getPostalCode()).append("\n");
                    sb.append(address.getCountryName());
                    myGeocodeLocation = sb.toString();
                }
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "Cant connect to Geocoder",
                        Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }

            if (myGeocodeLocation == null) {
                showAlert("GeoCode", myGeocodeLocation);
                myGeocodeLocation = String.valueOf(currentLattitude) + " " + String.valueOf(currentLongitude);
            }*/
            String helpMessage = "I have fallen and need help, sent by Fall Assistant App on Android";
            //+ " http://maps.google.com/maps?q=" + URLEncoder.encode(myGeocodeLocation, "utf-8")
            SmsManager smsManager = SmsManager.getDefault();
            //speech();
            //promptSpeechInput();
            smsManager.sendTextMessage(phoneNum,
                    null,
                    helpMsg,
                    null,
                    null);
            Toast.makeText(getApplicationContext(), "Your contact has been notified",
                    Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            // showAlert("GeoCode", myGeocodeLocation);
            Toast.makeText(getApplicationContext(), "SMS has failed...",
                    Toast.LENGTH_LONG).show();
            ex.printStackTrace();
            //mlocManager.removeUpdates(mLocationListener);
        }
        //mlocManager.removeUpdates(mLocationListener);
    }

    @Override
    public void onItemClick(AdapterView<?> container, View view, int position, long id) {
        Boolean serviceState = svcRunning;
        int x = 0;
        //Boolean pos = (position == 2);
        if (position == 1) {
            startActivity(mSamples[position].intent);
        } else {
            if (position == 2) {
                // position == 2, sending SMS
                help();
            }
            if (position == 0) {
                if (svcRunning == false) {
                    mSamples[0].titleResId = com.falldetect2015.android.fallassistant.R.string.nav_1a_titl;
                    mSamples[0].descriptionResId = com.falldetect2015.android.fallassistant.R.string.nav_1a_desc;
                    startSensorService();
                    //svcRunning = true;
                    mGridView.invalidateViews();
                } else {
                    mSamples[0].titleResId = com.falldetect2015.android.fallassistant.R.string.nav_1_titl;
                    mSamples[0].descriptionResId = com.falldetect2015.android.fallassistant.R.string.nav_1_desc;
                    stopSensorService();
                    //svcRunning = false;
                    mGridView.invalidateViews();
                }
            }
        }
    }

    @Subscribe
    public void getMessage(String msg) {
        if (DEBUG) {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
        if (msg.equalsIgnoreCase("start timer")) {
            startTimer();
        }
    }

    public void startTimer() {
        new CountDownTimer(waitSeconds, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Display Timer Secs Left
                Toast.makeText(getApplicationContext(),
                        Long.toString(millisUntilFinished).concat(" seconds left on movement timer"),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFinish() {
                if (noMovement) {
                    help();
                } else {
                    noMovement = true;
                    startSensorService();  // Re-enable service
                }
            }
        }.start();
    }

    @Subscribe
    public void getMessage(TestData msg) {
        if (DEBUG) {
            Toast.makeText(this, msg.message, Toast.LENGTH_LONG).show();
        }
        if (msg.message.equalsIgnoreCase("send for help")) {
            help();
        }
    }

    public void showAlert(String title, String alertMessage) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                this);
        // set title
        alertDialogBuilder.setTitle("Fall Alert " + title);
        // set dialog message
        alertDialogBuilder
                .setMessage(alertMessage)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // if this button is clicked, close
                        // current activity
                        MainActivity.this.finish();
                    }
                });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    public void showToast(CharSequence message) {
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(getApplicationContext(), message, duration);
        toast.show();
    }

    @Override
    public void onInit(int status) {
        //Log.d("Speech", "OnInit - Status [" + status + "]");

        if (status == TextToSpeech.SUCCESS) {
            if (DEBUG) Log.d("Speech", "Success!");
            tts.setLanguage(Locale.US);
        }
    }

    public class myLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location loc) {
            currentLocation = loc;
            loc.getLatitude();
            loc.getLongitude();
            showToast("Location now: " + Double.toString(loc.getLatitude()) + ", " + Double.toString(loc.getLongitude()));
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {
            Toast.makeText(getApplicationContext(),
                    "Gps Enabled",
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onProviderDisabled(String provider) {
            Toast.makeText(getApplicationContext(),
                    "Gps Disabled",
                    Toast.LENGTH_SHORT).show();
        }

    }/* End of Class MyLocationListener */

    private class SampleAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mSamples.length;
        }

        @Override
        public Object getItem(int position) {
            return mSamples[position];
        }

        @Override
        public long getItemId(int position) {
            return mSamples[position].hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup container) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(com.falldetect2015.android.fallassistant.R.layout.sample_dashboard_item,
                        container, false);
            }

            ((TextView) convertView.findViewById(android.R.id.text1)).setText(
                    mSamples[position].titleResId);
            ((TextView) convertView.findViewById(android.R.id.text2)).setText(
                    mSamples[position].descriptionResId);
            return convertView;
        }
    }

    private class Sample {
        int titleResId;
        int descriptionResId;
        Intent intent;

        private Sample(int titleResId, int descriptionResId,
                       Class<? extends Activity> activityClass) {
            this(titleResId, descriptionResId,
                    new Intent(MainActivity.this, activityClass));
        }

        private Sample(int titleResId, int descriptionResId, Intent intent) {
            this.intent = intent;
            this.titleResId = titleResId;
            this.descriptionResId = descriptionResId;
        }
    }

    class SensorItem {
        private Sensor sensor;
        private boolean sampling;

        SensorItem(Sensor sensor) {
            this.sensor = sensor;
            this.sampling = false;
        }

        public String getSensorName() {
            return sensor.getName();
        }

        Sensor getSensor() {
            return sensor;
        }

        boolean getSampling() {
            return sampling;
        }

        void setSampling(boolean sampling) {
            this.sampling = sampling;
        }
    }

    public class TestData {
        public String message;
        public float x,
                y,
                z,
                delta,
                maxAccelSeen;
        public long timeStamp;
    }
}


