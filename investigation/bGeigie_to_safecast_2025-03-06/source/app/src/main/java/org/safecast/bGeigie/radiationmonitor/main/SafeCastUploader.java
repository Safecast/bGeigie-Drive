package org.safecast.bGeigie.radiationmonitor.main;

import android.graphics.Color;
import android.util.Log;

import org.json.JSONException;
import org.safecast.bGeigie.radiationmonitor.log.LogEntry;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

class SafeCastUploader {
	// Necessary to communicate with Monitor
	private final Monitor monitor;

	// Tag for debugging
	private static final String TAG = "SafeCastUploader";

	// Stuff for the upload
	private static final String API_ENDPOINT_MEASUREMENT = "https://tt.safecast.org/measurements.json";
	private static final String API_ENDPOINT_LOG_FILE = "https://api.safecast.org/bgeigie_imports.json";
	private static final String userAgent = "bGeigie";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private final OkHttpClient client;

	public SafeCastUploader(Monitor monitor) {
		this.monitor = monitor;
		HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> Log.d(TAG, message));
		logging.setLevel(HttpLoggingInterceptor.Level.BODY);
		client = new OkHttpClient.Builder()
				.addInterceptor(logging)
				.build();
	}


	public void uploadEntry(LogEntry entry) {
		// Build the URL with the API key as a query parameter
		String apiUrl = API_ENDPOINT_MEASUREMENT + "?api_key=" + monitor.getApiKey();
		RequestBody body = null;
		try {
			body = RequestBody.create(entry.toSafeCastJsonEntry().toString(), JSON);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}

		Request request = new Request.Builder()
				.url(apiUrl)
				.header("Authorization", "Bearer " + monitor.getApiKey())
				.header("User-Agent", userAgent) // Add the User-Agent header
				.header("Content-Type", JSON.toString())
				.post(body)
				.build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				Log.e(TAG, "Upload of log entry failed", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				String responseBody = response.body() != null ? response.body().string() : "null";
				if (response.isSuccessful()) {
					Log.d(TAG, "Upload successful: " + responseBody);
				} else {
					Log.e(TAG, "Upload of log entry failed: " + response.code() + " " + response.message() + " Response: " + responseBody);
				}
				Objects.requireNonNull(response.body()).close();
			}
		});
	}

	public void uploadLogFile(org.safecast.bGeigie.radiationmonitor.log.Log log, String fileName) {
		// Build the URL with the API key as a query parameter
		String apiUrl = API_ENDPOINT_LOG_FILE + "?api_key=" + monitor.getApiKey();

		// Create a RequestBody from the ByteArrayOutputStream's byte array
		RequestBody fileBody = RequestBody.create(log.toByteArray(), MediaType.parse("application/octet-stream"));

		// Create the file part
		MultipartBody.Part filePart = MultipartBody.Part.createFormData("bgeigie_import[source]", fileName, fileBody);

		// Build the multipart request body
		MultipartBody requestBody = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addPart(filePart)
				.build();
		Request request = new Request.Builder()
				.url(apiUrl)
				.header("Authorization", "Bearer " + monitor.getApiKey())
				.header("User-Agent", userAgent)
				.post(requestBody)
				.build();

		client.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				Log.e(TAG, "Upload failed", e);
				monitor.getToastManager().showToast("Upload of log file failed: " + e.getMessage(), Color.RED);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				String responseBody = response.body() != null ? response.body().string() : "null";
				if (response.isSuccessful()) {
					Log.d(TAG, "Upload successful: " + responseBody);
					monitor.getToastManager().showToast("Log file successfully uploaded!!", Color.GREEN);
				} else {
					Log.e(TAG, "Upload failed: " + response.code() + " " + response.message() + " Response: " + responseBody);
					monitor.getToastManager().showToast("Upload of log file failed: " + response.code(), Color.RED);
				}
				Objects.requireNonNull(response.body()).close();
			}
		});
	}

}
