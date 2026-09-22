package local.attributedisplaystyle.client;

import local.attributedisplaystyle.config.DisplayConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Restyles the attribute lines other mods put into a tooltip so they use the same display types as
 * this mod's own lines and merge the same way.
 *
 * <p>Two shapes are handled:
 * <ul>
 * <li>Curios sections: a {@code curios.modifiers.<slot>} header followed by
 *     {@code attribute.modifier.plus|take|equals.<operation>} lines.</li>
 * <li>Loose attribute lines: any {@code attribute.modifier.*} line, plus the
 *     {@code prefix.confluence.tooltip.add|plus|take} lines Confluence uses for its item prefixes,
 *     wherever they appear in the tooltip.</li>
 * </ul>
 * Working from the rendered components keeps this free of any dependency on those mods; if a future
 * version changes the keys, the lines simply keep their original look.
 */
final class TooltipCompat {
    private static final String HEADER_PREFIX = "curios.modifiers.";
    private static final String SLOT_MODIFIER_PREFIX = "curios.modifiers.slots.";
    private static final String VALUE_PREFIX = "attribute.modifier.";
    private static final String PORTLIB_PREFIX = "portlib.modifier.";
    private static final String CONFLUENCE_PREFIX = "prefix.confluence.tooltip.";
    private static final String PLUS = "plus";
    private static final String TAKE = "take";
    private static final String EQUALS = "equals";
    private static final Map<String, String> ATTRIBUTE_IDS = new HashMap<>();

    private TooltipCompat() {}

    /** Restyles and merges the attribute list of every Curios slot section. */
    static void styleSections(List<Component> tooltip, TooltipFlag flag) {
        if (!DisplayConfig.get().styleOtherModAttributeTooltips) return;

        for (int index = 0; index < tooltip.size(); index++) {
            TranslatableContents header = translatable(tooltip.get(index), HEADER_PREFIX);
            if (header == null || header.getKey().startsWith(SLOT_MODIFIER_PREFIX)) continue;

            int end = index + 1;
            while (end < tooltip.size() && isSectionBody(tooltip.get(end))) end++;
            if (end == index + 1) continue;

            List<Component> styled = new ArrayList<>();
            int line = index + 1;
            while (line < end) {
                List<Entry> run = parseRun(tooltip, line, end);
                if (run.isEmpty()) {
                    styled.add(tooltip.get(line));
                    line++;
                    continue;
                }
                line += run.size();
                renderRun(run, styled, flag);
            }

            tooltip.set(index, tooltip.get(index).copy()
                .withStyle(AttributeTooltipHandler.style(DisplayConfig.get().headerColor)));
            tooltip.subList(index + 1, end).clear();
            tooltip.addAll(index + 1, styled);
            index += styled.size();
        }
    }

    /**
     * Restyles and merges the attribute lines other mods left outside a Curios section. Entries whose
     * attribute this mod already renders are dropped instead, so nothing is listed twice.
     */
    static void styleLooseLines(List<Component> tooltip, TooltipFlag flag, Set<String> covered) {
        if (!DisplayConfig.get().styleOtherModAttributeTooltips) return;

        int index = 0;
        while (index < tooltip.size()) {
            List<Entry> run = parseRun(tooltip, index, tooltip.size());
            if (run.isEmpty()) {
                index++;
                continue;
            }
            List<Entry> kept = new ArrayList<>();
            for (Entry entry : run) {
                if (!isCovered(entry, covered)) kept.add(entry);
            }
            List<Component> styled = new ArrayList<>();
            renderRun(kept, styled, flag);
            tooltip.subList(index, index + run.size()).clear();
            tooltip.addAll(index, styled);
            index += styled.size();
        }
    }

    private static List<Entry> parseRun(List<Component> tooltip, int from, int to) {
        List<Entry> run = new ArrayList<>();
        for (int index = from; index < to; index++) {
            Entry entry = parse(tooltip.get(index));
            if (entry == null) break;
            run.add(entry);
        }
        return run;
    }

    /** Parses a single line, or returns {@code null} when it is not an attribute-shaped line. */
    static Entry parseLine(Component line) {
        return parse(line);
    }

    /**
     * Whether another mod's line describes an attribute this mod already renders. Both the translation
     * key and the text it resolves to are compared, because mods name attributes either way.
     */
    static boolean isCovered(Entry entry, Set<String> covered) {
        if (covered.contains(entry.nameKey())) return true;
        String text = nameText(entry.name());
        return !text.isEmpty() && covered.contains(text);
    }

    private static String nameText(Component name) {
        try {
            return name.getString();
        } catch (Exception | LinkageError ignored) {
            return "";
        }
    }

    /** Parses every attribute-shaped line in [from, to); lines of any other shape are skipped. */
    static List<Entry> parse(List<Component> tooltip, int from, int to) {
        List<Entry> entries = new ArrayList<>();
        for (int index = from; index < to; index++) {
            Entry entry = parse(tooltip.get(index));
            if (entry != null) entries.add(entry);
        }
        return entries;
    }

    /** Lines in [from, to) that are not attribute-shaped and therefore keep their own rendering. */
    static List<Component> unparsed(List<Component> tooltip, int from, int to) {
        List<Component> leftovers = new ArrayList<>();
        for (int index = from; index < to; index++) {
            if (parse(tooltip.get(index)) == null) leftovers.add(tooltip.get(index));
        }
        return leftovers;
    }

    /** Renders parsed entries on their own, merging the ones that share a name, operation and style. */
    static void renderEntries(List<Entry> entries, List<Component> output, TooltipFlag flag) {
        if (entries.isEmpty()) return;
        renderRun(entries, output, flag);
    }

    private static boolean isSectionBody(Component line) {
        return !AttributeTooltipHandler.isEmpty(line) && translatable(line, HEADER_PREFIX) == null;
    }

    private static void renderRun(List<Entry> run, List<Component> output, TooltipFlag flag) {
        List<List<Entry>> groups = new ArrayList<>();
        Map<String, List<Entry>> byKey = new LinkedHashMap<>();
        for (Entry entry : run) {
            String key = entry.groupKey();
            List<Entry> group;
            if (key == null) {
                group = new ArrayList<>();
                groups.add(group);
            } else {
                group = byKey.get(key);
                if (group == null) {
                    group = new ArrayList<>();
                    byKey.put(key, group);
                    groups.add(group);
                }
            }
            group.add(entry);
        }

        DisplayConfig config = DisplayConfig.get();
        for (List<Entry> group : groups) {
            Entry first = group.get(0);
            if (group.size() == 1) {
                output.add(AttributeTooltipHandler.formatLine(first.operation(), first.amount(), first.percentage(),
                    first.name(), flag, AttributeTooltipHandler.singleStyle(first.amount())));
                continue;
            }

            double total = 0D;
            for (Entry entry : group) total += entry.amount();
            output.add(AttributeTooltipHandler.formatLine(first.operation(), total, first.percentage(), first.name(), flag,
                AttributeTooltipHandler.mergedStyle(total)));
            if (config.expandOnShift && Screen.hasShiftDown()) {
                for (Entry entry : group) {
                    output.add(AttributeTooltipHandler.formatLine(entry.operation(), entry.amount(), entry.percentage(),
                        entry.name(), flag, AttributeTooltipHandler.expandedStyle(entry.amount())));
                }
            }
        }
    }

    private static Entry parse(Component line) {
        TranslatableContents contents = translatable(line, VALUE_PREFIX);
        if (contents != null) return parseAttributeModifier(contents);
        contents = translatable(line, PORTLIB_PREFIX);
        if (contents != null) return parsePortlib(contents);
        contents = translatable(line, CONFLUENCE_PREFIX);
        if (contents != null) return parseConfluence(contents);
        return null;
    }

    /**
     * MesdagPortLib renders attribute values as {@code portlib.modifier.<plus|take|bool>} with the
     * formatted value as a component ({@code portlib.value.flat} / {@code portlib.value.percent}) and
     * the attribute name as {@code Component.translatable(attribute.getDescriptionId())}.
     */
    private static Entry parsePortlib(TranslatableContents contents) {
        String kind = contents.getKey().substring(PORTLIB_PREFIX.length());
        if (!PLUS.equals(kind) && !TAKE.equals(kind) && !"bool".equals(kind)) return null;

        Object[] args = contents.getArgs();
        if (args.length < 2) return null;
        double[] value = portlibValue(args[0]);
        if (Double.isNaN(value[0])) return null;

        double magnitude = value[0];
        boolean percentage = value[1] != 0D;
        boolean negative = magnitude < 0D || (TAKE.equals(kind) && magnitude > 0D);
        Component name = name(args[1]);
        return new Entry(Operation.ADDITION, negative ? -Math.abs(magnitude) : Math.abs(magnitude),
            percentage, name, nameKey(name), false);
    }

    /** Returns the value and whether it is a percentage, read from the formatted value component. */
    private static double[] portlibValue(Object argument) {
        if (argument instanceof Component component) {
            if (component.getContents() instanceof TranslatableContents contents) {
                Object[] args = contents.getArgs();
                if (args.length >= 1) {
                    double value = number(args[0]);
                    if (!Double.isNaN(value)) {
                        return new double[] {value, contents.getKey().contains("percent") ? 1D : 0D};
                    }
                }
            }
            String text = nameText(component);
            return new double[] {number(text), text.endsWith("%") ? 1D : 0D};
        }
        String text = String.valueOf(argument);
        return new double[] {number(text), text.endsWith("%") ? 1D : 0D};
    }

    /** {@code attribute.modifier.<plus|take|equals>.<operation>} with the amount and the attribute name. */
    private static Entry parseAttributeModifier(TranslatableContents contents) {
        String[] parts = contents.getKey().split("\\.");
        Object[] args = contents.getArgs();
        if (parts.length < 4 || args.length < 2) return null;

        Operation operation;
        try {
            operation = Operation.values()[Integer.parseInt(parts[3])];
        } catch (RuntimeException ignored) {
            return null;
        }

        double magnitude = number(args[0]);
        if (Double.isNaN(magnitude)) return null;

        String kind = parts[2];
        Component name = name(args[1]);
        double amount = TAKE.equals(kind) ? -magnitude : magnitude;
        boolean percentage = operation != Operation.ADDITION;
        if (!percentage && DisplayConfig.get().isAdditionAsPercentage(attributeId(name))) {
            percentage = true;
            amount *= 100D;
        }
        return new Entry(operation, amount, percentage, name, nameKey(name), EQUALS.equals(kind));
    }

    /** Confluence renders its item prefixes as {@code prefix.confluence.tooltip.<add|plus|take>}. */
    private static Entry parseConfluence(TranslatableContents contents) {
        String kind = contents.getKey().substring(CONFLUENCE_PREFIX.length());
        if (!PLUS.equals(kind) && !TAKE.equals(kind) && !"add".equals(kind)) return null;

        Object[] args = contents.getArgs();
        if (args.length < 2) return null;
        double magnitude = number(args[0]);
        if (Double.isNaN(magnitude)) return null;

        Component name = name(args[1]);
        boolean percentage = PLUS.equals(kind) || TAKE.equals(kind);
        double amount = TAKE.equals(kind) ? -magnitude : magnitude;
        return new Entry(Operation.ADDITION, amount, percentage, name, nameKey(name), false);
    }

    private static Component name(Object argument) {
        return argument instanceof Component component ? component : Component.literal(String.valueOf(argument));
    }

    private static double number(Object argument) {
        String text = (argument instanceof String value ? value : String.valueOf(argument)).trim();
        if (text.endsWith("%")) text = text.substring(0, text.length() - 1).trim();
        text = text.replace(',', '.');
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private static TranslatableContents translatable(Component component, String prefix) {
        if (component.getContents() instanceof TranslatableContents contents && contents.getKey().startsWith(prefix)) {
            return contents;
        }
        for (Component sibling : component.getSiblings()) {
            TranslatableContents found = translatable(sibling, prefix);
            if (found != null) return found;
        }
        return null;
    }

    private static String nameKey(Component name) {
        if (name.getContents() instanceof TranslatableContents contents) return contents.getKey();
        return name.getString();
    }

    /**
     * Resolves the registry id behind a rendered attribute name so the {@code addition_as_percentage_attributes}
     * rule applies to these lines too. Falls back to the "attribute.&lt;namespace&gt;.&lt;path&gt;" naming
     * convention modded attributes commonly use when the registry is not reachable.
     */
    private static String attributeId(Component name) {
        if (!(name.getContents() instanceof TranslatableContents contents)) return null;
        String key = contents.getKey();
        String cached = ATTRIBUTE_IDS.get(key);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String resolved = lookupAttributeId(key);
        ATTRIBUTE_IDS.put(key, resolved == null ? "" : resolved);
        return resolved;
    }

    private static String lookupAttributeId(String descriptionId) {
        try {
            for (Attribute attribute : ForgeRegistries.ATTRIBUTES.getValues()) {
                if (descriptionId.equals(attribute.getDescriptionId())) {
                    ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
                    if (id != null) return id.toString();
                }
            }
        } catch (Exception | LinkageError ignored) {
            // Registry not available in this context; the naming convention below still covers modded attributes.
        }
        return conventionAttributeId(descriptionId);
    }

    private static String conventionAttributeId(String descriptionId) {
        if (!descriptionId.startsWith("attribute.")) return null;
        String rest = descriptionId.substring("attribute.".length());
        int separator = rest.indexOf('.');
        if (separator <= 0 || separator == rest.length() - 1) return null;
        return rest.substring(0, separator) + ':' + rest.substring(separator + 1);
    }

    /** One parsed line: {@code amount} is already in display units and signed. */
    record Entry(Operation operation, double amount, boolean percentage, Component name, String nameKey, boolean equalsLine) {
        String groupKey() {
            if (equalsLine) return null;
            return nameKey + '|' + operation.name() + '|' + percentage;
        }
    }
}
