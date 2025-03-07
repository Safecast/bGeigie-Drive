package org.safecast.bGeigie.radiationmonitor.main;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import java.util.Random;

class ClickSoundGenerator {

	private static final String TAG = "ClickSoundGenerator";
	private static final int SAMPLE_RATE = 8000;
	private static final Random random = new Random();
	private static final float volume = 1.0f; // Volume control (0.0f to 1.0f)
	private static final int click_duration = 10; // Click duration in ms
	private static final int totalTime = 5000; // Interval between updates (roughly 5 s)
	private static final int numSamples = SAMPLE_RATE * click_duration / 1000;
	private static final byte[] generatedSnd = new byte[2 * numSamples];
	private static final AudioTrack audioTrack;

	static {
		generateClickWave(); // Pre-generate click waveform
		AudioAttributes audioAttributes = new AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_MEDIA)
				.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
				.build();

		AudioFormat audioFormat = new AudioFormat.Builder()
				.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
				.setSampleRate(SAMPLE_RATE)
				.setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
				.build();
		int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
		audioTrack = new AudioTrack.Builder()
				.setAudioAttributes(audioAttributes)
				.setAudioFormat(audioFormat)
				.setBufferSizeInBytes(Math.max(minBufferSize, generatedSnd.length))
				.setTransferMode(AudioTrack.MODE_STATIC)
				.build();
		audioTrack.write(generatedSnd, 0, generatedSnd.length);
	}

	private static void generateClickWave() {
		for (int i = 0; i < numSamples; i++) {
			double value = Math.exp(-i / (numSamples / 3.0)); // Exponential decay
			short val = (short) (value * 32767 * volume);
			generatedSnd[2 * i] = (byte) (val & 0x00ff);
			generatedSnd[2 * i + 1] = (byte) ((val & 0xff00) >>> 8);
		}
	}

	private static final ClickSoundGenerator inst = new ClickSoundGenerator();

	public static ClickSoundGenerator getInstance() {
		return inst;
	}

	private int clickRate;
	private boolean isRunning = true; // Track whether we should keep playing

	private ClickSoundGenerator() {
		new Thread(() -> {
			while (isRunning) {
				if (clickRate <= 0) continue;

				int[] delays = new int[clickRate];

				// Generate x-1 random timestamps
				for (int i = 0; i < clickRate - 1; i++) {
					delays[i] = random.nextInt(totalTime); // 0 to 4999 ms
				}
				delays[clickRate - 1] = totalTime; // Ensure the last call is exactly at 5s

				// Sort timestamps
				java.util.Arrays.sort(delays);

				long startTime = System.currentTimeMillis();
				for (int delay : delays) {
					try {
						long waitTime = startTime + delay - System.currentTimeMillis();
						if (waitTime > 0) {
							Thread.sleep(waitTime);
						}

						if (clickRate <= 0) break;

						playClick();
					} catch (InterruptedException e) {
						Log.e(TAG, "Click loop interrupted", e);
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}).start();
	}

	public void setClickRate(int clickRate) {
		this.clickRate = Math.min(clickRate, 999);
	}

	public void stop() {
		isRunning = false;
		audioTrack.release();
	}

	private void playClick() {
		if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
			audioTrack.stop();
		}
		audioTrack.setPlaybackHeadPosition(0);
		audioTrack.play();
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {
			Log.e(TAG, "sleep interrupted", e);
		}
	}
}