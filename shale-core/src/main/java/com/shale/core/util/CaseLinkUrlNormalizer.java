package com.shale.core.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/** UI-free Case Link URL normalization and validation. Does not perform network access. */
public final class CaseLinkUrlNormalizer {
	public static final int MAX_URL_LENGTH = 2048;

	private static final Pattern ANY_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*", Pattern.DOTALL);
	private static final Pattern HTTP_SCHEME = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private CaseLinkUrlNormalizer() {}

	public static String normalize(String rawUrl) {
		String value = rawUrl == null ? "" : rawUrl.trim();
		if (value.isBlank()) throw new IllegalArgumentException("URL is required.");
		if (containsControlCharacter(value)) throw new IllegalArgumentException("URL must not contain control characters.");
		if (containsWhitespace(value)) throw new IllegalArgumentException("URL must not contain whitespace.");

		String normalized;
		if (HTTP_SCHEME.matcher(value).matches()) {
			normalized = value;
		} else if (value.startsWith("//")) {
			normalized = "https:" + value;
		} else if (ANY_SCHEME.matcher(value).matches()) {
			throw new IllegalArgumentException("URL must be an absolute http or https URL.");
		} else if (isDomainStyleInput(value)) {
			normalized = "https://" + value;
		} else {
			throw new IllegalArgumentException("URL must include a recognizable host name.");
		}

		if (normalized.length() > MAX_URL_LENGTH) throw new IllegalArgumentException("URL must be 2048 characters or fewer.");
		if (containsControlCharacter(normalized)) throw new IllegalArgumentException("URL must not contain control characters.");
		if (containsWhitespace(normalized)) throw new IllegalArgumentException("URL must not contain whitespace.");
		return validateNormalized(normalized);
	}

	public static URI normalizeToUri(String rawUrl) {
		return URI.create(normalize(rawUrl));
	}

	public static boolean containsControlCharacter(String value) {
		if (value == null) return false;
		for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return true;
		return false;
	}

	private static String validateNormalized(String normalized) {
		try {
			URI uri = new URI(normalized);
			String scheme = uri.getScheme();
			if (!uri.isAbsolute() || scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
				throw new IllegalArgumentException("URL must be an absolute http or https URL.");
			}
			if (uri.getHost() == null || uri.getHost().isBlank()) throw new IllegalArgumentException("URL must include a host.");
			if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) throw new IllegalArgumentException("URL must not include credentials.");
			return normalized;
		} catch (URISyntaxException | IllegalArgumentException ex) {
			if (ex instanceof IllegalArgumentException && ex.getMessage() != null && ex.getMessage().startsWith("URL ")) throw (IllegalArgumentException) ex;
			throw new IllegalArgumentException("URL is not valid.", ex);
		}
	}

	private static boolean containsWhitespace(String value) {
		for (int i = 0; i < value.length(); i++) if (Character.isWhitespace(value.charAt(i))) return true;
		return false;
	}

	private static boolean isDomainStyleInput(String value) {
		String authority = value;
		int end = firstIndexOfAny(authority, '/', '?', '#');
		if (end >= 0) authority = authority.substring(0, end);
		if (authority.isBlank() || authority.contains("@")) return false;
		String host = authority;
		int colon = host.lastIndexOf(':');
		if (colon >= 0) host = host.substring(0, colon);
		host = host.toLowerCase(Locale.ROOT);
		return host.contains(".") && !host.startsWith(".") && !host.endsWith(".") && host.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '.' || ch == '-');
	}

	private static int firstIndexOfAny(String value, char... chars) {
		int first = -1;
		for (char ch : chars) {
			int idx = value.indexOf(ch);
			if (idx >= 0 && (first < 0 || idx < first)) first = idx;
		}
		return first;
	}
}
