package com.tiltus.bevywizard.template;

import org.rust.lang.core.psi.RsMetaItem;
import org.rust.lang.core.psi.RsMetaItemArgs;
import org.rust.lang.core.psi.RsOuterAttr;

import java.util.ArrayList;
import java.util.List;

/** Reads TiltUI registrations from Rust PSI attributes, including qualified macro paths. */
final class TiltRustAttributes {
    private TiltRustAttributes() {}

    static boolean has(List<RsOuterAttr> attributes, String name) {
        return attributes.stream().anyMatch(attribute -> name(attribute.getMetaItem()).equals(name));
    }

    static boolean derives(List<RsOuterAttr> attributes, String name) {
        return attributes.stream().map(RsOuterAttr::getMetaItem)
                .filter(item -> name(item).equals("derive"))
                .map(RsMetaItem::getMetaItemArgs).filter(args -> args != null)
                .flatMap(args -> args.getMetaItemList().stream())
                .anyMatch(item -> name(item).equals(name));
    }

    static boolean mutableStore(List<RsOuterAttr> attributes) {
        return attributes.stream().map(RsOuterAttr::getMetaItem)
                .filter(item -> name(item).equals("ui_store"))
                .map(RsMetaItem::getMetaItemArgs).filter(args -> args != null)
                .anyMatch(args -> args.getMetaItemList().stream()
                        .anyMatch(item -> name(item).equals("mutable")));
    }

    static List<String> stringArguments(List<RsOuterAttr> attributes, String name) {
        for (RsOuterAttr attribute : attributes) {
            RsMetaItem item = attribute.getMetaItem();
            if (!name(item).equals(name)) continue;
            RsMetaItemArgs args = item.getMetaItemArgs();
            if (args == null) continue;
            List<String> result = new ArrayList<>();
            for (var literal : args.getLitExprList()) {
                String value = stringLiteral(literal.getText());
                if (value == null) return List.of();
                result.add(value);
            }
            return result;
        }
        return List.of();
    }

    private static String name(RsMetaItem item) {
        if (item == null || item.getPath() == null) return "";
        String path = item.getPath().getText();
        int separator = path.lastIndexOf("::");
        return separator < 0 ? path : path.substring(separator + 2);
    }

    private static String stringLiteral(String text) {
        int first = text.indexOf('"');
        int last = text.lastIndexOf('"');
        if (first < 0 || last <= first) return null;
        String raw = text.substring(first + 1, last);
        if (text.startsWith("r")) return raw;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(++i);
                result.append(switch (next) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> next;
                });
            } else result.append(ch);
        }
        return result.toString();
    }
}
