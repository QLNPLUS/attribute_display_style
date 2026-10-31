# Changelog

## 1.4.0

- Added `item_name_colors`, which colors an item's own name by registry ID. Keys are `minecraft:elytra` for a single item, `examplemod:*` for everything one mod adds, or `*` for every item, and the most specific key that matches is the one used; a key without a namespace means `minecraft:`. Colors are written as `#rrggbb`, `0xrrggbb` or `rrggbb`. The name is recolored after the tooltip has been built, so the color wins over the item's rarity color and over what other mods painted on that line, while bold, italic and the font are kept. Only the name line changes.

## 1.3.0

- Added `empty_tooltip_lines` (`keep` / `collapse` / `remove`) for tooltip lines that carry no text at all, whoever wrote them. `remove` closes the gaps Confluence 1.2.8 wraps around its attribute block; section headers themselves are untouched.

## 1.2.0

- Added per-attribute display multipliers for item attribute tooltips. You can enlarge or shrink displayed values, including with factors below 1, without changing gameplay attributes.

## 1.1.0

- Modifiers for the same attribute are now grouped into one line. Hold Shift to inspect each contributing modifier and its operation.
- Attack damage and attack speed now show their combined totals, including the player's base values.
- Added built-in bitmap arrow icons and configurable glyph styling for merged and expanded modifiers.
