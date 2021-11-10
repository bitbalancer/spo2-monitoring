package info.plux.api.SpO2Monitoring.old.read_from_db;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ListenReceiver extends BroadcastReceiver {
    private int heartRate = 0;
    private double electrodermalActivity = -2; //always first value
    private double time = 0; // for correct display at beginning in time display


    @Override
    public void onReceive(Context context, Intent intent) {

        heartRate = intent.getIntExtra("HR", -1);
        electrodermalActivity = intent.getDoubleExtra("EDA",-1);
        time = intent.getDoubleExtra("time",-1);
        // System.out.println("3. appear: "+electrodermalActivity);

    }

    public int getHeartRate(){
        return heartRate;
    }

    public double getElectrodermalActivity() {
        return electrodermalActivity;
    }

    public double getTime(){ return time; }
}
