package info.plux.api.SpO2Monitoring.fragments;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
//import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

//import androidx.fragment.app.ListFragment;

//import com.example.observant_v9.R;

import androidx.annotation.NonNull;
import androidx.fragment.app.ListFragment;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;

import info.plux.api.SpO2Monitoring.R;
import info.plux.api.SpO2Monitoring.activities.MainActivity;
import info.plux.api.SpO2Monitoring.activities.ScanActivity;

public class ScanListFragment extends ListFragment implements AdapterView.OnItemClickListener {
    private boolean itemsClickable;
    private ArrayList<BluetoothDevice> uniqueDevices;
    private String deviceName, deviceAddress;
    CustomArrayAdapter adapter;

    //**********************************************************************************************
    // Class
    //**********************************************************************************************

    private class CustomArrayAdapter<T> extends ArrayAdapter<T>{

        public CustomArrayAdapter(@NonNull @NotNull Context context, int resource) {
            super(context, resource);
        }

        // Items disabled during scanning
        @Override
        public boolean isEnabled(int position) {
            if(itemsClickable){
                return true;
            }
            return false;
        }


    }

    //**********************************************************************************************
    // Lifecycle Callbacks
    //**********************************************************************************************

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.list_fragment_scan, container, false);
        itemsClickable = true;
        uniqueDevices = new ArrayList();
        return view;
    }

    //----------------------------------------------------------------------------------------------

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

        adapter = new CustomArrayAdapter(getActivity(), android.R.layout.simple_list_item_1);
        Bundle bundle = getArguments();
        // Gets the available devices previously detected
        ArrayList<BluetoothDevice> devices = bundle.getParcelableArrayList(ScanActivity.KEY_TRANSFER);

        uniqueDevices = removeDuplicates(devices);

        Iterator<BluetoothDevice> iterator = uniqueDevices.iterator();
        while( iterator.hasNext() ) {

            BluetoothDevice device = iterator.next();
            deviceName = device.getName();
            deviceAddress = device.getAddress();
            // Obtains name and address of devices
            adapter.add(deviceName + "   " + deviceAddress);

        }

        // displays the obtained list
        setListAdapter(adapter);

        // Registers a callback to be invoked when an item in this AdapterView has been clicked.
        getListView().setOnItemClickListener(this);


    }

    //**********************************************************************************************
    // Other Methods
    //**********************************************************************************************

    /**
     * Defines what happens when list item is clicked.
     *
     * @param parent
     * @param view
     * @param position
     * @param id
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

        BluetoothDevice device = uniqueDevices.get(position);


        if (device != null) {

            // No valid choice
            if (!device.getName().toLowerCase().contains("biosignalsplux")) {
                Toast.makeText(view.getContext(), "Biosignalsplux only.", Toast.LENGTH_SHORT).show();

            } else {

                // Starts main activity and passes on chosen device.
                Intent intent = new Intent(getActivity(), MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_DEVICE, device);

                startActivity(intent);

            }

        } else { // No valid choice
            Toast.makeText(view.getContext(), "No device available.", Toast.LENGTH_SHORT).show();

        }

    }

    //----------------------------------------------------------------------------------------------

    /**
     *  Makes clicking on devices ineffective
     */
    public void setItemsClickable(boolean itemsClickable) {
        this.itemsClickable = itemsClickable;
    }

    //----------------------------------------------------------------------------------------------

    /**
     *  Removes duplicates from an ArrayList
     */
    private <T> ArrayList<T> removeDuplicates(ArrayList<T> list)
    {

        // Create a new ArrayList
        ArrayList<T> newList = new ArrayList<T>();

        // Traverse through the first list
        for (T element : list) {

            // If this element is not present in newList
            // then add it
            if (!newList.contains(element)) {

                newList.add(element);
            }
        }

        // return the new list
        return newList;
    }


}
