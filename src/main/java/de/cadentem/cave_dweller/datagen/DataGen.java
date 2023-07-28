package de.cadentem.cave_dweller.datagen;

import de.cadentem.cave_dweller.CaveDweller;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = CaveDweller.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGen {
    @SubscribeEvent
    public static void configureDataGen(final GatherDataEvent event){
        DataGenerator generator = event.getGenerator();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        CompletableFuture<HolderLookup.Provider> registries = CompletableFuture.supplyAsync(VanillaRegistries::createLookup, Util.backgroundExecutor());

        generator.addProvider(event.includeClient(), new ModItemModelProvider(generator.getPackOutput(), CaveDweller.MODID, existingFileHelper));
        generator.addProvider(event.includeServer(), new ModBiomeTagsProvider(generator.getPackOutput(), registries, CaveDweller.MODID, existingFileHelper));
    }
}
