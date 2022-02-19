package info.plux.api.SpO2Monitoring.activities;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.material.tabs.TabLayout;

import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;

import info.plux.api.SpO2Monitoring.R;
import info.plux.api.SpO2Monitoring.ui.main.ColorFragment;
import info.plux.api.SpO2Monitoring.ui.main.ColorViewModel;
import info.plux.api.SpO2Monitoring.ui.main.SectionsPagerAdapter;
import info.plux.pluxapi.bioplux.BiopluxException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public final static String EXTRA_DEVICE = "info.plux.pluxandroid.DeviceActivity.EXTRA_DEVICE";
    private TabLayout tabs;
    private BluetoothDevice bluetoothDevice;
    //TextView visibletxt;
    //**********************************************************************************************
    // Lifecycle Callbacks
    //**********************************************************************************************

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);
        tabs = findViewById(R.id.tabs);
        tabs.setBackgroundColor(getResources().getColor(R.color.SteelBlue));
        tabs.setupWithViewPager(viewPager);

    }


     //----------------------------------------------------------------------------------------------
    @Override
    public void onResume() {
        super.onResume();

        // Save Bluetooth device in variable to make it available.
        bluetoothDevice = getIntent().getParcelableExtra(EXTRA_DEVICE);
    }
    //----------------------------------------------------------------------------------------------

    @Override
    public void onPause() {
        super.onPause();
    }

    //**********************************************************************************************
    // Other Methods
    //**********************************************************************************************

    @Override
    public void onBackPressed() {
        switch (tabs.getSelectedTabPosition()) {
            case 0:
                // Starts main activity and passes on chosen device to it.
                Intent intent = new Intent( getApplicationContext(), ScanActivity.class);
                ColorViewModel colorViewModel = new ViewModelProvider(ColorFragment.getInstance()).get(ColorViewModel.class);
                try {
                    colorViewModel.getBiopluxCommunication().disconnect();
                } catch (BiopluxException e) {
                    e.printStackTrace();
                }
                colorViewModel.setInitialized(false);
                startActivity(intent);
                Log.v(TAG,"ON BACK PRESSED");
                break;
            case 1:
                tabs.getTabAt(0).select();
                break;
            default:
                break;
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
     }

    //----------------------------------------------------------------------------------------------

    /**
     * Make BluetoothDevice available in main fragments
     */
    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.settings_menu, menu);
        return true;
    }


    @Override
    // Open new Activity2 depending on which Item of Menu was selected
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.preference_item) {
            Intent intent = new Intent(MainActivity.this, PreferencesActivity.class);
            startActivity(intent);
            return true;
        }
        else if (id == R.id.profile_item){
            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }






}