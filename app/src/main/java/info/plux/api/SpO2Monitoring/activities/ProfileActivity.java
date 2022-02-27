package info.plux.api.SpO2Monitoring.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import java.util.List;

import info.plux.api.SpO2Monitoring.R;

public class ProfileActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemSelectedListener {

    private Button btnSave;
    private EditText edFirstName, edLastName, edBirthDate, edAddress, edPhone, edSSN;
    Spinner spinner;
    ListView listView;
    private String firstName, lastName, birthDate, address, phone, ssn;
    private int bloodTypePos;
    private String[] keys = new String[]{"firstName","lastName","birthdate","address","tel","ssn","bloodType","allergies"};
    private Boolean[] allergyStatus;
    private String[] allergyList;
    private boolean skip = true;

    private ListView mListView;
    private ArrayAdapter allergyAdapter;
    private Typeface mTypeface;

    //----------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        ActionBar actionBar = this.getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }


        // Initialize UI elements

        edFirstName = (EditText) findViewById(R.id.edFirstName);
        edLastName = (EditText) findViewById(R.id.edLastName);
        edBirthDate = (EditText) findViewById(R.id.edBirthDate);
        edAddress = (EditText) findViewById(R.id.edAddress);
        edPhone = (EditText) findViewById(R.id.edPhone);
        edSSN = (EditText) findViewById(R.id.edSSN);

        // Button to save input information
        btnSave = (Button) findViewById(R.id.btnSave);

        spinner = findViewById(R.id.spinner);

        listView = (ListView) findViewById(R.id.allergyList);

        //------------------------------------------------------------------------------------------

        // Loading information from SharedPreferences in order to display it in UI.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        firstName = prefs.getString(keys[0],"John");
        edFirstName.setText(firstName);

        lastName = prefs.getString(keys[1], "Doe");
        edLastName.setText(lastName);

        birthDate = prefs.getString(keys[2], "dd/mm/yyyy");
        edBirthDate.setText(birthDate);

        address = prefs.getString(keys[3], "Main Square 1");
        edAddress.setText(address);

        phone = prefs.getString(keys[4], "+43 677 12345678");
        edPhone.setText(phone);

        ssn = prefs.getString(keys[5], "9999 010100");
        edSSN.setText(ssn);

        bloodTypePos = prefs.getInt(keys[6],0);

        allergyList = getResources().getStringArray(R.array.allergies);
        allergyStatus = loadArray(keys[7],prefs,allergyList.length);

        //------------------------------------------------------------------------------------------
        // Spinner
        //------------------------------------------------------------------------------------------


        // With the spinner item the User has the possibilty to choose his blood type
        // using the Blood Type Array and ArrayAdapter to make it work in the app
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.bloodTypes, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(bloodTypePos); // must be after setAdapter()
        spinner.setOnItemSelectedListener(this);


        //------------------------------------------------------------------------------------------
        // ListView
        //------------------------------------------------------------------------------------------

        // Creates ArrayAdapter directly from Ressources array.
        //ArrayAdapter<CharSequence> arrayAdapter = ArrayAdapter.createFromResource(this, R.array.allergies, android.R.layout.simple_list_item_1);
        //listView.setAdapter(arrayAdapter);

        // Initialize an array adapter
//        mAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,allergyList){
//            @Override
//            public View getView(int position, View convertView, ViewGroup parent){
//                // Cast the list view each item as text view
//                TextView item = (TextView) super.getView(position,convertView,parent);
//
//                if(allergyStatus[position]) {
//
//                    // Set the typeface/font for the current item
//                    item.setTypeface(mTypeface);
//
//                    // Set the list view item's text color
//                    item.setTextColor(Color.parseColor("#FF3E80F1"));
//
//                    // Set the item text style to bold
//                    item.setTypeface(item.getTypeface(), Typeface.BOLD);
//                }
//
//                // return the view
//                return item;
//            }
//        };
        allergyAdapter = new AllergyAdapter(this,android.R.layout.simple_list_item_1,allergyList);

        // Data bind the list view with array adapter items
        listView.setAdapter(allergyAdapter);

        //------------------------------------------------------------------------------------------

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                String[] allergies = getResources().getStringArray(R.array.allergies);

                String text;
                if(allergyStatus[position] = !allergyStatus[position]){
                    text = "selected: ";
                } else{
                    text = "unselected: ";
                }
                Toast.makeText(ProfileActivity.this, text + allergies[position], Toast.LENGTH_SHORT).show();

                allergyAdapter = new AllergyAdapter(ProfileActivity.this,android.R.layout.simple_list_item_1,allergyList);
                listView.setAdapter(allergyAdapter);
            }
        });

        //------------------------------------------------------------------------------------------
        // Save Button
        //------------------------------------------------------------------------------------------

        // Save Button save entered personal information.
        // Here the passed data of the UI are locally stored in SharedPreferences.
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {



                firstName = edFirstName.getText().toString();
                lastName = edLastName.getText().toString();
                birthDate = edBirthDate.getText().toString();
                address = edAddress.getText().toString();
                phone = edPhone.getText().toString();
                ssn = edSSN.getText().toString();
                bloodTypePos = spinner.getSelectedItemPosition();


                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ProfileActivity.this);
                SharedPreferences.Editor editor = prefs.edit();


                editor.putString(keys[0],firstName);
                editor.putString(keys[1],lastName);
                editor.putString(keys[2],birthDate);
                editor.putString(keys[3],address);
                editor.putString(keys[4],phone);
                editor.putString(keys[5],ssn);
                editor.putInt(keys[6], bloodTypePos);
                editor.apply();

                storeArray(allergyStatus,keys[7],prefs);

                Toast.makeText(ProfileActivity.this, "Information saved", Toast.LENGTH_SHORT).show();


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
        if(skip) {
            skip = false;
        }else{
            Object item = adapterView.getItemAtPosition(i);
            Toast.makeText(ProfileActivity.this, "selected: " + String.valueOf(item), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    //----------------------------------------------------------------------------------------------

    private boolean storeArray(Boolean[] array, String arrayName, SharedPreferences prefs) {

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(arrayName +"_size", array.length);

        for(int i=0;i<array.length;i++)
            editor.putBoolean(arrayName + "_" + i, array[i]);

        return editor.commit();
    }

    private Boolean[] loadArray(String arrayName, SharedPreferences prefs, int size) {

        Boolean array[] = new Boolean[size];
        for(int i=0;i<size;i++)
            array[i] = prefs.getBoolean(arrayName + "_" + i, false);

        return array;
    }

    //----------------------------------------------------------------------------------------------
    // Inner Adapter Class
    //----------------------------------------------------------------------------------------------

    class AllergyAdapter extends ArrayAdapter<String>{


        public AllergyAdapter(@NonNull Context context, int resource, @NonNull String[] objects) {
            super(context, resource, objects);

        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent){
            // Cast the list view each item as text view
            TextView item = (TextView) super.getView(position,convertView,parent);

            if(allergyStatus[position]) {

                // Set the typeface/font for the current item
                item.setTypeface(mTypeface);

                // Set the list view item's text color
                item.setTextColor(Color.parseColor("#FF3E80F1"));

                // Set the item text style to bold
                item.setTypeface(item.getTypeface(), Typeface.BOLD);
            }

            // return the view
            return item;
        }
    }


}
