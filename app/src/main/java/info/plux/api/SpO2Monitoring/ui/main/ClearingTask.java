package info.plux.api.SpO2Monitoring.ui.main;

import android.os.AsyncTask;

import info.plux.api.SpO2Monitoring.database.MeasureDB;

public class ClearingTask extends AsyncTask<Void,Void,Void> {
    private MeasureDB mDB;
    private ColorFragment colorFragment;


    public ClearingTask(MeasureDB mDB, ColorFragment cf){
        this.mDB = mDB;
        colorFragment = cf;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        try {
            Thread.sleep(ColorFragment.DELAY);

            mDB.clearAllTables();
            ColorViewModel.clearSeriesArr();
            if(mDB.dataRowDAO().getLastRow()==null){
                System.out.println("Database cleared!");
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void v){
        setbackTime();
        colorFragment.resetUi(); // Legit to touch views of main thread because onPostExecute is on main thread
    }

    // This method shouldn't be integrated in method ClearDatabase.
    // Incoming messages not yet deleted mess up the times at this point.
    protected static void setbackTime(){
        ColorViewModel.timeBefore = 0.;
        ColorViewModel.time = 0.;
    }
}