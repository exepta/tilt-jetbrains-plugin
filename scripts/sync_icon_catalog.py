"""Bundle the icon names, groups and SVGs used by TiltUI's icons-catalog example."""

import json
import re
import sys
from pathlib import Path


def main() -> None:
    source = Path(sys.argv[1])
    destination = Path(sys.argv[2])
    glyphs = {}
    for path in (source / "crates/tilt-ui-icons/src/glyphs").glob("*.rs"):
        for name, body in re.findall(
            r'pub\(crate\) const (\w+): &str\s*=\s*r#"(.*?)"#;',
            path.read_text(), re.S,
        ):
            glyphs[name] = body

    icon_source = (source / "crates/tilt-ui-icons/src/icon.rs").read_text()
    # The macro entries contain a variant, a public name and a glyph constant.
    names = {public: glyph for _, public, glyph in re.findall(
        r'(\w+) => \("([\w-]+)", glyphs::(\w+)\),', icon_source,
    )}
    example = (source / "examples/icons-catalog/src-ui/pages/catalog.component.rs").read_text()
    groups = re.findall(
        r'CatalogGroup\s*\{\s*key: "([\w-]+)",\s*title: "([^"]+)",'
        r'\s*description: "([^"]+)",\s*names: &\[(.*?)\],\s*\}',
        example, re.S,
    )
    entries = []
    for key, title, description, block in groups:
        for name in re.findall(r'"([\w-]+)"', block):
            body = glyphs[names[name]]
            svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" '
                   'fill="none" stroke="currentColor" stroke-width="1.8" '
                   'stroke-linecap="round" stroke-linejoin="round"><g>' + body + '</g></svg>')
            entries.append(dict(name=name, category=key, group=title,
                                description=description, svg=svg))
    if len(entries) != len(names) or len({entry['name'] for entry in entries}) != len(names):
        raise SystemExit(f"Incomplete catalog: {len(entries)} entries, {len(names)} TiltUI icons")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(entries, ensure_ascii=False, separators=(',', ':')) + '\n')
    print(f"Wrote {len(entries)} icons to {destination}")


if __name__ == '__main__':
    main()
