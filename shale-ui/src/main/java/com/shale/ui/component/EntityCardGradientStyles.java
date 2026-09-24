package com.shale.ui.component;

import com.shale.ui.util.ColorUtil;

import javafx.scene.paint.Color;

final class EntityCardGradientStyles {

	private EntityCardGradientStyles() {
	}

	static String caseStrengthGradient(String cssColor, boolean embeddedMini) {
	    if (embeddedMini) {
	        return "linear-gradient(to right, "
	                + "-shale-color-elevated-surface 0%, "
	                + "-shale-color-elevated-surface 76%, "
	                + cssColor + " 100%)";
	    }

	    return "linear-gradient(to right, "
	            + "-shale-color-elevated-surface 0%, "
	            + "-shale-color-elevated-surface 30%, "
	            + cssColor + " 100%)";
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
