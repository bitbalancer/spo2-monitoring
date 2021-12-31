package info.plux.api.SpO2Monitoring.ui.main;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.jjoe64.graphview.series.DataPoint;
import com.scottyab.HeartBeatView;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import info.plux.api.SpO2Monitoring.R;
import info.plux.api.SpO2Monitoring.SingleLiveEvent.SingleLiveEvent;
import info.plux.api.SpO2Monitoring.activities.MainActivity;
import info.plux.api.SpO2Monitoring.database.DataRow;
import info.plux.api.SpO2Monitoring.database.MeasureDB;
import info.plux.api.SpO2Monitoring.thermometer.Thermometer;
import info.plux.api.SpO2Monitoring.thermometer.ThermometerHorizontal;
import info.plux.pluxapi.Constants;
import info.plux.pluxapi.bioplux.BiopluxException;
import info.plux.pluxapi.bioplux.utils.Source;

import static info.plux.pluxapi.Constants.ACTION_STATE_CHANGED;
import static info.plux.pluxapi.Constants.EXTRA_STATE_CHANGED;
import static info.plux.pluxapi.Constants.IDENTIFIER;

// Hint: All Methods here have direct or indirect effects on UI.

/**
 * A placeholder fragment containing 6 views and 3 buttons
 */
public class ColorFragment extends Fragment {
    private static final String TAG = "ColorFragment";

    public static final long DELAY = 1000;
    // Suggestion: Do not save ui states in prefs but in savedInstanceState
    // State of running could be passed additionally instead of overly complex recording system
    // protected static SharedPreferences prefs;
    // protected static SharedPreferences.Editor editor;


    /* When database reaches a certain size and hence the loading process takes some time,
     * the observer is likely to register a change to database supposedly due to select queries in the loading process!
     * This starts the heart beating undesired. Hence we use SingleLiveEvent to prevent this side effect.
     */
    protected static SingleLiveEvent<DataRow> isLoaded = new SingleLiveEvent<>();

    private static final int BPM_LIMIT = 40;
    // Adjust maximally expected value for your quantity
    // In this for case Sp02 Sensor (Biosignalsplux)
    private static final double MAX_LEVEL = 1.2;
    protected static final String TIME_KEY = "time";
    protected static final String VAL_1_KEY = "val_1";
    protected static final float VAL_1_DEFAULT = -1f;
    protected static final String VAL_3_KEY = "heart_rate";
    protected static final String RUNNING_ON_ROT_KEY = "running";
    protected static final String STATE_KEY = "state";


    protected static final int FREQUENCY = 20; // up to 1000 Hz possible but not recommendable due to substantial delay
    protected static int maxDataPoints = (int) Math.ceil(PlotFragment.MAX_X) * FREQUENCY; // Number of data points maximally hold in a series (GraphView)


    // Setup of Ports of Biosignalsplux hub
    // Ports ranges from 0 to 9. The bitmask designates the channels of the sensor.
    private static final Source[] SOURCES = new Source[]{
            // Setup for Sp02 sensor with two channels. Connected to ground port (port 9)
            new Source(9,16,(byte) 0x01, 1),
            new Source(9,16,(byte) 0x02, 1),
    }; // Do not use (different) divisors! Missing values between actual measurements are set to 0. Won't work with filtering in observer.

    private static final List<Source> SOURCES_LIST = Arrays.asList(SOURCES);

    // ui elements
    // for heart
    private static HeartBeatView heartbeat2View;
    // for thermometer
    private static Thermometer thermometer;
    private static ThermometerHorizontal thermometerHorizontal;
    // for changing background color
    private ConstraintLayout colorFragmentLayout;
    // buttons
    protected static Button btStart, btPause, btClear;
    // for connection state report
    private TextView stateView;
    // More TextViews to display data
    private TextView colorLevelView, heartRateView, timeView, loadingView;
    private ColorViewModel colorViewModel;
    // orientation
    int currentOrientation;

    IntentFilter intentFilterForStateReceiver;
    // database
    private static MeasureDB mDB;
    // electrodermal activity
    protected static ColorFragment colorFragment;
    private float val_1 = -1f;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean runningOnRotation = false;
    private boolean toBeSaved = true;

    private static final String ARG_SECTION_NUMBER = "section_number";




    //**********************************************************************************************
    // Lifecycle Callbacks
    //**********************************************************************************************


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        colorViewModel = new ViewModelProvider(this).get(ColorViewModel.class);

        mDB = MeasureDB.getInstance(getContext());

        colorFragment = this;  // used in clearDatabase() for clear button
        // will be used in LoadingTask

        currentOrientation = getResources().getConfiguration().orientation;

    }

    //----------------------------------------------------------------------------------------------

    /**
     * Necessary preparations for communication and setup of observers
     *
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_color, container, false);

        //------------------------------------------------------------------------------------------
        // Create Broadcast Receiver
        //------------------------------------------------------------------------------------------

        // Receiver listens to state changes in Bioplux communication channel.
        intentFilterForStateReceiver = new IntentFilter();
        intentFilterForStateReceiver.addAction(ACTION_STATE_CHANGED);

        //------------------------------------------------------------------------------------------
        // Create Observer
        //------------------------------------------------------------------------------------------

        // Observes changes in database to display current values
        final DatabaseObserver databaseObserver = new DatabaseObserver();
        mDB.dataRowDAO().getLastRecord().observe(getViewLifecycleOwner(), databaseObserver);

        //------------------------------------------------------------------------------------------

        // Checks if all conditions are satisfied and hence enables/disables buttons.
        final Observer<Boolean[]> clickableObserver = new Observer<Boolean[]>() {

            // Debugging
            // int counter = 0;


            private boolean conditional, isSatisfied;
            int leng = 0;

            @Override
            public void onChanged(Boolean[] conditionals) {
                // counter++;

                // Checks if array exists
                if(conditionals != null) {
                    isSatisfied = true; // Must be true! Do not initialize once in declaration.

                    // Prevents buttons to be enabled in case of empty array
                    if( conditionals.length>0 ){
                        leng = conditionals.length;
                    } else{
                        isSatisfied = false;
                    }

                    // Checks if all conditions are satisfied.
                    for (int i = 0; i < leng; i++) {

                        conditional = conditionals[i].booleanValue();
                        Log.d(TAG, "Clickable_Condition "+i+ " == "+conditional);
                        isSatisfied = isSatisfied && conditionals[i].booleanValue();
                    }

                    // Enables buttons if all conditions met
                    btStart.setEnabled(isSatisfied);
                    btPause.setEnabled(isSatisfied);
                    btClear.setEnabled(isSatisfied);

                    Log.i(TAG, "Buttons enabled: " + isSatisfied);

                } else{
                    Log.w(TAG, "Clickable is null!");
                }
            }
        };

        colorViewModel.getClickable().observe(getViewLifecycleOwner(), clickableObserver);

        //------------------------------------------------------------------------------------------

//        // Checks if all conditions are satisfied and hence recording is continued.
//        final Observer<Boolean[]> continuantObserver = new Observer<Boolean[]>() {
//
//            // Debugging
//            // int counter = 0;
//
//
//            private boolean conditional, isSatisfied;
//            int leng = 0;
//
//            @Override
//            public void onChanged(Boolean[] conditionals) {
//                // counter++;
//
//                // Checks if array exists
//                if(conditionals != null) {
//                    isSatisfied = true; // Must be true! Do not initialize once in declaration.
//
//                    // Prevents buttons to be enabled in case of empty array
//                    if( conditionals.length>0 ){
//                        leng = conditionals.length;
//                    } else{
//                        isSatisfied = false;
//                    }
//
//                    // Checks if all conditions are satisfied.
//                    for (int i = 0; i < leng; i++) {
//
//                        conditional = conditionals[i].booleanValue();
//                        Log.d(TAG, "Continuant_Condition "+i+ " == "+conditional);
//                        isSatisfied = isSatisfied && conditionals[i].booleanValue();
//                    }
//
//                    colorViewModel.setIsContinuant(isSatisfied);
//
//
//                } else{
//                    Log.w(TAG, "Continuant is null!");
//                }
//            }
//        };
//
//        colorViewModel.getContinuant().observe(getViewLifecycleOwner(), continuantObserver);

        return root;
    }

    // Continues recording if all conditions are met
    public void checkOnRecording() {
        boolean continuant = colorViewModel.checkContinuant();
        if (continuant) {
            btStart.performClick();
        } else {
            // Reset simple moving average if recording is discontinued
            ColorViewModel.writeHandler.heartRateSMA.reset(false);
        }

        Log.i(TAG, "Recording continued: " + continuant);
    }


    //----------------------------------------------------------------------------------------------

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        View root = getView();

        loadingView = root.findViewById(R.id.text_loading);

        // heart beat
        heartbeat2View = root.findViewById(R.id.heartbeat2);
        heartRateView = root.findViewById(R.id.text_heart_rate);

        // traffic lights
        colorFragmentLayout = root.findViewById(R.id.fragment_color);
        colorLevelView = root.findViewById(R.id.text_color_level);
        thermometer = root.findViewById(R.id.thermometer);
        thermometerHorizontal = root.findViewById(R.id.thermometer_horizontal);

        // buttons
        btStart = root.findViewById(R.id.bt_start);
        btPause = root.findViewById(R.id.bt_pause);
        btClear = root.findViewById(R.id.bt_clear);
        //Buttons.disable();

        // time display
        timeView = root.findViewById(R.id.text_time);

        // state display
        stateView = root.findViewById(R.id.text_state);



        //------------------------------------------------------------------------------------------
        // Start loading and initializing process
        //------------------------------------------------------------------------------------------

        if(ColorViewModel.isInitialized){

            Log.v(TAG, "Loading already done!");

            if(savedInstanceState != null){ // Possibly redundant

                timeView.setText(savedInstanceState.getString(TIME_KEY));
                Float savedVal1 = savedInstanceState.getFloat(VAL_1_KEY);
                setColorAccordingToLevelOf(savedVal1);

                // Debugging
                Log.d(TAG,Float.toString(savedVal1));

                heartRateView.setText(savedInstanceState.getString(VAL_3_KEY));
                stateView.setText(savedInstanceState.getString(STATE_KEY));
                runningOnRotation = savedInstanceState.getBoolean(RUNNING_ON_ROT_KEY);

                if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                    thermometer.setCurrentTemp(savedVal1);
                } else {
                    thermometerHorizontal.setCurrentTemp(savedVal1);
                }

                Log.v(TAG, "SavedInstanceState used!");
            }

        } else{

            initialize();

        }

        //------------------------------------------------------------------------------------------
        // UI Buttons
        //------------------------------------------------------------------------------------------

        //==========================================================================================
        // 1. Start button
        //==========================================================================================

        btStart.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                coolDown(DELAY); // Disables buttons for short time

                // Checks if recording is already active
                if (!running.get()) {

                    running.set(true);

                    try {
                        // Starts recording
                        ColorViewModel.bioplux.start(FREQUENCY, SOURCES_LIST);
                    } catch (BiopluxException e) {
                        e.printStackTrace();
                    }

                }
            }
        });

        //==========================================================================================
        // 2. Pause button
        //==========================================================================================

        btPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                coolDown(DELAY); // Disables buttons for short time

                // Checks if recording is already active
                if (running.get()) {

                    running.set(false);

                    activateHeartBeat(false);

                    try {
                        // Stops recording
                        ColorViewModel.bioplux.stop();
                    } catch (BiopluxException e) {
                        e.printStackTrace();
                    }

                    ColorViewModel.writeHandler.removeCallbacksAndMessages(null); // Removes all pending messages

                    ColorViewModel.writeHandler.heartRateSMA.reset(false); // Deletes all previously saved values

                }
            }
        });

        //==========================================================================================
        // 3. Clear button
        //==========================================================================================

        btClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Prevents configuration change during clearing process
                // Because the clearing process effects UI, the app would crash.
                lockDeviceRotation(true);

                colorViewModel.changeClickable(3, false);

                if (running.get()) {

                    running.set(false);

                    try {
                        // Stops recording
                        ColorViewModel.bioplux.stop();
                    } catch (BiopluxException e) {
                        e.printStackTrace();
                    }
                }

                ColorViewModel.writeHandler.removeCallbacksAndMessages(null); // Removes all pending messages

                ColorViewModel.writeHandler.heartRateSMA.reset(true); // All related parameters to SMA are reset to initial values

                clearDatabase();

            }
        });


    }

    //----------------------------------------------------------------------------------------------

    @Override
    public void onResume() {
        super.onResume();

        Log.v(TAG,"ON RESUME");

        // statReceiver registers state changes in Bioplux communication channel.
        getActivity().registerReceiver(stateReceiver, intentFilterForStateReceiver);

        //------------------------------------------------------------------------------------------
        // Checking for Rotation
        //------------------------------------------------------------------------------------------

        checkAndSetOrientationInfo();

        if (colorViewModel.getRotating()) {

            Log.v(TAG, "Rotating");

            addSeriesToGraph();

            Log.v(TAG, "Series added to graph!");

        } else {

            Log.v(TAG, "Not rotating (task switch / home etc)");
        }

        // If recording is running when rotation happens, than continue recording after rotation.
        checkOnRecording();


//        boolean interrupt = true;
//
//        {
//
//            if (runningOnRotation && ColorViewModel.inTime) { // The last condition causes that rotations are ignored while the app is in the background.
//
//                btStart.performClick();
//                Log.v(TAG, "Continue recording after rotation!");
//                interrupt = false;
//            }
//
//        }
//
//
//        // Resets simple moving average when recording is discontinued
//        if(interrupt) {
//            ColorViewModel.writeHandler.heartRateSMA.reset(false);
//        }

    }

    //----------------------------------------------------------------------------------------------

    // Not called when program is aborted (red square) or rebuilt => Don't worry if latest heart rate wasn't saved in prefs
    @Override
    public void onPause() {
        super.onPause();

        Log.v(TAG,"ON PAUSE");

        //------------------------------------------------------------------------------------------
        // Update Rotation Parameters
        //------------------------------------------------------------------------------------------

        // Records if recording has been running when onPause is called.
        //runningOnRotation=running.get();
        colorViewModel.changeContinuant(1,running.get());
        running.set(false);

        // lockDeviceRotation(false) updates orientation which could have been already changed during lock phase.
        if(toBeSaved) {
            ColorViewModel.previousOrientation = getResources().getConfiguration().orientation;
        } else{
            toBeSaved = true;
        }
        Log.i(TAG,"Show orientation onPause: "+ColorViewModel.previousOrientation);

        // Rotation must occur in time slot to continue recording afterwards, provided that's the case.
        //TimingRotationTask timingTask = new TimingRotationTask();
        //timingTask.execute();
        timeRotation(DELAY);

        //------------------------------------------------------------------------------------------
        // Interrupt Processes
        //------------------------------------------------------------------------------------------

        try {
            ColorViewModel.bioplux.stop();
        } catch (BiopluxException e) {
            e.printStackTrace();
        }

        getActivity().unregisterReceiver(stateReceiver);

        activateHeartBeat(false);

        // Warning: This is CRUCIAL! Without it pending messages would be flooding in long after Bioplux has been put on hold.
        ColorViewModel.writeHandler.removeCallbacksAndMessages(null);

    }

    //----------------------------------------------------------------------------------------------

    @Override
    public void onStop() {
        super.onStop();

        Log.v(TAG,"ON STOP");

    }

    //Remember: Do not use it! Not reliable because it isn't executed everytime.
    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    public void onDetach() {
        super.onDetach();

        Log.v(TAG,"ON DETACH");

        // Do not use it!
        // handlerThread.quit();
    }

    //**********************************************************************************************
    // Class
    //**********************************************************************************************

    /**
     * This observer takes the last single data row from the database to extract the data and display it in
     * the interface like time, heart rate, electrodermal activity etc.
     */
    private class DatabaseObserver implements Observer<DataRow> {
        //float eda;
        double time;
        int heartRate;
        // Debugging
        // int counter = 0;

        @Override
        public void onChanged(DataRow dataRow) {

            // counter++;

            if (!ColorViewModel.ignore) { // Ignores changes to database during loading process

                if (dataRow != null) {

                    //------------------------------------------------------------------------------
                    // 1. Display of time
                    //------------------------------------------------------------------------------

                    time = dataRow.time;

                    ColorViewModel.currentTime = FancyStringConverter.convert(time);
                    timeView.setText(ColorViewModel.currentTime);


                    //------------------------------------------------------------------------------
                    // 2. Display of val_1 on temperature scale and through background color
                    //------------------------------------------------------------------------------

                    val_1 = getRelativeStrengthOf(dataRow.val_1); // in %

                    // The current orientation determines which kind of temperature scale is used for depicting val_1.
                    // The scales are adapted to their orientation.
                    if(currentOrientation==Configuration.ORIENTATION_PORTRAIT){
                        thermometer.setCurrentTemp(val_1);
                    } else {
                        thermometerHorizontal.setCurrentTemp(val_1);
                    }
                    setColorAccordingToLevelOf(val_1); // Determines background color of UI according to val_1 intensity



                    //------------------------------------------------------------------------------
                    // 3. Display of heart rate (val_3)
                    //------------------------------------------------------------------------------

                    heartRate = dataRow.val_3;

                    // Heart rate must be above 0
                    if(heartRate >= 0) {
                        // setDurationBasedOnBPM must not be used in too short intervals!
                        if (!ColorViewModel.currentTime.equals(ColorViewModel.previousTime)) { // Reduces to 1 Hz

                            heartRateView.setText(heartRate + " bpm"); // Displays heart rate

                            // Heart beats only over BPM limit
                            if (heartRate > BPM_LIMIT) { // 0 produces unwanted behaviour
                                heartbeat2View.setDurationBasedOnBPM(heartRate); // Determines speed of heart icon's beating
                                activateHeartBeat(true); // Starts heart beat if not already running
                            } else if (heartRate <= BPM_LIMIT) {
                                activateHeartBeat(false); // Stops heart beat if not already stopped
                            }
                            Log.v(TAG,"Time of previous heart rate : " + ColorViewModel.previousTime);
                            Log.v(TAG,"Time of current heart rate : " + ColorViewModel.currentTime);
                        }
                    }
                    ColorViewModel.previousTime = ColorViewModel.currentTime;



                    //------------------------------------------------------------------------------
                    // 4. Plot of val_1 and val_2
                    //------------------------------------------------------------------------------

                    if (dataRow.time > PlotFragment.MAX_X) { // When the series goes beyond the visible area, scroll to end of series.
                        append(dataRow, true);
                    } else {
                        append(dataRow, false);
                    }

                }

            }

        }

        // Expands and actualizes graph
        private void append(DataRow dataRow, boolean scrollToEnd){
            ColorViewModel.seriesArr[0].appendData(new DataPoint(dataRow.time, dataRow.val_1), scrollToEnd, maxDataPoints);
            ColorViewModel.seriesArr[1].appendData(new DataPoint(dataRow.time, dataRow.val_2), scrollToEnd, maxDataPoints);
        }


    }

    //**********************************************************************************************
    // Class
    //**********************************************************************************************

    /**
     * Bringing the time value into a nice displayable form.
     */
    protected static class FancyStringConverter {

        private static double time;
        private static String stringTime;

        private static void converTime() {
            int minutes = (int) Math.floor(time / 60);
            minutes = minutes % 60;
            int seconds = (int) Math.floor(time);
            seconds = seconds % 60;

            Integer min = new Integer(minutes);
            Integer sec = new Integer(seconds);

            String mm = formatMinutes(min);
            String ss = formatSeconds(sec);

            stringTime = mm + ":" + ss;
        }

        private static String formatSeconds(Integer sec) {
            if (sec.intValue() < 10) {
                return "0" + sec.toString();
            } else {
                return sec.toString();
            }
        }

        private static String formatMinutes(Integer min) {
            if (min.intValue() < 10) {
                return "0" + min.toString();
            } else {
                return min.toString();
            }
        }

        public static String convert(double time) {
            FancyStringConverter.time = time;
            converTime();
            return stringTime;
        }
    }


    //**********************************************************************************************
    // Further Methods & Attributes
    //**********************************************************************************************

    @Override
    public void onSaveInstanceState(Bundle outState) {

        outState.putString(TIME_KEY, timeView.getText().toString());
        outState.putFloat(VAL_1_KEY, val_1);
        outState.putString(VAL_3_KEY, heartRateView.getText().toString());
        outState.putBoolean(RUNNING_ON_ROT_KEY, runningOnRotation);
        outState.putString(STATE_KEY, stateView.getText().toString());


        super.onSaveInstanceState(outState);

        Log.v(TAG, "InstanceState saved!");

    }

    //----------------------------------------------------------------------------------------------

    /**
     * Used in SectionsPagerAdapter
     *
     * @param index
     * @return
     */
    public static ColorFragment newInstance(int index) {

        ColorFragment fragment = new ColorFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_SECTION_NUMBER, index);
        fragment.setArguments(bundle);
        return fragment;

    }

    //----------------------------------------------------------------------------------------------
    // UI Updates
    //----------------------------------------------------------------------------------------------

    //==============================================================================================
    // For Initialization
    //==============================================================================================

    private void initialize() {
        ColorViewModel.isInitialized = true;

        loadingView.setVisibility(View.VISIBLE); // Covers initially all other elements of UI
        lockDeviceRotation(true); // During loading is no rotation allowed

        colorViewModel.initialize(colorFragment);

    }

    //----------------------------------------------------------------------------------------------

    protected void initializeUI(DataRow dataRow) {

        addSeriesToGraph();
        Log.i(TAG, "Series has been initialized and added");


        if (dataRow != null) {
            val_1 = getRelativeStrengthOf(dataRow.val_1); // in %
            timeView.setText(FancyStringConverter.convert(dataRow.time));
            setColorAccordingToLevelOf(val_1);
            if (dataRow.val_3 >= 0) { // val_3 = heart rate
                heartRateView.setText(Integer.toString(dataRow.val_3) + " bpm");
            } else {
                heartRateView.setText(getString(R.string.heart_rate_default));
            }

            if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                thermometer.setCurrentTemp(val_1);
            } else {
                thermometerHorizontal.setCurrentTemp(val_1);
            }
            Log.v(TAG, "dataRow not null!");
        }


        //Buttons.tryEnabling(0);  // Activates Buttons if state of connection has been confirmed as CONNECTED by the broadcast receiver
        colorViewModel.changeClickable(0,true);
        loadingView.setVisibility(View.GONE); // LoadingView is removed to uncover UI elements
        lockDeviceRotation(false); // After loading process is completed rotation is permitted


        ColorViewModel.ignore = false;

        // Debugging
        // Observer is triggered once when registered to LiveData.
        // Log.d(TAG,"Observer counter: "+databaseObserver.counter);
    }

    //==============================================================================================
    //==============================================================================================

    /**
     * The start and stop methods of HeartbeatView are supposed to be used alternating.
     * This method allows not to care which method has been used before. E.g. if HeartbeatView
     * has been started, the method simply won't change the current state.
     *
     * @param on Determines if heart beat should be running or not
     */
    private void activateHeartBeat(boolean on){
        if(on){
            if(!heartbeat2View.isHeartBeating()){
                heartbeat2View.start();
            }
        } else{
            if(heartbeat2View.isHeartBeating()){
                heartbeat2View.stop();
            }
        }
    }

    //----------------------------------------------------------------------------------------------

    /**
     *  Disables buttons for some time to prevent crashes by pressing different buttons in quick sequence
     */
    private void coolDown(long delay) {

        colorViewModel.changeClickable(2,false);

        activateHandler.postDelayed(activateRunnable, delay); // After a certain time the buttons are enabled again

    }

    Handler activateHandler = new Handler(Looper.getMainLooper());

    Runnable activateRunnable = new Runnable() {
        @Override
        public void run() {
            colorViewModel.changeClickable(2,true);
        }
    };

    //----------------------------------------------------------------------------------------------

    private void timeRotation(long delay) {

        colorViewModel.changeContinuant(2,true);
        timingHandler.postDelayed(timingRunnable, delay); // After a certain time the buttons are enabled again

    }

    Handler timingHandler = new Handler(Looper.getMainLooper());

    Runnable timingRunnable = new Runnable() {
        @Override
        public void run() {
            colorViewModel.changeContinuant(2,false);
        }
    };
    //----------------------------------------------------------------------------------------------

    /**
     *  The series are added to the graph. The graph holds the series.
     */
    void addSeriesToGraph(){
        // Adds val_1 to scale
        PlotFragment.graph.addSeries(ColorViewModel.seriesArr[0]);

        // Makes val_2 series red
        ColorViewModel.seriesArr[1].setColor(Color.RED);

        // Adds val_2 series to scale
        PlotFragment.graph.addSeries(ColorViewModel.seriesArr[1]);

        // ALTERNATIVE: Adds val_2 series to second scale
        // PlotFragment.graph.getSecondScale().addSeries(ColorViewModel.seriesArr[1]);


        if(ColorViewModel.seriesArr[0].getHighestValueX()>PlotFragment.MAX_X){ // Do not scroll to end of series when whole series is still in visible area.
            PlotFragment.viewport.scrollToEnd(); // scroll to last data point of series
        }
    }

    //----------------------------------------------------------------------------------------------

    /**
     * Used for checking for state changes of communication channel to display it in UI
     */
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_STATE_CHANGED.equals(action)) {
                String identifier = intent.getStringExtra(IDENTIFIER);
                Constants.States state = Constants.States.getStates(intent.getIntExtra(EXTRA_STATE_CHANGED, 0));

                Log.i(TAG, identifier + " -> " + state.name());

                ColorViewModel.state = state.name();
                stateView.setText(ColorViewModel.state);
                // stateView.setText(state.name());

                switch (state) {
                    case NO_CONNECTION:
                        break;
                    case LISTEN:
                        break;
                    case CONNECTING:
                        colorViewModel.changeClickable(1,false);
                        activateHeartBeat(false);
                        running.set(false);
                        break;
                    case CONNECTED:
                        colorViewModel.changeClickable(1,true);
                        break;
                    case ACQUISITION_TRYING:
                        break;
                    case ACQUISITION_OK:
                        break;
                    case ACQUISITION_STOPPING:
                        break;
                    case DISCONNECTED:
                        break;
                    case ENDED:
                        break;
                }
            }

        }


    };

    //----------------------------------------------------------------------------------------------

    /**
     * Calculates relative strength of input value in %
     * @param input
     */
    private float getRelativeStrengthOf(double input){
        return 100f * (float)( input / MAX_LEVEL );
    }

    //----------------------------------------------------------------------------------------------

    /**
     * The relative strength ranging from 0 to 100 % determines the saturation of the background color
     * There are 5 labels for intensity: Very Low, Low, Normal, High, Very High.
     * The label is depending on the input.
     * @param x relative strength in %
     */
    private void setColorAccordingToLevelOf(double x) {
        int color;

        if( x < 0){

            color = getResources().getColor(R.color.White);

            colorLevelView.setText(R.string.start);

        } else {
            float[] hsv = new float[3];
            // hue
            hsv[0] = 1f;
            // saturation
            hsv[1] = (float) Math.pow(0.01*x,10); // Deviate from linear function
            // value
            hsv[2] = 1f;

            color = Color.HSVToColor(hsv); // Get background color

            // Adjust thresholds for your particular quantity.
            // In this for case Sp02 Sensor (Biosignalsplux)
            if (x < 80) {
                colorLevelView.setText(R.string.VL);
            } else if (x < 85) {
                colorLevelView.setText(R.string.LO);
            } else if (x < 90) {
                colorLevelView.setText(R.string.NO);
            } else if (x < 95) {
                colorLevelView.setText(R.string.HI);
            } else {
                colorLevelView.setText(R.string.VH);
            }
        }


        colorFragmentLayout.setBackgroundColor(color);
    }

    //----------------------------------------------------------------------------------------------
    /**
     * This method creates and starts the ClearTask. ClearTask deletes the database
     * and resets parameters to initial values. During deletion buttons are disabled
     * and rotation locked.
     */
    protected static void clearDatabase(){ // Remove static when not anymore necessary!
        // Will be overridden at this point due to incoming messages
        // ColorViewModel.time = 0.;

        // AsyncTask (that survives the lifecycle) is used to guarantee clearing of database
        // even when user finishes app during the clearing process.
        ClearingTask clearingTask = new ClearingTask(mDB,colorFragment);
        clearingTask.execute();
    }

    //----------------------------------------------------------------------------------------------

    /**
     * This method is executed in onPostExecute of ClearTask. The displayed values in the UI
     * are set to the initial ones.
     */
    protected void resetUi() {
        colorViewModel.changeClickable(3,true);
        //Buttons.tryEnabling(1);
        //Log.d(TAG,"Button access level after resetUI: " + Buttons.getLevel());

        activateHeartBeat(false);
        heartRateView.setText(getString(R.string.heart_rate_default));
        timeView.setText(getString(R.string.time_default));
        colorLevelView.setText(R.string.start);
        setColorAccordingToLevelOf(VAL_1_DEFAULT);
        val_1 = VAL_1_DEFAULT;

        lockDeviceRotation(false); // No rotation during resetting

    }

    //==============================================================================================
    // For Device Rotation
    //==============================================================================================

    private void checkAndSetOrientationInfo() {
        int currentOrientation = getResources().getConfiguration().orientation;
        debugDescribeOrientations(currentOrientation);
        if(ColorViewModel.previousOrientation != Configuration.ORIENTATION_UNDEFINED // starting undefined
                && ColorViewModel.previousOrientation != currentOrientation) {

            //ColorViewModel.rotating = true;
            colorViewModel.changeContinuant(0,true);

        } else{
            //ColorViewModel.rotating = false;
            colorViewModel.changeContinuant(0,false);
        }
        // ColorViewModel.previousOrientation = currentOrientation;
    }

    //----------------------------------------------------------------------------------------------

    private String getOrientationAsString(final int orientation) {
        if(orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return "Landscape";
        } else if(orientation == Configuration.ORIENTATION_PORTRAIT) {
            return "Portrait";
        } else return "Undefined";
    }

    //----------------------------------------------------------------------------------------------

    private void debugDescribeOrientations(final int currentOrientation) {
        Log.v("Orientation", "previousOrientation: " + getOrientationAsString(ColorViewModel.previousOrientation));
        Log.v("Orientation", "currentOrientation: " + getOrientationAsString(currentOrientation));
    }

    //----------------------------------------------------------------------------------------------

    private void lockDeviceRotation(boolean value) {
        Activity activity = getActivity();
        if (value) {
            int currentOrientation = getResources().getConfiguration().orientation;
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }


            // Records orientation before orientation will become unlocked again.
            ColorViewModel.previousOrientation=currentOrientation;
            toBeSaved = false;


        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
            } else {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            }
        }
    }



}