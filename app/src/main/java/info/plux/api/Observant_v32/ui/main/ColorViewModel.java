package info.plux.api.Observant_v32.ui.main;

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

import androidx.lifecycle.ViewModel;

import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import uk.me.berndporr.iirj.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

import info.plux.api.Observant_v32.activities.MainActivity;
import info.plux.api.Observant_v32.database.DataRow;
import info.plux.api.Observant_v32.database.MeasureDB;
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
    public static BiopluxCommunication bioplux;
    public final static String FRAME = "info.plux.pluxandroid.DeviceActivity.Frame";

    private static final LineGraphSeries<DataPoint> edaSeries = new LineGraphSeries<DataPoint>();
    private static final LineGraphSeries<DataPoint> hrSeries = new LineGraphSeries<DataPoint>();
    protected static LineGraphSeries<DataPoint>[] seriesArr = new LineGraphSeries[]{edaSeries,hrSeries};
    private static double timeBefore = 0.; // for controlling
    private static double time = 0.; // for recording the time when measurement was received
    protected static int previousOrientation = Configuration.ORIENTATION_UNDEFINED;
    protected static boolean rotating = false;
    protected static boolean inTime = false;
    protected static String state;
    public static boolean comeback = false;
    protected static String currentTime, previousTime;

    /** When database reaches a certain size and hence the loading process takes some time,
     * the observer is likely to register a change to database due to select queries in the loading process!
     * This starts the heart beating undesired. Hence we use a boolean flag to prevent this side effect.
     * @see info.plux.api.Observant_v32.ui.main.ColorFragment#onCreateView(LayoutInflater, ViewGroup, Bundle) inside_onCreateView
     */
    protected static boolean ignore = true;
    protected static HandlerThread handlerThread;
    protected static Handler loadAndInitHandler;

    protected static WriteHandler writeHandler;


    // *********************************************************************************************
    // Classes
    // *********************************************************************************************


    /**
     * WriteHandler prepares and processes the raw data which are received from messages by Biosignalsplux API
     */
    protected class WriteHandler extends Handler {

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
        private double eda;
        private double ecg;
        private double filteredEcg;
        private double heartRate;
        private double deriv2;

        private Butterworth butterworth;
        private double rWaveNow;
        private double rWavePrev;
        private double rrDistance;
        private boolean increasing;
        private double pauseTime; // Time value when recording is interrupted

        // PERIOD = 1/FREQUENCY
        // => PERIOD is the time interval in seconds between two measurements
        private final double PERIOD = 1. / (double) ColorFragment.FREQUENCY;

        private SimpleMovingAverage ecgSMA, edaSMA, filteredEcgSMA, deriv2SMA;
        protected SimpleMovingAverage heartRateSMA;


        // -----------------------------------------------------------------------------------------
        // Inner Class
        // -----------------------------------------------------------------------------------------

        public class SimpleMovingAverage{

            private ArrayList<Double> arr = new ArrayList<>();
            private int index;
            private int period;

            SimpleMovingAverage(int period){
                index = 0;
                this.period = period;
            }


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


            /**
             * All previous stored values are deleted. The current time value is saved.
             * This is useful in case of an interruption of recording.
             */
            protected void interrupt(){
                arr.clear();
                index = 0;
                pauseTime = time;
            }

            /**
             * All parameters are set to initial values.
             * This should be used when the database is cleared.
             */
            protected void reset(){
                arr.clear();
                index = 0;
                pauseTime = 0;
            }


        }

        // -----------------------------------------------------------------------------------------
        // Constructor
        // -----------------------------------------------------------------------------------------


        public WriteHandler(Looper looper, Context context) {
            super(looper);
            mDB = MeasureDB.getInstance(context);
            dataRow = new DataRow();

            indexCap = 0;

            butterworth = new Butterworth();
            butterworth.highPass(2, ColorFragment.FREQUENCY, 10); // sweet spot
            rWavePrev = 0;
            increasing = false;
            heartRate = -99; // Signals no valid value is available
            // See 2. observer in onViewCreated of ColorFragment
            pauseTime = 0;


            // These periods are the sweet spot with FREQUENCY = 1000 Hz!
            ecgSMA = new SimpleMovingAverage(20);
            edaSMA = new SimpleMovingAverage(20);
            filteredEcgSMA = new SimpleMovingAverage(300);
            deriv2SMA = new SimpleMovingAverage(10);
            heartRateSMA = new SimpleMovingAverage(10);

        }

        // -----------------------------------------------------------------------------------------
        // Methods
        // -----------------------------------------------------------------------------------------


        @Override
        public void handleMessage(Message msg) {

            // -------------------------------------------------------------------------------------
            // Preparation of Raw Data
            // -------------------------------------------------------------------------------------

            // The data from Bioplux is stored in frame
            Bundle bundle = msg.getData();
            BiopluxFrame frame = bundle.getParcelable(FRAME);
            analogData = frame.getAnalogData();
            eda = analogData[0];
            ecg = analogData[1];

            // Converts raw data according to manuals of the used sensors.
            // We use ECG and EDA sensors by Biosignalsplux.
            eda = eda / Math.pow(2, 16) * 3 / 0.12; // Converts raw signal to µS
            ecg = (ecg / Math.pow(2, 16) - 0.5) * 3 / 1019; // Converts to V
            ecg = 1000 * ecg; // to mV

            // Determines recording time
            time = time + PERIOD;
            // Applies SMA
            ecg = ecgSMA.add(ecg); // Smooths out the graph
            eda = edaSMA.add(eda);

            // -------------------------------------------------------------------------------------
            // Calculation of Heart Rate From ECG
            // -------------------------------------------------------------------------------------

            // Filters away all low frequencies.
            // We just interested in the peaks of the signal
            filteredEcg = butterworth.filter(ecg); // Low Pass filter
            filteredEcg = Math.pow(filteredEcg, 2);
            filteredEcg = filteredEcgSMA.add(filteredEcg);

            if (time > 1 + pauseTime) { // Signal at start or continuation of recording not reliable
                if (filteredEcg > THRESHOLD_R_WAVE && increasing) {
                    if (rWavePrev == 0) {
                        rWavePrev = dataRow.time;
                    } else {
                        rWaveNow = dataRow.time;
                        rrDistance = rWaveNow - rWavePrev;
                        heartRate = 60. / rrDistance; // result in bpm
                        heartRate = heartRateSMA.add(heartRate); // Filters noise
                        rWavePrev = rWaveNow;
                    }
                    increasing = false;

                } else if (filteredEcg <= THRESHOLD_R_WAVE) {
                    increasing = true;
                }
            }

            // -------------------------------------------------------------------------------------
            // 2. Derivative of ECG
            // -------------------------------------------------------------------------------------

            stencil.add(ecg);

            if (stencil.size() > 5) {

                stencil.remove(0);

                deriv2 = get2Derivative(stencil, PERIOD);
                deriv2 = deriv2SMA.add(deriv2); // This way extrema are recorded reliably

                // If the absolute value of 2. derivative of ECG surpasses a certain limit,
                // maxima or minima (peaks) are expected to come.
                if (Math.abs(deriv2) > 3000) { // sweet spot

                    indexCap = CAPTURE;

                }

            }

            // -------------------------------------------------------------------------------------
            // Frequency Reduction
            // -------------------------------------------------------------------------------------

            // Reduces frequency minimally to CAPTURE_FREQUENCY
            // This prevents intolerable delay between measuring and plotting the signal
            if (indexCap < CAPTURE) { // Saves values to database only when threshold is reached
                indexCap++;
            } else {

                indexCap = 0;


                dataRow.time = time;
                dataRow.electrodermalActivity = eda;
                dataRow.electroCardiogram = ecg; // ECG;
                dataRow.heartRate = (int) heartRate;


                // Store as well as display data simultaneously
                updateUI(dataRow);

                System.out.println("Input: " + dataRow.time);
            }
        }


        /**
         * 5-point stencil method to determine second derivative
         *
         * @param f 5-point stencil
         * @param h distance between points
         * @return 2. derivative
         */
        private double get2Derivative(ArrayList<Double> f, double h) {

            return (-1 * f.get(POS - 2) + 16 * f.get(POS - 1) - 30 * f.get(POS + 0) + 16 * f.get(POS + 1) - 1 * f.get(POS + 2)) / (12 * 1.0 * Math.pow(h, 2));

            // first derivative
            // return ( 1 * f.get(POS-2) - 8 * f.get(POS-1) + 0 * f.get(POS+0) + 8 * f.get(POS+1) - 1 * f.get(POS+2) ) / ( 12 * 1.0 * Math.pow(h,1) );

        }


        /**
         * Saves the new dataRow to the database
         *
         * @param dataRow
         */
        private void updateUI(DataRow dataRow) {

            // -------------------------------------------------------------------------------------
            // Monitoring and Controlling
            // -------------------------------------------------------------------------------------

            if (seriesArr[0].isEmpty()) {
                Log.d(TAG,"Series empty");
            }
            Log.v(TAG,"Time = " + dataRow.time);
            Log.v(TAG,"EDA = "+dataRow.electrodermalActivity);
            Log.v(TAG,"EKG = "+dataRow.electroCardiogram);

            // Kept for debugging
            //System.out.println(seriesArr[0].getLowestValueX());
            //System.out.println(seriesArr[0].getHighestValueX());

            // Controls to eliminate the possibility of IllegalArgumentException.
            // Explanation: appendData is called in observer that observes changes to database.
            // appendData throws error when x values (time) are not in strictly ascending order.
            if (timeBefore < dataRow.time) {

                // ---------------------------------------------------------------------------------
                // Actual Work
                // ---------------------------------------------------------------------------------

                // The data is stored persistently to load it later again after activity lifecycle has ended.
                // At the same time Observer in ColorFragment registers change to database and updates UI.
                // Remember: Do not try to to touch View of UI thread from another thread!
                // Never use appendData here => ConcurrentModificationException.
                mDB.dataRowDAO().insertAll(dataRow);
                timeBefore = dataRow.time;
            } else{
                Log.w(TAG,"Time value LOWER OR EQUAL than before: " + dataRow.time);
            }

            if (seriesArr[0].isEmpty()) {
                Log.d(TAG,"Series still empty");
            }

        }

    }


    /**
     *  Used for loading data from database when app is started
     */
    protected class LoadAndInitHandler extends Handler {
        private MeasureDB mDB;
        private List<DataRow> dataRows;
        private DataRow row;

        public LoadAndInitHandler(Looper looper, MeasureDB mDB) {
            super(looper);
            this.mDB = mDB;
        }

        // Warning: Never update ui elements in no other thread than main thread. Otherwise this will throw an error!
        @Override
        public void handleMessage(Message msg) {

            if(msg.what==0) {
                Log.i(TAG,"+++++++++++++++++++++++++++++++++++++" + " Loading started " + "+++++++++++++++++++++++++++++++++++++");

                dataRows = mDB.dataRowDAO().getAllRows();

                if (!dataRows.isEmpty()) {
                    // If order of x values (time) is NOT ascending than clear corrupt database
                    // Probably due to disconnect during usage
                    try {
                        // Sets time to last values.
                        time = mDB.dataRowDAO().getLastRow().time;
                        timeBefore = time;
                        Log.i(TAG,"This is the last time in database: " + time + " +++++++++++++++++++++++++++++++++++++");
                        // Fills array with LineGraphSeries.
                        seriesArr[0] = convertDataRowsToEdaSeries(dataRows);
                        seriesArr[1] = convertDataRowsToHrSeries(dataRows);
                    } catch (IllegalArgumentException ex) {
                        Log.w(TAG," Illegal Argument Exception: false order of x values. Database has been cleared.");
                        ColorFragment.clearDatabase();
                    } catch (NullPointerException ex) {
                        Log.w(TAG,"Missing values in data row. Database has been cleared.");
                        ColorFragment.clearDatabase();
                    }
                } else {
                    Log.i(TAG,"Nothing to load. Database empty.");
                }

                // Warning: Do not try to to touch View of UI thread from another thread!
                // Remember: In another thread than main thread use postValue instead of setValue.
                // The series are added to the graph when isLoaded is observed.

                previousTime = ColorFragment.FancyStringConverter.convert(time);
                ColorFragment.isLoaded.postValue(mDB.dataRowDAO().getLastRow());
                Log.i(TAG,"+++++++++++++++++++++++++++++++++++++" + " Loading finished " + "+++++++++++++++++++++++++++++++++++++");
            }
            else{
                ColorFragment.isLoaded.postValue(mDB.dataRowDAO().getLastRow());
            }
            ignore = false;
            Log.d(TAG,"+++++++++++++++++++++++++++++++++++++" + " isLoaded has been changed! " + "+++++++++++++++++++++++++++++++++++++");
        }


        /**
         * Converts a List of DataRows from the database to a LineGraphSeries of DataPoints.
         * Time and EDA are extracted from the DataRow and converted to a DataPoint.
         *
         * @param dataRows List of DataRows
         * @return The result is added to the graph.
         */
        // Converts data from database to format usable for graph.
        private LineGraphSeries<DataPoint> convertDataRowsToEdaSeries(List<DataRow> dataRows) {
            int length = dataRows.size();
            DataPoint[] dataPoints = new DataPoint[length];


            ListIterator<DataRow> dataRowIterator = dataRows.listIterator();
            while (dataRowIterator.hasNext()) {
                row = dataRowIterator.next();
                System.out.println("Time from DB: " + row.time);
                dataPoints[dataRowIterator.previousIndex()] = new DataPoint( row.time , row.electrodermalActivity );
            }

            return new LineGraphSeries<DataPoint>(dataPoints);
        }

        /**
         * Converts a List of DataRows from the database to a LineGraphSeries of DataPoints.
         * Time and ECG are extracted from the DataRow and converted to a DataPoint.
         *
         * @param dataRows List of DataRows
         * @return The result is added to the graph.
         */
        private LineGraphSeries<DataPoint> convertDataRowsToHrSeries(List<DataRow> dataRows) {
            int length = dataRows.size();
            DataPoint[] dataPoints = new DataPoint[length];


            ListIterator<DataRow> dataRowIterator = dataRows.listIterator();
            while (dataRowIterator.hasNext()) {
                row = dataRowIterator.next();
                dataPoints[dataRowIterator.previousIndex()] = new DataPoint( row.time, row.electroCardiogram );
            }

            return new LineGraphSeries<DataPoint>(dataPoints);
        }
    }

    // *********************************************************************************************
    // Methods
    // *********************************************************************************************


    /**
     *  When the user starts the app, this method does the necessary preparations before user interaction.
     *  Data from database is loaded, the handler writing to database is created
     *  and the communication with the Bioplux hub is established.
     *
     * @param context of ColorFragment
     */
    protected void prepare(Context context) {

        // 1. Loads previous data from database with help of LoadAndInitHandler
        handlerThread.start(); // Make sure to start handlerThread before you pass it over to the Handler!
        loadAndInitHandler = new LoadAndInitHandler(handlerThread.getLooper(), MeasureDB.getInstance(context));
        loadAndInitHandler.sendEmptyMessage(0);

        // 2. Creates and starts WriteHandler to process new data
        HandlerThread handlerThread = new HandlerThread("WriteHandler");
        handlerThread.start();

        WriteHandler writeHandler = new WriteHandler(handlerThread.getLooper(), context);

        this.writeHandler = writeHandler;

        // 3. Creates BiopluxCommunication object for controlling data flow
        setUpBiopluxCommunication(context, MainActivity.getBluetoothDevice());

    }


    /**
     *  Sets up communication with Bioplux hub
     */
    protected void setUpBiopluxCommunication(Context context, BluetoothDevice bluetoothDevice){


        Communication communication = Communication.getById(bluetoothDevice.getType());
        Log.d(TAG, "Communication: " + communication.name());
        if (communication.equals(Communication.DUAL)) {
            communication = Communication.BTH;
        }

        Log.d(TAG, "communication: " + communication.name());

        bioplux = new BiopluxCommunicationFactory().getCommunication(communication, context, this);

        try {
            bioplux.connect(bluetoothDevice.getAddress());
        } catch (BiopluxException e) {
            e.printStackTrace();
        }

    }

    /**
     * The DataPoints of LineGraphSeries are replaced by an empty Array.
     * The view point on the graph is shifted back to the initial state.
     */
    protected static void clearSeriesArr(){
        // To keep the same LineGraphSeries objects as in declaration is crucial.
        // Overriding like below makes app unstable and easily crash when database gets cleared.
        //edaSeries = new LineGraphSeries<DataPoint>();
        //hrSeries = new LineGraphSeries<DataPoint>();
        seriesArr[0].resetData(new DataPoint[]{});
        seriesArr[1].resetData(new DataPoint[]{});

        // Little trick to reset focus like when app is started
        seriesArr[0].appendData(new DataPoint(PlotFragment.MAX_X,0),true,1);
        seriesArr[1].appendData(new DataPoint(PlotFragment.MAX_X,0),true,1);

        seriesArr[0].resetData(new DataPoint[]{});
        seriesArr[1].resetData(new DataPoint[]{});

        Log.v("clearSeriesArr","Data array cleared");
    }


    protected static void setTimes(){
        timeBefore = 0.;
        time = 0.;
    }

    // ---------------------------------------------------------------------------------------------
    // OnBiopluxDataAvailable Interface
    // ---------------------------------------------------------------------------------------------

    /**
     * Every message contains one measurement.
     *
     * @param biopluxFrame holds the data of measurement.
     */
    @Override
    public void onBiopluxDataAvailable(BiopluxFrame biopluxFrame) {
        Message message = writeHandler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putParcelable(FRAME, biopluxFrame);
        message.setData(bundle);
        writeHandler.sendMessage(message);
    }

    @Override
    public void onBiopluxDataAvailable(String identifier, int[] biopluxFrame) {
        Log.d(TAG, identifier + ": " + Arrays.toString(biopluxFrame));
    }

}