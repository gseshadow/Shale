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
    public static URI email(String address) { return opaque("mailto",address); }
    public static URI maps(String address) {
        if(address==null||address.isBlank())throw new IllegalArgumentException("Address is required.");
        return URI.create("https://www.google.com/maps/search/?api=1&query="+URLEncoder.encode(address.trim(),StandardCharsets.UTF_8));
    }
    private static URI opaque(String scheme,String value) {
        if(value==null||value.isBlank()||ExternalBrowserHelper.containsControlCharacter(value))throw new IllegalArgumentException("A valid value is required.");
        try{return new URI(scheme,value.trim(),null);}catch(Exception ex){throw new IllegalArgumentException("The external action value is invalid.",ex);}
    }
}
