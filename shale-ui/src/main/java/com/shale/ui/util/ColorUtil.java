package com.shale.ui.util;

public final class ColorUtil {

	private ColorUtil() {
	}

	public static String toCssBackgroundColor(String argbHex) {
		if (argbHex == null)
			return "white";

		String normalized = argbHex.trim();
		if (normalized.isEmpty())
			return "white";

		if (normalized.startsWith("#"))
			normalized = normalized.substring(1);
		if (normalized.startsWith("0x") || normalized.startsWith("0X"))
			normalized = normalized.substring(2);

		if (!normalized.matches("(?i)[0-9a-f]{8}"))
			return "white";

		String rr = normalized.substring(0, 2);
		String gg = normalized.substring(2, 4);
		String bb = normalized.substring(4, 6);
		String aa = normalized.substring(6, 8);

		return "#" + rr + gg + bb + aa;
	}
}
