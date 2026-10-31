package local.attributedisplaystyle.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client configuration. Every rendered attribute line belongs to exactly one of six display types
 * and takes all of its presentation from that type: a single positive or negative modifier, a merged
 * (composite) line, or an expanded child line. The options that are not part of a line's look stay
 * at the top level.
 */
public final class DisplayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Path FILE = Path.of("config", "attribute_display_style.json");

    private static final int POSITIVE = 0x55FF55;
    private static final int NEGATIVE = 0xFF5555;
    private static final int POSITIVE_MERGED = 0x7A7AF9;
    private static final int NEGATIVE_MERGED = 0xF93131;
    private static final int EXPANDED = 0x777777;
    private static final String EXPANDED_PREFIX = "└ ";

    /** The look of one display type: its prefix, glyph and the color of every rendered segment. */
    public static final class Style {
        public String prefix;
        public String icon;
        public int prefixColor;
        public int iconColor;
        public int signColor;
        public int valueColor;
        public int nameColor;

        Style(String prefix, String icon, int color) {
            this(prefix, icon, color, color, color, color, color);
        }

        Style(String prefix, String icon, int prefixColor, int iconColor, int signColor, int valueColor, int nameColor) {
            this.prefix = prefix;
            this.icon = icon;
            this.prefixColor = prefixColor;
            this.iconColor = iconColor;
            this.signColor = signColor;
            this.valueColor = valueColor;
            this.nameColor = nameColor;
        }
    }

    /** How the lines of a tooltip that carry no text at all are handled, whoever wrote them. */
    public enum EmptyLineMode {
        /** Leave every line as it was written. */
        KEEP,
        /** A run of text-less lines becomes one; leading and trailing ones are dropped. */
        COLLAPSE,
        /** Every text-less line is dropped, no matter which mod added it. */
        REMOVE
    }

    public String iconFont = "attribute_display_style:icons";
    public int headerColor = 0xAAAAAA;
    public List<String> additionAsPercentageAttributes = new ArrayList<>();
    public Map<String, Double> attributeDisplayMultipliers = new LinkedHashMap<>();
    public Map<String, Integer> itemNameColors = new LinkedHashMap<>();
    public boolean showUnchangedModifiers = false;
    public boolean expandOnShift = true;
    public boolean replaceVanillaAttributeTooltips = true;
    public boolean styleOtherModAttributeTooltips = true;
    public EmptyLineMode emptyTooltipLines = EmptyLineMode.KEEP;

    public Style positiveSingle = new Style("", "\uDB80\uDC15", 0x55FF55, 0xFFFFFF, 0x777777, 0xFFFFFF, 0x777777);
    public Style negativeSingle = new Style("", "\uDB80\uDC15", 0xFF5555, 0xFFFFFF, 0x777777, 0xFF5555, 0x777777);
    public Style positiveMerged = new Style("", "\uDB80\uDC15", 0x55FF55, 0xFFFFFF, 0x777777, 0xFFFFFF, 0x777777);
    public Style negativeMerged = new Style("", "\uDB80\uDC15", 0xF93131, 0xF93131, 0xF93131, 0xF93131, 0xF93131);
    public Style positiveExpanded = new Style(" ", "\uDB80\uDC13", 0x777777, 0x777777, 0x777777, 0x5555FF, 0x777777);
    public Style negativeExpanded = new Style(" ", "\uDB80\uDC14", 0x777777, 0x777777, 0x777777, 0xFF5555, 0x777777);

    private static DisplayConfig current = new DisplayConfig();

    private DisplayConfig() {}

    public static DisplayConfig get() {
        return current;
    }

    public static void load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (!Files.exists(FILE)) {
                Files.writeString(FILE, GSON.toJson(toJson(current)) + System.lineSeparator(), StandardCharsets.UTF_8);
            } else {
                JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
                current = fromJson(json);
            }
        } catch (Exception e) {
            System.err.println("[attribute_display_style] Failed to load config: " + e);
            current = new DisplayConfig();
        }
    }

    private static DisplayConfig fromJson(JsonObject json) {
        DisplayConfig value = new DisplayConfig();
        value.iconFont = text(json, "icon_font", value.iconFont);
        value.headerColor = color(json, "header_color", value.headerColor);
        value.additionAsPercentageAttributes = strings(json, "addition_as_percentage_attributes");
        value.attributeDisplayMultipliers = multipliers(json, "attribute_display_multipliers");
        value.itemNameColors = itemColors(json, "item_name_colors");
        value.showUnchangedModifiers = bool(json, "show_unchanged_modifiers", value.showUnchangedModifiers);
        value.expandOnShift = bool(json, "expand_on_shift", value.expandOnShift);
        value.replaceVanillaAttributeTooltips = bool(json, "replace_vanilla_attribute_tooltips", value.replaceVanillaAttributeTooltips);
        value.styleOtherModAttributeTooltips = bool(json, "style_curio_attribute_tooltips",
            bool(json, "style_other_mod_attribute_tooltips", value.styleOtherModAttributeTooltips));
        value.emptyTooltipLines = emptyLineMode(json, "empty_tooltip_lines", value.emptyTooltipLines);

        if (json.has("positive_single") || json.has("negative_single")) {
            value.positiveSingle = style(json, "positive_single", value.positiveSingle);
            value.negativeSingle = style(json, "negative_single", value.negativeSingle);
            value.positiveMerged = style(json, "positive_merged", value.positiveMerged);
            value.negativeMerged = style(json, "negative_merged", value.negativeMerged);
            value.positiveExpanded = style(json, "positive_expanded", value.positiveExpanded);
            value.negativeExpanded = style(json, "negative_expanded", value.negativeExpanded);
        } else {
            migratePreviousFormat(json, value);
        }
        return value;
    }

    /** Reads the flat pre-1.0.2 keys so an existing file keeps its icons, colors and prefix. */
    private static void migratePreviousFormat(JsonObject json, DisplayConfig value) {
        int positive = color(json, "positive_color", POSITIVE);
        int negative = color(json, "negative_color", NEGATIVE);
        int merged = color(json, "merged_color", POSITIVE_MERGED);
        int mergedNegative = color(json, "merged_negative_color", NEGATIVE_MERGED);
        int expanded = color(json, "nested_color", EXPANDED);
        String prefix = text(json, "nested_prefix", EXPANDED_PREFIX);
        String defaultIcon = text(json, "default_icon", "▮▮▮");
        String positiveIcon = text(json, "positive_icon", "▲");
        String negativeIcon = text(json, "negative_icon", "▼");
        String expandedIcon = text(json, "nested_icon", null);

        value.positiveSingle = legacyStyle(json, "positive_parts", "", positiveIcon, positive);
        value.negativeSingle = legacyStyle(json, "negative_parts", "", negativeIcon, negative);
        value.positiveMerged = legacyStyle(json, "merged_parts", "", text(json, "merged_icon", defaultIcon), merged);
        value.negativeMerged = legacyStyle(json, "merged_negative_parts", "", text(json, "merged_negative_icon", defaultIcon), mergedNegative);
        value.positiveExpanded = legacyStyle(json, "nested_parts", prefix, expandedIcon == null ? positiveIcon : expandedIcon, expanded);
        value.negativeExpanded = legacyStyle(json, "nested_parts", prefix, expandedIcon == null ? negativeIcon : expandedIcon, expanded);
    }

    private static Style legacyStyle(JsonObject json, String partsKey, String prefix, String icon, int base) {
        if (!json.has(partsKey) || !json.get(partsKey).isJsonObject()) return new Style(prefix, icon, base);
        JsonObject parts = json.getAsJsonObject(partsKey);
        return new Style(prefix, icon,
            color(parts, "prefix_color", base),
            color(parts, "icon_color", base),
            color(parts, "sign_color", base),
            color(parts, "value_color", base),
            color(parts, "name_color", base));
    }

    private static Style style(JsonObject json, String key, Style fallback) {
        if (!json.has(key) || !json.get(key).isJsonObject()) return fallback;
        JsonObject object = json.getAsJsonObject(key);
        return new Style(
            text(object, "prefix", fallback.prefix),
            text(object, "icon", fallback.icon),
            color(object, "prefix_color", fallback.prefixColor),
            color(object, "icon_color", fallback.iconColor),
            color(object, "sign_color", fallback.signColor),
            color(object, "value_color", fallback.valueColor),
            color(object, "name_color", fallback.nameColor));
    }

    /** Reads one color key from an object. */
    private static int color(JsonObject json, String key, int fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
        return parseColor(json.get(key).getAsString(), fallback);
    }

    /** Reads a color written as {@code #rrggbb}, {@code 0xrrggbb} or {@code rrggbb}. */
    private static int parseColor(String value, int fallback) {
        try {
            String text = value.trim().toLowerCase(Locale.ROOT);
            if (text.startsWith("#")) text = text.substring(1);
            else if (text.startsWith("0x")) text = text.substring(2);
            if (text.length() != 6) return fallback;
            return Integer.parseInt(text, 16) & 0xFFFFFF;
        } catch (Exception | LinkageError ignored) {
            return fallback;
        }
    }

    private static String text(JsonObject json, String key, String fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
        return json.get(key).getAsString();
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
        return json.get(key).getAsBoolean();
    }

    /** Reads one of the three empty-line modes; an unknown value keeps the fallback. */
    private static EmptyLineMode emptyLineMode(JsonObject json, String key, EmptyLineMode fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
        try {
            return EmptyLineMode.valueOf(json.get(key).getAsString().trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> values = new ArrayList<>();
        if (!json.has(key) || !json.get(key).isJsonArray()) return values;
        json.getAsJsonArray(key).forEach(element -> {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                values.add(element.getAsString());
            }
        });
        return values;
    }

    private static void putColor(JsonObject json, String key, int value) {
        json.addProperty(key, String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF));
    }

    private static void putStyle(JsonObject json, String key, Style style) {
        JsonObject object = new JsonObject();
        object.addProperty("prefix", style.prefix);
        object.addProperty("icon", style.icon);
        putColor(object, "prefix_color", style.prefixColor);
        putColor(object, "icon_color", style.iconColor);
        putColor(object, "sign_color", style.signColor);
        putColor(object, "value_color", style.valueColor);
        putColor(object, "name_color", style.nameColor);
        json.add(key, object);
    }

    private static JsonObject toJson(DisplayConfig value) {
        JsonObject json = new JsonObject();
        json.addProperty("icon_font", value.iconFont);
        putColor(json, "header_color", value.headerColor);
        JsonArray attributes = new JsonArray();
        value.additionAsPercentageAttributes.forEach(attribute -> attributes.add(new JsonPrimitive(attribute)));
        json.add("addition_as_percentage_attributes", attributes);
        JsonObject multipliers = new JsonObject();
        if (value.attributeDisplayMultipliers != null) {
            value.attributeDisplayMultipliers.forEach((attributeId, multiplier) -> {
                String normalized = normalizeAttributeId(attributeId);
                if (normalized != null && multiplier != null && isValidMultiplier(multiplier)) {
                    multipliers.addProperty(normalized, multiplier);
                }
            });
        }
        json.add("attribute_display_multipliers", multipliers);
        JsonObject nameColors = new JsonObject();
        if (value.itemNameColors != null) {
            value.itemNameColors.forEach((itemId, color) -> {
                if (itemId != null && color != null && color >= 0) putColor(nameColors, itemId, color);
            });
        }
        json.add("item_name_colors", nameColors);
        json.addProperty("show_unchanged_modifiers", value.showUnchangedModifiers);
        json.addProperty("expand_on_shift", value.expandOnShift);
        json.addProperty("replace_vanilla_attribute_tooltips", value.replaceVanillaAttributeTooltips);
        json.addProperty("style_curio_attribute_tooltips", value.styleOtherModAttributeTooltips);
        json.addProperty("empty_tooltip_lines", value.emptyTooltipLines.name().toLowerCase(Locale.ROOT));
        putStyle(json, "positive_single", value.positiveSingle);
        putStyle(json, "negative_single", value.negativeSingle);
        putStyle(json, "positive_merged", value.positiveMerged);
        putStyle(json, "negative_merged", value.negativeMerged);
        putStyle(json, "positive_expanded", value.positiveExpanded);
        putStyle(json, "negative_expanded", value.negativeExpanded);
        return json;
    }

    /** Reads the item name colors, keyed by item registry id; an unparseable entry is skipped. */
    private static Map<String, Integer> itemColors(JsonObject json, String key) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (!json.has(key) || !json.get(key).isJsonObject()) return values;

        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject(key).entrySet()) {
            String itemId = normalizeItemId(entry.getKey());
            JsonElement element = entry.getValue();
            if (itemId == null || !element.isJsonPrimitive()) continue;

            int color = parseColor(element.getAsString(), -1);
            if (color >= 0) values.put(itemId, color);
        }
        return values;
    }

    private static Map<String, Double> multipliers(JsonObject json, String key) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (!json.has(key) || !json.get(key).isJsonObject()) return values;

        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject(key).entrySet()) {
            JsonElement element = entry.getValue();
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) continue;

            double multiplier;
            try {
                multiplier = element.getAsDouble();
            } catch (RuntimeException ignored) {
                continue;
            }
            String attributeId = normalizeAttributeId(entry.getKey());
            if (attributeId != null && isValidMultiplier(multiplier)) values.put(attributeId, multiplier);
        }
        return values;
    }

    private static String normalizeAttributeId(String attributeId) {
        if (attributeId == null || attributeId.isBlank()) return null;
        try {
            return new ResourceLocation(attributeId.trim().toLowerCase(Locale.ROOT)).toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Normalizes an item id, allowing the {@code mod:*} and {@code *} wildcards that cover a whole mod or
     * every item. An id without a namespace gets the minecraft one, and anything malformed gives null.
     */
    private static String normalizeItemId(String itemId) {
        if (itemId == null) return null;
        String trimmed = itemId.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        if ("*".equals(trimmed)) return trimmed;

        String namespace = "minecraft";
        String path = trimmed;
        int separator = trimmed.indexOf(':');
        if (separator >= 0) {
            namespace = trimmed.substring(0, separator);
            path = trimmed.substring(separator + 1);
        }
        if ("*".equals(path)) return namespace.isEmpty() ? "*" : namespace + ":*";
        if (path.isEmpty()) return null;
        try {
            return new ResourceLocation(namespace, path).toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isValidMultiplier(double multiplier) {
        return Double.isFinite(multiplier) && multiplier > 0D;
    }

    public double attributeDisplayMultiplier(String attributeId) {
        String normalized = normalizeAttributeId(attributeId);
        if (normalized == null || attributeDisplayMultipliers == null) return 1D;
        Double multiplier = attributeDisplayMultipliers.get(normalized);
        return multiplier != null && isValidMultiplier(multiplier) ? multiplier : 1D;
    }

    /**
     * The color configured for an item, looking the exact id up first, then its mod's wildcard and
     * finally the global one. Returns null when nothing is configured, which leaves the name as it is.
     */
    public Integer itemNameColor(String itemId) {
        String normalized = normalizeItemId(itemId);
        if (normalized == null || itemNameColors == null || itemNameColors.isEmpty()) return null;

        Integer color = itemNameColors.get(normalized);
        if (color == null) {
            int separator = normalized.indexOf(':');
            if (separator > 0) color = itemNameColors.get(normalized.substring(0, separator) + ":*");
        }
        if (color == null) color = itemNameColors.get("*");
        return color == null || color < 0 ? null : color;
    }

    public boolean isAdditionAsPercentage(String attributeId) {
        if (attributeId == null) return false;
        String normalized = attributeId.trim().toLowerCase(Locale.ROOT);
        return additionAsPercentageAttributes.stream()
            .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
            .anyMatch(normalized::equals);
    }

    public boolean isAdditionAsPercentage(String attributeId, String descriptionId) {
        return isAdditionAsPercentage(attributeId)
            || isAdditionAsPercentage(attributeIdFromDescription(descriptionId));
    }

    public static String attributeIdFromDescription(String descriptionId) {
        if (descriptionId == null) return null;
        String prefix = descriptionId.startsWith("attribute.name.")
            ? "attribute.name."
            : descriptionId.startsWith("attribute.") ? "attribute." : null;
        if (prefix == null) return null;
        String path = descriptionId.substring(prefix.length());
        int separator = path.indexOf('.');
        if (separator <= 0 || separator == path.length() - 1) return null;
        return path.substring(0, separator) + ':' + path.substring(separator + 1);
    }

    public ResourceLocation iconFontId() {
        try {
            return new ResourceLocation(iconFont);
        } catch (Exception ignored) {
            return new ResourceLocation("minecraft", "default");
        }
    }
}
