package local.attributedisplaystyle.client;

import com.google.common.collect.Multimap;
import com.google.common.collect.ArrayListMultimap;
import com.mojang.datafixers.util.Either;
import local.attributedisplaystyle.config.DisplayConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStack.TooltipPart;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AttributeTooltipHandler {
    static final String START = "ATTRIBUTE_DISPLAY_STYLE_START";
    static final String END = "ATTRIBUTE_DISPLAY_STYLE_END";
    private static final DecimalFormat NUMBER = new DecimalFormat("#.##");
    /** What this mod rendered for a tooltip: the attributes it covers and the block it inserted. */
    private record RenderedSection(Set<String> covered, Component first, Component last) {}
    private record ModifierKey(Attribute attribute, UUID id) {}

    private static final Map<List<Component>, RenderedSection> RENDERED_GROUPS = new IdentityHashMap<>();
    private static int debugDumps = 0;
    private static int debugBefore = 0;
    private static int debugProbe = 0;
    /** The stack the debug command was run on; dumps only fire for that same item. */
    private static ItemStack debugTarget = ItemStack.EMPTY;

    private static boolean debugTargetMatches(ItemStack stack) {
        return stack != null && !debugTarget.isEmpty() && ItemStack.isSameItemSameTags(debugTarget, stack);
    }
    // Item.BASE_ATTACK_*_UUID is protected in 1.20.1, so the vanilla values are repeated here.
    private static final UUID ATTACK_DAMAGE_MODIFIER = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID ATTACK_SPEED_MODIFIER = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");
    private static final UUID ENTITY_REACH_MODIFIER = UUID.fromString("89689AA7-C577-4D97-A03E-791FDE1798D4");

    public AttributeTooltipHandler() {
        DisplayConfig.load();
        TooltipCompat.clearAttributeCache();
    }

    public static final class ModBusEvents {
        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            DisplayConfig.load();
            TooltipCompat.clearAttributeCache();
        }
    }

    /**
     * Recolors the item name last, so the configured color wins over the one rarity gives the name and
     * over whatever other mods did to that line. The name is always the first line of a tooltip.
     *
     * <p>Only the tooltip event is covered here. Mods that rewrite the finished title while the tooltip is
     * assembled into render components run after this and would win, so {@link GatherNameColor} repeats
     * the recolor at that later stage.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemNameColor(ItemTooltipEvent event) {
        List<Component> lines = event.getToolTip();
        if (lines.isEmpty()) return;
        Integer color = DisplayConfig.get().itemNameColor(itemId(event.getItemStack()));
        if (color == null) return;
        lines.set(0, recolored(lines.get(0), color));
    }

    private static String itemId(ItemStack stack) {
        try {
            // var keeps this off the id class, whose name changed in the newer supported versions.
            var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return key == null ? null : key.toString();
        } catch (Exception | LinkageError ignored) {
            // Registry not reachable in this context; the name simply keeps the color it was given.
            return null;
        }
    }

    /**
     * Forces one color onto every segment of a component tree. The rest of each segment's style stays as
     * it is, so a bold or italic name is still bold or italic, only its color changes.
     */
    private static Component recolored(Component component, int color) {
        MutableComponent result = MutableComponent.create(component.getContents())
            .withStyle(component.getStyle().withColor(TextColor.fromRgb(color)));
        for (Component sibling : component.getSiblings()) result.append(recolored(sibling, color));
        return result;
    }

    @SubscribeEvent
    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("attribute_display_style")
            .then(Commands.literal("reload")
                .executes(context -> {
                    DisplayConfig.load();
                    TooltipCompat.clearAttributeCache();
                    context.getSource().sendSuccess(
                        () -> Component.translatable("attribute_display_style.command.reload.success"), false);
                    return 1;
                }))
            .then(Commands.literal("debug")
                .executes(context -> {
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player == null) return 0;
                    String held = player.getMainHandItem().getHoverName().getString();
                    int lines = dumpTooltip();
                    context.getSource().sendSuccess(
                        () -> Component.literal("Attribute Display Style: dumped " + lines + " tooltip lines of '"
                            + held + "' to the log; hover that exact item to record the render stages"),
                        false);
                    return lines;
                })));
    }

    /** Writes the held item's finished tooltip, line by line, to the client log. */
    private static int dumpTooltip() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        ItemStack stack = player.getMainHandItem();
        List<Component> lines = stack.getTooltipLines(player, TooltipFlag.NORMAL);
        dump("event", stack, lines);
        debugDumps = 5;
        debugBefore = 1;
        debugProbe = 2;
        debugTarget = stack.copy();
        return lines.size();
    }

    private static void dump(String stage, ItemStack stack, List<Component> lines) {
        System.out.println("[attribute_display_style] " + stage + " tooltip of '" + stack.getHoverName().getString()
            + "' id=" + itemId(stack) + " tag=" + (stack.getTag() == null ? "-" : stack.getTag())
            + " (" + lines.size() + " lines)");
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            TooltipCompat.Entry entry = TooltipCompat.parseLine(line);
            System.out.println("[attribute_display_style]   [" + index + "] keys=" + keys(line)
                + " color=" + (line.getStyle().getColor() == null ? "-" : Integer.toHexString(line.getStyle().getColor().getValue()))
                + " parsed=" + (entry == null ? "-" : entry.nameKey() + '/' + entry.amount())
                + " structure=" + structure(line)
                + " text=\"" + line.getString() + "\"");
        }
    }

    /** A compact view of what a line is made of, which shows who built it. */
    private static String structure(Component component) {
        StringBuilder builder = new StringBuilder();
        appendStructure(component, builder, 0);
        return builder.toString();
    }

    private static void appendStructure(Component component, StringBuilder builder, int depth) {
        if (depth > 1) return;
        if (builder.length() > 0) builder.append('>');
        if (component.getContents() instanceof LiteralContents contents) {
            builder.append("literal('").append(contents.text()).append("')");
        } else if (component.getContents() instanceof TranslatableContents contents) {
            builder.append("translatable(").append(contents.getKey()).append(')');
        } else {
            builder.append(component.getContents().getClass().getSimpleName());
        }
        for (Component sibling : component.getSiblings()) appendStructure(sibling, builder, depth + 1);
    }

    private static String keys(Component component) {
        List<String> found = new ArrayList<>();
        collectKeys(component, found);
        return found.isEmpty() ? "-" : String.join(" | ", found);
    }

    private static void collectKeys(Component component, List<String> found) {
        if (component.getContents() instanceof TranslatableContents contents) found.add(contents.getKey());
        for (Component sibling : component.getSiblings()) collectKeys(sibling, found);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onForeignTooltip(ItemTooltipEvent event) {
        styleForeignLines(event);
        applyEmptyLinePolicy(event.getToolTip());
    }

    /** Runs after other mods appended their attribute lines so those can be merged, dropped or moved. */
    private static void styleForeignLines(ItemTooltipEvent event) {
        if (!DisplayConfig.get().replaceVanillaAttributeTooltips) return;
        List<Component> lines = event.getToolTip();
        RenderedSection section = RENDERED_GROUPS.remove(lines);
        TooltipCompat.styleSections(lines, event.getFlags());
        if (!DisplayConfig.get().styleOtherModAttributeTooltips) return;
        if (section == null || section.last() == null) {
            TooltipCompat.styleLooseLines(lines, event.getFlags(), section == null ? Set.of() : section.covered());
            return;
        }

        // Attribute lines another mod placed outside this mod's block are moved into it, so the
        // attribute list stays in one piece instead of being spread over the tooltip.
        List<TooltipCompat.Entry> loose = new ArrayList<>();
        for (int index = lines.size() - 1; index >= 0; index--) {
            TooltipCompat.Entry entry = TooltipCompat.parseLine(lines.get(index));
            if (entry == null) continue;
            lines.remove(index);
            if (!TooltipCompat.isCovered(entry, section.covered())) loose.add(0, entry);
        }
        if (loose.isEmpty()) return;

        List<Component> moved = new ArrayList<>();
        TooltipCompat.renderEntries(loose, moved, event.getFlags());
        int last = indexOfIdentity(lines, section.last());
        if (last < 0) {
            lines.addAll(moved);
        } else {
            lines.addAll(last + 1, moved);
        }
    }

    private static int indexOfIdentity(List<Component> lines, Component target) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index) == target) return index;
        }
        return -1;
    }

    @SubscribeEvent
    public void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        // Blank lines are dropped straight out of the element list. The list is deliberately not rebuilt
        // here: tooltip mods such as smart tooltip scroll replay this event with their own subset of the
        // elements, and replacing every text line at once would drop or reorder what they kept.
        removeEmptyElements(event.getTooltipElements());

        // Safety net: some mods append their attribute lines after the tooltip events, so the finished
        // line list is handled once more right before the tooltip is turned into render components.
        if (!DisplayConfig.get().replaceVanillaAttributeTooltips) return;
        if (!DisplayConfig.get().styleOtherModAttributeTooltips) return;

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        List<Component> lines = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        for (int index = 0; index < elements.size(); index++) {
            if (elements.get(index).left().orElse(null) instanceof Component component) {
                lines.add(component);
                positions.add(index);
            }
        }
        if (lines.isEmpty()) return;

        if (debugDumps > 0 && debugTargetMatches(event.getItemStack())) {
            debugDumps--;
            dump("gather", event.getItemStack(), lines);
        }

        List<Component> before = new ArrayList<>(lines);
        TooltipCompat.styleSections(lines, TooltipFlag.NORMAL);
        // Nothing is dropped here: which lines this mod already covers was decided while the tooltip was
        // built, and an attribute the item carries but this mod renders no line for (Confluence's computed
        // base values, for instance) keeps the line the other mod wrote.
        TooltipCompat.styleLooseLines(lines, TooltipFlag.NORMAL, Set.of());
        if (sameInstances(before, lines)) return;

        int first = positions.get(0);
        for (int index = positions.size() - 1; index >= 1; index--) elements.remove((int) positions.get(index));
        elements.remove(first);
        for (int index = lines.size() - 1; index >= 0; index--) elements.add(first, Either.left(lines.get(index)));
    }

    /**
     * Repeats the name recolor once the tooltip is about to be turned into render components. Tooltip mods
     * that rebuild the finished title subscribe at LOWEST here and therefore land after the tooltip event,
     * so this listener is registered separately and last: at equal priority the event bus runs listeners in
     * registration order, and only then does the configured color still win.
     */
    public static final class GatherNameColor {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onGather(RenderTooltipEvent.GatherComponents event) {
            ItemStack stack = event.getItemStack();
            if (stack == null) return;
            Integer color = DisplayConfig.get().itemNameColor(itemId(stack));
            if (color == null) return;
            recolorFirstText(event.getTooltipElements(), color);
        }
    }

    /** Dumps the tooltip elements before any other {@code GatherComponents} listener touches them. */
    public static final class GatherProbe {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onGatherHead(RenderTooltipEvent.GatherComponents event) {
            if (debugProbe <= 0 || !debugTargetMatches(event.getItemStack())) return;
            debugProbe--;
            List<Component> texts = new ArrayList<>();
            for (Either<FormattedText, TooltipComponent> element : event.getTooltipElements()) {
                if (element.left().orElse(null) instanceof Component component) texts.add(component);
            }
            dump("gather-head", event.getItemStack(), texts);
        }
    }

    /** Recolors the first text element in place; tooltip components and the rest of the list stay untouched. */
    private static void recolorFirstText(List<Either<FormattedText, TooltipComponent>> elements, int color) {
        for (int index = 0; index < elements.size(); index++) {
            if (elements.get(index).left().orElse(null) instanceof Component component) {
                elements.set(index, Either.left(recolored(component, color)));
                return;
            }
        }
    }


    private static boolean sameInstances(List<Component> before, List<Component> after) {
        if (before.size() != after.size()) return false;
        for (int index = 0; index < before.size(); index++) {
            if (before.get(index) != after.get(index)) return false;
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTooltip(ItemTooltipEvent event) {
        List<Component> tooltip = event.getToolTip();
        // One-shot diagnostic: what every other handler has contributed before this one touches the list.
        if (debugBefore > 0 && debugTargetMatches(event.getItemStack())) {
            debugBefore--;
            dump("before", event.getItemStack(), tooltip);
        }
        // Neutralize Apothic Attributes if it is present: drop its markers so its handler no-ops,
        // then take over the attribute section ourselves.
        stripForeignMarkers(tooltip);
        int start = markerIndex(tooltip, START);
        int end = markerIndex(tooltip, END);

        // Always drop markers first so a missing pair cannot leak text.
        if (start >= 0 && end >= start) {
            DisplayConfig config = DisplayConfig.get();
            if (!config.replaceVanillaAttributeTooltips) {
                tooltip.remove(end);
                tooltip.remove(start);
                stripMarkers(tooltip);
                return;
            }

            // Lines another mod contributed are folded into this section. Which of them are duplicates is
            // decided below, after this mod has actually written its own lines: an attribute the item
            // carries but this mod renders no line for keeps the other mod's line instead of losing both.
            boolean styleOthers = config.styleOtherModAttributeTooltips;
            List<TooltipCompat.Entry> foreign = new ArrayList<>();
            List<Component> preserved = new ArrayList<>();
            if (styleOthers) {
                // Some mods put their attribute lines above the vanilla section; move those into it.
                for (int index = start - 1; index >= 0; index--) {
                    TooltipCompat.Entry entry = TooltipCompat.parseLine(tooltip.get(index));
                    if (entry == null) continue;
                    tooltip.remove(index);
                    start--;
                    end--;
                    foreign.add(entry);
                }
                // Every line between the markers is re-parsed, not just the ones marked as foreign: mods
                // such as Confluence write attribute lines here whose values live in their own item data
                // and cannot be rebuilt from the stack's modifiers, and those must survive the rebuild.
                int regionStart = start + 1;
                foreign.addAll(TooltipCompat.parse(tooltip, regionStart, end));
                // Spacing and the vanilla section headers are written again by the rebuild, so only the
                // lines this mod does not reproduce are carried over.
                for (Component line : TooltipCompat.unparsed(tooltip, regionStart, end)) {
                    if (!isEmpty(line) && !hasTranslatable(line, "item.modifiers.")) preserved.add(line);
                }
            } else {
                preserved.addAll(tooltip.subList(foreignStart(tooltip, start + 1, end), end));
            }
            for (int index = end; index >= start; index--) tooltip.remove(index);
            stripMarkers(tooltip);

            List<Component> replacement = new ArrayList<>();
            if (shouldShowInTooltip(event.getItemStack(), TooltipPart.MODIFIERS)) {
                Set<String> rendered = new HashSet<>();
                addModifiers(event.getItemStack(), event.getEntity(), event.getFlags(), List.of(), replacement, rendered);
                if (styleOthers) {
                    List<TooltipCompat.Entry> kept = new ArrayList<>();
                    for (TooltipCompat.Entry entry : foreign) {
                        if (!TooltipCompat.isCovered(entry, rendered)) kept.add(entry);
                    }
                    TooltipCompat.renderEntries(kept, replacement, event.getFlags());
                }
                replacement.addAll(preserved);
                RENDERED_GROUPS.put(tooltip, new RenderedSection(rendered,
                    replacement.isEmpty() ? null : replacement.get(0),
                    replacement.isEmpty() ? null : replacement.get(replacement.size() - 1)));
            }
            tooltip.addAll(start, replacement);
            return;
        }

        stripMarkers(tooltip);
    }

    /** Index of the first line in [from, to) that vanilla did not put there, or {@code to}. */
    static int foreignStart(List<Component> tooltip, int from, int to) {
        for (int index = from; index < to; index++) {
            if (isForeignLine(tooltip.get(index))) return index;
        }
        return to;
    }

    static boolean isForeignLine(Component line) {
        if (isEmpty(line)) return false;
        return !hasTranslatable(line, "item.modifiers.") && !hasTranslatable(line, "attribute.modifier.");
    }

    static boolean isEmpty(Component line) {
        if (line.getContents() == ComponentContents.EMPTY) return true;
        return line.getContents() instanceof LiteralContents contents && contents.text().isEmpty();
    }

    /**
     * A line that draws nothing. The whole component tree is measured, because a line is often built as
     * Component.empty().append(part).append(part) or Component.literal("").append(part): its own contents
     * are then empty while the text lives in the siblings, and reading only the contents would mistake
     * the finished line for a blank one.
     */
    static boolean isEmptyLine(Component line) {
        if (line == null) return false;
        try {
            return line.getString().isBlank();
        } catch (RuntimeException | LinkageError ignored) {
            // A line whose text cannot be resolved is left alone rather than guessed at.
            return false;
        }
    }

    /**
     * Applies the configured empty-line policy to a finished tooltip. Lines are removed in place, so a
     * mod that reads the list afterwards sees the same layout the tooltip is drawn with.
     */
    static void applyEmptyLinePolicy(List<Component> lines) {
        DisplayConfig.EmptyLineMode mode = DisplayConfig.get().emptyTooltipLines;
        if (mode == DisplayConfig.EmptyLineMode.KEEP || lines.isEmpty()) return;

        List<Component> kept = new ArrayList<>(lines.size());
        Component pending = null;
        for (Component line : lines) {
            if (isEmptyLine(line)) {
                if (mode == DisplayConfig.EmptyLineMode.REMOVE) continue;
                // The first blank of a run is kept so any style it carries survives.
                if (pending == null) pending = line;
                continue;
            }
            if (pending != null) {
                // Blanks ahead of the first text line are dropped; a trailing run never gets flushed.
                if (!kept.isEmpty()) kept.add(pending);
                pending = null;
            }
            kept.add(line);
        }
        lines.clear();
        lines.addAll(kept);
    }

    /**
     * Drops the text-less lines straight out of the render element list. Only those elements are touched,
     * so everything else the list holds - recipe stacks, another mod's tooltip image - keeps its place.
     */
    static void removeEmptyElements(List<Either<FormattedText, TooltipComponent>> elements) {
        DisplayConfig.EmptyLineMode mode = DisplayConfig.get().emptyTooltipLines;
        if (mode == DisplayConfig.EmptyLineMode.KEEP || elements.isEmpty()) return;

        List<Integer> drop = new ArrayList<>();
        int pending = -1;
        boolean seenText = false;
        for (int index = 0; index < elements.size(); index++) {
            if (!(elements.get(index).left().orElse(null) instanceof Component line)) continue;
            if (!isEmptyLine(line)) {
                seenText = true;
                pending = -1;
                continue;
            }
            // No blank belongs ahead of the first text line, and REMOVE keeps none at all.
            if (mode == DisplayConfig.EmptyLineMode.REMOVE || !seenText) {
                if (pending >= 0) drop.add(pending);
                drop.add(index);
                pending = -1;
                continue;
            }
            // COLLAPSE keeps a single blank per run; the newest one stands in for the whole run.
            if (pending >= 0) drop.add(pending);
            pending = index;
        }
        // A run that reaches the end of the tooltip would leave a trailing blank, so it goes too.
        if (pending >= 0) drop.add(pending);

        for (int index = drop.size() - 1; index >= 0; index--) elements.remove((int) drop.get(index));
    }

    static boolean hasTranslatable(Component component, String prefix) {
        if (component.getContents() instanceof TranslatableContents contents && contents.getKey().startsWith(prefix)) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (hasTranslatable(sibling, prefix)) return true;
        }
        return false;
    }

    private static void stripForeignMarkers(List<Component> tooltip) {
        tooltip.removeIf(line -> {
            String text = line.getString();
            return "APOTH_REMOVE_MARKER".equals(text) || "APOTH_REMOVE_MARKER_2".equals(text);
        });
    }

    private static void stripMarkers(List<Component> tooltip) {
        tooltip.removeIf(line -> {
            String text = line.getString();
            return START.equals(text) || END.equals(text);
        });
    }

    private static int markerIndex(List<Component> tooltip, String marker) {
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().equals(marker)) return i;
        }
        return -1;
    }

    private static boolean shouldShowInTooltip(ItemStack stack, TooltipPart part) {
        int flags = stack.hasTag() && stack.getTag().contains("HideFlags", 99)
            ? stack.getTag().getInt("HideFlags")
            : stack.getItem().getDefaultTooltipHideFlags(stack);
        return (flags & part.getMask()) == 0;
    }

    private static void addModifiers(ItemStack stack, Player player, TooltipFlag flag, List<TooltipCompat.Entry> foreign, List<Component> output, Set<String> rendered) {
        List<TooltipCompat.Entry> pending = new ArrayList<>(foreign);
        Multimap<Attribute, AttributeModifier> mainhand = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        Multimap<Attribute, AttributeModifier> offhand = stack.getAttributeModifiers(EquipmentSlot.OFFHAND);
        Set<ModifierKey> sharedHands = new HashSet<>();
        Multimap<Attribute, AttributeModifier> bothHands = ArrayListMultimap.create();

        // Apothic first groups same-attribute modifiers shared by both hands, keyed by modifier UUID.
        for (Map.Entry<Attribute, AttributeModifier> entry : mainhand.entries()) {
            Attribute attribute = entry.getKey();
            AttributeModifier modifier = entry.getValue();
            if (hasModifierId(offhand.get(attribute), modifier.getId())) {
                bothHands.put(attribute, modifier);
                sharedHands.add(new ModifierKey(attribute, modifier.getId()));
            }
        }

        addGroup(stack, bothHands, player, flag, pending, output, rendered, "both_hands");
        addGroup(stack, withoutModifiers(mainhand, sharedHands), player, flag, pending, output, rendered, "mainhand");
        addGroup(stack, withoutModifiers(offhand, sharedHands), player, flag, pending, output, rendered, "offhand");
        addGroup(stack, stack.getAttributeModifiers(EquipmentSlot.HEAD), player, flag, pending, output, rendered, "head");
        addGroup(stack, stack.getAttributeModifiers(EquipmentSlot.CHEST), player, flag, pending, output, rendered, "chest");
        addGroup(stack, stack.getAttributeModifiers(EquipmentSlot.LEGS), player, flag, pending, output, rendered, "legs");
        addGroup(stack, stack.getAttributeModifiers(EquipmentSlot.FEET), player, flag, pending, output, rendered, "feet");
        // Attributes only the other mod knows about have no modifier to join, so they keep their own lines.
        TooltipCompat.renderEntries(pending, output, flag);
    }

    private static boolean hasModifierId(Iterable<AttributeModifier> modifiers, UUID id) {
        for (AttributeModifier modifier : modifiers) {
            if (id.equals(modifier.getId())) return true;
        }
        return false;
    }

    private static Multimap<Attribute, AttributeModifier> withoutModifiers(Multimap<Attribute, AttributeModifier> source, Set<ModifierKey> excluded) {
        Multimap<Attribute, AttributeModifier> remaining = ArrayListMultimap.create();
        for (Map.Entry<Attribute, AttributeModifier> entry : source.entries()) {
            if (!excluded.contains(new ModifierKey(entry.getKey(), entry.getValue().getId()))) {
                remaining.put(entry.getKey(), entry.getValue());
            }
        }
        return remaining;
    }

    private static void addGroup(ItemStack stack, Multimap<Attribute, AttributeModifier> source, Player player, TooltipFlag flag, List<TooltipCompat.Entry> pending, List<Component> output, Set<String> rendered, String groupName) {
        if (source.isEmpty()) return;

        Map<Attribute, List<AttributeModifier>> grouped = new LinkedHashMap<>();
        for (Map.Entry<Attribute, AttributeModifier> entry : source.entries()) {
            grouped.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(entry.getValue());
        }

        List<Component> lines = new ArrayList<>();
        List<Attribute> order = new ArrayList<>(grouped.keySet());
        order.sort(java.util.Comparator.comparingInt(AttributeTooltipHandler::order));
        for (Attribute attribute : order) {
            List<AttributeModifier> modifiers = grouped.get(attribute);
            UUID baseId = baseModifierId(attribute);
            AttributeModifier baseModifier = null;
            if (baseId != null) {
                // Match Apothic's base-modifier lookup: the last matching modifier is the anchor.
                for (AttributeModifier modifier : modifiers) {
                    if (baseId.equals(modifier.getId())) baseModifier = modifier;
                }
            }

            if (baseModifier != null) {
                List<AttributeModifier> children = new ArrayList<>();
                for (AttributeModifier modifier : modifiers) {
                    if (modifier != baseModifier
                        && (DisplayConfig.get().showUnchangedModifiers || modifier.getAmount() != 0D)) {
                        children.add(modifier);
                    }
                }
                rendered.add(attribute.getDescriptionId());
                dropMatching(pending, attribute.getDescriptionId());
                addBaseValueLines(attribute, baseModifier, children, player, stack, flag, lines);
                continue;
            }

            EnumMap<Operation, List<AttributeModifier>> byOperation = new EnumMap<>(Operation.class);
            for (AttributeModifier modifier : modifiers) {
                if (!DisplayConfig.get().showUnchangedModifiers && modifier.getAmount() == 0D) continue;
                byOperation.computeIfAbsent(modifier.getOperation(), ignored -> new ArrayList<>()).add(modifier);
            }
            if (byOperation.isEmpty()) continue;
            rendered.add(attribute.getDescriptionId());
            // The other mod rendered this very attribute itself, so its line is a duplicate.
            dropMatching(pending, attribute.getDescriptionId());
            for (Map.Entry<Operation, List<AttributeModifier>> operation : byOperation.entrySet()) {
                addOperationLines(attribute, operation.getKey(), operation.getValue(), flag, lines);
            }
        }
        if (lines.isEmpty()) return;
        output.add(Component.empty());
        output.add(Component.translatable("item.modifiers." + groupName).withStyle(style(DisplayConfig.get().headerColor)));
        output.addAll(lines);
    }

    /** Removes the entries another mod rendered for an attribute this mod already displays. */
    /** The attributes a weapon shows are listed before the rest. */
    private static int order(Attribute attribute) {
        if (attribute == Attributes.ATTACK_DAMAGE) return 0;
        if (attribute == Attributes.ATTACK_SPEED) return 1;
        return 2;
    }

    private static void dropMatching(List<TooltipCompat.Entry> pending, String descriptionId) {
        pending.removeIf(entry -> !entry.equalsLine() && entry.nameKey().equals(descriptionId));
    }

    private static UUID baseModifierId(Attribute attribute) {
        if (attribute == Attributes.ATTACK_DAMAGE) return ATTACK_DAMAGE_MODIFIER;
        if (attribute == Attributes.ATTACK_SPEED) return ATTACK_SPEED_MODIFIER;
        if (attribute == ForgeMod.ENTITY_REACH.get()) return ENTITY_REACH_MODIFIER;
        return null;
    }

    private static void addBaseValueLines(Attribute attribute, AttributeModifier baseModifier, List<AttributeModifier> children, Player player, ItemStack stack, TooltipFlag flag, List<Component> output) {
        DisplayConfig config = DisplayConfig.get();
        double entityBase = player == null ? 0D : player.getAttributeBaseValue(attribute);
        double weaponBase = entityBase + baseModifier.getAmount();
        double additiveBase = weaponBase;
        double total = weaponBase;

        // Apply the child modifiers in the same operation phases used by AttributesLib.
        for (AttributeModifier modifier : children) {
            if (modifier.getOperation() == Operation.ADDITION) {
                additiveBase += modifier.getAmount();
                total += modifier.getAmount();
            }
        }
        for (AttributeModifier modifier : children) {
            if (modifier.getOperation() == Operation.MULTIPLY_BASE) {
                total += modifier.getAmount() * additiveBase;
            }
        }
        for (AttributeModifier modifier : children) {
            if (modifier.getOperation() == Operation.MULTIPLY_TOTAL) {
                total *= 1D + modifier.getAmount();
            }
        }

        double bonus = attribute == Attributes.ATTACK_DAMAGE
            ? EnchantmentHelper.getDamageBonus(stack, MobType.UNDEFINED)
            : 0D;
        total += bonus;
        Component name = Component.translatable(attribute.getDescriptionId());
        String registryId = attributeId(attribute);
        output.add(formatLine(Operation.ADDITION, total, false, name, registryId, flag, singleStyle(total)));

        if (!config.expandOnShift || !Screen.hasShiftDown() || (children.isEmpty() && bonus == 0D)) return;
        addNestedSource(attribute, Operation.ADDITION, false, weaponBase, flag, output);
        for (AttributeModifier modifier : children) {
            boolean percentage = percentageFor(attribute, modifier.getOperation());
            addNestedSource(attribute, modifier.getOperation(), percentage, modifier.getAmount(), flag, output);
        }
        if (bonus != 0D) addNestedSource(attribute, Operation.ADDITION, false, bonus, flag, output);
    }

    static void addOperationLines(Attribute attribute, Operation operation, List<AttributeModifier> modifiers, TooltipFlag flag, List<Component> output) {
        DisplayConfig config = DisplayConfig.get();
        double total = modifiers.stream().mapToDouble(AttributeModifier::getAmount).sum();
        boolean percentage = percentageFor(attribute, operation);
        Component name = Component.translatable(attribute.getDescriptionId());
        String registryId = attributeId(attribute);
        if (modifiers.size() > 1) {
            output.add(formatLine(operation, scaled(total, percentage), percentage, name, registryId, flag, mergedStyle(total)));
            if (config.expandOnShift && Screen.hasShiftDown()) {
                for (AttributeModifier modifier : modifiers) {
                    output.add(formatLine(operation, scaled(modifier.getAmount(), percentage), percentage, name, registryId, flag,
                        expandedStyle(modifier.getAmount())));
                }
            }
            return;
        }

        double amount = modifiers.get(0).getAmount();
        output.add(formatLine(operation, scaled(amount, percentage), percentage, name, registryId, flag, singleStyle(amount)));
    }

    private static boolean percentageFor(Attribute attribute, Operation operation) {
        return operation != Operation.ADDITION || DisplayConfig.get().isAdditionAsPercentage(
            attributeId(attribute), attribute.getDescriptionId());
    }

    /** A lone modifier. */
    static DisplayConfig.Style singleStyle(double amount) {
        return amount >= 0 ? DisplayConfig.get().positiveSingle : DisplayConfig.get().negativeSingle;
    }

    /** A line that merges several modifiers of the same attribute and operation. */
    static DisplayConfig.Style mergedStyle(double total) {
        return total >= 0 ? DisplayConfig.get().positiveMerged : DisplayConfig.get().negativeMerged;
    }

    /** A child line revealed by expanding a merged line or a weapon total. */
    static DisplayConfig.Style expandedStyle(double amount) {
        return amount >= 0 ? DisplayConfig.get().positiveExpanded : DisplayConfig.get().negativeExpanded;
    }

    static void addNestedSource(Attribute attribute, Operation operation, boolean percentage, double amount, TooltipFlag flag, List<Component> output) {
        output.add(formatLine(operation, scaled(amount, percentage), percentage,
            Component.translatable(attribute.getDescriptionId()), attributeId(attribute), flag, expandedStyle(amount)));
    }

    private static double scaled(double amount, boolean percentage) {
        return percentage ? amount * 100D : amount;
    }

    static MutableComponent formatLine(Operation operation, double amount, boolean percentage, Component name, String attributeId, TooltipFlag flag, DisplayConfig.Style appearance) {
        double scaledAmount = amount * DisplayConfig.get().attributeDisplayMultiplier(attributeId);
        if (debugDumps > 0) {
            System.out.println("[attribute_display_style]   render id=" + attributeId
                + " amount=" + amount + " percentage=" + percentage
                + " multiplier=" + DisplayConfig.get().attributeDisplayMultiplier(attributeId)
                + " shown=" + scaledAmount + (percentage ? "%" : ""));
        }
        double displayAmount = Double.isFinite(scaledAmount) ? scaledAmount : amount;
        String sign = displayAmount >= 0 ? "+" : "-";
        String value = NUMBER.format(Math.abs(displayAmount));
        String suffix = percentage ? "%" : "";
        MutableComponent line = Component.literal(appearance.prefix).withStyle(style(appearance.prefixColor))
            .append(Component.literal(appearance.icon).withStyle(style(appearance.iconColor).withFont(DisplayConfig.get().iconFontId())))
            .append(Component.literal(" " + sign + " ").withStyle(style(appearance.signColor)))
            .append(Component.literal(value + suffix + " ").withStyle(style(appearance.valueColor)))
            .append(styled(name, style(appearance.nameColor)));
        if (flag.isAdvanced()) line.append(Component.literal(" [" + operation.name() + "]").withStyle(ChatFormatting.GRAY));
        return line;
    }

    private static String attributeId(Attribute attribute) {
        try {
            net.minecraft.resources.ResourceLocation key = ForgeRegistries.ATTRIBUTES.getKey(attribute);
            return key == null ? null : key.toString();
        } catch (Exception | LinkageError ignored) {
            // Registry not available in this context; the attribute is simply not treated as a percentage one.
            return null;
        }
    }

    static Style style(int color) {
        return Style.EMPTY.withColor(TextColor.fromRgb(color));
    }

    /** Applies a style to a whole component tree so composite names like "Attack Damage (base value)" stay one color. */
    static MutableComponent styled(Component component, Style style) {
        MutableComponent result = MutableComponent.create(component.getContents()).withStyle(style);
        for (Component sibling : component.getSiblings()) result.append(styled(sibling, style));
        return result;
    }
}
