package info.plux.api.SpO2Monitoring.ui.main;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.LineGraphSeries;

import info.plux.api.SpO2Monitoring.R;

/**
 * A placeholder fragment containing GraphView
 */
public class PlotFragment extends Fragment {
    private final String TAG = this.getClass().getSimpleName();
    private static final String ARG_SECTION_NUMBER = "section_number";

    private static PlotFragment instance;

    // For plotting

    // Must be static. Gets manipulated by loading process outside of this fragment before an instance is created.
    // To guarantee independence of an instance attributes belong to class.
    private static GraphView graph;
    private static Viewport viewport;

    // time
    protected static final double MIN_X = 0;
    protected static final double MAX_X = 10;
    // SpO2 red, infrared
    protected static final double MIN_VAL1 = 0;
    protected static final double MAX_VAL1 = 1.25;


    // EDA
    // protected static final double MIN_VAL1 = 0;
    // protected static final double MAX_VAL1 = 8;

    // ECG
    // protected static final double MIN_VAL2 = -1;
    // protected static final double MAX_VAL2 = 1; // 75 for 1 deriv; // 8000 for 2 deriv


    //**********************************************************************************************
    // Methods
    //**********************************************************************************************

    /**
     *  The series are added to the graph. The graph holds the series.
     */
    public static void addSeriesToGraph(LineGraphSeries[] seriesArr){
        // Adds val_1 to scale
        graph.addSeries(seriesArr[0]);

        // Makes val_2 series red
        seriesArr[1].setColor(Color.RED);

        // Adds val_2 series to scale
        graph.addSeries(seriesArr[1]);

        // ALTERNATIVE: Adds val_2 series to second scale
        // PlotFragment.graph.getSecondScale().addSeries(ColorViewModel.seriesArr[1]);


        if(seriesArr[0].getHighestValueX()>PlotFragment.MAX_X){ // Do not scroll to end of series when whole series is still in visible area.
            viewport.scrollToEnd(); // scroll to last data point of series
        }
    }

    public static PlotFragment getInstance(){
        return instance;
    }


    public static PlotFragment newInstance(int index) {

        PlotFragment fragment = new PlotFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_SECTION_NUMBER, index);
        fragment.setArguments(bundle);
        return fragment;

    }

    //**********************************************************************************************
    // Lifecycle Callbacks
    //**********************************************************************************************

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"ON CREATE");

        instance = this; // not used

    }

    //----------------------------------------------------------------------------------------------

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_plot, container, false);

        return root;
    }

    //----------------------------------------------------------------------------------------------

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

        Log.d(TAG,"ON VIEW CREATED");
        View root = getView();

        instance = this;

        graph = root.findViewById(R.id.graph);
        viewport = graph.getViewport();



        //------------------------------------------------------------------------------------------
        // Graph Customization
        //------------------------------------------------------------------------------------------

        //==========================================================================================
        // Axes
        //==========================================================================================

        // Changes axis label
        graph.getGridLabelRenderer().setNumHorizontalLabels(4);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("time");


        // Custom label formatter to show the desired unit
        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    // show normal x values
                    return super.formatLabel(value, isValueX) + " s";
                } else {
                    // show unit for SpO2 signals
                    return super.formatLabel(value, isValueX) + " µA"; // µS for EDA
                }
            }
        });

//        // Second y-axis
//        graph.getSecondScale().setLabelFormatter(new DefaultLabelFormatter() {
//            @Override
//            public String formatLabel(double value, boolean isValueX) {
//                if (isValueX) {
//                    // show normal x values
//                    return super.formatLabel(value, isValueX) + " s";
//                } else {
//                    // show currency for y values
//                    return super.formatLabel(value, isValueX) + " " + Html.fromHtml("<sup>-3</sup>V") // mV for ECG
//                }
//            }
//        });
//
//        graph.getGridLabelRenderer().setVerticalLabelsSecondScaleColor(Color.RED);


        //==========================================================================================
        // View on Graph
        //==========================================================================================

        // activates horizontal zooming and scrolling
        viewport.setScalable(true);

        // activates horizontal scrolling
        viewport.setScrollable(true);

        // activates horizontal and vertical zooming and scrolling
        viewport.setScalableY(true);

        // activates vertical scrolling
        viewport.setScrollableY(true);


        // To set a fixed manual viewport use this:

        // sets manual X bounds
        viewport.setXAxisBoundsManual(true);
        viewport.setMinX(MIN_X);
        viewport.setMaxX(MAX_X);

        // sets manual Y bounds
        viewport.setYAxisBoundsManual(true);
        viewport.setMinY(MIN_VAL1);
        viewport.setMaxY(MAX_VAL1);


        // The y bounds are always manual for second scale.
        // graph.getSecondScale().setMinY(MIN_VAL2);
        // graph.getSecondScale().setMaxY(MAX_VAL2);

    }

}