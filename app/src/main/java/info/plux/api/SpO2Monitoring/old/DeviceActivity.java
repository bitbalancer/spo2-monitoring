package info.plux.api.SpO2Monitoring.old;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import info.plux.api.SpO2Monitoring.activities.ScanActivity;
import info.plux.api.SpO2Monitoring.database.DataRow;
import info.plux.api.SpO2Monitoring.database.DataRowDAO;
import info.plux.api.SpO2Monitoring.database.MeasureDB;
import info.plux.api.SpO2Monitoring.old.read_from_db.ListenReceiver;
import info.plux.api.SpO2Monitoring.old.read_from_db.ReadService;
import com.scottyab.HeartBeatView;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import info.plux.api.SpO2Monitoring.R;
import info.plux.pluxapi.Communication;
import info.plux.pluxapi.Constants;
import info.plux.pluxapi.bioplux.BiopluxCommunication;
import info.plux.pluxapi.bioplux.BiopluxCommunicationFactory;
import info.plux.pluxapi.bioplux.BiopluxException;
import info.plux.pluxapi.bioplux.OnBiopluxDataAvailable;
import info.plux.pluxapi.bioplux.utils.BiopluxFrame;
import info.plux.pluxapi.bioplux.utils.Source;

import static info.plux.pluxapi.Constants.ACTION_STATE_CHANGED;
import static info.plux.pluxapi.Constants.EXTRA_STATE_CHANGED;
import static info.plux.pluxapi.Constants.IDENTIFIER;

public class DeviceActivity extends AppCompatActivity implements OnBiopluxDataAvailable {
    private final String TAG = this.getClass().getSimpleName();

    // sleeping interval of uiThread
    private static final long REFRESH_INTERVAL = 100; // millis
    public static final long WRITE_THREAD_SLEEP_TIME = 200; // millis
    public static final long BROADCAST_THREAD_SLEEP_TIME = 100; // millis
    public static final long PLOTTER_MAX_X = 10; // seconds on x axis
    private static final float FREQUENCY =5;
    // setup of channel 1 and 2 for Biosignalsplux hub
    private static final Source[] SOURCES = new Source[]{
            new Source(1, 16, (byte)0x01, 1),
            new Source(2, 16, (byte)0x01, 1)
    };
    private static final List<Source> SOURCES_LIST = Arrays.asList( SOURCES );
    public final static String EXTRA_DEVICE = "info.plux.pluxandroid.DeviceActivity.EXTRA_DEVICE";
    public final static String FRAME = "info.plux.pluxandroid.DeviceActivity.Frame";


    // ui elements
    // for heart
    HeartBeatView heartbeat2;
    TextView bpm;
    // for changing background color
    ConstraintLayout layout;
    TextView level;
    // for time display
    TextView timeDisplay;
    // buttons
    Button btStart, btStop, btShow;
    // for connection state report
    private TextView stateTextView;

    // Listen to Service reading from db
    ListenReceiver listenReceiver;
    IntentFilter intentFilterForListenReceiver;
    // List to Communication state
    IntentFilter intentFilterForStateReceiver;
    // Thread manipulating UI
    private UiThread uiThread;
    // Intents for passing data
    Intent readFromDbIntent;
    // database
    MeasureDB db;
    DataRowDAO dataRowDAO;
    // used Biosignalsplux communication
    private BluetoothDevice bluetoothDevice;
    private BiopluxCommunication bioplux;
    private HandlerThread handlerThread;
    private Handler handler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initUiElements();

        if(getIntent().hasExtra(EXTRA_DEVICE)){

            bluetoothDevice = getIntent().getParcelableExtra(EXTRA_DEVICE);

            // kill activity if devices is bitalino
            if( bluetoothDevice.getName().toLowerCase().contains("bitalino")) {
                Toast.makeText(getApplicationContext(), "No BITalino", Toast.LENGTH_SHORT).show();
                DeviceActivity.this.finish();
                System.exit(0);
            }

        } else { // kill activity if no device available
            Toast.makeText(getApplicationContext(), "No Device available", Toast.LENGTH_SHORT).show();
            DeviceActivity.this.finish();
            System.exit(0);
        }

        prepareHandlerForCommunication();

        setUpBiopluxCommunication();

        setUpButtons();

    }


    @Override
    protected void onResume(){
        super.onResume();

        startService(readFromDbIntent);

        registerReceiver(listenReceiver, intentFilterForListenReceiver);
        registerReceiver(stateReceiver, intentFilterForStateReceiver);

        if (uiThread.isRunning()) {

            try {
                bioplux.start(FREQUENCY,SOURCES_LIST);
            } catch (BiopluxException e) {
                e.printStackTrace();
            }

            uiThread.start();

        }

    }




    @Override
    protected void onPause() {
        super.onPause();
        stopService(readFromDbIntent);
        System.out.println("ONPause. That can me kill. Are u crazy! ----------------------");
    }

    @Override
    public void onBackPressed() {
        Log.d("CDA", "onBackPressed Called");

        stopService(readFromDbIntent);

        uiThread.stop();

        try {
            bioplux.disconnect();
        } catch (BiopluxException e) {
            e.printStackTrace();
        }


        Intent intent = new Intent(this, ScanActivity.class);
        //intent.putExtra(DeviceActivity.EXTRA_DEVICE, device);
        startActivity(intent);


    }

    @Override
    protected void onStart(){
        super.onStart();
        System.out.println("Ich bin neu gestartet worden. --------------------------------------------------------------");
    }


    @Override
    protected void onStop() {
        super.onStop();
        /*
        try {
            bioplux.disconnect();
        } catch (BiopluxException e) {
            e.printStackTrace();
        }
        deleteDatabase("measure_db");
         */
        System.out.println("I AM FULLY STOPPED! I could be killed any time. Help me. --------------------");
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(stateReceiver);
        unregisterReceiver(listenReceiver);

        getApplicationContext().deleteDatabase("measure_db");
    }


    private void initUiElements(){
        setContentView(R.layout.activity_device);

        getApplicationContext().deleteDatabase("measure_db");

        listenReceiver = new ListenReceiver();

        intentFilterForListenReceiver = new IntentFilter();
        intentFilterForListenReceiver.addAction("ACTION");

        intentFilterForStateReceiver = new IntentFilter();
        intentFilterForStateReceiver.addAction(ACTION_STATE_CHANGED);

        // Start Service and let it run until App is closed
        readFromDbIntent = new Intent(this, ReadService.class);


        // we're going to simulate real time with thread that append data to the graph
        uiThread = new UiThread();

        // heart beat
        heartbeat2 = findViewById(R.id.heartbeat2);
        bpm = findViewById(R.id.text_bmp);
        // This initial value here is crucial.
        // Do not go below. Otherwise the heart shape gets deterred
        // Or if missing, the initial movement is not smooth.
        heartbeat2.setDurationBasedOnBPM(40);

        // traffic lights
        layout = findViewById(R.id.activity_device_id);
        level = findViewById(R.id.text_level);

        // buttons
        btStart = findViewById(R.id.bt_start);
        btStop = findViewById(R.id.bt_stop);
        btShow = findViewById(R.id.bt_show);
        turnOnButtons(false);

        // time display
        timeDisplay = findViewById(R.id.textView_time);

        // state display
        stateTextView = findViewById(R.id.state_text_viw);

        // database
        db = MeasureDB.getInstance(this);
        dataRowDAO = db.dataRowDAO();

    }

    private void prepareHandlerForCommunication() {


        handlerThread = new HandlerThread("HandlerThreadName");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper()) { // Thread not longer on MainLooper! Perfect Solution
            DataRow dataRow;
            double electrodermalActivity;
            int heartRate;
            long sleepTime = (long) (1000 / FREQUENCY);
            double time = 0;


            @Override
            public void handleMessage(Message msg) {
                // Process received messages here!

                Bundle bundle = msg.getData();
                Parcelable frame = bundle.getParcelable(FRAME);

                // Manipulate String to get intended results
                String filterFrame = frame.toString();
                electrodermalActivity = getEdaFromPort1(filterFrame);
                heartRate = getHrFromPort2(filterFrame);


                // Implement logic for database operations here
                dataRow = new DataRow();

                time = time + sleepTime*0.001;
                dataRow.time = time;
                dataRow.electrodermalActivity = electrodermalActivity*0.001;
                dataRow.heartRate = heartRate;

                dataRowDAO.insertAll(dataRow);


                // go to sleep
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

    }


    // callbacks
    @Override
    public void onBiopluxDataAvailable(BiopluxFrame biopluxFrame) {
        Message message = handler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putParcelable(FRAME, biopluxFrame);
        message.setData(bundle);
        handler.sendMessage(message);
    }

    @Override
    public void onBiopluxDataAvailable(String identifier, int[] biopluxFrame) {
        Log.d(TAG, identifier + ": " + Arrays.toString(biopluxFrame));
    }

    // used in handler
    private double getEdaFromPort1(String frame){
        String startMarker = "[";
        String endMarker = ",";

        String target = StringUtils.substringBetween(frame, startMarker,endMarker);
        double result = Double.parseDouble(target);
        return result;
    }

    private int getHrFromPort2(String frame){
        String startMarker = ", ";
        String endMarker = "]";

        String target = StringUtils.substringBetween(frame, startMarker,endMarker);
        int result = Integer.parseInt(target);
        return result;
    }



    // setup communication
    private void setUpBiopluxCommunication(){


        Communication communication = Communication.getById(bluetoothDevice.getType());
        Log.d(TAG, "Communication: " + communication.name());
        if (communication.equals(Communication.DUAL)) {
            communication = Communication.BTH;
        }

        Log.d(TAG, "communication: " + communication.name());

        bioplux = new BiopluxCommunicationFactory().getCommunication(communication, this, this);


        // Establish Connection
        try {
            bioplux.connect(bluetoothDevice.getAddress());
        } catch (BiopluxException e) {
            e.printStackTrace();
        }

    }


    private void setUpButtons(){

        btStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                coolDown();

                if (!uiThread.isRunning()) {

                    uiThread.start();

                    try {
                        bioplux.start(FREQUENCY,SOURCES_LIST);
                    } catch (BiopluxException e) {
                        e.printStackTrace();
                    }

                }
            }
        });


        btStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                coolDown();

                if (uiThread.isRunning()) {

                    uiThread.stop();

                    try {
                        bioplux.stop();
                    } catch (BiopluxException e) {
                        e.printStackTrace();
                    }

                }
            }
        });


        // for starting PlotterActivity
        final Intent PlotterIntent = new Intent(this, PlotterActivity.class);

        btShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                coolDown();

                if(uiThread.isRunning()) {
                    uiThread.stop();

                    try {
                        bioplux.stop();
                    } catch (BiopluxException e) {
                        e.printStackTrace();
                    }
                }

                startActivity(PlotterIntent);

            }
        });


    }

    private void turnOnButtons(boolean on){
        btStart.setEnabled(on);
        btStop.setEnabled(on);
        btShow.setEnabled(on);
    }

    // blocks buttons for some time to prevent crash from pressing different buttons in quick sequence
    private void coolDown(){

        turnOnButtons(false);

        unblocker.postDelayed( buttonReleaseRunnable,1000 );

    }

    Handler unblocker = new Handler(Looper.getMainLooper());

    Runnable buttonReleaseRunnable = new Runnable(){
        @Override
        public void run() {
            // update the ui from here
            turnOnButtons(true);
        }
    };

    // private classes

    private class UiThread implements Runnable {

        private Thread worker;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private double time;
        private double electroDermalActivity;
        private int heartRate;

        @Override
        public void run() {

            running.set(true);
            heartRate = listenReceiver.getHeartRate();
            if( heartRate > 40){
                heartbeat2.start();
            }

            while (running.get()) {
                try {
                    Thread.sleep(REFRESH_INTERVAL);
                } catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                    System.out.println(
                            "Thread was interrupted, Failed to complete operation");
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        time = listenReceiver.getTime();
                        timeDisplay.setText( TimeToStringConverter.convert(time) );
                        electroDermalActivity = listenReceiver.getElectrodermalActivity();
                        setTrafficLightAccordingTo(electroDermalActivity);
                        heartRate = listenReceiver.getHeartRate();
                        if(heartRate < 40){
                            heartbeat2.stop();
                        }
                        heartbeat2.setDurationBasedOnBPM(heartRate);
                        bpm.setText(heartRate+" bpm");

                    }
                });



            }
            heartbeat2.stop();
        }


        public void start() {
            worker = new Thread(this);
            worker.start();
        }

        public void stop() {
            running.set(false);
        }

        private boolean isRunning(){
            return running.get();
        }

        // The strength of electrodermal activity determines the level and hence the color
        private void setTrafficLightAccordingTo(double electroDermalActivity){
            double x = electroDermalActivity;
            int color;

            if(x<0) {
                System.out.println("Value missing!");
            } else if(x<0.5){
                color = getResources().getColor(R.color.Green);
                layout.setBackgroundColor(color);
                level.setText(R.string.VL);
            } else if (x<1){
                color = getResources().getColor(R.color.YellowGreen);
                layout.setBackgroundColor(color);
                level.setText(R.string.LO);
            } else if (x<1.5){
                color = getResources().getColor(R.color.Yellow);
                layout.setBackgroundColor(color);
                level.setText(R.string.NO);
            } else if (x<2){
                color = getResources().getColor(R.color.Yellowish);
                layout.setBackgroundColor(color);
                level.setText(R.string.HI);
            } else{
                color = getResources().getColor(R.color.Orange);
                layout.setBackgroundColor(color);
                level.setText(R.string.VH);
            }

        }

    }

    private static class TimeToStringConverter{

        private static double time;
        private static String stringTime;

        private static void converTime(){
            int minutes = (int) Math.floor(time/60);
            minutes = minutes%60;
            int seconds = (int) Math.floor(time);
            seconds = seconds%60;

            Integer min = new Integer(minutes);
            Integer sec = new Integer(seconds);

            String mm = formatMinutes(min);
            String ss = formatSeconds(sec);

            stringTime = mm+":"+ss;
        }

        private static String formatSeconds(Integer sec){
            if(sec.intValue()<10){
                return "0"+sec.toString();
            } else{
                return sec.toString();
            }
        }

        private static String formatMinutes(Integer min){
            if(min.intValue()<10){
                return "0"+min.toString();
            } else{
                return min.toString();
            }
        }

        public static String convert(double time){
            TimeToStringConverter.time = time;
            converTime();
            return stringTime;
        }
    }

    // checking state of communication
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_STATE_CHANGED.equals(action)) {
                String identifier = intent.getStringExtra(IDENTIFIER);
                Constants.States state = Constants.States.getStates(intent.getIntExtra(EXTRA_STATE_CHANGED, 0));

                Log.i(TAG, identifier + " -> " + state.name());

                stateTextView.setText(state.name());

                switch (state) {
                    case NO_CONNECTION:
                        break;
                    case LISTEN:
                        break;
                    case CONNECTING:
                        break;
                    case CONNECTED:
                        turnOnButtons(true);
                        break;
                    case ACQUISITION_TRYING:
                        break;
                    case ACQUISITION_OK:
                        break;
                    case ACQUISITION_STOPPING:
                        break;
                    case DISCONNECTED:
                        turnOnButtons(false);
                        uiThread.stop();
                        break;
                    case ENDED:
                        break;
                }
            }

        }


    };



}