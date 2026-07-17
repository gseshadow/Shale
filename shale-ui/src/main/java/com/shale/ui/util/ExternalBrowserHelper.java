package com.shale.ui.util;

import java.awt.Desktop;
import java.net.URI;

import com.shale.core.util.CaseLinkUrlNormalizer;

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
        return CaseLinkUrlNormalizer.normalizeToUri(rawUrl);
    }

    public static boolean containsControlCharacter(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return true;
        return CaseLinkUrlNormalizer.containsControlCharacter(value);
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null) cur = cur.getCause();
        String msg = cur.getMessage();
        return msg == null || msg.isBlank() ? cur.getClass().getSimpleName() : msg;
    }
}
