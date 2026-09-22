package local.attributedisplaystyle.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public String iconFont = "minecraft:default";
    public int headerColor = 0xAAAAAA;
    public List<String> additionAsPercentageAttributes = new ArrayList<>();
    public boolean showUnchangedModifiers = false;
    public boolean expandOnShift = true;
    public boolean replaceVanillaAttributeTooltips = true;
    public boolean styleOtherModAttributeTooltips = true;

    public Style positiveSingle = new Style("", "▲", POSITIVE);
    public Style negativeSingle = new Style("", "▼", NEGATIVE);
    public Style positiveMerged = new Style("", "▮▮▮", POSITIVE_MERGED);
    public Style negativeMerged = new Style("", "▮▮▮", NEGATIVE_MERGED);
    public Style positiveExpanded = new Style(EXPANDED_PREFIX, "▲", EXPANDED);
    public Style negativeExpanded = new Style(EXPANDED_PREFIX, "▼", EXPANDED);

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
        value.showUnchangedModifiers = bool(json, "show_unchanged_modifiers", value.showUnchangedModifiers);
        value.expandOnShift = bool(json, "expand_on_shift", value.expandOnShift);
        value.replaceVanillaAttributeTooltips = bool(json, "replace_vanilla_attribute_tooltips", value.replaceVanillaAttributeTooltips);
        value.styleOtherModAttributeTooltips = bool(json, "style_other_mod_attribute_tooltips", value.styleOtherModAttributeTooltips);

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

    private static int color(JsonObject json, String key, int fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
        try {
            String value = json.get(key).getAsString().trim();
            if (value.startsWith("#")) value = value.substring(1);
            if (value.length() != 6) return fallback;
            return Integer.parseInt(value, 16) & 0xFFFFFF;
        } catch (Exception ignored) {
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
        json.addProperty("show_unchanged_modifiers", value.showUnchangedModifiers);
        json.addProperty("expand_on_shift", value.expandOnShift);
        json.addProperty("replace_vanilla_attribute_tooltips", value.replaceVanillaAttributeTooltips);
        json.addProperty("style_other_mod_attribute_tooltips", value.styleOtherModAttributeTooltips);
        putStyle(json, "positive_single", value.positiveSingle);
        putStyle(json, "negative_single", value.negativeSingle);
        putStyle(json, "positive_merged", value.positiveMerged);
        putStyle(json, "negative_merged", value.negativeMerged);
        putStyle(json, "positive_expanded", value.positiveExpanded);
        putStyle(json, "negative_expanded", value.negativeExpanded);
        return json;
    }

    public boolean isAdditionAsPercentage(String attributeId) {
        if (attributeId == null) return false;
        String normalized = attributeId.trim().toLowerCase(Locale.ROOT);
        return additionAsPercentageAttributes.stream()
            .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
            .anyMatch(normalized::equals);
    }

    public ResourceLocation iconFontId() {
        try {
            return new ResourceLocation(iconFont);
        } catch (Exception ignored) {
            return new ResourceLocation("minecraft", "default");
        }
    }
}
