package org.safecast.bGeigie.radiationmonitor.main;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.safecast.bGeigie.R;

import java.util.ArrayList;
import java.util.List;

class BluetoothDeviceAdapter extends BaseAdapter {
	private final Context context;
	private final BluetoothHandler handler;
	private final List<BluetoothDevice> devices = new ArrayList<>();

	@SuppressLint("MissingPermission")
	public BluetoothDeviceAdapter(Context context, BluetoothHandler handler) {
		this.context = context;
		this.handler = handler;

		handler.discover(device -> {
			String name = device.getName();
			if (name == null || !name.startsWith("bGeigie")) {
				return;
			}

			// Check if the device name already exists in the list
			boolean exists = false;
			for (BluetoothDevice d : devices) {
				if (name.equals(d.getName())) {
					exists = true;
					break;
				}
			}
			if (!exists) {
				Log.i("BluetoothAdapter", "Found device: " + name);
				this.devices.add(device);
				notifyDataSetChanged();
			}
		});
	}


	@Override
	public int getCount() {
		return this.devices.size();
	}

	@Override
	public BluetoothDevice getItem(int position) {
		return this.devices.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@SuppressLint("MissingPermission")
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = LayoutInflater.from(context).inflate(R.layout.bt_device_list, parent, false);
		}

		TextView deviceName = convertView.findViewById(R.id.deviceNameTextView);
		TextView deviceAddress = convertView.findViewById(R.id.deviceAddressTextView);

		BluetoothDevice device = devices.get(position);
		String name = device.getName();
		deviceName.setText(name != null ? name : "Unknown Device");
		String address = device.getAddress();
		deviceAddress.setText(address != null ? address : "Unknown Address");

		return convertView;
	}
}
