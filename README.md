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
- attribute_display_multipliers: an object mapping attribute registry IDs to positive, finite display multipliers. The multiplier applies to the displayed number after existing percentage conversion, without changing gameplay values; values below 1 are supported. For a non-percentage modifier, a multiplier of 100 turns 0.05 into 5; 0.01 turns a displayed 1000 into 10. If the existing rules format a value as a percentage, the multiplier scales its numeric part and keeps the `%` suffix. Existing `#.##` formatting is retained, so results below about 0.005 may round to 0.
- show_unchanged_modifiers, expand_on_shift, replace_vanilla_attribute_tooltips: behavior switches.
- style_other_mod_attribute_tooltips: restyle and merge the attribute lists other mods add, default true. Covers the Curios slot lists and the Confluence item prefixes; set to false to leave them exactly as those mods render them.
- empty_tooltip_lines: what to do with tooltip lines that carry no text at all, default keep. The rule looks only at the line itself, never at which mod wrote it, and it measures the whole line - a line assembled from parts, such as a renamed item's name or one of this mod's own attribute lines, counts by the text it renders in full and is never mistaken for a blank one.
  - keep: nothing is removed, which is how the mod behaved before this option existed.
  - collapse: a run of consecutive text-less lines becomes a single one, and ones at the very start or end of the tooltip are dropped.
  - remove: every text-less line is dropped. Headers such as "When in mainhand:" stay - only the empty line in front of them goes.
- item_name_colors: an object mapping item registry IDs to the color of that item's name, default empty. The name is recolored after the tooltip is finished and once more while the tooltip is assembled for rendering, so the color wins over the one the item's rarity gives it and over what other mods painted on that line at either stage. Keys are `minecraft:elytra` for one item, `examplemod:*` for a whole mod, or `*` for every item, and the most specific key that matches is the one used. A key without a namespace means the minecraft one. Colors are written as `#rrggbb`, `0xrrggbb` or `rrggbb`, and this now also works for every other color option. The color is applied to every part of the name, so a name another mod colored piece by piece still comes out in your color, while bold, italic and the font are kept. Only the name line changes - the rest of the tooltip is left alone.

Confluence 1.2.8 wraps its attribute block with an empty line above the header and another one between the header and the values, which leaves gaps like the ones around "When affecting summons:". Set empty_tooltip_lines to remove to close them, or to collapse to keep a single gap without the doubles.

An existing config file is never rewritten, so add the key by hand:

```json
"empty_tooltip_lines": "remove"
```

Or delete config/attribute_display_style.json and let the mod regenerate it. Either way, run /attribute_display_style reload afterwards.

Example:

```json
"attribute_display_multipliers": {
  "examplemod:flat_stat": 100,
  "examplemod:large_stat": 0.01
}
```

```json
"item_name_colors": {
  "minecraft:elytra": "0xff55ff",
  "examplemod:*": "#55ff55",
  "*": "ffffff"
}
```

## 中文说明

`attribute_display_multipliers` 是按属性注册 ID 设置的显示倍率，只改变提示中的数字，不影响游戏内实际属性。倍率必须是大于 0 的有限数值，也支持小于 1 的倍率，例如 `0.01`。对于没有按百分比显示的 `0.05`，配置 `100` 后显示为 `5`；倍率 `0.01` 会把 `1000` 缩放为 `10`。若属性本身按百分比显示，倍率会缩放百分号前的数字并保留 `%`。现有 `#.##` 格式不变，因此缩放后小于约 `0.005` 的数值可能会舍入为 `0`。

编辑配置后运行 `/attribute_display_style reload`，或按 F3+T 重新加载。

`empty_tooltip_lines` 控制提示框里"完全没有文字"的行，默认 `keep`（与旧版行为一致，不做任何处理）。判定只看这一行本身有没有文字，不看它是哪个 mod 写的。`collapse` 把连续多条无文字行压成一条，并去掉提示框开头和结尾的无文字行；`remove` 删除全部无文字行，"主手："这类分组标题本身保留，只是它前面那条空行不再显示。

Confluence 1.2.8 会在自己的属性段上方、以及标题与数值之间各插入一条空行，因此"作用于召唤物时："上下都出现空隙。想要完全紧凑就设 `remove`，想保留一处间隔但去掉多余空行就设 `collapse`。已有配置文件不会被自动改写，需要手动加入该键，或删除 `config/attribute_display_style.json` 让 mod 重新生成。

`item_name_colors` 按物品注册 ID 覆盖物品名颜色，默认为空。着色发生在提示框内容全部生成之后，并在提示框被装配成渲染组件时再补一次，因此会盖过稀有度给的名字颜色，也盖过其他 mod 在任一阶段对这一行做的着色。键可以写单个物品 `minecraft:elytra`、某个 mod 的全部物品 `examplemod:*`，或用 `*` 表示所有物品，匹配到的键里最具体的那个生效；省略命名空间时按 `minecraft` 处理。颜色支持 `#rrggbb`、`0xrrggbb` 和 `rrggbb` 三种写法，其它颜色配置项同样支持。颜色会作用到名字的所有片段，所以别的 mod 分段上的颜色也会被统一成你设的颜色，加粗、斜体和字体保持不变。只有物品名这一行会变，提示框其余内容不受影响。

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
