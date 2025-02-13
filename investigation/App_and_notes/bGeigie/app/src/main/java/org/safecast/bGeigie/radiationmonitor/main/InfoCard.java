package org.safecast.bGeigie.radiationmonitor.main;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.safecast.bGeigie.R;
import org.safecast.bGeigie.radiationmonitor.log.LogEntry;

import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

class InfoCard {
	private final Monitor monitor;
	private final View infoCard;
	private final TextView cpmValueTextView;
	private final TextView cp5sValueTextView;
	private final TextView usvhValueTextView;
	private final TextView longitudeValueTextView;
	private final TextView latitudeValueTextView;
	private final TextView altitudeValuetextView;
	private final TextView CPM_ind_value_TextView;
	private final TextView GPS_ind_value_TextView;
	private final Button GPSStatusButton;
	private final Button CPMStatusButton;

	InfoCard(Monitor monitor) {
		this.monitor = monitor;
		this.infoCard = monitor.findViewById(R.id.measurements_card);
		this.cpmValueTextView = monitor.findViewById(R.id.CPM_Value_TextView);
		this.cp5sValueTextView = monitor.findViewById(R.id.CP5S_Value_TextView);
		this.usvhValueTextView = monitor.findViewById(R.id.usvh_Value_TextView);
		this.longitudeValueTextView = monitor.findViewById(R.id.longitude_value_TextView);
		this.latitudeValueTextView = monitor.findViewById(R.id.latitude_value_TextView);
		this.altitudeValuetextView = monitor.findViewById(R.id.altitude_value_TextView);
		this.CPM_ind_value_TextView = monitor.findViewById(R.id.CPM_ind_value_TextView);
		this.GPS_ind_value_TextView = monitor.findViewById(R.id.GPS_ind_value_TextView);
		this.GPSStatusButton = monitor.findViewById(R.id.indicator_GPS);
		this.CPMStatusButton = monitor.findViewById(R.id.indicator_CPM);

		refresh(null);
		// Update them at start
		updateGPSStatusButtonAppearance(false);
		updateCPMStatusButtonAppearance(false);
	}

	public View getCardView() {
		return infoCard;
	}

	private static final NavigableMap<Integer, Integer> textColors = new TreeMap<>();

	static {
		textColors.put(132, R.color.white);
		textColors.put(265, R.color.yellow);
		textColors.put(529, R.color.orange);
	}

	private String formatNumber(String format, double number) {
		return String.format(Locale.ENGLISH, format, number);
	}

	public void refresh(LogEntry withEntry) {
		if (withEntry == null) {
			updateGPSStatusButtonAppearance(false);
			updateCPMStatusButtonAppearance(false);
			cpmValueTextView.setText("");
			cp5sValueTextView.setText("");
			usvhValueTextView.setText("");
			longitudeValueTextView.setText("");
			latitudeValueTextView.setText("");
			altitudeValuetextView.setText("");
			CPM_ind_value_TextView.setText("");
			GPS_ind_value_TextView.setText("");
		} else {
			Map.Entry<Integer, Integer> e = textColors.ceilingEntry(withEntry.getCountPerMinute());
			int color = ContextCompat.getColor(monitor, e == null ? R.color.red : e.getValue());

			cpmValueTextView.setTextColor(color);
			cp5sValueTextView.setTextColor(color);
			usvhValueTextView.setTextColor(color);
			String cpm = withEntry.getCountPerMinute() < 1000 ? String.valueOf(withEntry.getCountPerMinute()) : "OFL";
			cpmValueTextView.setText(cpm);
			String cp5s = withEntry.getCountPer5s() < 1000 ? String.valueOf(withEntry.getCountPer5s()) : "OFL";
			cp5sValueTextView.setText(cp5s);
			usvhValueTextView.setText(formatNumber("%.2f", withEntry.getusvh()));
			longitudeValueTextView.setText(formatNumber("%.4f", withEntry.getLongitude()));
			latitudeValueTextView.setText(formatNumber("%.4f", withEntry.getLatitude()));
			altitudeValuetextView.setText(String.valueOf(withEntry.getAltitude()));
			updateCPMStatusButtonAppearance(withEntry.isCPMValid());
			if (withEntry.isCPMValid()) {
				CPM_ind_value_TextView.setText("OK");
			} else {
				CPM_ind_value_TextView.setText("NOK");
			}
			updateGPSStatusButtonAppearance(withEntry.isGPSValid());
			if (withEntry.isGPSValid()) {
				GPS_ind_value_TextView.setText("OK" + " (" + withEntry.getSatelliteCountUsed() + ":" + withEntry.getPositionDilutionofPrecision() + ")");
			} else {
				GPS_ind_value_TextView.setText("NOK (-:-)");
			}
		}
	}

	private void updateGPSStatusButtonAppearance(boolean gpsStatus) {
		GPSStatusButton.setBackgroundResource(gpsStatus ? R.drawable.button_green : R.drawable.button_red);
	}

	private void updateCPMStatusButtonAppearance(boolean cpmStatus) {
		CPMStatusButton.setBackgroundResource(cpmStatus ? R.drawable.button_green : R.drawable.button_red);
	}
}