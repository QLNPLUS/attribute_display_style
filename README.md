# Attribute Display Style

Client-side Minecraft mod that replaces item attribute tooltips with a compact, configurable display inspired by Apothic Attributes.

Features:

- Groups modifiers by attribute and operation.
- Shows merged values by default and expands the contributing modifiers while holding Shift.
- Configures all display colors through client JSON.
- Configures positive, negative, and default/merged icons as text or Unicode glyphs.
- Supports bitmap icons through Minecraft font providers supplied by a resource pack.

The client config is stored at config/attribute_display_style.json. The three icon fields accept text directly. To use a PNG, set an icon to a private-use glyph and set its font to a font provider that maps that glyph to your PNG.

The generated config exposes these display controls:

- positive_color, negative_color, merged_color, default_color, header_color, nested_color: six-digit RGB values such as #55FF55.
- positive_icon, negative_icon, default_icon: text, Unicode symbols, or private-use glyphs.
- icon_font: the font resource location used for the icon glyphs.
- addition_as_percentage_attributes: attribute registry IDs whose base addition should display as a percentage, for example examplemod:critical_hit_chance. A value of 0.05 is shown as +5%.
- nested_prefix: the prefix shown before expanded modifier lines.
- show_unchanged_modifiers, expand_on_shift, replace_vanilla_attribute_tooltips: behavior switches.

The default_icon is used for the merged line before Shift expansion. Expanded child lines use the positive or negative icon according to their value.

Supported branches:

- forge-1.20.1
- neoforge-1.20.4
- neoforge-1.21.1
- neoforge-26.1.2
