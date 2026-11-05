package com.javierrb35.flashlight;

import java.net.URI;
import java.util.Locale;

/** Never trust unbounded asset data or arbitrary URLs in a release response. */
final class UpdateAssetRules {
    static final String REPO = "JavierRB35/FlashLight";
    static final String API = "https://api.github.com/repos/" + REPO + "/releases?per_page=20";
    static final String RELEASE_PREFIX = "https://github.com/" + REPO + "/releases/tag/";
    static final String ASSET_PREFIX = "https://github.com/" + REPO + "/releases/download/";
    static final long MAX_BYTES = 100L * 1024 * 1024;

    private UpdateAssetRules() {}

    static boolean valid(String tag, long id, long size, String name, String digest, String url) {
        ReleaseVersions version = ReleaseVersions.parse(tag);
        if (version == null || !tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+-build\\.[0-9]+")
                || version.build < 1 || id <= 0
                || size < 4096 || size > MAX_BYTES
                || digest == null || !digest.matches("sha256:[0-9a-fA-F]{64}")) return false;
        String expected = "FlashLight-" + tag.substring(1) + "-release.apk";
        if (!expected.equals(name)) return false;
        return sameUrl(ASSET_PREFIX + tag + "/" + expected, url);
    }

    static boolean sameUrl(String expected, String raw) {
        if (raw == null || !expected.equals(raw)) return false;
        try {
            URI uri = URI.create(raw);
            return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException malformed) { return false; }
    }

    static String digestHex(String raw) {
        if (raw == null || !raw.matches("sha256:[0-9a-fA-F]{64}"))
            throw new IllegalArgumentException("Huella de actualización incorrecta");
        return raw.substring(7).toLowerCase(Locale.ROOT);
    }

    static boolean trustedRedirectHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("github.com") ||
                normalized.endsWith(".githubusercontent.com");
    }
}
