package de.cadentem.cave_dweller.datagen;

import de.cadentem.cave_dweller.CaveDweller;
import net.minecraft.core.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

public class ModBiomeTagsProvider extends BiomeTagsProvider {
    public static TagKey<Biome> CAVE_DWELLER_SURFACE_BIOMES = TagKey.create(Registry.BIOME_REGISTRY, new ResourceLocation(CaveDweller.MODID, "cave_dweller_surface_biomes"));

    public ModBiomeTagsProvider(final DataGenerator generator, final String modId, @Nullable final ExistingFileHelper existingFileHelper) {
        super(generator, modId, existingFileHelper);
    }

    @Override
    protected void addTags() {
        tag(CAVE_DWELLER_SURFACE_BIOMES).addOptionalTag(Tags.Biomes.IS_SPOOKY.location());
    }
}
