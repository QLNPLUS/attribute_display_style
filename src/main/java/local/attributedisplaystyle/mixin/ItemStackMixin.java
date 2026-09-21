package local.attributedisplaystyle.mixin;

import java.util.List;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Inject(method = "getTooltipLines(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;", at = @At(value = "INVOKE", ordinal = 4, target = "net/minecraft/world/item/ItemStack.shouldShowInTooltip(ILnet/minecraft/world/item/ItemStack$TooltipPart;)Z"), locals = LocalCapture.CAPTURE_FAILHARD, require = 1)
    private void attributeDisplayStyle$beforeModifiers(@Nullable Player player, TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir, List<Component> list) {
        list.add(Component.literal("ATTRIBUTE_DISPLAY_STYLE_START"));
    }

    @Inject(method = "getTooltipLines(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;", at = @At(value = "INVOKE", ordinal = 1, target = "net/minecraft/world/item/ItemStack.hasTag()Z"), locals = LocalCapture.CAPTURE_FAILHARD, require = 1)
    private void attributeDisplayStyle$afterModifiers(@Nullable Player player, TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir, List<Component> list) {
        list.add(Component.literal("ATTRIBUTE_DISPLAY_STYLE_END"));
    }
}

