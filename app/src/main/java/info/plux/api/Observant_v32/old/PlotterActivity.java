package info.plux.api.Observant_v32.old;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;

import androidx.appcompat.app.AppCompatActivity;

import info.plux.api.Observant_v32.Lists;
import info.plux.api.Observant_v32.R;
import info.plux.api.Observant_v32.database.DataRow;
import info.plux.api.Observant_v32.database.DataRowDAO;
import info.plux.api.Observant_v32.database.MeasureDB;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.List;
import java.util.ListIterator;

public class PlotterActivity extends AppCompatActivity {
    LineGraphSeries<DataPoint> series;
    LineGraphSeries<DataPoint> series2;
    DataRow row;

    private LineGraphSeries<DataPoint> getHrSeries(List<DataRow> dataRows){
        int length = dataRows.size();
        DataPoint[] dataPoints = new DataPoint[length];


        double time;
        double heartrate;

        ListIterator<DataRow> dataRowIterator = dataRows.listIterator();
        while(dataRowIterator.hasNext()){
            row = dataRowIterator.next();
            time = row.time;
            heartrate = row.heartRate;
            dataPoints[dataRowIterator.previousIndex()] = new DataPoint(time,heartrate);
        }

        return new LineGraphSeries<>(dataPoints);
    }


    private LineGraphSeries<DataPoint> getEdaSeries(List<DataRow> dataRows){
        int length = dataRows.size();
        DataPoint[] dataPoints = new DataPoint[length];


        double time;
        double electrodermalActivity;

        ListIterator<DataRow> dataRowIterator = dataRows.listIterator();
        while(dataRowIterator.hasNext()){
            row = dataRowIterator.next();
            time = row.time;
            electrodermalActivity = row.electrodermalActivity;
            dataPoints[dataRowIterator.previousIndex()] = new DataPoint(time,electrodermalActivity);
        }

        return new LineGraphSeries<>(dataPoints);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plotter);

        // we get GraphView instance
        final GraphView graph = (GraphView) findViewById(R.id.graph);

        // customize a little bit viewport
        final Viewport viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setXAxisBoundsManual(true);
        viewport.setMinY(0);
        viewport.setMaxY(10);
        viewport.setScrollable(true);


        // Look here for documentation of GraphView
        // https://github.com/jjoe64/GraphView/wiki/Documentation
        // GraphView supports real time depiction of data


        // Database operations must not be done on the main thread.
        // Hence we need an extra thread.
        final Context context = getApplicationContext();
        new Thread(new Runnable(){

            MeasureDB db = MeasureDB.getInstance(context);
            DataRowDAO dataRowDAO = db.dataRowDAO();

            List<DataRow> dataRows;

            DataRow lastElement;
            double time;

            @Override
            public void run() {
                //series = randomDataEDA.getRandomSeries(1000,10);
                //series2 = randomDataHR.getRandomSeries(1000,120);



                dataRows = dataRowDAO.getAllRows();


                series2 = getEdaSeries(dataRows);
                series = getHrSeries(dataRows);
                series.setColor(Color.RED);



                graph.addSeries(series);
                // set second scale
                graph.getSecondScale().addSeries(series2);

                lastElement = Lists.getLast(dataRows);
                time = lastElement.time;
                if(time> DeviceActivity.PLOTTER_MAX_X){
                    viewport.scrollToEnd();
                }



            }
        }).start();





        // data
        // series = new LineGraphSeries<DataPoint>();





        // the y bounds are always manual for second scale
        graph.getSecondScale().setMinY(0);
        graph.getSecondScale().setMaxY(100);

        graph.getGridLabelRenderer().setVerticalLabelsSecondScaleColor(Color.RED);


        // Change axis label
        graph.getGridLabelRenderer().setNumHorizontalLabels(4);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("time");


        // custom label formatter to show unit "µS"
        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    // show normal x values
                    return super.formatLabel(value, isValueX) + " s";
                } else {
                    // show currency for y values
                    return super.formatLabel(value, isValueX) + " µS";
                }
            }
        });


        // For second scale
        graph.getSecondScale().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    // show normal x values
                    return super.formatLabel(value, isValueX) + " s";
                } else {
                    // show currency for y values
                    return super.formatLabel(value, isValueX) + " "+ Html.fromHtml("m<sup>-1</sup>");
                }
            }
        });


        // activate horizontal zooming and scrolling
        graph.getViewport().setScalable(true);

        // activate horizontal scrolling
        graph.getViewport().setScrollable(true);

        // activate horizontal and vertical zooming and scrolling
        graph.getViewport().setScalableY(true);

        // activate vertical scrolling
        graph.getViewport().setScrollableY(true);


        // To set a fixed manual viewport use this:

        // set manual X bounds
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(DeviceActivity.PLOTTER_MAX_X);

        // set manual Y bounds
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(10);

    }
}