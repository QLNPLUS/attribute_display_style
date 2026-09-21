package local.attributedisplaystyle.client;

import local.attributedisplaystyle.config.DisplayConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.AddAttributeTooltipsEvent;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AttributeTooltipHandler {
    private static final DecimalFormat NUMBER = new DecimalFormat("#.##");

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
    public void skipVanillaTooltips(GatherSkippedAttributeTooltipsEvent event) {
        DisplayConfig config = DisplayConfig.get();
        ItemAttributeModifiers modifiers = event.getStack().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (config.replaceVanillaAttributeTooltips && modifiers.showInTooltip()) {
            event.setSkipAll(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void addCustomTooltips(AddAttributeTooltipsEvent event) {
        DisplayConfig config = DisplayConfig.get();
        if (!config.replaceVanillaAttributeTooltips || !event.shouldShow()) return;

        List<Component> lines = new ArrayList<>();
        addModifiers(event.getStack(), event.getContext().flag(), lines);
        event.addTooltipLines(lines.toArray(Component[]::new));
    }

    private static void addModifiers(ItemStack stack, TooltipFlag flag, List<Component> output) {
        for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
            addGroup(stack, flag, output, group);
        }
    }

    private static void addGroup(ItemStack stack, TooltipFlag flag, List<Component> output, EquipmentSlotGroup group) {
        Map<Holder<Attribute>, EnumMap<Operation, List<AttributeModifier>>> grouped = new LinkedHashMap<>();
        stack.forEachModifier(group, (attribute, modifier) -> grouped.computeIfAbsent(attribute, ignored -> new EnumMap<>(Operation.class))
            .computeIfAbsent(modifier.operation(), ignored -> new ArrayList<>())
            .add(modifier));

        List<Component> lines = new ArrayList<>();
        for (Map.Entry<Holder<Attribute>, EnumMap<Operation, List<AttributeModifier>>> entry : grouped.entrySet()) {
            for (Map.Entry<Operation, List<AttributeModifier>> operation : entry.getValue().entrySet()) {
                List<AttributeModifier> modifiers = operation.getValue();
                if (!DisplayConfig.get().showUnchangedModifiers) modifiers.removeIf(modifier -> modifier.amount() == 0);
                if (modifiers.isEmpty()) continue;
                addOperationLines(entry.getKey(), operation.getKey(), modifiers, flag, lines);
            }
        }
        if (lines.isEmpty()) return;
        output.add(Component.empty());
        output.add(Component.translatable("item.modifiers." + group.getSerializedName()).withStyle(style(DisplayConfig.get().headerColor)));
        output.addAll(lines);
    }

    private static void addOperationLines(Holder<Attribute> attribute, Operation operation, List<AttributeModifier> modifiers, TooltipFlag flag, List<Component> output) {
        DisplayConfig config = DisplayConfig.get();
        boolean merged = modifiers.size() > 1;
        double total = modifiers.stream().mapToDouble(AttributeModifier::amount).sum();
        if (merged) {
            output.add(formatModifier(attribute, operation, total, flag, config.defaultIcon, config.mergedColor));
            if (config.expandOnShift && Screen.hasShiftDown()) {
                for (AttributeModifier modifier : modifiers) {
                    output.add(Component.literal(config.nestedPrefix).withStyle(style(config.nestedColor))
                        .append(formatModifier(attribute, operation, modifier.amount(), flag, iconFor(modifier.amount()), config.nestedColor)));
                }
            }
            return;
        }

        AttributeModifier modifier = modifiers.get(0);
        output.add(formatModifier(attribute, operation, modifier.amount(), flag, iconFor(modifier.amount()), colorFor(modifier.amount())));
    }

    private static String iconFor(double amount) {
        DisplayConfig config = DisplayConfig.get();
        return amount > 0 ? config.positiveIcon : amount < 0 ? config.negativeIcon : config.defaultIcon;
    }

    private static int colorFor(double amount) {
        DisplayConfig config = DisplayConfig.get();
        return amount > 0 ? config.positiveColor : amount < 0 ? config.negativeColor : config.defaultColor;
    }

    private static MutableComponent formatModifier(Holder<Attribute> attribute, Operation operation, double amount, TooltipFlag flag, String icon, int color) {
        DisplayConfig config = DisplayConfig.get();
        boolean positive = amount >= 0;
        boolean addition = operation.name().equals("ADDITION") || operation.name().equals("ADD_VALUE");
        boolean additionAsPercentage = addition && config.isAdditionAsPercentage(attributeId(attribute));
        double visible = addition && !additionAsPercentage ? Math.abs(amount) : Math.abs(amount) * 100D;
        String value = NUMBER.format(visible);
        String sign = positive ? "+" : "-";
        String suffix = addition && !additionAsPercentage ? "" : "%";
        Style textStyle = style(color);
        MutableComponent line = Component.literal(icon).withStyle(textStyle.withFont(config.iconFontId()))
            .append(Component.literal(" " + sign + value + suffix + " ").withStyle(textStyle))
            .append(Component.translatable(attribute.value().getDescriptionId()).withStyle(textStyle));
        if (flag.isAdvanced()) line.append(Component.literal(" [" + operation.name() + "]").withStyle(ChatFormatting.GRAY));
        return line;
    }

    private static String attributeId(Holder<Attribute> attribute) {
        return attribute.unwrapKey().map(key -> key.location().toString()).orElse(null);
    }

    private static Style style(int color) {
        return Style.EMPTY.withColor(TextColor.fromRgb(color));
    }
}

