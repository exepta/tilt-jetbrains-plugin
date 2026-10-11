package com.tiltus.bevywizard.template;

import com.tiltus.bevywizard.project.TiltUiCompatibility;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Widget contract shared by the maintained 0.1.0 and 0.1.0-rc.2 runtimes. */
final class TiltWidgetCatalog {
    // Sources: docs/widgets/, docs/architecture/property-bindings.md and
    // tilt-ui-runtime 0.1.0 widgets/{controls,advanced,structure,content}.
    private static final Set<String> COMMON = Set.of("id", "class", "style", "title", "hidden", "tabindex", "role",
            "onclick", "onchange", "oninput", "onfocus", "onblur", "onkeydown", "onkeyup",
            "onmouseenter", "onmouseleave", "onscroll", "oninit");
    private static final Set<String> COMMON_BINDINGS = Set.of("text", "innerText", "textContent", "innerHtml",
            "innerHTML", "class", "style");
    private static final Map<String, Set<String>> STATIC = Map.ofEntries(
            Map.entry("form", Set.of("action", "validate")),
            Map.entry("button", Set.of("type", "name", "value", "disabled", "required", "loading",
                    "extensions", "max-size", "folder")),
            Map.entry("input", Set.of("type", "value", "placeholder", "name", "disabled", "readonly", "required",
                    "min", "max", "step", "minlength", "maxlength", "max-lines", "show-fields", "folder",
                    "extensions", "show-size", "max-size", "label", "icon", "clear-on-blur", "cap-text-at")),
            Map.entry("textarea", Set.of("value", "placeholder", "name", "disabled", "readonly", "required",
                    "minlength", "maxlength", "max-lines")),
            Map.entry("slider", Set.of("type", "orientation", "alignment", "min", "max", "value", "step",
                    "range-start", "range-end", "dots", "show-tip", "show-labels", "dot-anchor", "disabled")),
            Map.entry("dialog", Set.of("renderer", "layout", "type", "trigger", "title", "open", "animated")),
            Map.entry("toast", Set.of("type", "trigger", "duration", "title", "open")),
            Map.entry("tooltip", Set.of("for", "trigger", "variant", "alignment", "prio", "priority", "open")),
            Map.entry("icon", Set.of("name", "size", "loading", "lazy-buffer")),
            Map.entry("checkbox", Set.of("name", "value", "checked", "disabled", "required",
                    "checked-icon", "unchecked-icon")),
            Map.entry("avatar", Set.of("src", "alt")),
            Map.entry("badge", Set.of("value", "max", "for", "anchor")),
            Map.entry("divider", Set.of("alignment")),
            Map.entry("headline", Set.of("level")),
            Map.entry("switch-button", Set.of("name", "value", "checked", "disabled")),
            Map.entry("toggle-button", Set.of("name", "value", "checked", "disabled")),
            Map.entry("radio-button", Set.of("name", "value", "checked", "disabled", "required")),
            Map.entry("choice-box", Set.of("name", "value", "selected", "open", "disabled", "max-visible-items")),
            Map.entry("list-box", Set.of("name", "value", "selected", "multiple", "disabled")),
            Map.entry("option", Set.of("value", "selected", "disabled")),
            Map.entry("date-picker", Set.of("type", "value", "min", "max", "range", "range-start",
                    "range-end", "format", "placeholder", "open", "disabled")),
            Map.entry("color-picker", Set.of("value", "size", "alpha", "format", "open", "disabled")),
            Map.entry("image", Set.of("src", "alt", "size", "loading", "preview")),
            Map.entry("hyperlink", Set.of("href", "icon", "target")),
            Map.entry("progress-bar", Set.of("value", "max")),
            Map.entry("scroll", Set.of("loading", "lazy-buffer")),
            Map.entry("scrollbar", Set.of("loading", "lazy-buffer")));
    private static final Map<String, Set<String>> BINDINGS = Map.ofEntries(
            Map.entry("form", Set.of("action", "validate")),
            Map.entry("button", Set.of("type", "disabled", "loading")),
            Map.entry("input", Set.of("type", "value", "placeholder", "name", "disabled", "readonly", "required",
                    "min", "max", "step", "minlength", "maxlength", "max-lines", "show-fields", "folder",
                    "extensions", "show-size", "max-size", "label", "icon", "clear-on-blur", "cap-text-at")),
            Map.entry("textarea", Set.of("value", "placeholder", "name", "disabled", "readonly", "required",
                    "minlength", "maxlength", "max-lines")),
            Map.entry("badge", Set.of("value")),
            Map.entry("progress-bar", Set.of("value")),
            Map.entry("slider", Set.of("type", "orientation", "value", "min", "max", "step", "range-start", "range-end",
                    "dots", "show-tip", "show-labels", "dot-anchor", "disabled")),
            Map.entry("dialog", Set.of("renderer", "layout", "type", "trigger", "title", "open", "animated")),
            Map.entry("tooltip", Set.of("for", "trigger", "variant", "alignment", "prio", "priority", "open")),
            Map.entry("choice-box", Set.of("value", "selected", "open", "disabled")),
            Map.entry("date-picker", Set.of("value", "range-start", "range-end", "open", "disabled")),
            Map.entry("color-picker", Set.of("value", "open", "disabled")),
            Map.entry("image", Set.of("src", "alt")),
            Map.entry("hyperlink", Set.of("href")),
            Map.entry("checkbox", Set.of("checked", "disabled", "value")),
            Map.entry("radio-button", Set.of("checked", "disabled", "value")));
    private static final Map<String, List<String>> VALUES = Map.ofEntries(
            Map.entry("input:type", List.of("text", "email", "password", "number", "date", "file", "range")),
            Map.entry("button:type", List.of("button", "submit", "reset", "file", "loading")),
            Map.entry("slider:type", List.of("range")),
            Map.entry("slider:orientation", List.of("horizontal", "vertical")),
            Map.entry("slider:alignment", List.of("horizontal", "vertical")),
            Map.entry("slider:dot-anchor", List.of("top", "bottom")),
            Map.entry("date-picker:type", List.of("date", "range")),
            Map.entry("date-picker:format", List.of("mdy", "dmy", "ymd")),
            Map.entry("color-picker:format", List.of("hex", "rgb", "rgba")),
            Map.entry("divider:alignment", List.of("horizontal", "vertical")),
            Map.entry("dialog:renderer", List.of("bevy", "system")),
            Map.entry("dialog:layout", List.of("panel", "floating", "bottom-sheet", "bottom")),
            Map.entry("dialog:type", List.of("info", "warn", "warning", "error", "failure", "question", "blank")),
            Map.entry("toast:type", List.of("info", "success", "error", "failure", "warning", "warn")),
            Map.entry("form:validate", List.of("send", "always", "all", "interact")),
            Map.entry("tooltip:variant", List.of("follow", "point")),
            Map.entry("tooltip:alignment", List.of("horizontal", "vertical")),
            Map.entry("tooltip:priority", List.of("top", "bottom", "down", "left", "right")),
            Map.entry("tooltip:prio", List.of("top", "bottom", "down", "left", "right")),
            Map.entry("icon:loading", List.of("lazy", "eager")),
            Map.entry("image:loading", List.of("lazy", "eager")));

    private TiltWidgetCatalog() {}

    static boolean supports(String release) { return release != null && TiltUiCompatibility.supportsVersion(release); }

    static Set<String> staticNames(String release, String tag) {
        if (!supports(release)) return Set.of();
        LinkedHashSet<String> names = new LinkedHashSet<>(COMMON);
        names.addAll(STATIC.getOrDefault(canonical(tag), Set.of()));
        return names;
    }

    static Set<String> bindingNames(String release, String tag) {
        if (!supports(release)) return Set.of();
        LinkedHashSet<String> names = new LinkedHashSet<>(COMMON_BINDINGS);
        names.addAll(BINDINGS.getOrDefault(canonical(tag), Set.of()));
        return names;
    }

    static boolean accepts(String release, String tag, String attribute) {
        if (!supports(release)) return true;
        if (attribute.startsWith("on") || attribute.startsWith("data-") || attribute.startsWith("aria-")) return true;
        if (attribute.startsWith("[class.") && attribute.endsWith("]")) return true;
        if (attribute.startsWith("[...") && attribute.endsWith("]")) return true;
        if (attribute.startsWith("[") && attribute.endsWith("]"))
            return bindingNames(release, tag).contains(attribute.substring(1, attribute.length() - 1));
        return staticNames(release, tag).contains(attribute);
    }

    static List<String> values(String tag, String attribute) {
        return VALUES.getOrDefault(canonical(tag) + ":" + attribute, List.of());
    }

    private static String canonical(String tag) {
        return switch (tag) {
            case "select" -> "choice-box";
            case "colorpicker" -> "color-picker";
            case "a" -> "hyperlink";
            case "img" -> "image";
            case "progressbar" -> "progress-bar";
            case "scroll" -> "scrollbar";
            case "radio" -> "radio-button";
            case "switch" -> "switch-button";
            case "toggle" -> "toggle-button";
            default -> tag;
        };
    }

    static String description(String tag) {
        return switch (canonical(tag)) {
            case "form" -> "Collects named controls and calls the registered action on submit.";
            case "input" -> "Editable text, number, date, file, or range control.";
            case "textarea" -> "Multi-line editable text control.";
            case "slider" -> "Single-value or two-thumb range control; supports orientation, dots, and value tips.";
            case "dialog" -> "Modal dialog with configurable renderer, layout, trigger, and animation.";
            case "toast" -> "Timed notification in a viewport-level stack.";
            case "tooltip" -> "Contextual popup anchored to another element's ID.";
            case "icon" -> "Displays a TiltUI icon. Use name for the icon and size for its pixel size.";
            case "hyperlink" -> "Opens the URL in href when activated. Set icon to none to hide the indicator.";
            case "router-outlet" -> "Renders the component selected by the active TiltUI route.";
            default -> "Built-in TiltUI element.";
        };
    }

    static String attributeDescription(String tag, String attribute) {
        String base = attribute.startsWith("[") && attribute.endsWith("]")
                ? attribute.substring(1, attribute.length() - 1) : attribute;
        String explanation = switch (base) {
            case "type" -> "Selects this control's mode.";
            case "value" -> "Current control value.";
            case "action" -> "Registered form submit handler.";
            case "validate" -> "Form validation timing.";
            case "orientation" -> "Horizontal or vertical slider direction.";
            case "dots" -> "Number of slider intervals (1–100).";
            case "renderer" -> "Dialog renderer; system requires the file-dialog feature.";
            case "layout" -> "Dialog panel placement.";
            case "trigger" -> "ID of the element that opens this widget, or tooltip trigger mode.";
            case "duration" -> "Toast lifetime, for example 750ms or 5s; 0 is persistent.";
            case "name" -> tag.equals("icon") ? "Name from the TiltUI icon catalog." : "Control name used for form data.";
            case "size" -> "Icon or widget size in pixels.";
            case "src" -> "Image source; tilt-icon:name@size uses the bundled icon catalog.";
            case "loading" -> "Lazy or eager asset loading, or a button's loading state.";
            case "style" -> "TiltUI CSS declarations applied to this element.";
            case "class" -> "CSS class names; [class.name] toggles one class.";
            default -> "TiltUI " + tag + " attribute.";
        };
        return attribute.startsWith("[") ? "Reactive property binding. " + explanation : explanation;
    }
}
