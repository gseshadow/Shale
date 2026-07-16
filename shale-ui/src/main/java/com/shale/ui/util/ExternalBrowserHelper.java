package com.shale.ui.util;

import java.awt.Desktop;
import java.net.URI;

/** Safely opens user-facing external HTTP(S) links without inspecting or fetching content. */
public final class ExternalBrowserHelper {
    public interface BrowserLauncher { void browse(URI uri) throws Exception; }
    private final BrowserLauncher launcher;

    public ExternalBrowserHelper() { this(uri -> {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new UnsupportedOperationException("Opening links is not supported on this computer.");
        }
        Desktop.getDesktop().browse(uri);
    }); }

    public ExternalBrowserHelper(BrowserLauncher launcher) { this.launcher = launcher; }

    public void openHttpOrHttps(String rawUrl) {
        URI uri = validateHttpOrHttps(rawUrl);
        try { launcher.browse(uri); }
        catch (Exception ex) { throw new IllegalStateException(rootMessage(ex), ex); }
    }

    public static URI validateHttpOrHttps(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        if (value.isBlank()) throw new IllegalArgumentException("URL is required.");
        if (containsControlCharacter(value)) throw new IllegalArgumentException("URL cannot contain control characters.");
        URI uri;
        try { uri = URI.create(value); } catch (RuntimeException ex) { throw new IllegalArgumentException("Enter a valid absolute HTTP or HTTPS URL."); }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) throw new IllegalArgumentException("Only absolute HTTP or HTTPS URLs can be opened.");
        if (uri.getHost() == null || uri.getHost().isBlank()) throw new IllegalArgumentException("URL host is required.");
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) throw new IllegalArgumentException("URLs with embedded credentials are not allowed.");
        return uri;
    }

    public static boolean containsControlCharacter(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return true;
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null) cur = cur.getCause();
        String msg = cur.getMessage();
        return msg == null || msg.isBlank() ? cur.getClass().getSimpleName() : msg;
    }
}
