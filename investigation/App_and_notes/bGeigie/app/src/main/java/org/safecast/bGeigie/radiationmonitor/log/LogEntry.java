package org.safecast.bGeigie.radiationmonitor.log;

import org.osmdroid.util.GeoPoint;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogEntry implements Serializable {
	public static final String PREFIX = "$BNRDD";
	public static final LogEntry EMPTY = new LogEntry(0, LocalDate.now(), 0, 0, 0, false, new GeoPoint(0d, 0d), 0, false, 0, 0, 0);
	private static final DateTimeFormatter dateFormat = DateTimeFormatter.ISO_DATE_TIME;
	private final int deviceId;
	private final LocalDate date;
	private final int cpm, cp5s;
	private final int totalPulses;
	private final boolean isCPMValid;
	private final GeoPoint location;
	private final double altitude;
	private final boolean isGPSValid;
	private final int satellites;
	private final int dilution;
	private final int checksum;

	public LogEntry(int deviceId, LocalDate date, int cpm, int cp5s, int totalPulses, boolean isCPMValid, GeoPoint location, double altitude, boolean isGPSValid, int satellites, int dilution, int checksum) {
		this.deviceId = deviceId;
		this.date = date;
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

	public static LogEntry parse(String data) {
		String[] parts = data.split(",");

		// Check initials
		if (parts.length != 15) throw new IllegalArgumentException("invalid data");
		expectEquals(PREFIX, parts[0]);

		// Device and date
		int deviceId = Integer.parseInt(parts[1]);
		LocalDate date = LocalDate.from(ZonedDateTime.parse(parts[2].replace('‑', '-'), dateFormat));

		// Recorded data
		int cpm = Integer.parseInt(parts[3]);
		int cp5s = Integer.parseInt(parts[4]);
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

		int dilution = Integer.parseInt(parts[14].split("\\*")[0]);
		int checksum = Integer.parseInt(expectMatches("[\\dA-Z]+", parts[14].split("\\*")[1]), 16);

		return new LogEntry(deviceId, date, cpm, cp5s, totalPulses, isCPMValid, location, altitude, isGPSValid, satellites, dilution, checksum);
	}

	private static final Pattern positionPattern = Pattern.compile("(\\d{2})(\\d{2}\\.\\d{4})");

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

	public LocalDate getDate() {
		return date;
	}

	public int getCountPerMinute() {
		return cpm;
	}

	public double getusvh() {
		return cpm * 0.00378;
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

	public int getPositionDilutionofPrecision() {
		return dilution;
	}

	public int getChecksum() {
		return checksum;
	}

	@Override
	public String toString() {
		return "LogEntry{" +
				"deviceId=" + deviceId +
				", date=" + date +
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
				"," + date.toString() +
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
