package local.attributedisplaystyle.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DisplayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config", "attribute_display_style.json");

    public int positiveColor = 0x55FF55;
    public int negativeColor = 0xFF5555;
    public int mergedColor = 0x7A7AF9;
    public int baseColor = 0x55AA55;
    public int defaultColor = 0xAAAAAA;
    public int headerColor = 0xAAAAAA;
    public int nestedColor = 0x777777;
    public String positiveIcon = "▲";
    public String negativeIcon = "▼";
    public String defaultIcon = "▮▮▮";
    public String nestedPrefix = "└ ";
    public String iconFont = "minecraft:default";
    public List<String> additionAsPercentageAttributes = new ArrayList<>();
    public boolean showUnchangedModifiers = false;
    public boolean expandOnShift = true;
    public boolean replaceVanillaAttributeTooltips = true;

    private static DisplayConfig current = new DisplayConfig();

    private DisplayConfig() {}

    public static DisplayConfig get() {
        return current;
    }

    public static void load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (!Files.exists(FILE)) {
                Files.writeString(FILE, GSON.toJson(current) + System.lineSeparator(), StandardCharsets.UTF_8);
                return;
            }
            JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            current = fromJson(json);
        } catch (Exception ignored) {
            current = new DisplayConfig();
        }
    }

    private static DisplayConfig fromJson(JsonObject json) {
        DisplayConfig value = new DisplayConfig();
        value.positiveColor = color(json, "positive_color", value.positiveColor);
        value.negativeColor = color(json, "negative_color", value.negativeColor);
        value.mergedColor = color(json, "merged_color", value.mergedColor);
        value.baseColor = color(json, "base_color", value.baseColor);
        value.defaultColor = color(json, "default_color", value.defaultColor);
        value.headerColor = color(json, "header_color", value.headerColor);
        value.nestedColor = color(json, "nested_color", value.nestedColor);
        value.positiveIcon = text(json, "positive_icon", value.positiveIcon);
        value.negativeIcon = text(json, "negative_icon", value.negativeIcon);
        value.defaultIcon = text(json, "default_icon", value.defaultIcon);
        value.nestedPrefix = text(json, "nested_prefix", value.nestedPrefix);
        value.iconFont = text(json, "icon_font", value.iconFont);
        value.additionAsPercentageAttributes = strings(json, "addition_as_percentage_attributes");
        value.showUnchangedModifiers = bool(json, "show_unchanged_modifiers", value.showUnchangedModifiers);
        value.expandOnShift = bool(json, "expand_on_shift", value.expandOnShift);
        value.replaceVanillaAttributeTooltips = bool(json, "replace_vanilla_attribute_tooltips", value.replaceVanillaAttributeTooltips);
        return value;
    }

    private static int color(JsonObject json, String key, int fallback) {
        if (!json.has(key)) return fallback;
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
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
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
