package org.safecast.bGeigie.radiationmonitor.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.collect.ImmutableMap;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polygon;
import org.safecast.bGeigie.R;
import org.safecast.bGeigie.radiationmonitor.log.Log;
import org.safecast.bGeigie.radiationmonitor.log.LogEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Monitor extends AppCompatActivity {

	// Tag for debugging
	private static final String TAG = "Monitor";

	// Logging
	private Log log;
	private boolean isRecording;

	// Map
	private MapView map;
	private final List<LogEntry> logEntries = new ArrayList<>();
	private static final String APP_DIRECTORY = "/SafeCast/DriveLogs";

	// Bluetooth
	private BluetoothHandler btHandler;

	// Top Menu
	private Button logButton, btConnectButton, clearDataButton, loadDataButton;
	private ImageButton settingsButton;
	private String userAPIKey;
	private CardView settingsMenu;
	private boolean screenDimEnabled, connectRequest;
	private CheckBox preventDimCheckbox, enableSoundCheckbox;
	private RadioGroup uploadSelectionRadioGroup;
	private ActivityResultLauncher<Intent> openFileLauncher;

	// Upload
	private SafeCastUploader safeCastUploader;

	// GUI & Map Menu
	private Legend legend;
	private InfoCard infoCard;
	private ImageView mapSettingsButton;
	private CardView mapSettingsMenu;
	private CheckBox showInfoPaneCheckBox, showLegendCheckBox, autoCenterMapCheckBox, scaleMarkersCheckBox;
	private RadioGroup mapSelectionRadioGroup;
	private ToastManager toastManager;

	// Position from device
	private FusedLocationProviderClient fusedLocationClient;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_monitor);

		toastManager = new ToastManager(this);
		safeCastUploader = new SafeCastUploader(this);

		// Add stuff to get the current location from the device
		fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

		// Set the user agent for osmdroid
		Configuration.getInstance().setUserAgentValue("bGeigieZenDrive/0.1 (Android; osmdroid)");

		// Request all needed permissions and start BLE discovery if permissions are granted
		requestPermissions(new String[]{
				// Only for API 30 and below
				Manifest.permission.BLUETOOTH,
				Manifest.permission.BLUETOOTH_ADMIN,
				Manifest.permission.BLUETOOTH_SCAN,
				Manifest.permission.BLUETOOTH_CONNECT,

				Manifest.permission.ACCESS_COARSE_LOCATION,
				Manifest.permission.ACCESS_FINE_LOCATION,

				Manifest.permission.WRITE_EXTERNAL_STORAGE,
				Manifest.permission.READ_EXTERNAL_STORAGE,
				Manifest.permission.MANAGE_EXTERNAL_STORAGE
		}, 1);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		this.infoCard = new InfoCard(this);

		settingsMenu = findViewById(R.id.settings_menu);
		settingsMenu.setVisibility(View.GONE); // Hides the settings menu initially

		mapSettingsMenu = findViewById(R.id.map_Settings_menu);
		mapSettingsMenu.setVisibility(View.GONE); // Hides the map settings menu initially


		loadDataButton = findViewById(R.id.load_data_button);

		// Initialize the ActivityResultLauncher
		openFileLauncher = registerForActivityResult(
				new ActivityResultContracts.StartActivityForResult(),
				result -> {
					if (result.getResultCode() == Activity.RESULT_OK) {
						Intent data = result.getData();
						if (data != null) {
							Uri uri = data.getData();
							if (uri != null) {
								if (isFileInCorrectDirectory(uri)) {
									loadLogFileFromUri(uri);
								} else {
									toastManager.showToast("Select a file from the SafeCast/DriveLogs directory", Color.YELLOW);
								}
							}
						}
					} else {
						// Handle the case where the user canceled the file selection
						toastManager.showToast("File selection canceled", Color.RED);
					}
				}
		);

		this.legend = findViewById(R.id.legend);
		this.btHandler = new BluetoothHandler(this, toastManager);
		this.btHandler.onDisconnect(() -> {
			// If logging, stop logging (toggle)
			if (isRecording) {
				try {
					writeLogFile();
				} catch (IOException e) {
					android.util.Log.e(TAG, "Could not save log: " + e.getMessage());
					toastManager.showToast("Could not save log file!", Color.RED);
				}
			} else {
				log = new Log();
			}

			isRecording = false;
			updateLogButtonAppearance();
			updateConnectButtonAppearance();
			infoCard.refresh(LogEntry.EMPTY);
			ClickSoundGenerator.getInstance().setClickRate(0);
		});
		this.btHandler.onDataRead(new BluetoothHandler.DataCallback() {
			private StringBuffer buffer;

			@SuppressLint("DefaultLocale")
			@Override
			public void accept(String data) {
				runOnUiThread(() -> {
					android.util.Log.d(TAG, "Received data: " + data);

					if (data.startsWith(LogEntry.PREFIX)) {
						// Correct prefix detected, i.e. first part received -> reset the buffer and append data
						buffer = new StringBuffer();
					}

					buffer.append(data);

					if (!data.contains("*")) {
						return;
					}

					LogEntry entry;
					try {
						entry = LogEntry.parse(buffer.toString());
						android.util.Log.d(TAG, "Parsed entry: " + entry);
					} catch (Exception e) {
						android.util.Log.e(TAG, "Could not parse entry '" + buffer + "': " + e.getMessage());
						return;
					}

					// Only save to log if recording
					if (isRecording) {
						Monitor.this.log.add(entry);
					}

					// Perform realtime upload only if auto upload is enabled AND GPS is OK AND CPM is OK
					if (uploadSelectionRadioGroup.getCheckedRadioButtonId() == findViewById(R.id.upload_radio_option_2).getId() && entry.isGPSValid() && entry.isCPMValid()) {
						safeCastUploader.uploadEntry(entry);
					}


					if (enableSoundCheckbox.isChecked()) {
						// Only play sound if enabled
						ClickSoundGenerator.getInstance().setClickRate(entry.getCountPer5s());
					} else {
						// Stop playing sound if disabled
						ClickSoundGenerator.getInstance().setClickRate(0);
					}

					plot(entry);
					logEntries.add(entry);

					// Check if new legend is needed. If so, redraw legend and markers
					if (entry.getusvh() > legend.getMaxValue()) {
						plotMarkers();
					}

					infoCard.refresh(entry);
					map.invalidate();
				});
			}
		});

		//----Add stuff for the bluetooth button
		btConnectButton = findViewById(R.id.bt_connectButton);
		btConnectButton.setOnClickListener(v -> {
			// If disconnected, connect to a device (toggle)
			if (!btHandler.isConnected()) {
				showBluetoothDevices();
				return;
			}

			// If connected, disconnect (toggle)
			btHandler.disconnect();
			toastManager.showToast("Bluetooth: Device disconnected.", Color.RED);

			// If recording while connected, stop recording
			if (isRecording) {
				try {
					writeLogFile();
					isRecording = false;
					updateLogButtonAppearance();
				} catch (IOException e) {
					android.util.Log.e(TAG, "Could not save log: " + e.getMessage());
					toastManager.showToast("Could not save log file.", Color.RED);
				}
				log = null;
			}
		});
		updateConnectButtonAppearance();

		//----Add stuff for the log file and the button
		logButton = findViewById(R.id.logButton);
		updateLogButtonAppearance();
		logButton.setOnClickListener(v -> {
			if (!this.btHandler.isConnected() && !isRecording) {
				return;
			}

			if (logEntries.isEmpty()) {
				return;
			}
			if (!isRecording) {
				// Log will start -> inform the user where the files are stored
				toastManager.showToast("Log files are stored in:\n" + Environment.DIRECTORY_DOWNLOADS + APP_DIRECTORY, Color.YELLOW, 6000);
			}

			LogEntry last = logEntries.get(logEntries.size() - 1);
			if (!last.isGPSValid() || !last.isCPMValid()) {
				return;
			}

			// If logging, stop logging (toggle)
			if (isRecording) {
				try {
					writeLogFile();
				} catch (IOException e) {
					android.util.Log.e(TAG, "Could not save log: " + e.getMessage());
					toastManager.showToast("Could not save log file!", Color.RED);
				}
			} else {

				log = new Log();
			}
			isRecording = !isRecording;
			updateLogButtonAppearance();
		});

		//----Add stuff for the settings menu
		settingsButton = findViewById(R.id.settingsButton);

		settingsButton.setOnClickListener(v -> {
			toggleVisibility(settingsMenu);
		});

		preventDimCheckbox = findViewById(R.id.prevent_Screen_Dim_CheckBox);
		preventDimCheckbox.setOnCheckedChangeListener((view, isChecked) -> toggleScreenDim());

		enableSoundCheckbox = findViewById(R.id.enable_Sound_CheckBox);

		uploadSelectionRadioGroup = findViewById(R.id.upload_selection_RadioGroup);


		clearDataButton = findViewById(R.id.clear_data_button);
		clearDataButton.setOnClickListener(v -> {
			logEntries.clear();
			map.getOverlayManager().clear();
			map.invalidate();
			infoCard.refresh(LogEntry.EMPTY);
		});

		loadDataButton.setOnClickListener(v -> openFilePicker());

		Button apiKeyButton = findViewById(R.id.api_key_button);
		apiKeyButton.setOnClickListener(v -> {
			showAPIKeyDialog();
		});

		Button aboutButton = findViewById(R.id.about_button);
		aboutButton.setOnClickListener(v -> {
			String url = "https://github.com/Safecast/bGeigie-Drive";
			Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			startActivity(browserIntent);
		});


		// Add stuff for the map
		map = findViewById(R.id.mapView);
		map.setMultiTouchControls(true);
		IMapController mapController = map.getController();
		mapController.setZoom(16d);
		map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
		setCurrentLocationOnMapUsingPositionFromDevice();
		map.addMapListener(new MapListener() {
			private Runnable zoomRunnable;

			@Override
			public boolean onScroll(ScrollEvent event) {
				return false;
			}

			@Override
			public boolean onZoom(ZoomEvent zoomEvent) {
				if (!scaleMarkersCheckBox.isChecked()) {
					return true;
				}

				// Remove any pending zoomRunnable callbacks
				map.removeCallbacks(zoomRunnable);

				// Create a new runnable to process the zoom event
				zoomRunnable = Monitor.this::plotMarkers;

				// Post the runnable with a delay
				map.postDelayed(zoomRunnable, 100);
				return true;
			}
		});

		// Add event for click on SafeCast icon
		ImageView appIconImageView = findViewById(R.id.appIconImageView);
		appIconImageView.setOnClickListener(v -> {
			String url = "https://safecast.org";
			Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			startActivity(browserIntent);
		});

		// Add stuff for map settings
		mapSettingsButton = findViewById(R.id.mapSettingsButton);

		// Add handling for the map setting controls
		showInfoPaneCheckBox = findViewById(R.id.show_Info_Pane_CheckBox);
		showLegendCheckBox = findViewById(R.id.show_Legend_CheckBox);
		autoCenterMapCheckBox = findViewById(R.id.auto_Center_Map_CheckBox);
		scaleMarkersCheckBox = findViewById(R.id.scale_Markers_CheckBox);
		mapSelectionRadioGroup = findViewById(R.id.map_selection_RadioGroup);

		if (mapSelectionRadioGroup.getCheckedRadioButtonId() == findViewById(R.id.map_radio_option_1).getId()) {
			map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
		} else {
			map.setTileSource(TileSourceFactory.OpenTopo);
		}

		mapSettingsButton.setOnClickListener(v -> {
			toggleVisibility(mapSettingsMenu);
		});
		showInfoPaneCheckBox.setOnCheckedChangeListener((view, isChecked) -> setVisible(infoCard.getCardView(), isChecked));
		showLegendCheckBox.setOnCheckedChangeListener((view, isChecked) -> setVisible(legend, isChecked));
		scaleMarkersCheckBox.setOnCheckedChangeListener((view, isChecked) -> plotMarkers());

		mapSelectionRadioGroup.setOnCheckedChangeListener((group, id) -> {
			if (mapSelectionRadioGroup.getCheckedRadioButtonId() == findViewById(R.id.map_radio_option_1).getId()) {
				map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
			} else {
				map.setTileSource(TileSourceFactory.OpenTopo);
			}
		});

		restoreState();
	}

	ToastManager getToastManager() {
		return toastManager;
	}

	String getApiKey() {
		return userAPIKey;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions,
										   int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		for (int i = 0; i < permissions.length; i++) {
			if (!permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
				continue;
			}

			switch (grantResults[i]) {
				case PackageManager.PERMISSION_GRANTED -> {
					setCurrentLocationOnMapUsingPositionFromDevice();
				}
				case PackageManager.PERMISSION_DENIED -> {
					this.map.getController().setCenter(new GeoPoint(37.762089, 140.458334));
				}
			}
		}
	}

	//-------------------------------------------------
	private void updateConnectButtonAppearance() {
		boolean isConnected = this.btHandler.isConnected();
		this.btConnectButton.setBackgroundResource(isConnected
				? R.drawable.button_green
				: R.drawable.button_red);
		this.btConnectButton.setText(isConnected
				? "Disconnect"
				: "Connect");
	}

	private void updateLogButtonAppearance() {
		this.logButton.setBackgroundResource(isRecording
				? R.drawable.button_green
				: R.drawable.button_red);
		this.logButton.setText(isRecording
				? "Stop Log"
				: "Start Log");
	}

	@SuppressLint("MissingPermission")
	private void showBluetoothDevices() {
		BluetoothDeviceAdapter adapter = new BluetoothDeviceAdapter(this, btHandler);
		ListPopupWindow btList = new ListPopupWindow(this);
		btList.setAdapter(adapter);
		btList.setOnItemClickListener((parent, view, position, id) -> {
			this.btHandler.setConnectedDevice(adapter.getItem(position));
			updateConnectButtonAppearance();
			connectRequest = true;
			toastManager.showToast("Bluetooth: Device connected.", Color.GREEN);
			btList.dismiss();
		});

		btList.setHeight(ListPopupWindow.WRAP_CONTENT); // Set height dynamically

		// Show the popup menu
		btList.setAnchorView(btConnectButton);
		btList.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		btList.setWidth(600);
		btList.setVerticalOffset(30);
		btList.show();

		btList.setOnDismissListener(() -> {
			if (!connectRequest) {
				//this.btHandler.disconnect();
				btHandler.stopDiscover();
				updateConnectButtonAppearance();
				toastManager.showToast("Bluetooth discovery cancelled.", Color.RED);
			}
		});
	}

	@SuppressLint("MissingPermission")
	@Override
	protected void onDestroy() {
		super.onDestroy();
		btHandler.disconnect();
		btHandler.stopDiscover();
		ClickSoundGenerator.getInstance().stop();
	}

	@Override
	protected void onResume() {
		super.onResume();
		map.onResume(); // needed for compass, my location overlays, v6.0.0 and up
	}

	@Override
	protected void onPause() {
		super.onPause();
		map.onPause();  // needed for compass, my location overlays, v6.0.0 and up
	}

	@Override
	protected void onStop() {
		super.onStop();
		saveState();
	}

	// Map ========================================================================

	private void plotMarkers() {
		map.getOverlayManager().clear();
		logEntries.forEach(Monitor.this::plot);
		map.invalidate();
	}

	private static final GeoPoint zeroLocation = new GeoPoint(0.0, 0.0);

	public void plot(LogEntry entry) {
		if (autoCenterMapCheckBox.isChecked() && !entry.getLocation().equals(zeroLocation)) {
			this.map.getController().setCenter(entry.getLocation());
		}
		Polygon marker = new Polygon();
		if (scaleMarkersCheckBox.isChecked()) {
			marker.setPoints(Polygon.pointsAsCircle(entry.getLocation(), 10 * getScaleFactor(map.getZoomLevelDouble())));
		} else {
			marker.setPoints(Polygon.pointsAsCircle(entry.getLocation(), 10));
		}

		marker.getOutlinePaint().setColor(Color.BLACK);
		marker.getOutlinePaint().setStrokeWidth(1.0f);
		marker.getFillPaint().setColor(legend.getMarkerColor(entry.getusvh()).toArgb());
		map.getOverlayManager().add(marker);
	}

	private double getScaleFactor(double zoom) {
		return Objects.requireNonNull(scaleMap.getOrDefault((int) Math.round(zoom), 1.0));
	}

	private static final Map<Integer, Double> scaleMap;

	static {
		ImmutableMap.Builder<Integer, Double> builder = ImmutableMap.builder();
		builder.put(0, 20000.0);
		builder.put(1, 10000.0);
		builder.put(2, 10000.0);
		builder.put(3, 5000.0);
		builder.put(4, 2000.0);
		builder.put(5, 1000.0);
		builder.put(6, 500.0);
		builder.put(7, 200.0);
		builder.put(8, 100.0);
		builder.put(9, 75.0);
		builder.put(10, 50.0);
		builder.put(11, 20.0);
		builder.put(12, 10.0);
		builder.put(13, 5.0);
		builder.put(14, 3.0);
		builder.put(15, 2.0);
		builder.put(16, 1.0);
		scaleMap = builder.build();
	}

	@SuppressLint("MissingPermission")
	private void setCurrentLocationOnMapUsingPositionFromDevice() {
		android.util.Log.d(TAG, "Passed permission check...");
		// Get the last known location
		fusedLocationClient.getLastLocation()
				.addOnCompleteListener(this, task -> {
					if (!task.isSuccessful() || task.getResult() == null) {
						android.util.Log.d(TAG, "Failed to get device location");
						return;
					}

					// Get the device location, task result
					GeoPoint startPoint = new GeoPoint(task.getResult());

					// Update the map with the current location
					this.map.getController().setCenter(startPoint);
				});
	}

	// Log ========================================================================
	public static final String driveFileHeaderData = """
			# NEW LOG
			# format=3.2.8-zen/drives
			# deadtime=off
			""";
	public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

	private void writeLogFile() throws IOException {
		if (log.isEmpty()) {
			return;
		}

		// Scoped Storage method for Android 10+
		String fileName = "log_" + LocalDateTime.now().format(dateTimeFormatter) + ".log";
		ContentValues values = new ContentValues();
		values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
		values.put(MediaStore.Downloads.MIME_TYPE, "application/x-safecast-log");
		values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + APP_DIRECTORY);

		Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
		if (uri != null) {
			try (OutputStream output = getContentResolver().openOutputStream(uri)) {
				output.write(log.toByteArray());
				toastManager.showToast("Log file successfully saved!!", Color.GREEN);
			} catch (IOException e) {
				android.util.Log.e(TAG, "Error writing log: " + e.getMessage());
			}
		}
		// Perform upload only if file upload is enabled
		if (uploadSelectionRadioGroup.getCheckedRadioButtonId() == findViewById(R.id.upload_radio_option_3).getId()) {
			safeCastUploader.uploadLogFile(log, fileName);
		}

	}

	private void toggleVisibility(View view) {
		setVisible(view, view.getVisibility() == View.GONE);
	}

	private void setVisible(View view, boolean visible) {
		view.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	private static final String TAG_SETTINGS_DIM = "prevent_screen_dim";
	private static final String TAG_SETTINGS_SOUND = "enable_sound";
	private static final String TAG_MAP_SETTINGS_MENU = "map_settings";
	private static final String TAG_SHOW_INFO_CARD = "show_info_card";
	private static final String TAG_SHOW_LEGEND = "show_legend";
	private static final String TAG_AUTO_CENTER_MAP = "auto_center_map";
	private static final String TAG_SCALE_MARKERS = "scale_markers";
	private static final String TAG_MAP_OPTION = "map_choice";
	private static final String TAG_UPLOAD_OPTION = "upload_choice";
	private static final String TAG_API_KEY = "api_key";

	private void saveState() {
		// Get SharedPreferences instance
		SharedPreferences sharedPreferences = getSharedPreferences(TAG_MAP_SETTINGS_MENU, MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPreferences.edit();

		// ---- Settings menu
		editor.putBoolean(TAG_SETTINGS_DIM, preventDimCheckbox.isChecked());
		editor.putBoolean(TAG_SETTINGS_SOUND, enableSoundCheckbox.isChecked());
		editor.putString(TAG_UPLOAD_OPTION, UploadType.forId(uploadSelectionRadioGroup.getCheckedRadioButtonId()).name());
		editor.putString(TAG_API_KEY, userAPIKey);

		// ---- Map menu
		// Save CheckBox states
		editor.putBoolean(TAG_SHOW_INFO_CARD, showInfoPaneCheckBox.isChecked());
		editor.putBoolean(TAG_SHOW_LEGEND, showLegendCheckBox.isChecked());
		editor.putBoolean(TAG_AUTO_CENTER_MAP, autoCenterMapCheckBox.isChecked());
		editor.putBoolean(TAG_SCALE_MARKERS, scaleMarkersCheckBox.isChecked());

		// Save RadioGroup selection (RadioButton ID)
		editor.putString(TAG_MAP_OPTION, MapType.forId(mapSelectionRadioGroup.getCheckedRadioButtonId()).name());

		// Apply changes
		editor.apply();
	}

	private void restoreState() {
		// Get SharedPreferences instance
		SharedPreferences sharedPreferences = getSharedPreferences(TAG_MAP_SETTINGS_MENU, MODE_PRIVATE);

		// Retrieve API key
		userAPIKey = sharedPreferences.getString(TAG_API_KEY, "");

		// Restore CheckBox states
		preventDimCheckbox.setChecked(sharedPreferences.getBoolean(TAG_SETTINGS_DIM, true));
		enableSoundCheckbox.setChecked(sharedPreferences.getBoolean(TAG_SETTINGS_SOUND, true));
		UploadType uploadType = UploadType.valueOf(sharedPreferences.getString(TAG_UPLOAD_OPTION, UploadType.DEFAULT.name()));
		((RadioButton) findViewById(uploadType.id())).setChecked(true);

		showInfoPaneCheckBox.setChecked(sharedPreferences.getBoolean(TAG_SHOW_INFO_CARD, true));
		setVisible(infoCard.getCardView(), showInfoPaneCheckBox.isChecked());

		showLegendCheckBox.setChecked(sharedPreferences.getBoolean(TAG_SHOW_LEGEND, true));
		setVisible(legend, showLegendCheckBox.isChecked());

		autoCenterMapCheckBox.setChecked(sharedPreferences.getBoolean(TAG_AUTO_CENTER_MAP, false));
		scaleMarkersCheckBox.setChecked(sharedPreferences.getBoolean(TAG_SCALE_MARKERS, true));

		// Restore RadioGroup selection
		MapType mapType = MapType.valueOf(sharedPreferences.getString(TAG_MAP_OPTION, MapType.DEFAULT.name()));
		((RadioButton) findViewById(mapType.id())).setChecked(true);
	}

	public void toggleScreenDim() {
		if (screenDimEnabled) {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		screenDimEnabled = !screenDimEnabled;
	}

	private void showAPIKeyDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		View dialog_API_Key_View = getLayoutInflater().inflate(R.layout.api_key_dialog, null);
		builder.setView(dialog_API_Key_View);

		// Create an EditText view for user input
		EditText keyInput = dialog_API_Key_View.findViewById(R.id.keyInput);
		Button btnCancel = dialog_API_Key_View.findViewById(R.id.btnCancel);
		Button btnSave = dialog_API_Key_View.findViewById(R.id.btnSave);
		keyInput.setText(userAPIKey);

		AlertDialog keyDialog = builder.create();

		btnSave.setOnClickListener(v -> {
			userAPIKey = keyInput.getText().toString().trim();
			saveState();
			keyDialog.dismiss(); // Close dialog after saving
		});

		btnCancel.setOnClickListener(v -> keyDialog.dismiss());
		keyDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		// Disable the dimming effect (keeping the rest of the screen unaffected)
		keyDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
		keyDialog.show();
	}

	private void openFilePicker() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		openFileLauncher.launch(intent);
	}

	private void loadLogFileFromUri(Uri uri) {
		logEntries.clear(); // Clear existing entries
		try {
			logEntries.addAll(Log.parse(getContentResolver().openInputStream(uri)));

			// logEntries list filled with LogEntry objects
			toastManager.showToast("Loaded " + logEntries.size() + " log entries", Color.GREEN);
			plotMarkers();
		} catch (IOException e) {
			toastManager.showToast("Error loading file", Color.RED);
		}
		settingsMenu.setVisibility(View.GONE); // Hides the settings menu
	}

	private boolean isFileInCorrectDirectory(Uri uri) {
		try {
			return Objects.requireNonNull(getPathFromUri(uri)).contains(APP_DIRECTORY);
		} catch (Exception e) {
			android.util.Log.e(TAG, "Error getting path from URI", e);
			return false;
		}
	}

	@SuppressLint("Range")
	private String getPathFromUri(Uri uri) {
		if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
			//URI is from Downloads provider"
			String id = DocumentsContract.getDocumentId(uri);
			if (id.startsWith("msf:")) {
				return Environment.DIRECTORY_DOWNLOADS + APP_DIRECTORY + "/" + id.split(":")[1];
			} else {
				return Environment.DIRECTORY_DOWNLOADS + APP_DIRECTORY + "/" + id;
			}
		}

		try (Cursor cursor = getContentResolver().query(uri, new String[]{MediaStore.Downloads.RELATIVE_PATH}, null, null, null)) {
			Objects.requireNonNull(cursor).moveToFirst();
			return cursor.getString(cursor.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH));
		}
	}
}