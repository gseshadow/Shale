package com.shale.ui.util;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** PHI-safe URI construction and OS hand-off for Contact actions. */
public final class ContactExternalActions {
    public interface Launcher { void open(URI uri) throws Exception; }
    private final Launcher launcher;

    public ContactExternalActions() { this(uri -> {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            throw new UnsupportedOperationException("No application is registered for this action.");
        Desktop.getDesktop().browse(uri);
    }); }
    public ContactExternalActions(Launcher launcher) { this.launcher=java.util.Objects.requireNonNull(launcher); }
    public void open(URI uri) { try { launcher.open(uri); } catch(Exception ex) { throw new IllegalStateException("No application is available for this action.",ex); } }
    public static URI telephone(String displayNumber) { return opaque("tel",displayNumber); }
    public static URI telephone(String normalizedNumber, String extension) {
        if (normalizedNumber == null || !normalizedNumber.trim().matches("\\+?[0-9*#]{3,}"))
            throw new IllegalArgumentException("A valid dialable phone number is required.");
        String target = normalizedNumber.trim();
        if (extension != null && !extension.isBlank()) {
            if (!extension.trim().matches("[0-9]{1,12}")) throw new IllegalArgumentException("A valid phone extension is required.");
            target += ";ext=" + extension.trim();
        }
        return opaque("tel", target);
    }
    public static URI email(String address) { return opaque("mailto",address); }
    public static URI maps(String address) {
        if(address==null||address.isBlank())throw new IllegalArgumentException("Address is required.");
        return URI.create("https://www.google.com/maps/search/?api=1&query="+URLEncoder.encode(address.trim(),StandardCharsets.UTF_8));
    }
    public static URI website(String value) {
        if (value == null || value.isBlank() || ExternalBrowserHelper.containsControlCharacter(value))
            throw new IllegalArgumentException("A valid website is required.");
        String target = value.trim().matches("^[A-Za-z][A-Za-z0-9+.-]*:.*") ? value.trim() : "https://" + value.trim();
        URI uri;
        try { uri = URI.create(target); } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("The website is invalid.", ex); }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
            throw new IllegalArgumentException("Only HTTP and HTTPS websites can be opened.");
        return uri;
    }
    private static URI opaque(String scheme,String value) {
        if(value==null||value.isBlank()||ExternalBrowserHelper.containsControlCharacter(value))throw new IllegalArgumentException("A valid value is required.");
        try{return new URI(scheme,value.trim(),null);}catch(Exception ex){throw new IllegalArgumentException("The external action value is invalid.",ex);}
    }
}
