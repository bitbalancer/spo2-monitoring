package info.plux.api.SpO2Monitoring.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {DataRow.class}, version=6)
public abstract class MeasureDB extends RoomDatabase {


    // Singleton stuff: ensures only one instance of DB is running
    public static MeasureDB measureDbInstance;
    public static MeasureDB getInstance(Context context){
        if(MeasureDB.measureDbInstance==null){
            synchronized (MeasureDB.class){
                if(MeasureDB.measureDbInstance==null){
                    MeasureDB.measureDbInstance = Room.databaseBuilder(context, MeasureDB.class, "measure_db").build();
                }

            }
        }
        return(MeasureDB.measureDbInstance);
    }

    //
    public abstract DataRowDAO dataRowDAO();


}
