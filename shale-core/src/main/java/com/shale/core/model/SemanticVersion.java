package com.shale.core.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, canonical Shale application version ({@code major.minor.build}). */
public record SemanticVersion(int major, int minor, int build) implements Comparable<SemanticVersion> {
	private static final Pattern CANONICAL = Pattern.compile("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)");

	public SemanticVersion {
		if (major < 0 || minor < 0 || build < 0) throw new IllegalArgumentException("Version components must be nonnegative");
	}

	public static SemanticVersion parse(String value) {
		Objects.requireNonNull(value, "value");
		Matcher matcher = CANONICAL.matcher(value);
		if (!matcher.matches()) throw new IllegalArgumentException("Version must be canonical major.minor.build: " + value);
		try {
			return new SemanticVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Version component exceeds Java int range: " + value, ex);
		}
	}

	@Override public int compareTo(SemanticVersion other) {
		Objects.requireNonNull(other, "other");
		int result = Integer.compare(major, other.major);
		if (result == 0) result = Integer.compare(minor, other.minor);
		return result == 0 ? Integer.compare(build, other.build) : result;
	}

	@Override public String toString() { return major + "." + minor + "." + build; }
}
