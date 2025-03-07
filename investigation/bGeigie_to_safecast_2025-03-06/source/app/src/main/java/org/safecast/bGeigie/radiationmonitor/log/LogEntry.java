package org.safecast.bGeigie.radiationmonitor.log;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.Serializable;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogEntry implements Serializable {
	public static final String PREFIX = "$BNRDD";
	public static final LogEntry EMPTY = new LogEntry(0, ZonedDateTime.now(ZonedDateTime.now().getZone()), 0, 0, 0, false, new GeoPoint(0d, 0d), 0, false, 0, 0, 0);
	private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC")); //Formatter for input
	private static final DateTimeFormatter outputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"); // Formatter for output
	private final int deviceId;
	private final ZonedDateTime timestamp;
	private final int cpm, cp5s;
	private final int totalPulses;
	private final boolean isCPMValid;
	private final GeoPoint location;
	private final double altitude;
	private final boolean isGPSValid;
	private final int satellites;
	private final int dilution;
	private final int checksum;

	public LogEntry(int deviceId, ZonedDateTime timestamp, int cpm, int cp5s, int totalPulses, boolean isCPMValid, GeoPoint location, double altitude, boolean isGPSValid, int satellites, int dilution, int checksum) {
		this.deviceId = deviceId;
		this.timestamp = timestamp;
		this.cpm = cpm;
		this.cp5s = cp5s;
		this.totalPulses = totalPulses;
		this.isCPMValid = isCPMValid;
		this.location = location;
		this.altitude = altitude;
		this.isGPSValid = isGPSValid;
		this.satellites = satellites;
		this.dilution = dilution;
		this.checksum = checksum;
	}

	private static final boolean upscale_count = false;
	private static double growth = 1;

	public static LogEntry parse(String data) {
		String[] parts = data.split(",");

		// Check initials
		if (parts.length != 15) throw new IllegalArgumentException("invalid data");
		expectEquals(PREFIX, parts[0]);

		// Device
		int deviceId = Integer.parseInt(parts[1]);

		//Date and time
		String timeString = parts[2].replace('‑', '-');
		ZonedDateTime timestamp = ZonedDateTime.parse(timeString, dateFormat);

		// Recorded data
		int cpm = (int) (Integer.parseInt(parts[3]) * (upscale_count ? growth : 1));
		int cp5s = (int) (Integer.parseInt(parts[4]) * (upscale_count ? growth : 1));
		growth *= 2;
		int totalPulses = Integer.parseInt(parts[5]);
		boolean isCPMValid = expectMatches("[AV]", parts[6]).equals("A");

		// Geo positioning
		double latitude = decimalsToDegrees(parts[7]);
		if (expectMatches("[NS]", parts[8]).equals("S")) {
			latitude = -latitude;
		}
		double longitude = decimalsToDegrees(parts[9]);
		if (expectMatches("[EW]", parts[10]).equals("W")) {
			longitude = -longitude;
		}
		GeoPoint location = new GeoPoint(latitude, longitude);

		double altitude = Double.parseDouble(parts[11]);
		boolean isGPSValid = expectMatches("[AV]", parts[12]).equals("A");
		int satellites = Integer.parseInt(parts[13]);

		String[] astrSplit = parts[14].split("\\*");
		int dilution = Integer.parseInt(astrSplit[0]);
		int checksum = astrSplit.length == 1 ? -1 : Integer.parseInt(expectMatches("[\\da-zA-Z]+", astrSplit[1]), 16);

		return new LogEntry(deviceId, timestamp, cpm, cp5s, totalPulses, isCPMValid, location, altitude, isGPSValid, satellites, dilution, checksum);
	}

	private static final Pattern positionPattern = Pattern.compile("(\\d{2,3})(\\d{2}\\.\\d{4})");

	private static double decimalsToDegrees(String coordinate) {
		Matcher m = positionPattern.matcher(coordinate);
		if (!m.matches()) {
			throw new IllegalArgumentException("invalid coordinate: " + coordinate);
		}

		// Split degrees and minutes
		int degrees = Integer.parseInt(m.group(1));         // Get degree part
		double minutes = Double.parseDouble(m.group(2));    // Get minute part
		return degrees + (minutes / 60);
	}

	private static String expectEquals(String expected, String data) {
		if (data.equals(expected)) return data;

		throw new IllegalArgumentException("expected token: '" + expected + "' but got '" + data + "'");
	}

	private static String expectMatches(String expectedRegex, String data) {
		if (data.matches(expectedRegex)) return data;

		throw new IllegalArgumentException("expected token matching: '" + expectedRegex + "' but got '" + data + "'");
	}

	public int getDeviceId() {
		return deviceId;
	}

	public ZonedDateTime getDate() {
		return timestamp;
	}

	public int getCountPerMinute() {
		return cpm;
	}

	public double getusvh() {
		return (double) cpm / 340;
	}

	public int getCountPer5s() {
		return cp5s;
	}

	public int getTotalPulses() {
		return totalPulses;
	}

	public boolean isCPMValid() {
		return isCPMValid;
	}

	public GeoPoint getLocation() {
		return location;
	}

	public double getLongitude() {
		return getLocation().getLongitude();
	}

	public double getLatitude() {
		return getLocation().getLatitude();
	}

	public double getAltitude() {
		return altitude;
	}

	public boolean isGPSValid() {
		return isGPSValid;
	}

	public int getSatelliteCountUsed() {
		return satellites;
	}

	public int getPositionDilutionOfPrecision() {
		return dilution;
	}

	public int getChecksum() {
		return checksum;
	}

	@Override
	public String toString() {
		return "LogEntry{" +
				"deviceId=" + deviceId +
				", timestamp=" + timestamp.format(outputFormat) +
				", cpm=" + cpm +
				", cp5s=" + cp5s +
				", totalPulses=" + totalPulses +
				", isCPMValid=" + isCPMValid +
				", location=" + location +
				", altitude=" + altitude +
				", isGPSValid=" + isGPSValid +
				", satellites=" + satellites +
				", dilution=" + dilution +
				", checksum=" + checksum +
				'}';
	}

	public String serialize() {
		return PREFIX +
				"," + deviceId +
				"," + outputFormat.format(timestamp) +
				"," + cpm +
				"," + cp5s +
				"," + totalPulses +
				"," + boolToChar(isCPMValid) +
				"," + locationToString(location) +
				"," + altitude +
				"," + boolToChar(isGPSValid) +
				"," + satellites +
				"," + dilution +
				"*" + Integer.toHexString(checksum);
	}

	public JSONObject toSafeCastJsonEntry() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("captured_at", getDate());
		json.put("longitude", getLongitude());
		json.put("latitude", getLatitude());
		json.put("device_id", getDeviceId());
		json.put("value", getCountPerMinute());
		json.put("unit", "cpm");
		json.put("height", getAltitude());
		return json;
	}

	private String locationToString(GeoPoint location) {
		double longitude = location.getLongitude();
		double latitude = location.getLatitude();

		// Latitude conversion (ddmm.mmmm)
		int latDegrees = (int) Math.abs(latitude);
		double latMinutes = (Math.abs(latitude) - latDegrees) * 60;
		char latDirection = (latitude >= 0) ? 'N' : 'S';

		// Longitude conversion (dddmm.mmmm)
		int lonDegrees = (int) Math.abs(longitude);
		double lonMinutes = (Math.abs(longitude) - lonDegrees) * 60;
		char lonDirection = (longitude >= 0) ? 'E' : 'W';

		// Format the result in the required format
		return String.format(Locale.ENGLISH, "%02d%07.4f,%c,%03d%07.4f,%c",
				latDegrees, latMinutes, latDirection, lonDegrees, lonMinutes, lonDirection);
	}

	private char boolToChar(boolean bool) {
		return bool ? 'A' : 'V';
	}

}
