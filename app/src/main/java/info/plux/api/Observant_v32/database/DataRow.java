package info.plux.api.Observant_v32.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class DataRow {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo
    public double time;

    @ColumnInfo(name = "EDA")
    public double electrodermalActivity;

    @ColumnInfo(name ="ECG")
    public double electroCardiogram;

    @ColumnInfo(name = "HR")
    public int heartRate;

}
