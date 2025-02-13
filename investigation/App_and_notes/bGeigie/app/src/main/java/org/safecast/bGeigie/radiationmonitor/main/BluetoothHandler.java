package org.safecast.bGeigie.radiationmonitor.main;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.gms.common.internal.Preconditions;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

class BluetoothHandler {
	private static final UUID SERVICE_DATA_UUID = UUID.fromString("EF080D8C-C3BE-41FF-BD3F-05A5F4795D7F");
	private static final UUID CHARACTERISTIC_READ_UUID = UUID.fromString("A1E8F5B1-696B-4E4C-87C6-69DFE0B0093B");
	private static final UUID CHARACTERISTIC_WRITE_UUID = UUID.fromString("1494440E-9A58-4CC0-81E4-DDEA7F74F623");
	private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	private static final String TAG = "BluetoothHandler";
	private final Monitor monitor;
	private final BluetoothAdapter adapter;
	private BluetoothGatt bluetoothGatt;
	private final Set<DataCallback> callbacks = new HashSet<>();
	private BluetoothDevice connectedDevice;
	private boolean isDiscovering;

	public BluetoothHandler(Monitor monitor) {
		this.monitor = monitor;
		this.adapter = ((BluetoothManager) monitor.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
		if (adapter == null) {
			toast("Bluetooth is not supported on this device.").show();
			return;
		}

		if (!this.adapter.isEnabled()) {
			monitor.registerForActivityResult(
					new ActivityResultContracts.StartActivityForResult(),
					result -> {
						if (result.getResultCode() == RESULT_OK) {
							Log.d(TAG, "Bluetooth enabled by user.");
						} else {
							Log.d(TAG, "Bluetooth enabling denied by user.");
						}
					}
			).launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
		}
	}

	public interface OnDeviceFound {
		void accept(BluetoothDevice device);
	}

	@SuppressLint("MissingPermission")
	public void discover(OnDeviceFound callback) {
		if (isDiscovering) stopDiscover();

		isDiscovering = true;
		monitor.registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (!BluetoothDevice.ACTION_FOUND.equals(action)) {
					return;
				}
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device != null) {
					callback.accept(device);
				}
			}
		}, new IntentFilter(BluetoothDevice.ACTION_FOUND));
		adapter.startDiscovery();
	}

	@SuppressLint("MissingPermission")
	public void stopDiscover() {
		if (isDiscovering) {
			adapter.cancelDiscovery();
			isDiscovering = false;
		}
	}

	@SuppressLint("MissingPermission")
	public void setConnectedDevice(BluetoothDevice device) {
		if (isConnected()) disconnect();
		stopDiscover();

		Log.d(TAG, "Attempting to connect to device:");
		Log.d(TAG, "	Name: " + device.getName());
		Log.d(TAG, "	Address: " + device.getAddress());

		this.connectedDevice = Preconditions.checkNotNull(device);
		this.bluetoothGatt = device.connectGatt(monitor, true, new BluetoothGattCallback() {

			@Override
			public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
				if (newState != BluetoothProfile.STATE_CONNECTED) {
					Log.d(TAG, "Disconnected from GATT server");
					disconnect();
					return;
				}

				Log.d(TAG, "Connected to GATT server. Discovering services...");
				gatt.discoverServices();
			}

			@Override
			public void onServicesDiscovered(BluetoothGatt gatt, int status) {
				if (status != BluetoothGatt.GATT_SUCCESS) {
					Log.e(TAG, "Failed to discover GATT services");
					return;
				}

				Log.d(TAG, "GATT services discovered");

				// Get the service by UUID
				BluetoothGattService service = gatt.getService(SERVICE_DATA_UUID);
				if (service == null) {
					Log.e(TAG, "Service with UUID " + SERVICE_DATA_UUID + " not found.");
					return;
				}

				// Get the read characteristic by UUID
				BluetoothGattCharacteristic readCharacteristic = service.getCharacteristic(CHARACTERISTIC_READ_UUID);
				if (readCharacteristic == null) {
					Log.e(TAG, "Read characteristic not found.");
					return;
				}

				// Enable notifications for the read characteristic
				boolean success = gatt.setCharacteristicNotification(readCharacteristic, true);
				if (!success) {
					Log.e(TAG, "Failed to enable notifications for read characteristic.");
					return;
				}

				// Get the descriptor for the characteristic and enable notifications
				BluetoothGattDescriptor descriptor = readCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
				if (descriptor == null) {
					Log.e(TAG, "Descriptor for read characteristic not found.");
					return;
				}

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
					success = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
							== BluetoothStatusCodes.SUCCESS;
				} else { // API 30+
					descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
					success = gatt.writeDescriptor(descriptor);
				}
				//success = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS;
				if (!success) {
					Log.e(TAG, "Failed to write descriptor for read characteristic.");
				}
			}

			@Override
			public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {

				// Get the raw byte array data from the characteristic
				String data = new String(value);  // Convert byte data to a string (if appropriate)

				// Pass the data to all registered callbacks
				for (DataCallback callback : callbacks) {
					callback.accept(data);
				}
			}

			@Override
			public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
				if (status != BluetoothGatt.GATT_SUCCESS) {
					Log.e(TAG, "Failed to read characteristic");
					return;
				}

				String data = new String(value);
				Log.d(TAG, "Read characteristic data: " + data);
				callbacks.forEach(c -> c.accept(data));
			}

			@Override
			public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
				if (status == BluetoothGatt.GATT_SUCCESS) {
					Log.d(TAG, "Successfully wrote characteristic");
				} else {
					Log.e(TAG, "Failed to write characteristic");
				}
			}
		});
	}

	public interface DataCallback {
		void accept(String data);
	}

	public void onDataRead(DataCallback callback) {
		this.callbacks.add(callback);
	}

	@SuppressLint("MissingPermission")
	public boolean isConnected() {
		return this.bluetoothGatt != null;
	}

	@SuppressLint("MissingPermission")
	public void disconnect() {
		if (bluetoothGatt == null) {
			return;
		}

		bluetoothGatt.disconnect();
		bluetoothGatt.close();
		bluetoothGatt = null;
		connectedDevice = null;
		Log.d(TAG, "Disconnected from GATT server");
	}

	private Toast toast(String message) {
		return Toast.makeText(monitor, message, Toast.LENGTH_SHORT);
	}
}
