package local.attributedisplaystyle.client;

import com.google.common.collect.Multimap;
import com.google.common.collect.ArrayListMultimap;
import local.attributedisplaystyle.config.DisplayConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
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
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AttributeTooltipHandler {
    private static final String START = "ATTRIBUTE_DISPLAY_STYLE_START";
    private static final String END = "ATTRIBUTE_DISPLAY_STYLE_END";
    private static final DecimalFormat NUMBER = new DecimalFormat("#.##");
    private record ModifierKey(Attribute attribute, UUID id) {}

    private static final UUID ATTACK_DAMAGE_MODIFIER = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID ATTACK_SPEED_MODIFIER = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");
    private static final UUID ENTITY_REACH_MODIFIER = UUID.fromString("89689AA7-C577-4D97-A03E-791FDE1798D4");

    public AttributeTooltipHandler() {
        DisplayConfig.load();
    }

    public static final class ModBusEvents {
        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            DisplayConfig.load();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTooltip(ItemTooltipEvent event) {
        List<Component> tooltip = event.getToolTip();
        stripForeignMarkers(tooltip);
        int start = markerIndex(tooltip, START);
        int end = markerIndex(tooltip, END);
        if (start < 0 || end < start) return;

        DisplayConfig config = DisplayConfig.get();
        if (!config.replaceVanillaAttributeTooltips) {
            tooltip.remove(end);
            tooltip.remove(start);
            return;
        }

        for (int index = end; index >= start; index--) tooltip.remove(index);
        if (!shouldShowInTooltip(event.getItemStack(), TooltipPart.MODIFIERS)) return;

        List<Component> replacement = new ArrayList<>();
        Player player = event.getEntity();
        addModifiers(event.getItemStack(), player, event.getFlags(), replacement);
        tooltip.addAll(start, replacement);
    }

    private static void stripForeignMarkers(List<Component> tooltip) {
        tooltip.removeIf(line -> {
            String text = line.getString();
            return "APOTH_REMOVE_MARKER".equals(text) || "APOTH_REMOVE_MARKER_2".equals(text);
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

    private static void addModifiers(ItemStack stack, Player player, TooltipFlag flag, List<Component> output) {
        Multimap<Attribute, AttributeModifier> mainhand = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        Multimap<Attribute, AttributeModifier> offhand = stack.getAttributeModifiers(EquipmentSlot.OFFHAND);
        Set<ModifierKey> sharedHands = new HashSet<>();
        Multimap<Attribute, AttributeModifier> bothHands = ArrayListMultimap.create();
        for (Map.Entry<Attribute, AttributeModifier> entry : mainhand.entries()) {
            Attribute attribute = entry.getKey();
            AttributeModifier modifier = entry.getValue();
            if (hasModifierId(offhand.get(attribute), modifier.getId())) {
                bothHands.put(attribute, modifier);
                sharedHands.add(new ModifierKey(attribute, modifier.getId()));
            }
        }

        addGroup(stack, bothHands, player, flag, output, "both_hands");
        addGroup(stack, withoutModifiers(mainhand, sharedHands), player, flag, output, "mainhand");
        addGroup(stack, withoutModifiers(offhand, sharedHands), player, flag, output, "offhand");
        addGroup(stack, stack.getAttributeModifiers(EquipmentSlot.HEAD), player, flag, output, "head");
        addGroup(stack, stack.getAttributeModifiers(EquipmentSlot.CHEST), player, flag, output, "chest");
        addGroup(stack, stack.getAttributeModifiers(EquipmentSlot.LEGS), player, flag, output, "legs");
        addGroup(stack, stack.getAttributeModifiers(EquipmentSlot.FEET), player, flag, output, "feet");
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

    private static void addGroup(ItemStack stack, Multimap<Attribute, AttributeModifier> source, Player player, TooltipFlag flag, List<Component> output, String groupName) {
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
                for (AttributeModifier modifier : modifiers) {
                    if (baseId.equals(modifier.getId())) baseModifier = modifier;
                }
            }

            if (baseModifier != null) {
                List<AttributeModifier> children = new ArrayList<>();
                for (AttributeModifier modifier : modifiers) {
                    if (modifier != baseModifier && (DisplayConfig.get().showUnchangedModifiers || modifier.getAmount() != 0D)) {
                        children.add(modifier);
                    }
                }
                addBaseValueLines(attribute, baseModifier, children, player, stack, flag, lines);
                continue;
            }

            EnumMap<Operation, List<AttributeModifier>> byOperation = new EnumMap<>(Operation.class);
            for (AttributeModifier modifier : modifiers) {
                if (!DisplayConfig.get().showUnchangedModifiers && modifier.getAmount() == 0D) continue;
                byOperation.computeIfAbsent(modifier.getOperation(), ignored -> new ArrayList<>()).add(modifier);
            }
            for (Map.Entry<Operation, List<AttributeModifier>> operation : byOperation.entrySet()) {
                addOperationLines(attribute, operation.getKey(), operation.getValue(), flag, lines);
            }
        }
        if (lines.isEmpty()) return;
        output.add(Component.empty());
        output.add(Component.translatable("item.modifiers." + groupName).withStyle(style(DisplayConfig.get().headerColor)));
        output.addAll(lines);
    }

    private static int order(Attribute attribute) {
        if (attribute == Attributes.ATTACK_DAMAGE) return 0;
        if (attribute == Attributes.ATTACK_SPEED) return 1;
        return 2;
    }

    private static UUID baseModifierId(Attribute attribute) {
        if (attribute == Attributes.ATTACK_DAMAGE) return ATTACK_DAMAGE_MODIFIER;
        if (attribute == Attributes.ATTACK_SPEED) return ATTACK_SPEED_MODIFIER;
        if (attribute == NeoForgeMod.ENTITY_REACH.value()) return ENTITY_REACH_MODIFIER;
        return null;
    }

    private static void addBaseValueLines(Attribute attribute, AttributeModifier baseModifier, List<AttributeModifier> children, Player player, ItemStack stack, TooltipFlag flag, List<Component> output) {
        DisplayConfig config = DisplayConfig.get();
        double entityBase = player == null ? 0D : player.getAttributeBaseValue(attribute);
        double weaponBase = entityBase + baseModifier.getAmount();
        double additiveBase = weaponBase;
        double total = weaponBase;
        for (AttributeModifier modifier : children) {
            if (modifier.getOperation() == Operation.ADDITION) {
                additiveBase += modifier.getAmount();
                total += modifier.getAmount();
            }
        }
        for (AttributeModifier modifier : children) {
            if (modifier.getOperation() == Operation.MULTIPLY_BASE) total += modifier.getAmount() * additiveBase;
        }
        for (AttributeModifier modifier : children) {
            if (modifier.getOperation() == Operation.MULTIPLY_TOTAL) total *= 1D + modifier.getAmount();
        }

        double enchantmentBonus = attribute == Attributes.ATTACK_DAMAGE
            ? EnchantmentHelper.getDamageBonus(stack, MobType.UNDEFINED)
            : 0D;
        total += enchantmentBonus;
        Component name = Component.translatable(attribute.getDescriptionId());
        output.add(formatLine(Operation.ADDITION, total, false, name, flag, singleStyle(total)));

        if (!config.expandOnShift || !Screen.hasShiftDown() || (children.isEmpty() && enchantmentBonus == 0D)) return;
        addNestedSource(attribute, Operation.ADDITION, false, weaponBase, flag, output);
        for (AttributeModifier modifier : children) {
            addNestedSource(attribute, modifier.getOperation(), percentageFor(attribute, modifier.getOperation()), modifier.getAmount(), flag, output);
        }
        if (enchantmentBonus != 0D) addNestedSource(attribute, Operation.ADDITION, false, enchantmentBonus, flag, output);
    }

    private static void addOperationLines(Attribute attribute, Operation operation, List<AttributeModifier> modifiers, TooltipFlag flag, List<Component> output) {
        DisplayConfig config = DisplayConfig.get();
        double total = modifiers.stream().mapToDouble(AttributeModifier::getAmount).sum();
        boolean percentage = percentageFor(attribute, operation);
        Component name = Component.translatable(attribute.getDescriptionId());
        if (modifiers.size() > 1) {
            output.add(formatLine(operation, scaled(total, percentage), percentage, name, flag, mergedStyle(total)));
            if (config.expandOnShift && Screen.hasShiftDown()) {
                for (AttributeModifier modifier : modifiers) {
                    output.add(formatLine(operation, scaled(modifier.getAmount(), percentage), percentage, name, flag, expandedStyle(modifier.getAmount())));
                }
            }
            return;
        }

        double amount = modifiers.get(0).getAmount();
        output.add(formatLine(operation, scaled(amount, percentage), percentage, name, flag, singleStyle(amount)));
    }

    private static boolean percentageFor(Attribute attribute, Operation operation) {
        return operation != Operation.ADDITION || DisplayConfig.get().isAdditionAsPercentage(attributeId(attribute));
    }

    private static DisplayConfig.Style singleStyle(double amount) {
        return amount >= 0 ? DisplayConfig.get().positiveSingle : DisplayConfig.get().negativeSingle;
    }

    private static DisplayConfig.Style mergedStyle(double amount) {
        return amount >= 0 ? DisplayConfig.get().positiveMerged : DisplayConfig.get().negativeMerged;
    }

    private static DisplayConfig.Style expandedStyle(double amount) {
        return amount >= 0 ? DisplayConfig.get().positiveExpanded : DisplayConfig.get().negativeExpanded;
    }

    private static void addNestedSource(Attribute attribute, Operation operation, boolean percentage, double amount, TooltipFlag flag, List<Component> output) {
        output.add(formatLine(operation, scaled(amount, percentage), percentage, Component.translatable(attribute.getDescriptionId()), flag, expandedStyle(amount)));
    }

    private static double scaled(double amount, boolean percentage) {
        return percentage ? amount * 100D : amount;
    }

    private static MutableComponent formatLine(Operation operation, double amount, boolean percentage, Component name, TooltipFlag flag, DisplayConfig.Style appearance) {
        String sign = amount >= 0 ? "+" : "-";
        String value = NUMBER.format(Math.abs(amount));
        String suffix = percentage ? "%" : "";
        MutableComponent line = Component.literal(appearance.prefix).withStyle(style(appearance.prefixColor))
            .append(Component.literal(appearance.icon).withStyle(style(appearance.iconColor).withFont(DisplayConfig.get().iconFontId())))
            .append(Component.literal(" " + sign + " ").withStyle(style(appearance.signColor)))
            .append(Component.literal(value + suffix + " ").withStyle(style(appearance.valueColor)))
            .append(styled(name, style(appearance.nameColor)));
        if (flag.isAdvanced()) line.append(Component.literal(" [" + operation.name() + "]").withStyle(ChatFormatting.GRAY));
        return line;
    }

    private static MutableComponent styled(Component component, Style style) {
        MutableComponent result = MutableComponent.create(component.getContents()).withStyle(style);
        for (Component sibling : component.getSiblings()) result.append(styled(sibling, style));
        return result;
    }

    private static String attributeId(Attribute attribute) {
        net.minecraft.resources.ResourceLocation key = BuiltInRegistries.ATTRIBUTE.getKey(attribute);
        return key == null ? null : key.toString();
    }

    private static Style style(int color) {
        return Style.EMPTY.withColor(TextColor.fromRgb(color));
    }
}
