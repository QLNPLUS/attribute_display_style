# Attribute Display Style

Client-side Minecraft mod that replaces item attribute tooltips with a compact, configurable display inspired by Apothic Attributes.

Features:

- Groups modifiers by attribute and operation.
- Shows merged values by default and expands the contributing modifiers while holding Shift.
- Configures all display colors through client JSON.
- Configures positive, negative, and default/merged icons as text or Unicode glyphs.
- Supports bitmap icons through Minecraft font providers supplied by a resource pack.

The client config is stored at config/attribute_display_style.json. The three icon fields accept text directly. To use a PNG, set an icon to a private-use glyph and set its font to a font provider that maps that glyph to your PNG.

Supported branches:

- forge-1.20.1
- neoforge-1.20.4
- neoforge-1.21.1
- neoforge-26.1.2

