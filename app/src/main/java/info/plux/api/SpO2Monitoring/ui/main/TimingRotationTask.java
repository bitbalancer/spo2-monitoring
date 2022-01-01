package info.plux.api.SpO2Monitoring.ui.main;

import android.os.AsyncTask;

public class TimingRotationTask extends AsyncTask<Void,Void,Void> {

    @Override
    protected Void doInBackground(Void... voids) {

        ColorViewModel.inTime=true;
        try {
            Thread.sleep(ColorFragment.DELAY);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void v){

        ColorViewModel.inTime=false;
    }
}