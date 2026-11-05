package com.javierrb35.flashlight;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semantic version and monotonically increasing Android build code. */
final class ReleaseVersions implements Comparable<ReleaseVersions> {
    private static final Pattern TAG = Pattern.compile(
            "^[vV]?([0-9]{1,4})\\.([0-9]{1,4})\\.([0-9]{1,4})(?:-build\\.([0-9]{1,9}))?$");
    final int major, minor, patch, build;

    private ReleaseVersions(int major, int minor, int patch, int build) {
        this.major = major; this.minor = minor; this.patch = patch; this.build = build;
    }

    static ReleaseVersions parse(String raw) {
        if (raw == null) return null;
        Matcher match = TAG.matcher(raw);
        if (!match.matches()) return null;
        try {
            return new ReleaseVersions(Integer.parseInt(match.group(1)),
                    Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3)),
                    match.group(4) == null ? 0 : Integer.parseInt(match.group(4)));
        } catch (NumberFormatException invalid) { return null; }
    }

    @Override public int compareTo(ReleaseVersions other) {
        if (major != other.major) return Integer.compare(major, other.major);
        if (minor != other.minor) return Integer.compare(minor, other.minor);
        if (patch != other.patch) return Integer.compare(patch, other.patch);
        return Integer.compare(build, other.build);
    }

    String versionName() { return major + "." + minor + "." + patch; }

    static boolean newer(String tag, String installedVersion, int installedCode) {
        ReleaseVersions candidate = parse(tag);
        ReleaseVersions installed = parse(installedVersion);
        return candidate != null && installed != null
                && candidate.compareTo(installed) > 0 && candidate.build > installedCode;
    }
}
