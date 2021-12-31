package info.plux.api.SpO2Monitoring.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class DataRow {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo
    public double time;

    @ColumnInfo(name = "sig1") // previous EDA
    public double val_1;

    @ColumnInfo(name ="sig2") // previous ECG
    public double val_2;

    @ColumnInfo(name = "heart rate")
    public int val_3;

}
