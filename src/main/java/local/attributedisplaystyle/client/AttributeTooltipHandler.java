package local.attributedisplaystyle.client;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import local.attributedisplaystyle.config.DisplayConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.event.AddAttributeTooltipsEvent;
import net.neoforged.neoforge.event.GatherSkippedAttributeTooltipsEvent;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AttributeTooltipHandler {
    private static final DecimalFormat NUMBER = new DecimalFormat("#.##");
    private record ModifierKey(Holder<Attribute> attribute, Identifier id) {}

    public AttributeTooltipHandler() {
        DisplayConfig.load();
    }

    public static final class ModBusEvents {
        @SubscribeEvent
        public static void reload(AddClientReloadListenersEvent event) {
            DisplayConfig.load();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void skipVanillaTooltips(GatherSkippedAttributeTooltipsEvent event) {
        DisplayConfig config = DisplayConfig.get();
        if (config.replaceVanillaAttributeTooltips) {
            event.setSkipAll(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void addCustomTooltips(AddAttributeTooltipsEvent event) {
        DisplayConfig config = DisplayConfig.get();
        if (!config.replaceVanillaAttributeTooltips || !event.shouldShow()) return;

        List<Component> lines = new ArrayList<>();
        addModifiers(event.getStack(), event.getContext(), lines);
        event.addTooltipLines(lines.toArray(Component[]::new));
    }

    private static void addModifiers(ItemStack stack, net.neoforged.neoforge.common.util.AttributeTooltipContext context, List<Component> output) {
        Multimap<Holder<Attribute>, AttributeModifier> mainhand = modifiersFor(stack, EquipmentSlotGroup.MAINHAND);
        Multimap<Holder<Attribute>, AttributeModifier> offhand = modifiersFor(stack, EquipmentSlotGroup.OFFHAND);
        Set<ModifierKey> sharedHands = new HashSet<>();
        Multimap<Holder<Attribute>, AttributeModifier> bothHands = ArrayListMultimap.create();
        for (Map.Entry<Holder<Attribute>, AttributeModifier> entry : mainhand.entries()) {
            Holder<Attribute> attribute = entry.getKey();
            AttributeModifier modifier = entry.getValue();
            if (hasModifierId(offhand.get(attribute), modifier.id())) {
                bothHands.put(attribute, modifier);
                sharedHands.add(new ModifierKey(attribute, modifier.id()));
            }
        }

        addGroup(bothHands, context, output, "both_hands");
        addGroup(withoutModifiers(mainhand, sharedHands), context, output, "mainhand");
        addGroup(withoutModifiers(offhand, sharedHands), context, output, "offhand");
        for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
            if (group == EquipmentSlotGroup.MAINHAND || group == EquipmentSlotGroup.OFFHAND) continue;
            addGroup(modifiersFor(stack, group), context, output, group.getSerializedName());
        }
    }

    private static Multimap<Holder<Attribute>, AttributeModifier> modifiersFor(ItemStack stack, EquipmentSlotGroup group) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = ArrayListMultimap.create();
        stack.forEachModifier(group, (attribute, modifier, display) -> modifiers.put(attribute, modifier));
        return modifiers;
    }

    private static boolean hasModifierId(Iterable<AttributeModifier> modifiers, Identifier id) {
        for (AttributeModifier modifier : modifiers) {
            if (id.equals(modifier.id())) return true;
        }
        return false;
    }

    private static Multimap<Holder<Attribute>, AttributeModifier> withoutModifiers(Multimap<Holder<Attribute>, AttributeModifier> source, Set<ModifierKey> excluded) {
        Multimap<Holder<Attribute>, AttributeModifier> remaining = ArrayListMultimap.create();
        for (Map.Entry<Holder<Attribute>, AttributeModifier> entry : source.entries()) {
            if (!excluded.contains(new ModifierKey(entry.getKey(), entry.getValue().id()))) {
                remaining.put(entry.getKey(), entry.getValue());
            }
        }
        return remaining;
    }

    private static void addGroup(Multimap<Holder<Attribute>, AttributeModifier> source, net.neoforged.neoforge.common.util.AttributeTooltipContext context, List<Component> output, String groupName) {
        Map<Holder<Attribute>, List<AttributeModifier>> grouped = new LinkedHashMap<>();
        for (Map.Entry<Holder<Attribute>, AttributeModifier> entry : source.entries()) {
            grouped.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(entry.getValue());
        }

        List<Component> lines = new ArrayList<>();
        List<Holder<Attribute>> order = new ArrayList<>(grouped.keySet());
        order.sort(java.util.Comparator.comparingInt(AttributeTooltipHandler::order));
        for (Holder<Attribute> attribute : order) {
            List<AttributeModifier> modifiers = grouped.get(attribute);
            AttributeModifier baseModifier = null;
            Identifier baseId = attribute.value().getBaseId();
            if (baseId != null) {
                for (AttributeModifier modifier : modifiers) {
                    if (baseId.equals(modifier.id())) baseModifier = modifier;
                }
            }

            if (baseModifier != null) {
                List<AttributeModifier> children = new ArrayList<>();
                for (AttributeModifier modifier : modifiers) {
                    if (modifier != baseModifier && (DisplayConfig.get().showUnchangedModifiers || modifier.amount() != 0D)) {
                        children.add(modifier);
                    }
                }
                addBaseValueLines(attribute, baseModifier, children, context, lines);
                continue;
            }

            EnumMap<Operation, List<AttributeModifier>> byOperation = new EnumMap<>(Operation.class);
            for (AttributeModifier modifier : modifiers) {
                if (!DisplayConfig.get().showUnchangedModifiers && modifier.amount() == 0D) continue;
                byOperation.computeIfAbsent(modifier.operation(), ignored -> new ArrayList<>()).add(modifier);
            }
            for (Map.Entry<Operation, List<AttributeModifier>> operation : byOperation.entrySet()) {
                addOperationLines(attribute, operation.getKey(), operation.getValue(), context.flag(), lines);
            }
        }
        if (lines.isEmpty()) return;
        output.add(Component.empty());
        output.add(Component.translatable("item.modifiers." + groupName).withStyle(style(DisplayConfig.get().headerColor)));
        output.addAll(lines);
    }

    private static int order(Holder<Attribute> attribute) {
        if (attribute.equals(Attributes.ATTACK_DAMAGE)) return 0;
        if (attribute.equals(Attributes.ATTACK_SPEED)) return 1;
        return 2;
    }

    private static void addBaseValueLines(Holder<Attribute> attribute, AttributeModifier baseModifier, List<AttributeModifier> children, net.neoforged.neoforge.common.util.AttributeTooltipContext context, List<Component> output) {
        DisplayConfig config = DisplayConfig.get();
        double entityBase = context.player() == null ? 0D : context.player().getAttributeBaseValue(attribute);
        double weaponBase = entityBase + baseModifier.amount();
        double additiveBase = weaponBase;
        double total = weaponBase;
        for (AttributeModifier modifier : children) {
            if (modifier.operation() == Operation.ADD_VALUE) {
                additiveBase += modifier.amount();
                total += modifier.amount();
            }
        }
        for (AttributeModifier modifier : children) {
            if (modifier.operation() == Operation.ADD_MULTIPLIED_BASE) total += modifier.amount() * additiveBase;
        }
        for (AttributeModifier modifier : children) {
            if (modifier.operation() == Operation.ADD_MULTIPLIED_TOTAL) total *= 1D + modifier.amount();
        }

        Component name = Component.translatable(attribute.value().getDescriptionId());
        output.add(formatLine(Operation.ADD_VALUE, total, false, name, context.flag(), singleStyle(total)));
        if (!config.expandOnShift || !context.flag().hasShiftDown() || children.isEmpty()) return;
        addNestedSource(attribute, Operation.ADD_VALUE, false, weaponBase, context.flag(), output);
        for (AttributeModifier modifier : children) {
            boolean percentage = isPercentage(attribute, modifier.operation());
            output.add(formatLine(modifier.operation(), scaled(modifier.amount(), percentage), percentage, name, context.flag(), expandedStyle(modifier.amount())));
        }
    }

    private static void addOperationLines(Holder<Attribute> attribute, Operation operation, List<AttributeModifier> modifiers, TooltipFlag flag, List<Component> output) {
        DisplayConfig config = DisplayConfig.get();
        double total = modifiers.stream().mapToDouble(AttributeModifier::amount).sum();
        boolean percentage = isPercentage(attribute, operation);
        Component name = Component.translatable(attribute.value().getDescriptionId());
        if (modifiers.size() > 1) {
            output.add(formatLine(operation, scaled(total, percentage), percentage, name, flag, mergedStyle(total)));
            if (config.expandOnShift && flag.hasShiftDown()) {
                for (AttributeModifier modifier : modifiers) {
                    output.add(formatLine(operation, scaled(modifier.amount(), percentage), percentage, name, flag, expandedStyle(modifier.amount())));
                }
            }
            return;
        }

        double amount = modifiers.get(0).amount();
        output.add(formatLine(operation, scaled(amount, percentage), percentage, name, flag, singleStyle(amount)));
    }

    private static boolean isAddition(Operation operation) {
        return operation == Operation.ADD_VALUE;
    }

    private static boolean isPercentage(Holder<Attribute> attribute, Operation operation) {
        return !isAddition(operation) || DisplayConfig.get().isAdditionAsPercentage(attributeId(attribute));
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

    private static void addNestedSource(Holder<Attribute> attribute, Operation operation, boolean percentage, double amount, TooltipFlag flag, List<Component> output) {
        output.add(formatLine(operation, scaled(amount, percentage), percentage, Component.translatable(attribute.value().getDescriptionId()), flag, expandedStyle(amount)));
    }

    private static double scaled(double amount, boolean percentage) {
        return percentage ? amount * 100D : amount;
    }

    private static MutableComponent formatLine(Operation operation, double amount, boolean percentage, Component name, TooltipFlag flag, DisplayConfig.Style appearance) {
        String sign = amount >= 0 ? "+" : "-";
        String value = NUMBER.format(Math.abs(amount));
        String suffix = percentage ? "%" : "";
        MutableComponent line = Component.literal(appearance.prefix).withStyle(style(appearance.prefixColor))
            .append(Component.literal(appearance.icon).withStyle(style(appearance.iconColor).withFont(new FontDescription.Resource(DisplayConfig.get().iconFontId()))))
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

    private static String attributeId(Holder<Attribute> attribute) {
        return attribute.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
    }

    private static Style style(int color) {
        return Style.EMPTY.withColor(TextColor.fromRgb(color));
    }
}
