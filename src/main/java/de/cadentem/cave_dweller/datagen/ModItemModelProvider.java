package de.cadentem.cave_dweller.datagen;

import de.cadentem.cave_dweller.CaveDweller;
import de.cadentem.cave_dweller.registry.ModItems;
import net.minecraft.data.DataGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(final DataGenerator generator, final String modId, final ExistingFileHelper existingFileHelper) {
        super(generator, modId, existingFileHelper);
    }

    protected void registerModels() {
        this.withExistingParent(ModItems.CAVE_DWELLER_SPAWN_EGG.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
    }
}