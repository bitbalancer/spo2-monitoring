package info.plux.api.Observant_v32.ui.main;

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

import info.plux.api.Observant_v32.R;
import info.plux.api.Observant_v32.SingleLiveEvent.SingleLiveEvent;
import info.plux.api.Observant_v32.activities.MainActivity;
import info.plux.api.Observant_v32.database.DataRow;
import info.plux.api.Observant_v32.database.MeasureDB;
import info.plux.api.Observant_v32.thermometer.Thermometer;
import info.plux.api.Observant_v32.thermometer.ThermometerHorizontal;
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
    protected static final String TIME_KEY = "time";
    protected static final String EDA_KEY = "eda";
    protected static final float EDA_DEFAULT = -1;
    protected static final String HR_KEY = "hr";
    protected static final String RUNNING_ON_ROT_KEY = "running";
    protected static final String STATE_KEY = "state";


    protected static final int FREQUENCY = 1000; // up to 1000 Hz possible but not recommendable due to substantial delay
    protected static int maxDataPoints = PlotFragment.MAX_X * FREQUENCY;


    // setup of channel 1 and 2 for Biosignalsplux hub
    private static final Source[] SOURCES = new Source[]{
            new Source(1, 16, (byte) 0x01, 1),
            new Source(2, 16, (byte) 0x01, 1)
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
    private float eda = -1;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean runningOnRotation = false;
    private boolean toBeSaved = true;

    private static final String ARG_SECTION_NUMBER = "section_number";

    private DatabaseObserver databaseObserver;




    // *********************************************************************************************
    // Lifecycle Callbacks
    // *********************************************************************************************


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        colorViewModel = new ViewModelProvider(this).get(ColorViewModel.class);

        mDB = MeasureDB.getInstance(getContext());

        colorFragment = this;  // used in clearDatabase() for clear button

        currentOrientation = getResources().getConfiguration().orientation;

    }


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

        // -----------------------------------------------------------------------------------------
        // Initialization of Broadcast Receiver
        // -----------------------------------------------------------------------------------------

        // Receiver listens to state changes in Bioplux communication channel.
        intentFilterForStateReceiver = new IntentFilter();
        intentFilterForStateReceiver.addAction(ACTION_STATE_CHANGED);

        // -----------------------------------------------------------------------------------------
        // Setup of Observers
        // -----------------------------------------------------------------------------------------

        // 1. observer

        // Observes changes in database to display current values
        databaseObserver = new DatabaseObserver();
        mDB.dataRowDAO().getLastRecord().observe(getViewLifecycleOwner(), databaseObserver);


        // 2. observer

        // Observes when data from database is loaded to activate buttons and add data series to graph.
        isLoaded.observe(getViewLifecycleOwner(), new Observer<DataRow>() {

            @Override
            public void onChanged(DataRow dataRow) { // Remember: SingleLiveEvent is NOT called after rotation when onResume begins in comparison to MutableLiveData!
                // MutableLiveData is also called after restarting or recreating of activity when onResume begins!
                // Explanation: When a new observer is registered on LiveData, LiveData is initialized with value from earlier.

                addSeriesToGraph();
                Log.i(TAG,"Series has been initialized and added");

                // If OS killed process (activity + ViewModel), than desired UI data is still available in the surviving savedInstanceState
                if(savedInstanceState==null) {


                    // Gets last record in database to initialize UI elements if no temporarily saved data is available
                    if (dataRow != null) {
                        eda = (float) dataRow.electrodermalActivity;
                        timeView.setText(FancyStringConverter.convert(dataRow.time));
                        setColorAccordingToLevelOf(eda);
                        if(dataRow.heartRate >= 0) {
                            heartRateView.setText(Integer.toString(dataRow.heartRate) + " bpm");
                        } else{
                            heartRateView.setText(getString(R.string.heart_rate_default));
                        }

                        if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                            thermometer.setCurrentTemp(eda);
                        } else {
                            thermometerHorizontal.setCurrentTemp(eda);
                        }
                        Log.v(TAG,"dataRow not null!");
                    }
                }

                Buttons.tryEnabling(0);  // Activates Buttons if state of connection has been confirmed as CONNECTED by the broadcast receiver
                loadingView.setVisibility(View.GONE); // LoadingView is removed to uncover UI elements
                lockDeviceRotation(false); // After loading process is completed rotation is permitted


                Log.d(TAG,"Loaded: level = " + Buttons.getLevel());
                try {
                    Log.d(TAG,"Loaded: last time = " + dataRow.time);
                } catch (NullPointerException ex) {
                    Log.d(TAG,"Loaded: last time = NOT AVAILABLE");
                }
            }

        });

        return root;
    }


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
        Buttons.disable();

        // time display
        timeView = root.findViewById(R.id.text_time);

        // state display
        stateView = root.findViewById(R.id.text_state);

        // -----------------------------------------------------------------------------------------
        // Checking Stuff Related to Handler for Loading Process
        // -----------------------------------------------------------------------------------------

        // Checks if handler has been already created
        if (ColorViewModel.handlerThread == null) {
            ColorViewModel.handlerThread = new HandlerThread("LoadAndInitThread");
            Log.v(TAG, "Handler initialized!");
        } else if(savedInstanceState != null){
            Float savedEda = savedInstanceState.getFloat(EDA_KEY);
            timeView.setText(savedInstanceState.getString(TIME_KEY));
            setColorAccordingToLevelOf(savedEda);
            Log.d(TAG,Float.toString(savedInstanceState.getFloat(EDA_KEY)));
            heartRateView.setText(savedInstanceState.getString(HR_KEY));
            runningOnRotation = savedInstanceState.getBoolean(RUNNING_ON_ROT_KEY);
            stateView.setText(savedInstanceState.getString(STATE_KEY));

            if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                thermometer.setCurrentTemp(savedEda);
            } else {
                thermometerHorizontal.setCurrentTemp(savedEda);
            }
            Log.v(TAG, "SavedInstanceState used!");
        }

        // Thread can be started only ONCE! Otherwise IllegalThreadStateException is thrown!
        if (!ColorViewModel.handlerThread.isAlive()) { // checks if thread has been started and yet not died.

            loadingView.setVisibility(View.VISIBLE); // Covers initially all other elements of UI
            lockDeviceRotation(true); // During loading is no rotation allowed

            // Execute necessary Preparations
            colorViewModel.prepare(getContext());

            Log.v(TAG, "Handler has become alive!");


        } else {
            Log.v(TAG, "Handler already alive!");
        }

        // -----------------------------------------------------------------------------------------
        // UI Buttons
        // -----------------------------------------------------------------------------------------

        // 1. Start button

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

        // 2. Pause button

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

                    ColorViewModel.writeHandler.heartRateSMA.interrupt(); // Deletes all previously saved values

                }
            }
        });

        // 3. Clear button

        btClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Prevents configuration change during clearing process
                // Because the clearing process effects UI, the app would crash.
                lockDeviceRotation(true);

                Buttons.disable(); // Disable all buttons

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

                ColorViewModel.writeHandler.heartRateSMA.reset(); // All related parameters to SMA are reset to initial values

                clearDatabase();

            }
        });


    }


    @Override
    public void onResume() {
        super.onResume();

        Log.v(TAG,"ON RESUME");

        checkAndSetOrientationInfo();

        // Monitoring
        if (ColorViewModel.rotating) {

            Log.v(TAG, "Rotating: Level = "+Buttons.getLevel());

        } else {

            Log.v(TAG, "Not rotating (task switch / home etc)");
        }

        // statReceiver registers state changes in Bioplux communication channel.
        getActivity().registerReceiver(stateReceiver, intentFilterForStateReceiver);

        // -----------------------------------------------------------------------------------------
        // Handling of Different Cases of Resume
        // -----------------------------------------------------------------------------------------

        boolean interrupt = true;

        if(ColorViewModel.comeback){ // The user has returned to scan activity and has come back
            ColorViewModel.comeback=false;

            colorViewModel.setUpBiopluxCommunication(getContext(),MainActivity.getBluetoothDevice()); // Repeats this part with the new Bioplux device

            ColorViewModel.loadAndInitHandler.sendEmptyMessage(1);

        } else{

            // button control
            Buttons.tryEnabling(1);

            if(ColorViewModel.rotating){

                addSeriesToGraph();
                Log.v(TAG,"Series has been added!");

                // If recording is running when rotation happens, than continue recording after rotation.
                if(runningOnRotation && ColorViewModel.inTime){ // The last condition causes that rotations are ignored while the app is in the background.

                    btStart.performClick();
                    Log.v(TAG,"Continue recording after rotation!");
                    interrupt = false;
                }

            }

        }

        // Resets simple moving average when recording is discontinued
        if(interrupt) {
            ColorViewModel.writeHandler.heartRateSMA.interrupt();
        }

    }

    // Not called when program is aborted (red square) or rebuilt => Don't worry if latest heart rate wasn't saved in prefs
    @Override
    public void onPause() {
        super.onPause();

        Log.v(TAG,"ON PAUSE");

        getActivity().unregisterReceiver(stateReceiver);

        activateHeartBeat(false);
        // Records if recording has been running when onPause is called.
        runningOnRotation=running.get();
        running.set(false);
        // Rotation must occur in time slot to continue recording afterwards, provided that's the case.
        TimeSlotTask timeSlotTask = new TimeSlotTask();
        timeSlotTask.execute();

        // lockDeviceRotation(false) updates orientation which could have been already changed during lock phase.
        if(toBeSaved) {
            ColorViewModel.previousOrientation = getResources().getConfiguration().orientation;
        } else{
            toBeSaved = true;
        }
        Log.i(TAG,"Show orientation onPause: "+ColorViewModel.previousOrientation);

        try {
            ColorViewModel.bioplux.stop();
        } catch (BiopluxException e) {
            e.printStackTrace();
        }

        // Warning: This is CRUCIAL! Without it pending messages would be flooding in long after Bioplux has been put on hold.
        ColorViewModel.writeHandler.removeCallbacksAndMessages(null);

    }


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

        //handlerThread.quit(); // Do not use it!
    }

    // *********************************************************************************************
    // Classes
    // *********************************************************************************************

    /**
     * This observer takes the last single data row from the database to extract the data and display it in
     * the interface like time, heart rate, electrodermal activity etc.
     */
    private class DatabaseObserver implements Observer<DataRow> {
        //float eda;
        double time;
        int heartRate;


        @Override
        public void onChanged(DataRow dataRow) {

            if (!ColorViewModel.ignore) { // Ignores changes to database during loading process

                if (dataRow != null) {

                    // -----------------------------------------------------------------------------
                    // 1. Display of Time
                    // -----------------------------------------------------------------------------

                    time = dataRow.time;

                    ColorViewModel.currentTime = FancyStringConverter.convert(time);
                    timeView.setText(ColorViewModel.currentTime);


                    // -----------------------------------------------------------------------------
                    // 2. Display of Electrodermal Activity on Temperature Scale
                    // -----------------------------------------------------------------------------

                    eda = (float) dataRow.electrodermalActivity;

                    // The current orientation determines which kind of temperature scale is used for depicting EDA.
                    // The scales are adapted to their orientation.
                    if(currentOrientation==Configuration.ORIENTATION_PORTRAIT){
                        thermometer.setCurrentTemp(eda);
                    } else {
                        thermometerHorizontal.setCurrentTemp(eda);
                    }
                    setColorAccordingToLevelOf(eda); // Determines background color of UI according to EDA intensity

                    // -----------------------------------------------------------------------------
                    // 3. Display of Heart Rate
                    // -----------------------------------------------------------------------------

                    heartRate = dataRow.heartRate;

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

                    // -----------------------------------------------------------------------------
                    // 4. Plot of Electrodermal Activity & Electrocardiogram
                    // -----------------------------------------------------------------------------

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
            ColorViewModel.seriesArr[0].appendData(new DataPoint(dataRow.time, dataRow.electrodermalActivity), scrollToEnd, maxDataPoints);
            ColorViewModel.seriesArr[1].appendData(new DataPoint(dataRow.time, dataRow.electroCardiogram), scrollToEnd, maxDataPoints);
        }


    }

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

    /**
     * This class controls the buttons in accordance to loading, connection state and other circumstances.
     */
    public static class Buttons {

        /**
         * The chosen case if levelUp = 0
         */
        private static int level = 0;



        /**
         * This method coordinates all the different conditions that determines if the buttons
         * should be enabled or not. E.g. when the app is started, buttons should only be active if
         * loading process is finished AND connection state confirmed. During connection loss buttons
         * should be disabled as long as it lasts and not effected by other circumstances.
         * Case 1 and 3 are missing because this way first method call in onResume does not interfere.
         *
         * @param increment The parameter can increase the current level to create the desired outcome
         *                  depending on the situation. Only 0 and 1 is used.
         */
        private static void tryEnabling(int increment) {

            int input = level + increment;
            switch (input) {
                case 0:
                    level += 2;
                    break;
                case 2:
                    activate(true);
                    level += 2; // By first time loading finished and connection confirmed
                    break;
                case 4: // This input gets ignored, e.g. multiple CONNECTED confirmations.
                    break;
                case 5:
                    activate(true);
                    break;
                default:
                    Log.d(TAG,"Default case: input = "+input);
                    break;
            }

        }

        /**
         * The method disables the buttons all together.
         */
        private static void disable() {
            activate(false);
        }

        private static void activate(boolean on) {
            btStart.setEnabled(on);
            btPause.setEnabled(on);
            btClear.setEnabled(on);
            Log.i(TAG, "Button state changed: " + on);
        }


        public static void setLevel(int level) {
            Buttons.level = level;
        }

        private static int getLevel() {
            return Buttons.level;
        }

    }


    // *********************************************************************************************
    // Other Methods & Attributes
    // *********************************************************************************************

    @Override
    public void onSaveInstanceState(Bundle outState) {

        outState.putString(TIME_KEY, timeView.getText().toString());
        outState.putFloat(EDA_KEY, eda);
        outState.putString(HR_KEY, heartRateView.getText().toString());
        outState.putBoolean(RUNNING_ON_ROT_KEY, runningOnRotation);
        outState.putString(STATE_KEY, stateView.getText().toString());


        super.onSaveInstanceState(outState);

        Log.v(TAG, "InstanceState saved!");

    }

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


    /**
     *  Disables buttons for some time to prevent crashes by pressing different buttons in quick sequence
     */
    private void coolDown(long delay) {

        Buttons.disable();

        activateHandler.postDelayed(activateRunnable, delay); // After a certain time the buttons are enabled again

    }

    Handler activateHandler = new Handler(Looper.getMainLooper());

    Runnable activateRunnable = new Runnable() {
        @Override
        public void run() {
            // update the ui from here
            Buttons.tryEnabling(1);
        }
    };


    /**
     *  The series are added to the graph. The graph holds the series.
     */
    private void addSeriesToGraph(){
        PlotFragment.graph.addSeries(ColorViewModel.seriesArr[0]); // EDA added
        ColorViewModel.seriesArr[1].setColor(Color.RED); // Make ECG series red
        PlotFragment.graph.getSecondScale().addSeries(ColorViewModel.seriesArr[1]); // ECG series added

        if(ColorViewModel.seriesArr[0].getHighestValueX()>PlotFragment.MAX_X){ // Do not scroll to end of series when whole series is still in visible area.
            PlotFragment.viewport.scrollToEnd(); // scroll to last data point of series
        }
    }


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
                //stateView.setText(state.name());

                switch (state) {
                    case NO_CONNECTION:
                        break;
                    case LISTEN:
                        break;
                    case CONNECTING:
                        Buttons.setLevel(2);
                        Buttons.disable();
                        activateHeartBeat(false);
                        running.set(false);
                        Log.d(TAG,"CONNECTING: level = "+Buttons.getLevel());
                        break;
                    case CONNECTED:
                        Buttons.tryEnabling(0);
                        Log.d(TAG,"CONNECTED: level = "+Buttons.getLevel());
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


    /**
     * The strength of electrodermal activity determines the color
     */
    private void setColorAccordingToLevelOf(double electroDermalActivity) {
        double x = electroDermalActivity;
        int color;

        if (x < 0) {
            color = getResources().getColor(R.color.White);
            colorFragmentLayout.setBackgroundColor(color);
            colorLevelView.setText(R.string.start);
        } else if (x < 0.5) {
            color = getResources().getColor(R.color.VeryLow);
            colorFragmentLayout.setBackgroundColor(color);
            colorLevelView.setText(R.string.VL);
        } else if (x < 1) {
            color = getResources().getColor(R.color.Low);
            colorFragmentLayout.setBackgroundColor(color);
            colorLevelView.setText(R.string.LO);
        } else if (x < 3) {
            color = getResources().getColor(R.color.Normal);
            colorFragmentLayout.setBackgroundColor(color);
            colorLevelView.setText(R.string.NO);
        } else if (x < 7) {
            color = getResources().getColor(R.color.High);
            colorFragmentLayout.setBackgroundColor(color);
            colorLevelView.setText(R.string.HI);
        } else {
            color = getResources().getColor(R.color.VeryHigh);
            colorFragmentLayout.setBackgroundColor(color);
            colorLevelView.setText(R.string.VH);
        }

    }


    /**
     * This method creates and starts the ClearTask. ClearTask deletes the database
     * and resets parameters to initial values. During deletion buttons are disabled
     * and rotation locked.
     */
    protected static void clearDatabase(){
        //ColorViewModel.time = 0.; // will be overridden at this point due to incoming messages

        // AsyncTask (that survives the lifecycle) is used to guarantee clearing of database
        // even when user finishes app during the clearing process.
        ClearTask clearTask = new ClearTask(mDB,colorFragment);
        clearTask.execute();
    }

    /**
     * This method is executed in onPostExecute of ClearTask. The displayed values in the UI
     * are set to the initial ones.
     */
    protected void resetUi() {
        Buttons.tryEnabling(1);
        Log.d(TAG,"Button access level after resetUI: " + Buttons.getLevel());

        activateHeartBeat(false);
        heartRateView.setText(getString(R.string.heart_rate_default));
        timeView.setText(getString(R.string.time_default));
        colorLevelView.setText(R.string.start);
        setColorAccordingToLevelOf(EDA_DEFAULT);
        eda = EDA_DEFAULT;

        lockDeviceRotation(false); // No rotation during resetting

    }

    // ---------------------------------------------------------------------------------------------
    // For Device Rotation
    // ---------------------------------------------------------------------------------------------

    private void checkAndSetOrientationInfo() {
        int currentOrientation = getResources().getConfiguration().orientation;
        debugDescribeOrientations(currentOrientation);
        if(ColorViewModel.previousOrientation != Configuration.ORIENTATION_UNDEFINED // starting undefined
                && ColorViewModel.previousOrientation != currentOrientation) {

            ColorViewModel.rotating = true;
        } else{
            ColorViewModel.rotating = false;
        }
        //ColorViewModel.previousOrientation = currentOrientation;
    }

    private String getOrientationAsString(final int orientation) {
        if(orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return "Landscape";
        } else if(orientation == Configuration.ORIENTATION_PORTRAIT) {
            return "Portrait";
        } else return "Undefined";
    }

    private void debugDescribeOrientations(final int currentOrientation) {
        Log.v("Orientation", "previousOrientation: " + getOrientationAsString(ColorViewModel.previousOrientation));
        Log.v("Orientation", "currentOrientation: " + getOrientationAsString(currentOrientation));
    }


    private void lockDeviceRotation(boolean value) {
        Activity activity = getActivity();
        if (value) {
            int currentOrientation = getResources().getConfiguration().orientation;
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }


            // records orientation before orientation will become unlocked again.
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