package de.cadentem.cave_dweller;

import com.mojang.logging.LogUtils;
import de.cadentem.cave_dweller.client.CaveDwellerRenderer;
import de.cadentem.cave_dweller.config.ServerConfig;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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

    private boolean initialized; // TODO :: Currently needed since config values are not present at server start
    private int calmTimer;
    private int noiseTimer;
    private boolean anySpelunkers = false;
    private final List<Player> spelunkers = new ArrayList<>();

    public CaveDweller() {
        GeckoLib.initialize();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::clientSetup);

        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        ModEntityTypes.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        EntityRenderers.register(ModEntityTypes.CAVE_DWELLER.get(), CaveDwellerRenderer::new);
    }

    @SubscribeEvent
    public void serverTick(final TickEvent.ServerTickEvent event) {
        if (!initialized) {
            resetNoiseTimer();
            resetCalmTimer();
            initialized = true;
        }

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
        if (this.noiseTimer <= 0 && (dwellerExists.get() || this.calmTimer <= Utils.secondsToTicks(ServerConfig.RESET_CALM_MAX.get()) / 2)) {
            overworld.getPlayers(this::playCaveSoundToSpelunkers);
        }

        boolean canSpawn = this.calmTimer <= 0;

        --this.calmTimer;
        if (canSpawn && !dwellerExists.get()) {
            Random random = new Random();

            double chanceToSpawnPerTick = ServerConfig.SPAWN_CHANCE_PER_TICK.get();
            if (random.nextDouble() <= chanceToSpawnPerTick) {
                this.spelunkers.clear();
                this.anySpelunkers = false;

                overworld.getPlayers(this::listSpelunkers);

                if (this.anySpelunkers) {
                    Player victim = this.spelunkers.get(random.nextInt(this.spelunkers.size()));
                    overworld.getPlayers(this::playCaveSoundToSpelunkers);

                    CaveDwellerEntity caveDweller = new CaveDwellerEntity(ModEntityTypes.CAVE_DWELLER.get(), overworld);
                    caveDweller.setInvisible(true);
                    caveDweller.setPos(caveDweller.generatePos(victim));
                    overworld.addFreshEntity(caveDweller);
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

//        /* FIXME :: Move to some client method
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
//        */

        return true;
    }

    public boolean checkIfPlayerIsSpelunker(final Player player) {
        if (player == null) {
            return false;
        } else {
            Level level = player.getLevel();
            BlockPos playerBlockPos = new BlockPos(player.position().x, player.position().y, player.position().z);
            return player.position().y < ServerConfig.SPAWN_HEIGHT.get() && (ServerConfig.ALLOW_SURFACE_SPAWN.get() || !level.canSeeSky(playerBlockPos));
        }
    }

    private void resetCalmTimer() {
        Random random = new Random();
        this.calmTimer = random.nextInt(Utils.secondsToTicks(ServerConfig.RESET_CALM_MIN.get()), Utils.secondsToTicks(ServerConfig.RESET_CALM_MAX.get()));

        if (random.nextDouble() <= ServerConfig.RESET_CALM_COOLDOWN_CHANCE.get()) {
            this.calmTimer = Utils.secondsToTicks(ServerConfig.RESET_CALM_COOLDOWN.get());
        }
    }

    private void resetNoiseTimer() {
        Random random = new Random();
        this.noiseTimer = random.nextInt(Utils.secondsToTicks(ServerConfig.RESET_NOISE_MIN.get()), Utils.secondsToTicks(ServerConfig.RESET_NOISE_MAX.get()));
    }
}
