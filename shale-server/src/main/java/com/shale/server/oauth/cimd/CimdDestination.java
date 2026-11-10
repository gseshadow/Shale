package com.shale.server.oauth.cimd;

import java.net.*;
import java.util.*;

/** Connection policy, separate from identifier syntax. No proxy or URL-driven resolver. */
final class CimdDestination {
    private CimdDestination() {}

    static URI url(String value) {
        var uri = URI.create(value);
        if (value.length() > 2048 || value.chars().anyMatch(c -> c <= 32 || c >= 127) ||
                !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                uri.getUserInfo() != null || uri.getRawFragment() != null || uri.getRawQuery() != null ||
                uri.getPort() == 0 || uri.getPort() > 65535 ||
                !uri.normalize().equals(uri)) throw invalid();
        for (String segment:uri.getPath().split("/",-1))
            if (segment.equals(".") || segment.equals("..")) throw invalid();
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        // Public DNS names only: exclude IP literals, integer/hex/abbreviated IP spellings,
        // single-label/search-domain names and special-use/internal namespaces.
        if (host.length() > 253 || !host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?") ||
                !host.contains(".") || host.matches("[0-9.]+") || host.startsWith("0x") ||
                Arrays.stream(host.split("\\.",-1)).anyMatch(s -> s.isEmpty() || s.length()>63 || s.startsWith("-") || s.endsWith("-")) ||
                List.of(".localhost", ".local", ".internal", ".home", ".lan", ".test", ".invalid", ".example", ".onion", ".arpa").stream().anyMatch(host::endsWith)) throw invalid();
        return uri;
    }

    static boolean publicAddress(InetAddress address) {
        byte[] b = address.getAddress();
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        if (b.length == 4) {
            int a=b[0]&255, c=b[1]&255, d=b[2]&255, e=b[3]&255;
            // Azure's platform virtual IP is not public application egress despite its spelling.
            return !(a==168 && c==63 && d==129 && e==16 || a==0 || a==10 || a==127 || a>=224 || a==169 && c==254 ||
                    a==172 && c>=16 && c<=31 || a==192 && (c==168 || c==0 || c==2 || c==88 && d==99) ||
                    a==100 && c>=64 && c<=127 || a==198 && (c==18 || c==19 || c==51 && d==100) ||
                    a==203 && c==0 && d==113);
        }
        // Fail closed outside ordinary global unicast; exclude special-purpose 2001::/23,
        // documentation, 6to4 and newer documentation allocation. This excludes mapped/NAT64.
        int first=((b[0]&255)<<8)|(b[1]&255), next=((b[2]&255)<<8)|(b[3]&255);
        return (first & 0xe000)==0x2000 && !(first==0x2001 && (next<0x0200 || next==0x0db8)) &&
                first!=0x2002 && !(first==0x3fff && next<0x1000);
    }

    static InetAddress approved(InetAddress[] addresses) {
        if (addresses.length==0 || addresses.length>16 || Arrays.stream(addresses).anyMatch(a -> !publicAddress(a))) throw invalid();
        return addresses[0];
    }
    static IllegalArgumentException invalid() { return new IllegalArgumentException("CIMD retrieval rejected."); }
}
