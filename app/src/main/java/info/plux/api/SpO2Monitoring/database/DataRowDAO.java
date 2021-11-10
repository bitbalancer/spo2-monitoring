package info.plux.api.SpO2Monitoring.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DataRowDAO {
    //Not all commands are used but kept in case of...

    @Insert
    void insertAll(DataRow... dataRows);

    @Delete
    void delete(DataRow dataRow);

    @Query("DELETE FROM DataRow")
    void deleteAll();

    @Query("SELECT * FROM DataRow")
    LiveData<List<DataRow>> getAll();

    // Executed when database gets changed in any way.
    @Query("SELECT * FROM DataRow ORDER BY id DESC LIMIT 1")
    LiveData<DataRow> getLastRecord();

    @Query("SELECT * FROM DataRow ORDER BY id DESC LIMIT :number")
    List<DataRow> getLastRows(int number);

    @Query("SELECT * FROM DataRow ORDER BY id DESC LIMIT 1")
    DataRow getLastRow();

    @Query("SELECT * FROM DataRow ORDER BY id ASC")
    List<DataRow> getAllRows();

}
