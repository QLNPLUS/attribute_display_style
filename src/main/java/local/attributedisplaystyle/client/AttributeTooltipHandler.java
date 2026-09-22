package local.attributedisplaystyle.client;

import com.google.common.collect.Multimap;
import local.attributedisplaystyle.config.DisplayConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStack.TooltipPart;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AttributeTooltipHandler {
    private static final String START = "ATTRIBUTE_DISPLAY_STYLE_START";
    private static final String END = "ATTRIBUTE_DISPLAY_STYLE_END";
    private static final DecimalFormat NUMBER = new DecimalFormat("#.##");

    public AttributeTooltipHandler() {
        DisplayConfig.load();
    }

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        DisplayConfig.load();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTooltip(ItemTooltipEvent event) {
        List<Component> tooltip = event.getToolTip();
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
        addModifiers(event.getItemStack(), event.getFlags(), replacement);
        tooltip.addAll(start, replacement);
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

    private static void addModifiers(ItemStack stack, TooltipFlag flag, List<Component> output) {
        addGroup(stack, flag, output, EquipmentSlot.MAINHAND, "mainhand");
        addGroup(stack, flag, output, EquipmentSlot.OFFHAND, "offhand");
        addGroup(stack, flag, output, EquipmentSlot.HEAD, "head");
        addGroup(stack, flag, output, EquipmentSlot.CHEST, "chest");
        addGroup(stack, flag, output, EquipmentSlot.LEGS, "legs");
        addGroup(stack, flag, output, EquipmentSlot.FEET, "feet");
    }

    private static void addGroup(ItemStack stack, TooltipFlag flag, List<Component> output, EquipmentSlot slot, String groupName) {
        Multimap<Attribute, AttributeModifier> source = stack.getAttributeModifiers(slot);
        if (source.isEmpty()) return;

        Map<Attribute, EnumMap<Operation, List<AttributeModifier>>> grouped = new LinkedHashMap<>();
        for (Map.Entry<Attribute, AttributeModifier> entry : source.entries()) {
            grouped.computeIfAbsent(entry.getKey(), ignored -> new EnumMap<>(Operation.class))
                .computeIfAbsent(entry.getValue().getOperation(), ignored -> new ArrayList<>())
                .add(entry.getValue());
        }

        List<Component> lines = new ArrayList<>();
        for (Map.Entry<Attribute, EnumMap<Operation, List<AttributeModifier>>> entry : grouped.entrySet()) {
            for (Map.Entry<Operation, List<AttributeModifier>> operation : entry.getValue().entrySet()) {
                List<AttributeModifier> modifiers = operation.getValue();
                if (!DisplayConfig.get().showUnchangedModifiers) modifiers.removeIf(modifier -> modifier.getAmount() == 0);
                if (modifiers.isEmpty()) continue;
                addOperationLines(entry.getKey(), operation.getKey(), modifiers, flag, lines);
            }
        }
        if (lines.isEmpty()) return;
        output.add(Component.empty());
        output.add(Component.translatable("item.modifiers." + groupName).withStyle(style(DisplayConfig.get().headerColor)));
        output.addAll(lines);
    }

    private static void addOperationLines(Attribute attribute, Operation operation, List<AttributeModifier> modifiers, TooltipFlag flag, List<Component> output) {
        DisplayConfig config = DisplayConfig.get();
        boolean merged = modifiers.size() > 1;
        double total = modifiers.stream().mapToDouble(AttributeModifier::getAmount).sum();
        if (merged) {
            output.add(formatModifier(attribute, operation, total, flag, config.defaultIcon, config.mergedColor));
            if (config.expandOnShift && Screen.hasShiftDown()) {
                for (AttributeModifier modifier : modifiers) {
                    output.add(Component.literal(config.nestedPrefix).withStyle(style(config.nestedColor))
                        .append(formatModifier(attribute, operation, modifier.getAmount(), flag, iconFor(modifier.getAmount()), config.nestedColor)));
                }
            }
            return;
        }

        AttributeModifier modifier = modifiers.get(0);
        output.add(formatModifier(attribute, operation, modifier.getAmount(), flag, iconFor(modifier.getAmount()), colorFor(modifier.getAmount())));
    }

    private static String iconFor(double amount) {
        DisplayConfig config = DisplayConfig.get();
        return amount > 0 ? config.positiveIcon : amount < 0 ? config.negativeIcon : config.defaultIcon;
    }

    private static int colorFor(double amount) {
        DisplayConfig config = DisplayConfig.get();
        return amount > 0 ? config.positiveColor : amount < 0 ? config.negativeColor : config.defaultColor;
    }

    private static MutableComponent formatModifier(Attribute attribute, Operation operation, double amount, TooltipFlag flag, String icon, int color) {
        DisplayConfig config = DisplayConfig.get();
        boolean positive = amount >= 0;
        boolean additionAsPercentage = operation == Operation.ADDITION
            && config.isAdditionAsPercentage(attributeId(attribute));
        double visible = operation == Operation.ADDITION && !additionAsPercentage
            ? Math.abs(amount)
            : Math.abs(amount) * 100D;
        String value = NUMBER.format(visible);
        String sign = positive ? "+" : "-";
        String suffix = operation == Operation.ADDITION && !additionAsPercentage ? "" : "%";
        Style textStyle = style(color);
        MutableComponent line = Component.literal(icon).withStyle(textStyle.withFont(config.iconFontId()))
            .append(Component.literal(" " + sign + " " + value + suffix + " ").withStyle(textStyle))
            .append(Component.translatable(attribute.getDescriptionId()).withStyle(textStyle));
        if (flag.isAdvanced()) line.append(Component.literal(" [" + operation.name() + "]").withStyle(ChatFormatting.GRAY));
        return line;
    }

    private static String attributeId(Attribute attribute) {
        net.minecraft.resources.ResourceLocation key = ForgeRegistries.ATTRIBUTES.getKey(attribute);
        return key == null ? null : key.toString();
    }

    private static Style style(int color) {
        return Style.EMPTY.withColor(TextColor.fromRgb(color));
    }
}
