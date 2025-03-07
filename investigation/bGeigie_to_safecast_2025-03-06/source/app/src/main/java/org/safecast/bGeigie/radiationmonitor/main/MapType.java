package org.safecast.bGeigie.radiationmonitor.main;

import org.safecast.bGeigie.R;

enum MapType {
	DEFAULT(R.id.map_radio_option_1),
	OPEN_TOPO(R.id.map_radio_option_2);

	private final int id;

	MapType(int id) {
		this.id = id;
	}

	int id() {
		return id;
	}

	static MapType forId(int id) {
		for (MapType type : values()) {
			if (type.id == id) return type;
		}

		throw new IllegalArgumentException("Unknown id: " + id);
	}
}