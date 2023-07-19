package de.cadentem.cave_dweller.registry;

import de.cadentem.cave_dweller.CaveDweller;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CaveDweller.MODID);
    public static final RegistryObject<Item> WORM = ITEMS.register("worm", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BABY_SPIDER = ITEMS.register("baby_spider", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CAVE_DWELLER_SPAWN_EGG = ITEMS.register(
            "cave_dweller_spawn_egg", () -> new ForgeSpawnEggItem(ModEntityTypes.CAVE_DWELLER, 12895428, 790333, new Item.Properties())
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
