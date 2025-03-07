package org.safecast.bGeigie.radiationmonitor.log;

import org.safecast.bGeigie.radiationmonitor.main.Monitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class Log extends ArrayList<LogEntry> {

	public Log() {
	}

	public Log(List<LogEntry> entries) {
		super(entries);
	}

	public byte[] toByteArray() {
		return (Monitor.driveFileHeaderData + stream().map(LogEntry::serialize).collect(Collectors.joining("\n"))).getBytes();
	}

	public static Log parse(File logFile) throws FileNotFoundException {
		return parse(new FileInputStream(logFile));
	}

	public static Log parse(InputStream stream) {
		Log log = new Log();
		new BufferedReader(new InputStreamReader(stream)).lines()
				.filter(s -> s.startsWith(LogEntry.PREFIX))
				.map(LogEntry::parse)
				.forEach(log::add);
		return log;
	}
}
