package com.ehtid.p2pchat;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Only HTTPS root URLs accepted for EHTID Home Relay and compatible servers. */
public final class HomeServerUrl {
    private HomeServerUrl() {}

    public static String normalize(String input) {
        if (input == null) return "";
        String text = input.trim().replaceAll("/+$", "");
        if (text.isEmpty()) return "";
        try {
            URI uri = new URI(text);
            if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                uri.getHost() == null || uri.getHost().isEmpty() ||
                uri.getRawUserInfo() != null || uri.getRawQuery() != null ||
                uri.getRawFragment() != null ||
                (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) return "";
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.indexOf(':') >= 0) host = "[" + host + "]";
            int port = uri.getPort();
            return "https://" + host + (port < 0 || port == 443 ? "" : ":" + port);
        } catch (URISyntaxException problem) {
            return "";
        }
    }
}
