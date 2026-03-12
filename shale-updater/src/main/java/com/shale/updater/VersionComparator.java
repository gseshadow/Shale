package com.shale.updater;

public final class VersionComparator {

	private VersionComparator() {
	}

	public static int compare(String a, String b) {
		int[] av = parse(a);
		int[] bv = parse(b);

		int len = Math.max(av.length, bv.length);
		for (int i = 0; i < len; i++) {
			int ai = i < av.length ? av[i] : 0;
			int bi = i < bv.length ? bv[i] : 0;

			if (ai != bi) {
				return Integer.compare(ai, bi);
			}
		}
		return 0;
	}

	private static int[] parse(String version) {
		if (version == null || version.isBlank()) {
			return new int[] {0};
		}

		String[] parts = version.trim().split("\\.");
		int[] values = new int[parts.length];

		for (int i = 0; i < parts.length; i++) {
			try {
				values[i] = Integer.parseInt(parts[i]);
			} catch (NumberFormatException ex) {
				values[i] = 0;
			}
		}

		return values;
	}
}