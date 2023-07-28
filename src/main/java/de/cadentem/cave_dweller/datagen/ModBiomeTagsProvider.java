package de.cadentem.cave_dweller.datagen;

import de.cadentem.cave_dweller.CaveDweller;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModBiomeTagsProvider extends BiomeTagsProvider {
    public static TagKey<Biome> CAVE_DWELLER_SURFACE_BIOMES = TagKey.create(Registries.BIOME, new ResourceLocation(CaveDweller.MODID, "cave_dweller_surface_biomes"));

    public ModBiomeTagsProvider(final PackOutput packOutput, final CompletableFuture<HolderLookup.Provider> registries, final String modId, @Nullable final ExistingFileHelper existingFileHelper) {
        super(packOutput, registries, modId, existingFileHelper);
    }

    @Override
    protected void addTags(final @NotNull HolderLookup.Provider provider) {
        tag(CAVE_DWELLER_SURFACE_BIOMES).addOptionalTag(Tags.Biomes.IS_SPOOKY.location());
    }
}
