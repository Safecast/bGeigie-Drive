package org.safecast.bGeigie.radiationmonitor.main;

import org.safecast.bGeigie.R;

enum UploadType {
	DEFAULT(R.id.upload_radio_option_1),
	REAL_TIME(R.id.upload_radio_option_2),
	LOG_FILE(R.id.upload_radio_option_3);

	private final int id;

	UploadType(int id) {
		this.id = id;
	}

	int id() {
		return id;
	}

	static UploadType forId(int id) {
		for (UploadType type : values()) {
			if (type.id == id) return type;
		}

		throw new IllegalArgumentException("Unknown id: " + id);
	}
}