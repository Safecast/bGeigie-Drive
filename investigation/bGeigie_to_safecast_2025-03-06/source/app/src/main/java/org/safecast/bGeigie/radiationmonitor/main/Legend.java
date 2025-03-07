package org.safecast.bGeigie.radiationmonitor.main;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class Legend extends View {
	private final Paint paint = new Paint();
	private final Paint textPaint;
	private static final int LABEL_COUNT = 10; // Number of labels to display

	public Legend(Context context, AttributeSet attrs) {
		super(context, attrs);

		// Paint for the labels
		textPaint = new Paint();
		textPaint.setColor(Color.BLACK);  // Black color for visibility
		textPaint.setTextSize(30);       // Text size
		textPaint.setAntiAlias(true);
		textPaint.setTextAlign(Paint.Align.LEFT);  // Align left
		rescale(gradientInitialMax);
	}

	public double getMaxValue() {
		return legend.lastKey();
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		super.onDraw(canvas);
		canvas.drawRect(0, 0, getWidth(), getHeight(), paint);  // Draw the gradient bar
		drawLabels(canvas);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		paint.setShader(new LinearGradient(
				0, getHeight(), 0, 0,  // Start at bottom → top
				generateInterpolatedColors(),
				null, Shader.TileMode.CLAMP));
	}

	private void drawLabels(Canvas canvas) {
		int width = getWidth(), height = getHeight();

		// Calculate spacing between labels
		float spacing = (float) height / (LABEL_COUNT - 1);  // Spacing between labels
		double valueStep = getMaxValue() / (LABEL_COUNT - 1);  // Step size for the labels (from 0 to maxValue)

		// Paint for the label outline
		Paint outlinePaint = new Paint();
		outlinePaint.setColor(Color.WHITE); // Set the outline color to white (or any visible color)
		outlinePaint.setStyle(Paint.Style.STROKE); // Stroke style to make the outline
		outlinePaint.setStrokeWidth(3); // Set the thickness of the outline
		outlinePaint.setAntiAlias(true);
		outlinePaint.setTextSize(textPaint.getTextSize()); // Ensure the outline has the same text size

		// Draw each label at the calculated positions
		for (int i = 0; i < LABEL_COUNT; i++) {
			// Invert the Y position to start from bottom to top (we reverse the order here)
			float y = (LABEL_COUNT - 1 - i) * spacing;  // Reversed order

			// Format the value with one decimal place
			String text = String.format(Locale.ENGLISH, "%.1f", valueStep * i);  // 0 is at the bottom, max value at top

			// Get the bounds of the text
			android.graphics.Rect textBounds = new android.graphics.Rect();
			textPaint.getTextBounds(text, 0, text.length(), textBounds);

			// Calculate the text height
			int textHeight = textBounds.height();

			// Adjust the y coordinate to position the text correctly
			float adjustedY = y + textHeight / 2f;  // Position text vertically in the middle of each segment

			// Ensure top label (100) fits inside the bar
			if (i == LABEL_COUNT - 1) {
				// The top label should not exceed the top boundary of the bar
				adjustedY = Math.min(adjustedY, textHeight) + 10;  // Avoid going above the top boundary
			}

			// Adjustments for the last label (bottom of the bar)
			if (i == 0) {
				adjustedY = y - textHeight / 2f;  // Adjust the bottom label so it doesn't overflow
				// Ensure bottom label fits within the bar
				adjustedY = Math.max(adjustedY, height - textHeight); // Avoid going below the bottom boundary
			}

			// Set the x-coordinate to the center of the bar
			float adjustedX = width / 2f - textBounds.width() / 2f;  // Center the text horizontally inside the bar

			// Draw the text outline first (white outline)
			canvas.drawText(text, adjustedX, adjustedY, outlinePaint);

			// Draw the filled text inside the bar
			canvas.drawText(text, adjustedX, adjustedY, textPaint);  // Position text inside the bar
		}
	}

	private static final int INTERPOLATION_STEPS = 4;

	// Generate a gradient of interpolated colors based on the base colors
	private int[] generateInterpolatedColors() {
		List<Integer> colors = new ArrayList<>();

		for (int i = 0; i < gradient.size() - 1; i++) {
			int left = gradient.get(i);
			int right = gradient.get(i + 1);

			// Interpolate between each pair of colors
			for (int j = 0; j <= INTERPOLATION_STEPS; j++) {
				float scale = (float) j / (INTERPOLATION_STEPS + 1);
				colors.add(mix(left, right, scale).toArgb());
			}
		}

		colors.add(gradient.get(gradient.size() - 1));  // Add last color (no interpolation)
		return colors.stream().mapToInt(i -> i).toArray();
	}

	private static final List<Integer> gradient = List.of(
			0xff160e3d,
			0xff190f51,
			0xff130ad9,
			0xff304eff,
			0xff36bcff,
			0xff4bedff,
			0xffc094ff,
			0xfffa24ff,
			0xffff038b,
			0xffff0323,
			0xffff5c03,
			0xffffc503,
			0xffffff1c,
			0xffffff93
	);
	private final NavigableMap<Double, Color> legend = new TreeMap<>();
	private static final double gradientInitialMax = 1;

	public void rescale(double max) {
		legend.clear();

		// Generate legend
		double step = (max * 1.10) / (gradient.size() - 1);
		double key = 0;
		for (int i = 0; i < gradient.size(); i++, key += step) {
			legend.put(key, Color.valueOf(gradient.get(i)));
		}

		invalidate(); // Causes a redraw (calls onDraw) to redraw labels
	}

	public Color getMarkerColor(double value) {
		Map.Entry<Double, Color> ceil = legend.ceilingEntry(value);
		Map.Entry<Double, Color> floor = legend.floorEntry(value);
		if (ceil == null) {
			rescale(value);
			return getMarkerColor(value);
		}

		float scale;
		if (ceil.equals(floor)) {
			scale = 0;
		} else {
			scale = (float) ((value - floor.getKey()) / (ceil.getKey() - floor.getKey()));
		}

		return mix(floor.getValue(), ceil.getValue(), scale);
	}

	private Color mix(int left, int right, float scale) {
		return mix(Color.valueOf(left), Color.valueOf(right), scale);
	}

	private Color mix(Color left, Color right, float scale /* scale [0, 1] where 0 is left and 1 is right */) {
		float red = (left.red() + scale * (right.red() - left.red()));
		float green = (left.green() + scale * (right.green() - left.green()));
		float blue = (left.blue() + scale * (right.blue() - left.blue()));
		float alpha = (left.alpha() + scale * (right.alpha() - left.alpha()));
		return Color.valueOf(red, green, blue, alpha);
	}
}
