package com.shale.ui.component;

import com.shale.ui.util.ColorUtil;

import javafx.scene.paint.Color;

final class EntityCardGradientStyles {

	private EntityCardGradientStyles() {
	}

	static String caseStrengthGradient(String cssColor, boolean embeddedMini) {
	    if (embeddedMini) {
	        return "-shale-color-elevated-surface, "
	                + "linear-gradient(to right, "
	                + ColorUtil.toCssRgba(cssColor, 0.48) + " 0%, "
	                + ColorUtil.toCssRgba(cssColor, 0.44) + " 70%, "
	                + ColorUtil.toCssRgba(cssColor, 0.20) + " 92%, "
	                + ColorUtil.toCssRgba(cssColor, 0.00) + " 100%)";
	    }

	    return "-shale-color-elevated-surface, "
	            + "linear-gradient(to right, "
	            + ColorUtil.toCssRgba(cssColor, 0.58) + " 0%, "
	            + ColorUtil.toCssRgba(cssColor, 0.54) + " 65%, "
	            + ColorUtil.toCssRgba(cssColor, 0.34) + " 84%, "
	            + ColorUtil.toCssRgba(cssColor, 0.14) + " 96%, "
	            + ColorUtil.toCssRgba(cssColor, 0.00) + " 100%)";
	}

	private static String tintStop(String cssColor, double weight) {
		try {
			Color sourceColor = ColorUtil.toFxColor(cssColor);
			Color tint = Color.WHITE.interpolate(sourceColor, weight);
			return "rgba(%d, %d, %d, %.3f)".formatted(
					Math.round(tint.getRed() * 255),
					Math.round(tint.getGreen() * 255),
					Math.round(tint.getBlue() * 255),
					tint.getOpacity());
		} catch (Exception ignored) {
			return "#F8FAFC";
		}
	}
}
