package info.plux.api.SpO2Monitoring.ui.main;

import android.os.AsyncTask;

import androidx.lifecycle.ViewModelProvider;

import info.plux.api.SpO2Monitoring.database.MeasureDB;

public class ClearingTask extends AsyncTask<Void,Void,Void> {
    private MeasureDB measureDB;
    private ColorFragment colorFragment;
    private ColorViewModel colorViewModel;


    public ClearingTask(ColorFragment cf){
        colorViewModel = new ViewModelProvider(cf).get(ColorViewModel.class);
        measureDB = MeasureDB.getInstance(cf.getContext());
        colorFragment = cf;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        try {
            Thread.sleep(ColorFragment.DELAY);

            measureDB.clearAllTables();
            colorViewModel.clearSeriesArr();
            if(measureDB.dataRowDAO().getLastRow()==null){
                System.out.println("Database cleared!");
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void v){
        colorViewModel.setbackTime();
        colorFragment.resetUi(); // Legit to touch views of main thread because onPostExecute is on main thread
    }

}