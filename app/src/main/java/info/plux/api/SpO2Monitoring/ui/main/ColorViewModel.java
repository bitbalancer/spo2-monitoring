package info.plux.api.SpO2Monitoring.ui.main;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import uk.me.berndporr.iirj.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;

import info.plux.api.SpO2Monitoring.activities.MainActivity;
import info.plux.api.SpO2Monitoring.database.DataRow;
import info.plux.api.SpO2Monitoring.database.MeasureDB;
import info.plux.pluxapi.Communication;
import info.plux.pluxapi.bioplux.BiopluxCommunication;
import info.plux.pluxapi.bioplux.BiopluxCommunicationFactory;
import info.plux.pluxapi.bioplux.BiopluxException;
import info.plux.pluxapi.bioplux.OnBiopluxDataAvailable;
import info.plux.pluxapi.bioplux.utils.BiopluxFrame;

// Hint: Methods here have no DIRECT effects on UI.

// code is lifecycle aware! Don't put it into fragment!
public class ColorViewModel extends ViewModel implements OnBiopluxDataAvailable {
    private final String TAG = this.getClass().getSimpleName();

    // used in Biosignalsplux communication
    private BiopluxCommunication biopluxCommunication;
    public final static String FRAME = "info.plux.pluxandroid.DeviceActivity.Frame";

    private final LineGraphSeries<DataPoint> series1 = new LineGraphSeries<DataPoint>();
    private final LineGraphSeries<DataPoint> series2 = new LineGraphSeries<DataPoint>();
    private LineGraphSeries<DataPoint>[] seriesArr = new LineGraphSeries[]{series1,series2};
    private double timeBefore = 0.; // for controlling
    private double time = 0.; // for recording the time when measurement was received
    private int previousOrientation = Configuration.ORIENTATION_UNDEFINED;
    protected static boolean inTime = false; // not used
    private String state;
    private boolean initialized = false;
    private String currentTime, previousTime;
    private boolean running = false;
    private boolean resuming = false;

    /** When the observer is registered to LiveData, LiveData becomes initialized and hence triggers the observer once.
     * This starts the heart beating undesired. For this reason we use a boolean flag to prevent this side effect.
     * @see info.plux.api.SpO2Monitoring.ui.main.ColorFragment#onCreateView(LayoutInflater, ViewGroup, Bundle) inside_onCreateView
     */
    private boolean ignore = true;

    private HandlerThread writeToDBHandlerThread;
    private WriteToDBHandler writeToDBHandler;


    //**********************************************************************************************
    // Class
    //**********************************************************************************************

    /**
     * WriteHandler prepares and processes the raw data which are received from messages by Biosignalsplux API
     */
    protected class WriteToDBHandler extends Handler {

        /* Hint:
        1000 Measurements per second are necessary to get a proper ECG signal.
        But the number of values is too much for the plotting tool. Hence you need not to increase the measuring rate
        but to measure only when it's necessary. This is the case when the second derivative (acceleration)
        surpasses a certain threshold. Acceleration is high from maximum to minimum (positive and negative peaks) of the original
        signal (ECG). This points have to be measured to get an accurate picture of the curve.
        SOLUTION: We use numerical differentiation to calculate the second derivative and
        compare the result to a certain threshold! The sweet spot was discovered by experimenting!
        */


        private MeasureDB mDB;
        private DataRow dataRow;
        private int[] analogData;

        private final int CAPTURE_FREQUENCY = 100; // Hz
        private final int CAPTURE = ColorFragment.FREQUENCY / CAPTURE_FREQUENCY;
        private final double THRESHOLD_R_WAVE = 0.002; // sweet spot!
        private ArrayList<Double> stencil = new ArrayList<>();
        private final int POS = 2;
        private int indexCap;
        private double sig1, sig2;
        private double eda, ecg;
        private double filteredEcg;
        private double heartRate;
        private double deriv2;

        private Butterworth butterworth;
        private double rWaveNow;
        private double rWavePrev;
        private double rrDistance;
        private boolean increasing;
        // Time value when recording is interrupted.
        // Used for correct calculation of ECG.
        private double timeWhenPausing;

        // PERIOD = 1/FREQUENCY
        // => PERIOD is the time interval in seconds between two measurements
        private final double PERIOD = 1. / (double) ColorFragment.FREQUENCY;

        private SimpleMovingAverage sig1SMA, sig2SMA;
        private SimpleMovingAverage  edaSMA, ecgSMA, filteredEcgSMA, deriv2SMA;
        protected SimpleMovingAverage heartRateSMA;


        //------------------------------------------------------------------------------------------
        // Inner Class
        //------------------------------------------------------------------------------------------

        public class SimpleMovingAverage{

            private ArrayList<Double> arr = new ArrayList<>();
            private int index;
            private int period;

            //--------------------------------------------------------------------------------------
            // Constructor
            //--------------------------------------------------------------------------------------

            SimpleMovingAverage(int period){
                index = 0;
                this.period = period;
            }

            //--------------------------------------------------------------------------------------
            // Methods
            //--------------------------------------------------------------------------------------

            /**
             * This method adds numbers to its limited storage and returns their average value.
             * If the limited storage is surpassed, the oldest value is removed.
             *
             * @param elem new added value
             * @return average value
             */
            public double add(double elem){

                arr.add(elem);

                if (index < period) {
                    index++;
                } else{
                    arr.remove(0);
                }

                return average(arr);
            }

            //--------------------------------------------------------------------------------------

            /**
             * Calculates the mean value of all values in the list.
             * If the list is empty, it returns 0.
             *
             * @param arr list of values
             * @return mean value
             */
            private double average(ArrayList<Double> arr) {


                double sum = 0;


                if(!arr.isEmpty()) {
                    for (int i = 0; i < arr.size(); i++) {
                        sum = sum + arr.get(i);
                    }

                    sum = sum / arr.size();
                }


                return sum;
            }

            //--------------------------------------------------------------------------------------

            /**
             * All previously stored values are deleted.
             * In case of an interrupt of recording the current time is saved to timeWhenPausing.
             * In case of database clearing timeWhenPausing is set back to 0.
             * @param clearingDatabase Set true when using reset due to database clearing
             */
            protected void reset(boolean clearingDatabase){
                arr.clear();
                index = 0;
                if(clearingDatabase) {
                    timeWhenPausing = 0;
                } else{
                    timeWhenPausing = time;
                }
            }


        }

        //------------------------------------------------------------------------------------------
        // Constructor
        //------------------------------------------------------------------------------------------


        public WriteToDBHandler(Looper looper, Context context) {
            super(looper);
            mDB = MeasureDB.getInstance(context);
            dataRow = new DataRow();

            indexCap = 0;

            butterworth = new Butterworth();
            // Used in calculation of RR distance
            // butterworth.highPass(2, ColorFragment.FREQUENCY, 10); // sweet spot
            rWavePrev = 0;
            increasing = false;
            heartRate = -99; // Signals no valid value is available
            // See 2. observer in onViewCreated of ColorFragment
            timeWhenPausing = 0;


            // These periods are the sweet spot with FREQUENCY = 1000 Hz!
            sig1SMA = new SimpleMovingAverage(20);
            sig2SMA = new SimpleMovingAverage(20);

            ecgSMA = new SimpleMovingAverage(20);
            edaSMA = new SimpleMovingAverage(20);
            filteredEcgSMA = new SimpleMovingAverage(300);
            deriv2SMA = new SimpleMovingAverage(10);
            heartRateSMA = new SimpleMovingAverage(10);

        }

        //------------------------------------------------------------------------------------------
        // Methods
        //------------------------------------------------------------------------------------------


        @Override
        public void handleMessage(Message msg) {

            //--------------------------------------------------------------------------------------
            //  Preparation of Raw Data
            //--------------------------------------------------------------------------------------

            // The data from Bioplux is stored in frame
            Bundle bundle = msg.getData();
            BiopluxFrame frame = bundle.getParcelable(FRAME);
            // When adapting to other sensors, change Source[] of ColorFragment.
            // Order of signals in analogData[] is equal to order of Sources.
            analogData = frame.getAnalogData();

//            // Debugging
//            // Shows all received data in Logcat
//            for(int i = 0; i < analogData.length; i++) {
//
//                Log.d(TAG,"analogData "+i+" : "+analogData[i]+"\n");
//
//            }



//            // For EDA & ECG
//
//            eda = analogData[0];
//            ecg = analogData[1];
//
//            // Converts raw data according to manuals of the used sensors.
//            // We use ECG and EDA sensors (Biosignalsplux).
//            eda = eda / Math.pow(2, 16) * 3 / 0.12; // Converts raw signal to µS
//            ecg = (ecg / Math.pow(2, 16) - 0.5) * 3 / 1019; // Converts to V
//            ecg = 1000 * ecg; // to mV
//
//            // Simple Moving Average
//            ecg = ecgSMA.add(ecg); // Smooths out the graph
//            eda = edaSMA.add(eda);
//
//            // Determines time of record
//            time = time + PERIOD;
//
//            //--------------------------------------------------------------------------------------
//            // Calculation of Heart Rate From ECG
//            //--------------------------------------------------------------------------------------
//
//            // Filters away all low frequencies.
//            // We just interested in the peaks of the signal
//            filteredEcg = butterworth.filter(ecg); // High Pass filter
//            filteredEcg = Math.pow(filteredEcg, 2);
//            filteredEcg = filteredEcgSMA.add(filteredEcg);
//
//            if (time > 1 + timeWhenPausing) { // Signal at start or continuation of recording not reliable
//                if (filteredEcg > THRESHOLD_R_WAVE && increasing) {
//                    if (rWavePrev == 0) {
//                        rWavePrev = dataRow.time;
//                    } else {
//                        rWaveNow = dataRow.time;
//                        rrDistance = rWaveNow - rWavePrev;
//                        heartRate = 60. / rrDistance; // result in bpm
//                        heartRate = heartRateSMA.add(heartRate); // Filters noise
//                        rWavePrev = rWaveNow;
//                    }
//                    increasing = false;
//
//                } else if (filteredEcg <= THRESHOLD_R_WAVE) {
//                    increasing = true;
//                }
//            }
//
//            //--------------------------------------------------------------------------------------
//            // 2. Derivative of ECG
//            //--------------------------------------------------------------------------------------
//
//            stencil.add(ecg);
//
//            if (stencil.size() > 5) {
//
//                stencil.remove(0);
//
//                deriv2 = get2Derivative(stencil, PERIOD);
//                deriv2 = deriv2SMA.add(deriv2); // This way extrema are recorded reliably
//
//                // If the absolute value of 2. derivative of ECG surpasses a certain limit,
//                // maxima or minima (peaks) are expected to come.
//                if (Math.abs(deriv2) > 3000) { // sweet spot
//
//                    indexCap = CAPTURE;
//
//                }
//
//            }
//
//            //--------------------------------------------------------------------------------------
//            // Frequency Reduction
//            //--------------------------------------------------------------------------------------
//
//            // Reduces frequency minimally to CAPTURE_FREQUENCY
//            // This prevents intolerable delay between measuring and plotting the signal
//            if (indexCap < CAPTURE) { // Saves values to database only when threshold is reached
//                indexCap++;
//            } else {
//
//                indexCap = 0;
//
//                dataRow.time = time;
//                dataRow.val_1 = eda;
//                dataRow.val_2 = ecg;
//                dataRow.val_3 = (int) heartRate;
//
//                addNewRecordToDB(dataRow);
//
//                System.out.println("Time: " + dataRow.time + " s");
//            }



            // Converts raw data according to manuals of the used sensors.
            // We use 1 and 2 channel of SpO2 sensor (Biosignalsplux).
            sig1 = analogData[0]; // red
            sig2 = analogData[1]; // infrared
            sig1 = 1.2 * sig1 / (Math.pow(2,16) * 1 /*MOhm*/ ); // Converts to µA
            sig2 = 1.2 * sig2 / (Math.pow(2,16) * 1 /*MOhm*/ ); // Converts to µA

            // Simple moving average
            sig1 = sig1SMA.add(sig1);
            sig2 = sig2SMA.add(sig2);

            // Fills dataRow
            dataRow.time = time;
            dataRow.val_1 = sig1;
            dataRow.val_2 = sig2;
            //--------------------------------------------------------------------------------------
            dataRow.val_3 = 60; // dummy value
            //--------------------------------------------------------------------------------------

            /*%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

            TODO data processing to obtain SpO2 level in % from red and infrared signals

            R = modulation ratio
            AC_r = Alternating current in red spectrum
            DC_r = Direct current in red spectrum
            AC_ir = Alternating current in infrared spectrum
            DC_ir = Direct current in infrared spectrum

            R = (AC_r / DC_r) / (AC_ir / DC_ir)

            R ~ Sp02 in %

            AC = p-p amplitude of periodic component
            DC = amplitude of offset component

            AC and DC component could be obtained by high or low pass filter
            Explained: The offset component is represented by low frequencies close to 0 in FFT.
            The periodic component is represented by peaks at higher frequencies.
            When amplitude of periodic component is not too small compared to the amplitude
            of the offset component, peaks at higher frequencies are clearly recognizable in FFT.

            To determine AC, find maximum and the following minimum of AC component by means of 1. and 2 derivative.
            When 1. derivative is almost 0, the extremum is roughly reached. There may be less error prone
            and preciser methods to determine the p-p amplitude.

            That said, we could calculate R from our results so far. The functional relation
            between R and SpO2 must be derived from any source.

            >>> PROBLEM: The Sensor does not deliver signals with distinctive AC components!

            %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%*/


            // Determines time of record
            time = time + PERIOD;

            addNewRecordToDB(dataRow);

        }

        //------------------------------------------------------------------------------------------

        /**
         * 5-point stencil method to determine second derivative
         *
         * @param f 5-point stencil
         * @param h distance between points
         * @return 2. derivative
         */
        private double get2Derivative(ArrayList<Double> f, double h) {

            return (-1 * f.get(POS - 2) + 16 * f.get(POS - 1) - 30 * f.get(POS + 0) + 16 * f.get(POS + 1) - 1 * f.get(POS + 2)) / (12 * 1.0 * Math.pow(h, 2));

        }

        //------------------------------------------------------------------------------------------

//        // first derivative
//        private double get1Derivative(ArrayList<Double> f, double h) {
//
//            return ( 1 * f.get(POS-2) - 8 * f.get(POS-1) + 0 * f.get(POS+0) + 8 * f.get(POS+1) - 1 * f.get(POS+2) ) / ( 12 * 1.0 * Math.pow(h,1) );
//
//        }

        /**
         * Saves the new dataRow to the database
         *
         * @param dataRow
         */
        private void addNewRecordToDB(DataRow dataRow) {

            //--------------------------------------------------------------------------------------
            // Checking
            //--------------------------------------------------------------------------------------

            if (seriesArr[0].isEmpty()) {
                Log.d(TAG,"Series empty");
            }


            System.out.println("time: " + dataRow.time + " sec\n"
                    +"val_1: "+ dataRow.val_1 + "\n"
                    +"val_2: "+ dataRow.val_2 + "\n"
                    +"val_3: "+ dataRow.val_3 + "\n" );


            // Debugging
            // System.out.println(seriesArr[0].getLowestValueX());
            // System.out.println(seriesArr[0].getHighestValueX());

            // Controls to eliminate the possibility of IllegalArgumentException.
            // Explanation: appendData is called in observer that observes changes to database.
            // appendData throws error when x values (time) are not in strictly ascending order.
            if (timeBefore < dataRow.time) {

                //----------------------------------------------------------------------------------
                // Update
                //----------------------------------------------------------------------------------

                // The data is stored persistently to load it later again after activity lifecycle has ended.
                // At the same time Observer in ColorFragment registers change to database and updates UI.
                // Remember: Do not try to to touch View of UI thread from another thread!
                // Never use appendData here => ConcurrentModificationException.
                mDB.dataRowDAO().insertAll(dataRow); // Triggers UI update
                timeBefore = dataRow.time;
            } else{
                Log.w(TAG,"Time value LOWER OR EQUAL than before: " + dataRow.time);
            }

            if (seriesArr[0].isEmpty()) {
                Log.d(TAG,"Series still empty");
            }

        }

    }

    //**********************************************************************************************
    // Methods & Further Attributes
    //**********************************************************************************************

    //----------------------------------------------------------------------------------------------
    // Enable/Disable Buttons
    //----------------------------------------------------------------------------------------------

    // Conditionals: loading finished, connected, no cool down, no clearing
    private Boolean[] clickableConditionals = new Boolean[]{false, false, true, true};
    private MutableLiveData<Boolean[]> clickable;

    public MutableLiveData<Boolean[]> getClickable() {
        if (clickable == null) {
            clickable = new MutableLiveData<Boolean[]>(clickableConditionals);
        }
        return clickable;
    }

    public void changeClickable(int condition, boolean satisfied) {
        boolean previousSatisfied;


        Boolean[] conditionals = clickable.getValue();
        if (condition < 0 || condition >= conditionals.length) {
            Log.w(TAG, "Clickable could not be changed! Length: " + conditionals.length + "\n Condition: " + condition + " out of range.");
        } else {
            previousSatisfied = conditionals[condition];
            conditionals[condition] = Boolean.valueOf(satisfied);
            clickable.setValue(conditionals);
            Log.d(TAG, "Clickable condition " + condition + " : " + previousSatisfied + " --> " + satisfied);
        }

    }

    //----------------------------------------------------------------------------------------------

    /**
     *  When the user starts the app, this method does the necessary preparations before user interaction.
     *  Data from database is loaded, the handler writing to database is created
     *  and the communication with the Bioplux hub is established.
     *
     * @param cf
     */
    protected void initialize(ColorFragment cf) {

        prepareWriterToDBHandler(cf);

        loadPreviousDataFromDB(cf);

        setUpBiopluxCommunication(cf);

    }

    private void prepareWriterToDBHandler(ColorFragment cf){

        Context context = cf.getContext();

        writeToDBHandlerThread = new HandlerThread("WriteToDB");
        writeToDBHandlerThread.start();

        WriteToDBHandler writeHandler = new WriteToDBHandler(writeToDBHandlerThread.getLooper(), context);

        this.writeToDBHandler = writeHandler;
    }

    private void loadPreviousDataFromDB(ColorFragment cf){
        LoadingTask loadingTask = new LoadingTask(cf);
        loadingTask.execute();
    }

    /**
     *  Sets up communication with Bioplux hub
     *  Used extra in case of comeback
     */
    private void setUpBiopluxCommunication(ColorFragment cf){

        Context context = cf.getContext();

        // Gets bluetooth device from MainActivity
        BluetoothDevice bluetoothDevice = ( (MainActivity) cf.getActivity() ).getBluetoothDevice();

        Communication communication = Communication.getById(bluetoothDevice.getType());
        Log.d(TAG, "Communication: " + communication.name());
        if (communication.equals(Communication.DUAL)) {
            communication = Communication.BTH;
        }

        Log.d(TAG, "communication: " + communication.name());

        biopluxCommunication = new BiopluxCommunicationFactory().getCommunication(communication, context, this);

        try {
            biopluxCommunication.connect(bluetoothDevice.getAddress());
        } catch (BiopluxException e) {
            e.printStackTrace();
        }

    }

    //----------------------------------------------------------------------------------------------

    /**
     * The DataPoints of LineGraphSeries are replaced by an empty Array.
     * The view point on the graph is shifted back to the initial state.
     */
    protected void clearSeriesArr(){
        // To keep the same LineGraphSeries objects as in declaration is crucial.
        // Overriding like below makes app unstable and easily crash when database gets cleared.
        //edaSeries = new LineGraphSeries<DataPoint>();
        //hrSeries = new LineGraphSeries<DataPoint>();
        seriesArr[0].resetData(new DataPoint[]{});
        seriesArr[1].resetData(new DataPoint[]{});

        // Little trick to reset focus as same as when app is started
        seriesArr[0].appendData(new DataPoint(PlotFragment.MAX_X,0),true,1);
        seriesArr[1].appendData(new DataPoint(PlotFragment.MAX_X,0),true,1);

        seriesArr[0].resetData(new DataPoint[]{});
        seriesArr[1].resetData(new DataPoint[]{});

        Log.v("clearSeriesArr","Data array cleared");
    }

    //----------------------------------------------------------------------------------------------
    // OnBiopluxDataAvailable Interface
    //----------------------------------------------------------------------------------------------

    /**
     * Every message contains one measurement.
     *
     * @param biopluxFrame holds the data of measurement.
     */
    @Override
    public void onBiopluxDataAvailable(BiopluxFrame biopluxFrame) {
        Message message = writeToDBHandler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putParcelable(FRAME, biopluxFrame);
        message.setData(bundle);
        writeToDBHandler.sendMessage(message);
    }

    //----------------------------------------------------------------------------------------------

    @Override
    public void onBiopluxDataAvailable(String identifier, int[] biopluxFrame) {
        Log.d(TAG, identifier + ": " + Arrays.toString(biopluxFrame));
    }

    //----------------------------------------------------------------------------------------------
    // Getter and Setter
    //----------------------------------------------------------------------------------------------

    public boolean getRunning(){
        return running;
    }

    public boolean getResuming(){ return resuming; }

    public boolean getIgnore(){ return ignore; }

    public boolean getInitialized(){ return initialized; }

    public String getState(){ return state; }

    public int getPreviousOrientation(){ return previousOrientation; }

    public String getCurrentTime(){ return currentTime; }

    public String getPreviousTime(){ return previousTime; }

    public WriteToDBHandler getWriteToDBHandler(){ return writeToDBHandler; }

    public LineGraphSeries<DataPoint>[] getSeriesArr(){ return seriesArr; }

    public LineGraphSeries<DataPoint> getSeries(int pos){ return seriesArr[pos]; }

    public double getTime(){ return time; }

    public double getTimeBefore(){ return timeBefore; }

    public BiopluxCommunication getBiopluxCommunication(){ return biopluxCommunication; }


    public void setRunning(boolean running){ this.running = running; }

    public void setResuming(boolean resuming){ this.resuming = resuming; }

    public void setIgnore(boolean ignore) { this.ignore = ignore; }

    public void setInitialized(boolean initialized){ this.initialized = initialized; }

    public void setState(String state){ this.state = state; }

    public void setPreviousOrientation(int previousOrientation){ this.previousOrientation = previousOrientation; }

    public void setSeries(int pos, LineGraphSeries<DataPoint> series){ seriesArr[pos] = series; }

    public void setCurrentTime(String currentTime){ this.currentTime = currentTime; }

    public void setPreviousTime(String previousTime){ this.previousTime = previousTime; }

    public void setTime(double time){ this.time = time; }

    public void setTimeBefore(double timeBefore){ this.timeBefore = timeBefore; }

    // This method shouldn't be integrated in method ClearDatabase.
    // Incoming messages not yet deleted mess up the times at this point.
    protected void setbackTime(){
        timeBefore = 0.;
        time = 0.;
    }
}
