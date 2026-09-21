package local.attributedisplaystyle;

import local.attributedisplaystyle.client.AttributeTooltipHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(AttributeDisplayStyle.MOD_ID)
public final class AttributeDisplayStyle {
    public static final String MOD_ID = "attribute_display_style";

    public AttributeDisplayStyle(IEventBus modBus) {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(new AttributeTooltipHandler());
            modBus.register(AttributeTooltipHandler.ModBusEvents.class);
        }
    }
}

