package org.safecast.bGeigie.radiationmonitor.main;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import org.safecast.bGeigie.R;

import java.util.LinkedList;
import java.util.Queue;

class ToastManager {
	private static final int DEFAULT_TOAST_DELAY = 3500; // 3.5 seconds (default)
	private final Queue<ToastData> toastQueue = new LinkedList<>();
	private final Handler handler = new Handler(Looper.getMainLooper());
	private boolean isShowingToast = false;
	private final Monitor monitor;

	public ToastManager(Monitor monitor) {
		this.monitor = monitor;
	}

	public void showToast(String message, int color) {
		showToast(message, color, DEFAULT_TOAST_DELAY);
	}

	public void showToast(String message, int color, int duration_ms) {
		// Duration in ms. Parameter is optional and overrides the default 3500 ms
		ToastData toastData = new ToastData(message, color, duration_ms);
		toastQueue.add(toastData);
		processQueue();
	}

	private void processQueue() {
		if (isShowingToast || toastQueue.isEmpty()) {
			return;
		}

		isShowingToast = true;
		ToastData nextToast = toastQueue.remove();
		toast(nextToast.message(), nextToast.color(), nextToast.duration());
		handler.postDelayed(() -> {
			isShowingToast = false;
			processQueue();
		}, nextToast.duration());
	}

	private record ToastData(String message, int color, int duration) {
	}

	private void toast(String message, int color, int duration) {
		monitor.runOnUiThread(() -> {
			CardView toastView = monitor.findViewById(R.id.custom_toast);
			ImageButton toastIcon = monitor.findViewById(R.id.exclamationButton);
			TextView toastMessage = monitor.findViewById(R.id.toast_Message_TextView);
			toastIcon.setColorFilter(color);
			toastMessage.setText(message);
			toastView.setVisibility(View.VISIBLE);
			new Handler(Looper.getMainLooper()).postDelayed(() -> {
				toastView.setVisibility(View.GONE);
			}, duration); // Hide after duration ms
		});
	}
}