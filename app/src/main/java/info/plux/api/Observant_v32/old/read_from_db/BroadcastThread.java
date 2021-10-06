package info.plux.api.Observant_v32.old.read_from_db;

import android.content.Context;
import android.content.Intent;

import info.plux.api.Observant_v32.old.DeviceActivity;
import info.plux.api.Observant_v32.database.DataRow;
import info.plux.api.Observant_v32.database.DataRowDAO;
import info.plux.api.Observant_v32.database.MeasureDB;

public class BroadcastThread extends Thread {
    Context context;
    private long sleepTime = DeviceActivity.BROADCAST_THREAD_SLEEP_TIME;

    BroadcastThread(Context context){
        this.context = context;
    }


    @Override
    public void run() {


        double heartRate;
        double electrodermalActivity;
        double time;


        MeasureDB db = MeasureDB.getInstance(context);
        DataRowDAO dataRowDAO = db.dataRowDAO();
        DataRow dataRow;

        while(true){

            /*
            heartRate = RandomData.getRandomInt(120);
            electrodermalActivity = RandomData.getRandomDouble(10);
            */

            dataRow = dataRowDAO.getLastRow();

            if(dataRow!=null) {
                heartRate = dataRow.heartRate;
                electrodermalActivity = dataRow.electrodermalActivity;
                time = dataRow.time;
                // System.out.println("2. appear: "+electrodermalActivity);

                try {
                    Thread.sleep(sleepTime);
                    Intent intent = new Intent();
                    intent.setAction("ACTION");

                    intent.putExtra("HR", heartRate);
                    intent.putExtra("EDA", electrodermalActivity);
                    intent.putExtra("time", time);

                    context.sendBroadcast(intent);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }

        }

    }

}
