package de.cadentem.cave_dweller;

import com.mojang.logging.LogUtils;
import de.cadentem.cave_dweller.client.CaveDwellerModel;
import de.cadentem.cave_dweller.client.CaveDwellerRenderer;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.registry.ModEntityTypes;
import de.cadentem.cave_dweller.registry.ModItems;
import de.cadentem.cave_dweller.registry.ModSounds;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import software.bernie.geckolib3.GeckoLib;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(CaveDweller.MODID)
public class CaveDweller {
    public static final String MODID = "cave_dweller";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final int ticksCalmResetMin;
    private final int ticksCalmResetMax;
    private final int ticksCalmResetCooldown;
    private final int ticksNoiseResetMin;
    private final int ticksNoiseResetMax;
    private int calmTimer;
    private int noiseTimer;
    private boolean anySpelunkers = false;
    private final List<Player> spelunkers = new ArrayList<>();

    public CaveDweller() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::clientSetup);

        GeckoLib.initialize();

        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        ModEntityTypes.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        // FIXME :: Creative tab

        boolean USING_FAST_TIMERS = true;
        if (!USING_FAST_TIMERS) {
            this.ticksCalmResetMin = Utils.minutesToTicks(8);
            this.ticksCalmResetMax = Utils.minutesToTicks(10);
            this.ticksCalmResetCooldown = Utils.secondsToTicks(800);
            this.ticksNoiseResetMin = Utils.secondsToTicks(100);
            this.ticksNoiseResetMax = Utils.minutesToTicks(8);
            this.calmTimer = Utils.minutesToTicks(20);
        } else {
            this.ticksCalmResetMin = Utils.secondsToTicks(48);
            this.ticksCalmResetMax = Utils.minutesToTicks(1);
            this.ticksCalmResetCooldown = Utils.secondsToTicks(80);
            this.ticksNoiseResetMin = Utils.secondsToTicks(50);
            this.ticksNoiseResetMax = Utils.secondsToTicks(40);
            this.calmTimer = Utils.minutesToTicks(1);
        }

        this.noiseTimer = Utils.minutesToTicks(4);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        EntityRenderers.register(ModEntityTypes.CAVE_DWELLER.get(), manager -> new CaveDwellerRenderer(manager, new CaveDwellerModel()));
    }

    @SubscribeEvent
    public void onServerStarting(final ServerStartingEvent event) {
        this.resetCalmTimer();
    }

    @SubscribeEvent
    public void serverTick(final TickEvent.ServerTickEvent event) {
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);

        if (overworld == null) {
            return;
        }

        Iterable<Entity> entities = overworld.getAllEntities();
        AtomicBoolean dwellerExists = new AtomicBoolean(false);

        entities.forEach(entity -> {
            if (entity instanceof CaveDwellerEntity) {
                dwellerExists.set(true);
                this.resetCalmTimer();
            }
        });

        --this.noiseTimer;
        if (this.noiseTimer <= 0 && (dwellerExists.get() || this.calmTimer <= 6000)) {
            overworld.getPlayers(this::playCaveSoundToSpelunkers);
        }

        boolean canSpawn = this.calmTimer <= 0;

        --this.calmTimer;
        if (canSpawn && !dwellerExists.get()) {
            Random rand = new Random();

            double chanceToSpawnPerTick = 0.005;
            if (rand.nextDouble() <= chanceToSpawnPerTick) {
                this.spelunkers.clear();
                this.anySpelunkers = false;

                overworld.getPlayers(this::listSpelunkers);

                if (this.anySpelunkers) {
                    Player victim = this.spelunkers.get(rand.nextInt(this.spelunkers.size()));
                    overworld.getPlayers(this::playCaveSoundToSpelunkers);

                    CaveDwellerEntity caveDweller = new CaveDwellerEntity(ModEntityTypes.CAVE_DWELLER.get(), overworld);
                    caveDweller.setInvisible(true);
                    caveDweller.setPos(caveDweller.generatePos(victim));
                    this.resetCalmTimer();
                }
            }
        }
    }

    private boolean listSpelunkers(final ServerPlayer player) {
        if (this.checkIfPlayerIsSpelunker(player)) {
            this.anySpelunkers = true;
            this.spelunkers.add(player);
        }

        return true;
    }

    public boolean playCaveSoundToSpelunkers(final ServerPlayer player) {
        Random rand = new Random();
        BlockPos playerBlockPos = new BlockPos(player.position().x, player.position().y, player.position().z);

        /* FIXME :: Move to some client method
        if (this.checkIfPlayerIsSpelunker(player) && !player.isCreative() && !player.isSpectator()) {
            switch (rand.nextInt(4)) {
                case 0 -> Minecraft.getInstance()
                        .getSoundManager()
                        .play(new SimpleSoundInstance(ModSounds.CAVENOISE_1.get(), SoundSource.AMBIENT, 2.0F, 1.0F, RandomSource.create(), playerBlockPos));
                case 1 -> Minecraft.getInstance()
                        .getSoundManager()
                        .play(new SimpleSoundInstance(ModSounds.CAVENOISE_2.get(), SoundSource.AMBIENT, 2.0F, 1.0F, RandomSource.create(), playerBlockPos));
                case 2 -> Minecraft.getInstance()
                        .getSoundManager()
                        .play(new SimpleSoundInstance(ModSounds.CAVENOISE_3.get(), SoundSource.AMBIENT, 2.0F, 1.0F, RandomSource.create(), playerBlockPos));
                case 3 -> Minecraft.getInstance()
                        .getSoundManager()
                        .play(new SimpleSoundInstance(ModSounds.CAVENOISE_4.get(), SoundSource.AMBIENT, 2.0F, 1.0F, RandomSource.create(), playerBlockPos));
            }

            this.resetNoiseTimer();
        }
        */

        return true;
    }

    public boolean checkIfPlayerIsSpelunker(final Player player) {
        if (player == null) {
            return false;
        } else {
            Level level = player.getLevel();
            BlockPos playerBlockPos = new BlockPos(player.position().x, player.position().y, player.position().z);
            return player.position().y < 40.0 && !level.canSeeSky(playerBlockPos);
        }
    }

    private void resetCalmTimer() {
        Random rand = new Random();
        this.calmTimer = this.ticksCalmResetMin + rand.nextInt(this.ticksCalmResetMax);
        double chanceToCooldown = 0.4;
        if (rand.nextDouble() <= chanceToCooldown) {
            this.calmTimer = this.ticksCalmResetCooldown + rand.nextInt(this.ticksCalmResetCooldown);
        }
    }

    private void resetNoiseTimer() {
        Random rand = new Random();
        this.noiseTimer = this.ticksNoiseResetMin + rand.nextInt(this.ticksNoiseResetMax);
    }
}
