package info.plux.api.SpO2Monitoring.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import java.util.ArrayList;

import info.plux.api.SpO2Monitoring.R;

public class ProfileActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemSelectedListener {

    Button btnLogout,btnSave;
    EditText edName, edVorname, edDate, edAdres, edNumb, edSVN;
    int numbInt, svnInt;
    String nameStr, vorStr, adrStr, dateStr;
    SharedPreferences prefs;
    Spinner spinner;
    ListView listView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        ActionBar actionBar = this.getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }



        btnSave= (Button) findViewById(R.id.btnSave);

        edName= (EditText)findViewById(R.id.edName);
        edVorname= (EditText)findViewById(R.id.edVorname);
        edDate= (EditText)findViewById(R.id.edDate);
        edAdres= (EditText)findViewById(R.id.edAdres);
        edNumb= (EditText)findViewById(R.id.edNumb);
        edSVN= (EditText)findViewById(R.id.edSVN);

        Spinner spinner= findViewById(R.id.spinner);

        ListView listView= (ListView)findViewById(R.id.allerList);


        /**
         * Representation of the Allergie Array in the App for the User
         */

        ArrayAdapter<CharSequence> arrayAdapter= ArrayAdapter.createFromResource(this,R.array.Allergie,android.R.layout.simple_list_item_1);
        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(ProfileActivity.this,"Allergie ausgewählt: "+ R.array.Allergie,Toast.LENGTH_SHORT).show();
            }
        });

        /**Saving personal info
         * By using the SharedPreferences the entered data of the user of the String and Integer
         * values are passed and stored, in order to convert with a repeated use.
         */

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int number = prefs.getInt("numbInt",0);
        edNumb.setText(""+number);

        int svn= prefs.getInt("svnInt",0);
        edSVN.setText(""+svn);

        String name= prefs.getString("nameStr","");
        edName.setText(name);

        String vorname = prefs.getString("vorStr","");
        edVorname.setText(vorname);

        String adresse = prefs.getString("adrStr", "");
        edAdres.setText(adresse);

        String date= prefs.getString("dateStr","");
        edDate.setText(date);

        /**With the spinner item the User has the possibilty to choose his blood type
         * using the Blood Type Array and Arrayadapter to make it work in the app
         */

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.Blutgruppe, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        /**Enter personal Information
         * Here the passed strings and integers of the originating EditTexts are converted
         * from personal information to strings for multiple editing by the sharedPrefs editor.
         */

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                numbInt= Integer.parseInt(edNumb.getText().toString());
                svnInt = Integer.parseInt(edSVN.getText().toString());

                nameStr= edName.getText().toString();
                vorStr= edVorname.getText().toString();
                adrStr= edAdres.getText().toString();
                dateStr= edDate.getText().toString();

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ProfileActivity.this);
                SharedPreferences.Editor editor=prefs.edit();

                editor.putInt("numbInt",numbInt);
                editor.putInt("svnInt",svnInt);


                editor.putString("nameStr",nameStr);
                editor.putString("vorStr",vorStr);
                editor.putString("adrStr",adrStr);
                editor.putString("dateStr",dateStr);

                /*int selectedPos = spinner.getSelectedItemPosition();
                editor.putInt("spinnerSelction", selectedPos);*/

                editor.apply();
                Toast.makeText(ProfileActivity.this,"Informationen gespeichert",Toast.LENGTH_SHORT).show();



            }
        });

 }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view) {

    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        String text = adapterView.getItemAtPosition(i).toString();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}
