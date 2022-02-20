package info.plux.api.SpO2Monitoring.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
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
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.preference.Preference;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import info.plux.api.SpO2Monitoring.R;
import info.plux.api.SpO2Monitoring.activities.MainActivity;
import info.plux.api.SpO2Monitoring.activities.ProfileActivity;


public class ProfileFragment extends PreferenceFragmentCompat implements View.OnClickListener, AdapterView.OnItemSelectedListener, SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceChangeListener {

    Button btnLogout,btnSave;
    EditText edName, edVorname, edDate, edAdres, edNumb, edSVN;
    int numbInt, svnInt;
    String nameStr, vorStr, adrStr, dateStr;
    SharedPreferences prefs;
    Spinner spinner;
    ListView listView;
    private static final String TAG= "ProfileFragment";


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings_profile);
        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
        PreferenceScreen prefScreen = getPreferenceScreen();


        ArrayList<String> arrayList= new ArrayList<>();
        arrayList.add("Hausstaub");
        arrayList.add("Pollen");
        arrayList.add("Tierhaare");


        //Arrayadapter !!
        ArrayAdapter arrayAdapter= new ArrayAdapter(getContext(), android.R.layout.simple_list_item_1,arrayList);
        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(getContext(),"Allergie ausgewählt: "+ arrayList.get(position).toString(),Toast.LENGTH_SHORT).show();
            }
        });


        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
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



        /*
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.Blutgruppe, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);*/



        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                numbInt= Integer.parseInt(edNumb.getText().toString());
                svnInt = Integer.parseInt(edSVN.getText().toString());

                nameStr= edName.getText().toString();
                vorStr= edVorname.getText().toString();
                adrStr= edAdres.getText().toString();
                dateStr= edDate.getText().toString();

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
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
                Toast.makeText(getContext(),"Informationen gespeichert",Toast.LENGTH_SHORT).show();



            }
        });







    }



    @Override
    public View onCreateView(@NonNull @NotNull LayoutInflater inflater, @Nullable @org.jetbrains.annotations.Nullable ViewGroup container, @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        //return super.onCreateView(inflater, container, savedInstanceState);

        //View rootView = inflater.inflate(R.layout.activity_profile, container,false);
        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.activity_profile, container, false);
        //ConstraintLayout content = (ConstraintLayout) rootView.findViewById(R.id.content);
        //content.addView(ProfileFragment);
        View rootView= inflater.inflate(R.layout.activity_profile,container,false);
        spinner= rootView.findViewById(R.id.spinner);
        initspinnerfooter();
        return rootView;
    }

    private void initspinnerfooter() {
        String[] items = new String[]{
                "Blutgruppe A", "Blutgruppe B", "Blutgruppe AB", "Blutgruppe 0",
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, items);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.v("item", (String) parent.getItemAtPosition(position));
                ((TextView) parent.getChildAt(0)).setTextColor(Color.WHITE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
    }



    @Override
    public void onViewCreated(@NonNull @NotNull View view, @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);




        //btnLogout= (Button) view.findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(this);

        btnSave= (Button) view.findViewById(R.id.btnSave);

        edName= (EditText) view.findViewById(R.id.edName);
        edVorname= (EditText) view.findViewById(R.id.edVorname);
        edDate= (EditText) view.findViewById(R.id.edDate);
        edAdres= (EditText) view.findViewById(R.id.edAdres);
        edNumb= (EditText) view.findViewById(R.id.edNumb);
        edSVN= (EditText) view.findViewById(R.id.edSVN);

        Spinner spinner= view.findViewById(R.id.spinner);

        ListView listView= (ListView) view.findViewById(R.id.allerList);
    }




    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        return true;
    }
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Figure out which preference was changed
        Preference preference = findPreference(key);

        }




    private void setPreferenceSummary(Preference preference, String value) {
        if (preference instanceof EditTextPreference) {
            // For EditTextPreferences, set the summary to the value's simple string representation.
            preference.setSummary(value);
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }


    @Override
    public void onClick(View v) {
        /*SharedPreferences preferences= getSharedPreferences("loggedIn",MODE_PRIVATE);
        SharedPreferences.Editor preferencesEditor = preferences.edit();
        preferencesEditor.putBoolean("loggedIn",false);
        preferencesEditor.commit();
        Intent intent = new Intent(this, PreferencesFragment.class);
        startActivity(intent);
        this.finish();*/

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

        String text = parent.getItemAtPosition(position).toString();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
}
