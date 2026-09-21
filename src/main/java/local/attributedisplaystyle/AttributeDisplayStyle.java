package local.attributedisplaystyle;

import local.attributedisplaystyle.client.AttributeTooltipHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(AttributeDisplayStyle.MOD_ID)
public final class AttributeDisplayStyle {
    public static final String MOD_ID = "attribute_display_style";

    public AttributeDisplayStyle() {
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
            () -> () -> MinecraftForge.EVENT_BUS.register(new AttributeTooltipHandler()));
    }
}

