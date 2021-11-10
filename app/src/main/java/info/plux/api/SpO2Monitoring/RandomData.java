package info.plux.api.SpO2Monitoring;

import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.Random;

public class RandomData {

    private static final Random RANDOM = new Random();
    private int time = 0;


    // add random data to graph
    public static double getRandomDouble(long upperLimit){
        // here, we choose to display max 10 points on the viewport and we scroll to end
        double random = RANDOM.nextDouble()*upperLimit;

        // System.out.println("1. appear "+random);
        return random;
    }

    public static int getRandomInt(int upperLimit){
        return RANDOM.nextInt(upperLimit);
    }


    public LineGraphSeries<DataPoint> getRandomSeries(int number, long upperLimit){
        DataPoint[] dataPoints = new DataPoint[number];
        int limit = time + number;
        while( time < limit){
            dataPoints[time] = new DataPoint(time,getRandomDouble(upperLimit)); // implicit casting
            time++;
        }
        return new LineGraphSeries<>(dataPoints);
    }

}
