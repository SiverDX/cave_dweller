package de.cadentem.cave_dweller.events;

import de.cadentem.cave_dweller.CaveDweller;
import de.cadentem.cave_dweller.client.CaveDwellerEyesLayer;
import de.cadentem.cave_dweller.config.ClientConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.registry.ModEntityTypes;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = CaveDweller.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {
    @SubscribeEvent
    public static void entityAttributeEvent(final EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.CAVE_DWELLER.get(), CaveDwellerEntity.getAttributeBuilder());
    }

    @SubscribeEvent
    public void reloadConfiguration(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            CaveDwellerEyesLayer.TEXTURE = new ResourceLocation(CaveDweller.MODID, "textures/entity/cave_dweller_eyes_texture" + Utils.getTextureAppend() + ".png");
        }
    }
}
