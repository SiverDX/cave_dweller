package de.cadentem.cave_dweller.registry;

import de.cadentem.cave_dweller.CaveDweller;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CaveDweller.MODID);
    public static final RegistryObject<EntityType<CaveDwellerEntity>> CAVE_DWELLER = ENTITY_TYPES.register(
            "cave_dweller",
            () -> EntityType.Builder.of(CaveDwellerEntity::new, MobCategory.MONSTER)
                    .sized(0.5F, 2.7F)
                    .build(new ResourceLocation(CaveDweller.MODID, "cave_dweller").toString())
    );

    public static void register(final IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
