
//package com.shale.desktop.net;
//
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.nio.charset.StandardCharsets;
//
//public final class NegotiateClient {
//	private final String negotiateEndpointUrl;
//	private final HttpClient http = HttpClient.newHttpClient();
//
//	public NegotiateClient(String negotiateEndpointUrl) {
//		this.negotiateEndpointUrl = negotiateEndpointUrl;
//	}
//
//	/**
//	 * Calls your negotiate endpoint and expects the response body to be the client WebSocket
//	 * URL (plain text or JSON with "url"). You can adjust this to your actual response shape
//	 * later.
//	 */
//	public String negotiateForTenant(int shaleClientId, int userId) throws Exception {
//		String url = negotiateEndpointUrl + "?tenantId=" + shaleClientId + "&userId=" + userId;
//		HttpRequest req = HttpRequest.newBuilder()
//				.uri(URI.create(url))
//				.header("Accept", "text/plain")
//				.GET()
//				.build();
//
//		HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
//		if (res.statusCode() / 100 != 2) {
//			throw new IllegalStateException("Negotiate failed: HTTP " + res.statusCode());
//		}
//
//		String body = res.body().trim();
//		// If your endpoint returns JSON, replace this with a tiny parser.
//		return body;
//	}
//}
package com.shale.desktop.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NegotiateClient {
	private static final Pattern URL_FIELD = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
	private static final int TIMEOUT_SECS = 12;

	private final String negotiateEndpointUrl; // usually includes ?code=...
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(TIMEOUT_SECS))
			.build();

	public NegotiateClient(String negotiateEndpointUrl) {
		this.negotiateEndpointUrl = negotiateEndpointUrl;
	}

	/**
	 * Calls your negotiate endpoint and returns the tokenized WebSocket URL. - Preserves your
	 * signature (tenant + user), but appends them safely. - Works whether the function
	 * returns plain text or {"url":"wss://..."} JSON. - Supports header auth via env var
	 * FUNCTION_KEY (optional).
	 */
	public String negotiateForTenant(int shaleClientId, int userId) throws Exception {
		// Safely append tenant/user without breaking existing query (?code=...)
		String url = appendQuery(appendQuery(negotiateEndpointUrl, "tenantId", Integer.toString(shaleClientId)),
				"userId", Integer.toString(userId));

		HttpRequest.Builder rb = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(TIMEOUT_SECS))
				// Ask for JSON first; server may still return text/plain
				.header("Accept", "application/json, text/plain; q=0.8")
				.GET();

		// Optional: use header key instead of ?code=… if provided
		String fnKey = System.getenv("FUNCTION_KEY");
		if (fnKey != null && !fnKey.isBlank() && !hasQueryParam(negotiateEndpointUrl, "code")) {
			rb.header("x-functions-key", fnKey);
		}

		HttpResponse<String> res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() / 100 != 2) {
			throw new IllegalStateException("Negotiate failed: HTTP " + res.statusCode()
					+ " body=" + preview(res.body(), 200));
		}

		String body = res.body() == null ? "" : res.body().trim();
		String wss = extractWssUrl(body);
		if (wss == null || !wss.startsWith("wss://")) {
			throw new IllegalStateException("Negotiate response did not contain a valid wss:// URL: "
					+ preview(body, 200));
		}
		return wss; // IMPORTANT: use verbatim; do not modify the returned URL.
	}

	// -------- helpers --------

	private static String appendQuery(String base, String key, String val) {
		if (key == null || key.isEmpty())
			return base;
		String sep = base.contains("?") ? "&" : "?";
		return base + sep + key + "=" + urlEncode(val);
	}

	private static boolean hasQueryParam(String url, String key) {
		int q = url.indexOf('?');
		if (q < 0)
			return false;
		String qs = url.substring(q + 1);
		for (String part : qs.split("&")) {
			int i = part.indexOf('=');
			String k = (i >= 0) ? part.substring(0, i) : part;
			if (k.equalsIgnoreCase(key))
				return true;
		}
		return false;
	}

	private static String extractWssUrl(String body) {
		if (body == null || body.isEmpty())
			return null;
		// If the function already returns plain text wss://...
		if (body.startsWith("wss://"))
			return body;

		// Try to parse {"url":"wss://..."} (very small regex, no JSON lib needed)
		Matcher m = URL_FIELD.matcher(body);
		if (m.find())
			return m.group(1);

		return null;
	}

	private static String urlEncode(String s) {
		if (s == null)
			return "";
		// Minimal safe encode for digits-only inputs we pass (ids). Keep simple.
		// If you pass arbitrary strings later, switch to URLEncoder.encode(..., UTF-8).
		return s;
	}

	private static String preview(String s, int n) {
		return s == null ? "<null>" : s.substring(0, Math.min(n, s.length())).replaceAll("\\s+", " ");
	}
}
