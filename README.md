# Attribute Display Style

Client-side Minecraft mod that replaces item attribute tooltips with a compact, configurable display inspired by Apothic Attributes.

Features:

- Groups modifiers by attribute and operation.
- Shows merged values by default and expands the contributing modifiers while holding Shift.
- Describes every line type - its prefix, glyph and all five segment colors - in one place.
- Supports text, Unicode and private-use glyphs, including bitmap icons through resource-pack font providers.

The client config is stored at config/attribute_display_style.json. Reload it at runtime without restarting the game: edit the file, then run /attribute_display_style reload. This is a client-side command, so it works in single player and on any server without operator permissions. Pressing F3+T (resource reload) also reloads the config.

## Display types

Every rendered line belongs to one of six types, and each type owns its prefix, its glyph and the color of every segment:

| Key | Used for |
|---|---|
| positive_single, negative_single | A single modifier |
| positive_merged, negative_merged | A line that merges several modifiers of the same attribute and operation |
| positive_expanded, negative_expanded | A child line revealed by holding Shift |

All six objects have the same seven fields:

- prefix: text placed before the glyph, such as the └ marker on expanded lines. May be empty.
- icon: the glyph before the number, such as ▲, ▼, ▮▮▮, or a private-use glyph backed by a resource-pack font.
- prefix_color, icon_color, sign_color, value_color, name_color: six-digit RGB values such as #55FF55, one per segment of the line.

```json
"positive_single": {
  "prefix": "",
  "icon": "▲",
  "prefix_color": "#55FF55",
  "icon_color": "#55FF55",
  "sign_color": "#55FF55",
  "value_color": "#55FF55",
  "name_color": "#55FF55"
}
```

## Options

- icon_font: the font resource location used for the glyphs. To use a PNG, set an icon to a private-use glyph and point this at a font provider that maps that glyph to your image.
- header_color: the color of headers such as "When in mainhand:" and the Curios equivalents.
- addition_as_percentage_attributes: attribute registry IDs whose base addition should display as a percentage, for example examplemod:critical_hit_chance. A value of 0.05 is shown as +5%. This applies to the vanilla equipment sections and to the Curios lists.
- show_unchanged_modifiers, expand_on_shift, replace_vanilla_attribute_tooltips: behavior switches.
- style_other_mod_attribute_tooltips: restyle and merge the attribute lists other mods add, default true. Covers the Curios slot lists and the Confluence item prefixes; set to false to leave them exactly as those mods render them.

A config written by an earlier version is migrated automatically: the previous flat color, icon, `*_parts` and nested_prefix keys are folded into the six types the next time the file is read, so nothing is lost.

## Weapon totals

Attack damage and attack speed are shown the way vanilla shows them: as the total the weapon reaches with the player's own base value, so a sword with a -2.4 attack speed modifier is displayed as + 1.6 Attack Speed rather than - 2.4. Totals use the same + or - sign as every other line, and they use the single-modifier types. While Shift is held, a total expands into the weapon's own contribution (its base value plus the item modifier) and, when the item is enchanted, the enchantment damage bonus - an iron sword with Sharpness shows + 7 and + 3.

## Other mods

Attribute lines other mods add are merged into this mod's own lines, so the same attribute never shows up twice:

- Curios: the per-slot lists headed by lines such as "When worn as ring:".
- Confluence: the item prefix bonuses. A prefix bonus on attack damage or attack speed is folded into the weapon total, a bonus on another attribute is merged with that attribute's line, and a stat only the prefix knows (attack range, for example) gets its own line in the same style.

Lines that are not attribute-shaped, such as Confluence's mana cost, are kept exactly as that mod rendered them. No dependency on those mods is required and nothing needs configuring; set style_other_mod_attribute_tooltips to false to leave other mods' lines completely untouched.

Supported branches:

- forge-1.20.1
- neoforge-1.20.4
- neoforge-1.21.1
- neoforge-26.1.2
