package com.tiltus.bevywizard.catalog;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compares asset version tags and crate requirements with a Bevy release. */
public final class BevyCompatibility {
    private static final Pattern REQUIREMENT = Pattern.compile("^(\\^|~|>=|<=|>|<|=)?\\s*(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(-[A-Za-z0-9.-]+)?$");
    private static final Pattern SIMPLE_TAG = Pattern.compile("^(?:\\^|~|=)?\\s*(\\d+)\\.(\\d+)(?:\\.(\\d+))?(-[A-Za-z0-9.-]+)?$");

    private BevyCompatibility() {}

    /** Checks whether any asset tag accepts a Bevy release. */
    public static boolean supports(String bevyVersion, List<String> tags) {
        return tags.stream().anyMatch(tag -> matches(bevyVersion, tag));
    }

    /** Groups stable asset tags by Bevy series while keeping release candidates distinct. */
    public static String filterLabel(String tag) {
        String trimmed = tag.trim();
        Matcher match = SIMPLE_TAG.matcher(trimmed);
        if (!match.matches()) return "Bevy " + trimmed;
        String series = "Bevy " + match.group(1) + "." + match.group(2);
        if (match.group(4) == null) return series;
        return series + "." + (match.group(3) == null ? "0" : match.group(3)) + match.group(4);
    }

    /** Matches a display filter; release compatibility is checked separately. */
    public static boolean matchesFilter(List<String> tags, String filter) {
        return tags.stream().anyMatch(tag -> filterLabel(tag).equals(filter));
    }

    /** Evaluates a Cargo-style version requirement against a version. */
    public static boolean matches(String version, String requirement) {
        for (String alternative : requirement.split("\\|\\|")) {
            boolean all = true;
            for (String part : alternative.trim().split("\\s*,\\s*|\\s+(?=[<>~=^])")) {
                if (part.isBlank()) continue;
                if (!matchesSingle(version, part.trim())) { all = false; break; }
            }
            if (all) return true;
        }
        return false;
    }

    /** Evaluates one version constraint without alternatives. */
    private static boolean matchesSingle(String version, String requirement) {
        Matcher match = REQUIREMENT.matcher(requirement);
        if (!match.matches()) return false;
        String operator = match.group(1) == null ? "^" : match.group(1);
        String lower = match.group(2) + "." + (match.group(3) == null ? "0" : match.group(3)) + "."
                + (match.group(4) == null ? "0" : match.group(4)) + (match.group(5) == null ? "" : match.group(5));
        int comparison = compare(version, lower);
        return switch (operator) {
            case ">=" -> comparison >= 0;
            case ">" -> comparison > 0;
            case "<=" -> comparison <= 0;
            case "<" -> comparison < 0;
            case "=" -> comparison == 0;
            case "~" -> comparison >= 0 && compare(version, match.group(2) + "." +
                    (Integer.parseInt(match.group(3) == null ? "0" : match.group(3)) + 1) + ".0") < 0;
            default -> {
                int major = Integer.parseInt(match.group(2));
                int minor = Integer.parseInt(match.group(3) == null ? "0" : match.group(3));
                int patch = Integer.parseInt(match.group(4) == null ? "0" : match.group(4));
                String upper = major > 0 ? (major + 1) + ".0.0"
                        : minor > 0 ? "0." + (minor + 1) + ".0" : "0.0." + (patch + 1);
                yield comparison >= 0 && compare(version, upper) < 0;
            }
        };
    }

    /** Compares semantic versions, including release candidates. */
    public static int compare(String first, String second) {
        String[] a = first.split("-", 2)[0].split("\\.");
        String[] b = second.split("-", 2)[0].split("\\.");
        if (a.length != 3 || b.length != 3) return -1;
        try {
            for (int i = 0; i < 3; i++) {
                int result = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
                if (result != 0) return result;
            }
            boolean aPre = first.contains("-");
            boolean bPre = second.contains("-");
            if (aPre != bPre) return aPre ? -1 : 1;
            if (!aPre) return 0;
            String[] preA = first.substring(first.indexOf('-') + 1).split("\\.");
            String[] preB = second.substring(second.indexOf('-') + 1).split("\\.");
            for (int i = 0; i < Math.min(preA.length, preB.length); i++) {
                boolean numericA = preA[i].matches("\\d+");
                boolean numericB = preB[i].matches("\\d+");
                int result = numericA && numericB ? Integer.compare(Integer.parseInt(preA[i]), Integer.parseInt(preB[i]))
                        : numericA != numericB ? (numericA ? -1 : 1) : preA[i].compareTo(preB[i]);
                if (result != 0) return result;
            }
            return Integer.compare(preA.length, preB.length);
        } catch (NumberFormatException e) { return -1; }
    }
}
