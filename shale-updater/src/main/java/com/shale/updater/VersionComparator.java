package com.shale.updater;

public final class VersionComparator {

	private VersionComparator() {
	}

	public static int compare(String a, String b) {
		ParsedVersion av = parse(a);
		ParsedVersion bv = parse(b);

		int len = Math.max(av.core().length, bv.core().length);
		for (int i = 0; i < len; i++) {
			int ai = i < av.core().length ? av.core()[i] : 0;
			int bi = i < bv.core().length ? bv.core()[i] : 0;

			if (ai != bi) {
				return Integer.compare(ai, bi);
			}
		}

		if (av.preRelease().isEmpty() && bv.preRelease().isEmpty()) {
			return 0;
		}
		if (av.preRelease().isEmpty()) {
			return 1;
		}
		if (bv.preRelease().isEmpty()) {
			return -1;
		}

		int preReleaseLength = Math.max(av.preRelease().size(), bv.preRelease().size());
		for (int i = 0; i < preReleaseLength; i++) {
			if (i >= av.preRelease().size()) {
				return -1;
			}
			if (i >= bv.preRelease().size()) {
				return 1;
			}

			int preReleaseComparison = compareIdentifier(av.preRelease().get(i), bv.preRelease().get(i));
			if (preReleaseComparison != 0) {
				return preReleaseComparison;
			}
		}

		return 0;
	}

	private static ParsedVersion parse(String version) {
		if (version == null || version.isBlank()) {
			return new ParsedVersion(new int[] {0}, java.util.List.of());
		}

		String normalized = version.trim();
		if (normalized.startsWith("v") || normalized.startsWith("V")) {
			normalized = normalized.substring(1);
		}

		int buildMetadataSeparator = normalized.indexOf('+');
		if (buildMetadataSeparator >= 0) {
			normalized = normalized.substring(0, buildMetadataSeparator);
		}

		String preReleasePart = "";
		int preReleaseSeparator = normalized.indexOf('-');
		if (preReleaseSeparator >= 0) {
			preReleasePart = normalized.substring(preReleaseSeparator + 1);
			normalized = normalized.substring(0, preReleaseSeparator);
		}

		String[] parts = normalized.split("\\.");
		int[] values = new int[Math.max(parts.length, 1)];

		for (int i = 0; i < parts.length; i++) {
			values[i] = parseNumericPart(parts[i]);
		}

		java.util.List<String> preReleaseIdentifiers = preReleasePart.isBlank()
				? java.util.List.of()
				: java.util.Arrays.stream(preReleasePart.split("\\."))
						.map(String::trim)
						.filter(part -> !part.isEmpty())
						.toList();

		return new ParsedVersion(values, preReleaseIdentifiers);
	}

	private static int parseNumericPart(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}

		String trimmed = value.trim();
		int end = 0;
		while (end < trimmed.length() && Character.isDigit(trimmed.charAt(end))) {
			end++;
		}

		if (end == 0) {
			return 0;
		}

		try {
			return Integer.parseInt(trimmed.substring(0, end));
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private static int compareIdentifier(String a, String b) {
		boolean aNumeric = isNumeric(a);
		boolean bNumeric = isNumeric(b);

		if (aNumeric && bNumeric) {
			return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
		}
		if (aNumeric) {
			return -1;
		}
		if (bNumeric) {
			return 1;
		}
		return a.compareToIgnoreCase(b);
	}

	private static boolean isNumeric(String value) {
		if (value == null || value.isEmpty()) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			if (!Character.isDigit(value.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private record ParsedVersion(int[] core, java.util.List<String> preRelease) {
	}
}
