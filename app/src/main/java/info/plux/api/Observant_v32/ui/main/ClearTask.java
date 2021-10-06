package info.plux.api.Observant_v32.ui.main;

import android.os.AsyncTask;

import info.plux.api.Observant_v32.database.MeasureDB;

public class ClearTask extends AsyncTask<Void,Void,Void> {
    private MeasureDB mDB;
    private ColorFragment colorFragment;


    public ClearTask(MeasureDB mDB, ColorFragment cf){
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
        ColorViewModel.setTimes(); // Shouldn't be moved to method ClearDatabase
        // Incoming messages not yet deleted mess up at this point the times.
        colorFragment.resetUi(); // Legit to touch views of main thread because onPostExecute is on main thread
    }
}