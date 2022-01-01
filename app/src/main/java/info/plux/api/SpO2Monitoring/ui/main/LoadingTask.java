package info.plux.api.SpO2Monitoring.ui.main;

import android.content.Context;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.List;
import java.util.ListIterator;

import info.plux.api.SpO2Monitoring.R;
import info.plux.api.SpO2Monitoring.database.DataRow;
import info.plux.api.SpO2Monitoring.database.MeasureDB;

public class LoadingTask extends AsyncTask<Void,Void,DataRow> {
    private final String TAG = this.getClass().getSimpleName();
    private MeasureDB mDB;
    private List<DataRow> dataRows;
    private DataRow row;
    private ColorFragment colorFragment;
    private Context context;


    public LoadingTask(ColorFragment cf) {
        context = cf.getContext();
        this.mDB = MeasureDB.getInstance(context);
        this.colorFragment = cf;
    }

    @Override
    protected DataRow doInBackground(Void... Voids) {

        Log.i(TAG, "+++++++++++++++++++++++++++++++++++++" + " Loading started " + "+++++++++++++++++++++++++++++++++++++");

        dataRows = mDB.dataRowDAO().getAllRows();

        if (!dataRows.isEmpty()) {
            // If order of x values (time) is NOT ascending than clear corrupt database
            // Probably due to disconnect during usage
            try {
                // Sets time to last values.
                ColorViewModel.time = mDB.dataRowDAO().getLastRow().time;
                ColorViewModel.timeBefore = ColorViewModel.time;

                // Fills array with LineGraphSeries.
                ColorViewModel.seriesArr[0] = convertValToSeries(dataRows, 1,true);
                ColorViewModel.seriesArr[1] = convertValToSeries(dataRows, 2,false);

                Log.i(TAG, "This is the last time in database: " + ColorViewModel.time + " +++++++++++++++++++++++++++++++++++++");

            } catch (IllegalArgumentException ex) {
                Log.w(TAG, " Illegal Argument Exception: false order of x values. Database has been cleared.");
                colorFragment.clearDatabase();
            } catch (NullPointerException ex) {
                Log.w(TAG, "Missing values in data row. Database has been cleared.");
                colorFragment.clearDatabase();
            }
        } else {
            Log.i(TAG, "Nothing to load. Database empty.");
        }

        // Warning: Do not try to to touch View of UI thread from another thread!
        // Remember: In another thread than main thread use postValue instead of setValue.
        // The series are added to the graph when isLoaded is observed.


        Log.i(TAG, "+++++++++++++++++++++++++++++++++++++" + " Loading finished " + "+++++++++++++++++++++++++++++++++++++");


        return mDB.dataRowDAO().getLastRow();
    }

    @Override
    protected void onPostExecute(DataRow lastRow){


        colorFragment.initializeUI(lastRow);

    }


    /**
     * Converts a List of DataRows from the database to a LineGraphSeries of DataPoints.
     * Time and EDA are extracted from the DataRow and converted to a DataPoint.
     *
     * @param dataRows List of DataRows
     * @param val_index Index of column with (indirectly) measured values
     * @return The result is added to the graph.
     */
    // Converts data from database to format usable for graph.
    private LineGraphSeries<DataPoint> convertValToSeries(List<DataRow> dataRows, int val_index, boolean printToConsole ) {

        int length = dataRows.size();
        DataPoint[] dataPoints = new DataPoint[length];

        ListIterator<DataRow> dataRowIterator = dataRows.listIterator();
        while (dataRowIterator.hasNext()) {
            row = dataRowIterator.next();

            //Shows time series in console
            if(printToConsole){
                System.out.println("Time from DB: " + row.time);
            }

            switch(val_index){
                case 1: dataPoints[dataRowIterator.previousIndex()] = new DataPoint( row.time , row.val_1 );
                    break;
                case 2: dataPoints[dataRowIterator.previousIndex()] = new DataPoint( row.time , row.val_2 );
                    break;
                case 3: dataPoints[dataRowIterator.previousIndex()] = new DataPoint( row.time , row.val_3 );
                    break;
                default:
                    Log.e(TAG, "Not allowed val_index!");
            }
        }

        return new LineGraphSeries<DataPoint>(dataPoints);
    }

}