package com.tiltus.bevywizard.template;

import java.util.LinkedHashMap;
import java.util.Map;

/** Names and documentation of the TiltUI template language. */
final class TiltSyntax {
    record Entry(String name, String signature, String description) {}

    static final Map<String, Entry> DIRECTIVES = entries(
            new Entry("@if", "@if (condition) { ... }", "Renders its block when the condition is true."),
            new Entry("@else", "@else { ... }", "Renders an alternative block after @if. Use @else if for another condition."),
            new Entry("@for", "@for (item in items; track item.id) { ... }", "Repeats the block for a collection or range. A track expression gives each item a stable identity."),
            new Entry("@match", "@match (value) { ... }", "Selects a block according to the value."),
            new Entry("@let", "@let name = expression;", "Creates a local template value for following expressions."),
            new Entry("@use", "@use \"crate::module::Type\" as alias;", "Imports a Rust resource exposed with #[html_shared] or #[html_use] into this template."));

    static final Map<String, Entry> ACTIONS = entries(
            new Entry("$set", "$set(target, value)", "Sets a mutable UI store field to a value."),
            new Entry("$add", "$add(target, amount)", "Adds an amount to a mutable UI store field."),
            new Entry("$sub", "$sub(target, amount)", "Subtracts an amount from a mutable UI store field."),
            new Entry("$min", "$min(target, amount)", "Alias for $sub; subtracts an amount from a mutable UI store field."),
            new Entry("$mul", "$mul(target, factor)", "Multiplies a mutable UI store field by a factor."),
            new Entry("$div", "$div(target, divisor)", "Divides a mutable UI store field by a divisor."),
            new Entry("$clamp", "$clamp(target, minimum, maximum)", "Keeps a mutable UI store field within the supplied bounds."),
            new Entry("$toggle", "$toggle(target)", "Flips a mutable Boolean UI store field."),
            new Entry("$append", "$append(target, value)", "Appends a value to a mutable UI store collection."));

    private TiltSyntax() {}

    /** Returns documentation for a built-in directive or inline action. */
    static Entry get(String name) {
        Entry entry = DIRECTIVES.get(name);
        return entry != null ? entry : ACTIONS.get(name);
    }

    private static Map<String, Entry> entries(Entry... values) {
        Map<String, Entry> result = new LinkedHashMap<>();
        for (Entry value : values) result.put(value.name(), value);
        return Map.copyOf(result);
    }
}
