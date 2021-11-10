package info.plux.api.SpO2Monitoring.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;


//import android.support.v4.app.FragmentTransaction;
//import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

//import androidx.appcompat.app.AppCompatActivity;
//import androidx.fragment.app.FragmentTransaction;

//import com.example.observant_v9.R;


import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import info.plux.api.SpO2Monitoring.R;
import info.plux.api.SpO2Monitoring.fragments.ScanListFragment;
import info.plux.pluxapi.BTHDeviceScan;
import info.plux.pluxapi.Constants;

// Hint: No Method has direct effects on UI with exception to onClick (called in onResume).

public class ScanActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getSimpleName();

    public static FragmentTransaction fragmentTransaction;
    protected ScanListFragment scanListFragment;

    Button scan;

    public static final String KEY_TRANSFER = "transfer";
    private static final int REQUEST_ENABLE_BT = 1;
    private final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 123;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private BluetoothAdapter mBluetoothAdapter;
    private BTHDeviceScan bthDeviceScan;

    private ArrayList deviceList, transferList;


    // *********************************************************************************************
    // Lifecycle Callbacks
    // *********************************************************************************************


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null); // Trash previous state when returning
        setContentView(R.layout.activity_scan);

        // Initializes a Bluetooth adapter. For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Error - Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bthDeviceScan = new BTHDeviceScan(this);

        // for fragment list
        deviceList = new ArrayList();

        // -----------------------------------------------------------------------------------------
        // Creating Scan Button
        // -----------------------------------------------------------------------------------------

        // Initializes button
        scan = findViewById(R.id.button_scan);
        scan.setEnabled(false);

        // Sets up the scan button
        scan.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                scan.setText("SCANNING");

                // disabled during scan
                scan.setEnabled(false);
                // list items disabled too
                if(scanListFragment != null) {
                    scanListFragment.setItemsClickable(false);
                    Log.v(TAG,"SetItmesClickable now false");
                } else{
                    Log.v(TAG,"ScanList Fragment does not exist");
                }

                // Scans for devices
                scanDevice();

            }
        });


    }

    @Override
    protected void onResume() {
        super.onResume();

        // The Receiver will be called with any broadcast Intent that matches filter, in the main application thread.
        registerReceiver(scanDevicesUpdateReceiver, new IntentFilter(Constants.ACTION_MESSAGE_SCAN));

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

        }

        // For testing
        if(scanListFragment!=null){


            fragmentTransaction.remove(scanListFragment);
            scanListFragment.onDestroyView();
            Log.v(TAG,"FragmentList has been removed");
        }

        // Scans for devices already when activity is started.
        scan.performClick();
        // Asks for permissions.
        permissionCheck();

    }

    @Override
    protected void onPause() {
        super.onPause();

        bthDeviceScan.stopScan();
        deviceList.clear();
        unregisterReceiver(scanDevicesUpdateReceiver);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(bthDeviceScan != null){
            bthDeviceScan.closeScanReceiver();
        }

        Log.v(TAG," ScanActivity destroyed");

    }

    // *********************************************************************************************
    // Other Methods
    // *********************************************************************************************

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstancState){}

    // Detects devices
    private final BroadcastReceiver scanDevicesUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            Log.i(TAG,"I've received something! "+"---------------------------------------");

            // Redundant: Filter already set to ACTION_MESSAGE_SCAN. But kept for safety in case of changes to filter.
            if(action.equals(Constants.ACTION_MESSAGE_SCAN)){

                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(Constants.EXTRA_DEVICE_SCAN);

                Log.i(TAG,"bluetoothDevice: " + bluetoothDevice.toString());

                if(bluetoothDevice != null){
                    deviceList.add(bluetoothDevice);
                }

            }

        }

    };

    /**
     *  Stops scanning after a pre-defined scan period.
     */
    private void scanDevice() {

        Handler mHandler = new Handler(getMainLooper());
        mHandler.postDelayed(new Runnable() {

            @Override
            public void run() {

                bthDeviceScan.stopScan();

                sendScanResultsToFragmentList();

            }

        }, SCAN_PERIOD);

        bthDeviceScan.doDiscovery();

    }

    private void sendScanResultsToFragmentList(){
        Bundle bundle = new Bundle();

        if( deviceList.isEmpty() ) {
            String text = "No devices detected";
            System.out.println(text);
            Toast toast = Toast.makeText(getApplicationContext(),text,Toast.LENGTH_SHORT);
            toast.show();
        }

        // copies list before it gets cleared
        transferList = new ArrayList(deviceList);

        // removes current scan results
        deviceList.clear();

        // Fills bundle with devices
        bundle.putParcelableArrayList(KEY_TRANSFER,transferList);

        scanListFragment = new ScanListFragment();
        scanListFragment.setArguments(bundle);

        fragmentTransaction = getSupportFragmentManager().beginTransaction();
        // Creates new ListFragment with the provided devices in it
        // and replaces the old ListFragment.
        fragmentTransaction.replace(R.id.flFragment, scanListFragment);
        fragmentTransaction.commitAllowingStateLoss();

        // re-enables scan button und list items
        scan.setText("SCAN");
        scan.setEnabled(true);
        scanListFragment.setItemsClickable(true);

    }

    // ---------------------------------------------------------------------------------------------
    // Checking for Permissions (Adopted from Bioplux API)
    // ---------------------------------------------------------------------------------------------

    private void permissionCheck(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){

            List<String> permissionsNeeded = new ArrayList<String>();

            final List<String> permissionsList = new ArrayList<String>();
            if (!addPermission(permissionsList, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                permissionsNeeded.add("Bluetooth Scan");
            }
            //Android Marshmallow and above permission check
            if(!permissionsList.isEmpty()){
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.permission_check_dialog_title))
                        .setMessage(getString(R.string.permission_check_dialog_message))
                        .setPositiveButton(getString(R.string.permission_check_dialog_positive_button), null)
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @TargetApi(Build.VERSION_CODES.M)
                            @Override
                            public void onDismiss(DialogInterface dialogInterface) {
                                requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
                            }
                        });
                builder.show();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean addPermission(List<String> permissionsList, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            // Check for Rationale Option
            if (!shouldShowRequestPermissionRationale(permission)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS:
            {
                Map<String, Integer> permissionsMap = new HashMap<String, Integer>();
                // Initial
                permissionsMap.put(Manifest.permission.ACCESS_COARSE_LOCATION, PackageManager.PERMISSION_GRANTED);
                // Fill with results
                for (int i = 0; i < permissions.length; i++) {
                    permissionsMap.put(permissions[i], grantResults[i]);
                }
                // Check if all permissions are granted
                if (permissionsMap.get(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    // All Permissions Granted
                    Log.d(TAG, "All Permissions Granted -> start welcome activity");
                } else {
                    // Permission Denied
                    Toast.makeText(this, "Some Permission is denied", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
            break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


}
