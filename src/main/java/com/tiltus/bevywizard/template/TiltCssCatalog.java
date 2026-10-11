package com.tiltus.bevywizard.template;

import com.google.gson.JsonParser;
import com.tiltus.bevywizard.project.TiltUiCompatibility;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** CSS extensions and parser-backed values of the maintained TiltUI releases. */
final class TiltCssCatalog {
    // Source: tilt-ui-css 0.1.0 and 0.1.0-rc.2 src/parser/declaration.rs.
    static final Map<String, String> CUSTOM = Map.of(
            "background-filter", "Filters a cached background image. Supports blur, grayscale, oil-paint, contrast, and invert.",
            "backdrop-filter", "Filters content behind the element. Oil-paint is not supported here.",
            "animated-filter", "Runs animated image effects such as noise, bloom, and water-wave.",
            "effect-quality", "Render quality for animated effects: auto, low, medium, or high.",
            "border-brush-strength", "Strength of a brushed border, from 0 to 1 or 0% to 100%.",
            "scroll-width", "Width of the scroll bar in non-negative pixels.");
    static final Map<String, List<String>> VALUES = Map.ofEntries(
            Map.entry("effect-quality", List.of("auto", "low", "medium", "high")),
            Map.entry("display", List.of("block", "flex", "grid", "none")),
            Map.entry("position", List.of("static", "relative", "absolute")),
            Map.entry("flex-direction", List.of("row", "column", "row-reverse", "column-reverse")),
            Map.entry("flex-wrap", List.of("nowrap", "wrap", "wrap-reverse")),
            Map.entry("justify-content", List.of("start", "flex-start", "end", "flex-end", "center",
                    "space-between", "space-around", "space-evenly")),
            Map.entry("align-items", List.of("start", "flex-start", "end", "flex-end", "center", "stretch")),
            Map.entry("align-self", List.of("auto", "start", "flex-start", "end", "flex-end", "center", "stretch")),
            Map.entry("overflow", List.of("visible", "hidden", "clip", "scroll", "auto")),
            Map.entry("overflow-x", List.of("visible", "hidden", "clip", "scroll", "auto")),
            Map.entry("overflow-y", List.of("visible", "hidden", "clip", "scroll", "auto")),
            Map.entry("animation-timing-function", List.of("linear", "ease", "ease-in", "ease-out", "ease-in-out")),
            Map.entry("transition-timing-function", List.of("linear", "ease", "ease-in", "ease-out", "ease-in-out")),
            Map.entry("animation-direction", List.of("normal", "reverse", "alternate", "alternate-reverse")),
            Map.entry("animation-iteration-count", List.of("1", "2", "infinite")),
            Map.entry("border-style", List.of("none", "solid", "dotted", "dashed", "dash-dot", "brushed", "skeleton", "skelleton")));
    static final Map<String, String> MOTION_DOCS = Map.ofEntries(
            Map.entry("animation", "Keyframe name, non-negative duration/delay in ms or s, timing, iteration count, and direction."),
            Map.entry("animation-name", "Name declared by @keyframes, or none."),
            Map.entry("animation-duration", "Non-negative duration in ms or s."),
            Map.entry("animation-delay", "Non-negative delay in ms or s."),
            Map.entry("animation-timing-function", "linear, ease, ease-in, ease-out, or ease-in-out."),
            Map.entry("animation-iteration-count", "Non-negative iteration count or infinite."),
            Map.entry("animation-direction", "normal, reverse, alternate, or alternate-reverse."),
            Map.entry("transition", "Property, non-negative duration/delay in ms or s, and timing function."),
            Map.entry("transition-property", "Property name, all, or none."),
            Map.entry("transition-duration", "Non-negative duration in ms or s."),
            Map.entry("transition-delay", "Non-negative delay in ms or s."),
            Map.entry("transition-timing-function", "linear, ease, ease-in, ease-out, or ease-in-out."),
            Map.entry("transform", "Supports translate, translateX/Y, scale, scaleX/Y, and rotate functions."));
    static final Map<String, String> LAYOUT_DOCS = Map.ofEntries(
            Map.entry("display", "Layout mode: block, flex, grid, or none."),
            Map.entry("position", "Position mode: static, relative, or absolute."),
            Map.entry("flex-direction", "Direction of the flex main axis."),
            Map.entry("flex-wrap", "Whether flex children wrap."),
            Map.entry("flex-flow", "Flex direction and/or wrap mode."),
            Map.entry("justify-content", "Alignment along the main axis."),
            Map.entry("align-items", "Alignment along the cross axis."),
            Map.entry("align-self", "Cross-axis alignment for one child."),
            Map.entry("overflow", "Overflow behavior for both axes."));
    private static final Pattern FUNCTION = Pattern.compile("([a-z-]+)\\(([^()]*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("[+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");
    private static final List<String> ICONS = loadIcons();

    private TiltCssCatalog() {}

    static boolean supports(String release) { return release != null && TiltUiCompatibility.supportsVersion(release); }

    static String documentation(String name) {
        if (name.equals("tilt-icon")) return "Bundled SVG icon source: tilt-icon:name@16, @32, or @64. Requires the tilt-icons feature.";
        String known = CUSTOM.get(name);
        if (known != null) return known;
        known = MOTION_DOCS.get(name);
        return known != null ? known : LAYOUT_DOCS.get(name);
    }

    static List<String> completions(String property) {
        if (property.equals("background-filter"))
            return List.of("none", "blur(4px)", "grayscale(100%)", "oil-paint(2)", "contrast(1.5)", "invert(1)");
        if (property.equals("backdrop-filter"))
            return List.of("none", "blur(4px)", "grayscale(100%)", "contrast(1.5)", "invert(1)");
        if (property.equals("animated-filter"))
            return List.of("none", "noise(0.4, 1.5)", "signal-lost(0.8, 1.2)", "old-movie(0.8)",
                    "side-glow(0.8)", "bloom(0.8)", "water-pearls(0.8)", "water-wave(0.8, 2)");
        if (property.equals("border-brush-strength")) return List.of("0.5", "50%", "100%");
        if (property.equals("animation-duration") || property.equals("animation-delay")
                || property.equals("transition-duration") || property.equals("transition-delay"))
            return List.of("150ms", "250ms", "1s");
        if (property.equals("animation") || property.equals("transition"))
            return List.of("150ms ease", "250ms ease-in-out", "1s linear");
        if (property.equals("transform")) return List.of("translate(10px, 0px)", "scale(1.1)", "rotate(15deg)");
        if (property.equals("flex-flow")) return List.of("row nowrap", "row wrap", "column wrap");
        if (property.equals("background-image")) return List.of("url(\"tilt-icon:home@32\")");
        return VALUES.getOrDefault(property, List.of());
    }

    static List<String> iconCompletions(String prefix) {
        return ICONS.stream().filter(name -> name.startsWith(prefix)).map(name -> name + "@32").toList();
    }

    /** Returns an error only for a value that the maintained release parser definitely rejects. */
    static String invalidValue(String property, String source) {
        String value = source.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty()) return null;
        if (property.equals("effect-quality") && !VALUES.get(property).contains(value))
            return "TiltUI effect-quality accepts auto, low, medium, or high.";
        if (property.equals("border-brush-strength")) {
            boolean percent = value.endsWith("%");
            String number = percent ? value.substring(0, value.length() - 1) : value;
            if (!NUMBER.matcher(number).matches()) return "TiltUI border-brush-strength needs a number or percentage.";
            double strength = Double.parseDouble(number);
            if (strength < 0 || strength > (percent ? 100 : 1))
                return "TiltUI border-brush-strength must be between 0 and 1, or 0% and 100%.";
        }
        if (property.equals("background-filter") || property.equals("backdrop-filter")
                || property.equals("animated-filter")) return invalidFilter(property, value);
        if (Set.of("display", "position", "flex-direction", "flex-wrap", "justify-content",
                "align-items", "align-self").contains(property)
                && !VALUES.get(property).contains(value))
            return "TiltUI " + property + " does not support '" + source.trim() + "'.";
        if (Set.of("animation-direction", "animation-timing-function", "transition-timing-function")
                .contains(property) && !allChoices(value, VALUES.get(property)))
            return "Unsupported TiltUI " + property + " value.";
        if (Set.of("animation-duration", "animation-delay", "transition-duration", "transition-delay")
                .contains(property) && !allTimes(value))
            return "TiltUI " + property + " needs non-negative ms or s values.";
        if (property.equals("animation-iteration-count") && !allIterations(value))
            return "TiltUI animation-iteration-count needs non-negative numbers or infinite.";
        return null;
    }

    private static boolean allChoices(String value, List<String> choices) {
        for (String item : value.split(",", -1)) if (!choices.contains(item.trim())) return false;
        return true;
    }

    private static boolean allTimes(String value) {
        for (String item : value.split(",", -1)) {
            String time = item.trim();
            if (!time.endsWith("ms") && !time.endsWith("s")) return false;
            String number = time.substring(0, time.length() - (time.endsWith("ms") ? 2 : 1));
            if (!NUMBER.matcher(number).matches()) return false;
        }
        return true;
    }

    private static boolean allIterations(String value) {
        for (String item : value.split(",", -1))
            if (!item.trim().equals("infinite") && !NUMBER.matcher(item.trim()).matches()) return false;
        return true;
    }

    private static String invalidFilter(String property, String value) {
        if (value.equals("none")) return null;
        Matcher matcher = FUNCTION.matcher(value);
        int at = 0, count = 0;
        while (matcher.find()) {
            if (!value.substring(at, matcher.start()).isBlank()) return "Unsupported TiltUI " + property + " syntax.";
            count++;
            String name = matcher.group(1), argument = matcher.group(2).trim();
            if (property.equals("animated-filter")) {
                if (!Set.of("noise", "signal-lost", "retro-tv", "old-movie", "old-film", "side-glow",
                        "bloom", "water-pearls", "water-wave").contains(name))
                    return "Unsupported TiltUI animated-filter effect: " + name + ".";
                String[] args = argument.split(",", -1);
                if (args.length < 1 || args.length > 2 || !inRange(args[0].trim(), 0, 1)
                        || args.length == 2 && !inRange(args[1].trim(), 0, 4))
                    return "TiltUI animated-filter needs strength 0–1 and optional speed 0–4.";
            } else if (name.equals("blur")) {
                if (!argument.endsWith("px") || !inRange(argument.substring(0, argument.length() - 2), 0, 24))
                    return "TiltUI blur needs 0–24px.";
            } else if (name.equals("oil-paint") || name.equals("oil")) {
                if (property.equals("backdrop-filter")) return "TiltUI backdrop-filter does not support oil-paint.";
                if (!inRange(argument, 1, 3)) return "TiltUI oil-paint radius must be 1–3.";
            } else if (Set.of("grayscale", "black-white", "invert", "contrast").contains(name)) {
                String number = argument.endsWith("%") ? argument.substring(0, argument.length() - 1) : argument;
                double maximum = argument.endsWith("%") ? (name.equals("contrast") ? 400 : 100)
                        : (name.equals("contrast") ? 4 : 1);
                if (!inRange(number, 0, maximum)) return "Unsupported TiltUI " + name + " amount.";
            } else return "Unsupported TiltUI " + property + " effect: " + name + ".";
            at = matcher.end();
        }
        return count == 0 || count > 8 || !value.substring(at).isBlank()
                ? "Unsupported TiltUI " + property + " syntax (up to eight effects)." : null;
    }

    private static boolean inRange(String value, double min, double max) {
        if (!NUMBER.matcher(value).matches()) return false;
        double parsed = Double.parseDouble(value);
        return parsed >= min && parsed <= max;
    }

    private static List<String> loadIcons() {
        try (var stream = TiltCssCatalog.class.getResourceAsStream("/icon-catalog.json")) {
            if (stream == null) return List.of();
            var array = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
            List<String> names = new ArrayList<>();
            array.forEach(item -> names.add(item.getAsJsonObject().get("name").getAsString()));
            return List.copyOf(names);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
