# Changelog

## 1.5.0

- Added `rewrite_confluence_tooltip_lines` (default on), which rewrites Confluence's computed weapon values into this mod's own attribute lines: a line Confluence prints as `击退：0.05` becomes an ordinary attribute line, so it uses the configured icons, colors and `attribute_display_multipliers` like every other line. A computed value of zero drops the line instead of showing it. Other Confluence tooltip text is left alone.

## 1.4.2

- Attribute lines written by other mods that this mod cannot rebuild from the item's own modifiers are now kept instead of being dropped, so Confluence's computed base values and its summon modifiers (`作用于召唤物时`) show up again.
- Display multipliers now resolve the attribute by the name a line renders to, so mods that render an attribute under their own key still pick up the configured factor.
- `/attribute_display_style debug` now follows the item it was run on and records the tooltip at each stage, so hovering a different item no longer pollutes the log.

## 1.4.1

- Fixed `item_name_colors` losing to mods that recolor the finished title while the tooltip is assembled into render components, such as Confluence's rarity remapping. The name is now recolored once more at that stage, so the configured color is the one that shows. No config change is needed.

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
