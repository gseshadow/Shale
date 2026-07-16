package com.shale.ui.util;

import java.util.Locale;

import javafx.scene.paint.Color;

public final class ColorUtil {

	private ColorUtil() {
	}

	public static String toCssBackgroundColor(String storedColor) {
		String normalized = normalizeStoredColor(storedColor);
		return normalized == null ? "rgba(0,0,0,0.06)" : "#" + normalized;
	}

	public static String toCssBackgroundColorOrNull(String storedColor) {
		String normalized = normalizeStoredColor(storedColor);
		return normalized == null ? null : "#" + normalized;
	}

	public static String normalizeStoredColor(String storedColor) {
		if (storedColor == null)
			return null;

		String normalized = storedColor.trim();
		if (normalized.isEmpty())
			return null;

		if (normalized.startsWith("#"))
			normalized = normalized.substring(1);
		if (normalized.startsWith("0x") || normalized.startsWith("0X"))
			normalized = normalized.substring(2);

		if (normalized.matches("(?i)[0-9a-f]{6}"))
			return (normalized + "FF").toUpperCase(Locale.ROOT);
		if (normalized.matches("(?i)[0-9a-f]{8}"))
			return normalized.toUpperCase(Locale.ROOT);

		return null;
	}

	public static Color toFxColor(String storedColor) {
		String normalized = normalizeStoredColor(storedColor);
		if (normalized == null)
			return Color.WHITE;

		int red = Integer.parseInt(normalized.substring(0, 2), 16);
		int green = Integer.parseInt(normalized.substring(2, 4), 16);
		int blue = Integer.parseInt(normalized.substring(4, 6), 16);
		int alpha = Integer.parseInt(normalized.substring(6, 8), 16);
		return Color.rgb(red, green, blue, alpha / 255.0);
	}

	public static String toStoredColor(Color color) {
		if (color == null)
			return null;
		return toHex(color.getRed()) + toHex(color.getGreen()) + toHex(color.getBlue()) + toHex(color.getOpacity());
	}

	public static String toDisplayValue(String storedColor) {
		String normalized = normalizeStoredColor(storedColor);
		return normalized == null ? "—" : "#" + normalized;
	}

	public static String readableTextColor(String storedColor) {
		return readableTextColor(toFxColor(storedColor));
	}

	public static String toCssRgba(String storedColor, double opacity) {
		Color color = toFxColor(storedColor);
		double resolvedOpacity = Math.max(0.0, Math.min(1.0, opacity)) * color.getOpacity();
		return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
				(int) Math.round(color.getRed() * 255.0),
				(int) Math.round(color.getGreen() * 255.0),
				(int) Math.round(color.getBlue() * 255.0),
				resolvedOpacity);
	}

	public static String readableTextColor(Color color) {
		Color resolved = color == null ? Color.WHITE : color;
		double luminance = 0.2126 * linearized(resolved.getRed())
				+ 0.7152 * linearized(resolved.getGreen())
				+ 0.0722 * linearized(resolved.getBlue());
		return luminance > 0.48 ? "#172033" : "white";
	}

	private static double linearized(double channel) {
		return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
	}

	private static String toHex(double channel) {
		int value = (int) Math.round(channel * 255.0);
		value = Math.max(0, Math.min(255, value));
		return String.format(Locale.ROOT, "%02X", value);
	}
}
