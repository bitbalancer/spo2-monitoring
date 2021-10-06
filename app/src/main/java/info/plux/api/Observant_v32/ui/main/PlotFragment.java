package info.plux.api.Observant_v32.ui.main;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;

import info.plux.api.Observant_v32.R;

/**
 * A placeholder fragment containing GraphView
 */
public class PlotFragment extends Fragment {
    private static final String ARG_SECTION_NUMBER = "section_number";

    // for plotting
    // time
    protected static final int MIN_X = 0;
    protected static final int MAX_X = 10;
    // electrodermal activity
    protected static final int MIN_EDA = 0;
    protected static final int MAX_EDA = 10;
    // heart rate
    protected static final int MIN_ECG = -1;
    protected static final int MAX_ECG = 1; // 75 for 1 deriv; // 8000 for 2 deriv

    protected static GraphView graph;
    protected static Viewport viewport;


    // *********************************************************************************************
    // Methods
    // *********************************************************************************************


    public static PlotFragment newInstance(int index) {

        PlotFragment fragment = new PlotFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_SECTION_NUMBER, index);
        fragment.setArguments(bundle);
        return fragment;

    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_plot, container, false);

        return root;
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        View root = getView();

        graph = root.findViewById(R.id.graph);
        viewport = graph.getViewport();

        // -----------------------------------------------------------------------------------------
        // Graph Customization
        // -----------------------------------------------------------------------------------------

        // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        // Axes
        // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

        // Changes axis label
        graph.getGridLabelRenderer().setNumHorizontalLabels(4);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("time");


        // Custom label formatter to show unit "µS"
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


        // Second y-axis
        graph.getSecondScale().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    // show normal x values
                    return super.formatLabel(value, isValueX) + " s";
                } else {
                    // show currency for y values
                    return super.formatLabel(value, isValueX) + " " + Html.fromHtml("mV"); //"<sup>-1</sup>"
                }
            }
        });

        graph.getGridLabelRenderer().setVerticalLabelsSecondScaleColor(Color.RED);


        // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        // View on Graph
        // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

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
        viewport.setMinY(MIN_EDA);
        viewport.setMaxY(MAX_EDA);

        // the y bounds are always manual for second scale
        graph.getSecondScale().setMinY(MIN_ECG);
        graph.getSecondScale().setMaxY(MAX_ECG);

    }

}