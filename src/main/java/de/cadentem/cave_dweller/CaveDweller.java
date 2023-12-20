package de.cadentem.cave_dweller;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import de.cadentem.cave_dweller.client.CaveDwellerRenderer;
import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.network.CaveSound;
import de.cadentem.cave_dweller.network.NetworkHandler;
import de.cadentem.cave_dweller.registry.ModEntityTypes;
import de.cadentem.cave_dweller.registry.ModItems;
import de.cadentem.cave_dweller.registry.ModSounds;
import de.cadentem.cave_dweller.util.Timer;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import software.bernie.geckolib3.GeckoLib;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@Mod(CaveDweller.MODID)
public class CaveDweller {
    public static final String MODID = "cave_dweller";
    public static final Logger LOG = LogUtils.getLogger();
    public static final Random RANDOM = new Random();

    private static final HashMap<String, Timer> TIMERS = new HashMap<>();

    public static boolean RELOAD_ALL = false;
    public static boolean RELOAD_MISSING = false;

    public CaveDweller() {
        GeckoLib.initialize();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::commonSetup);

        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        ModEntityTypes.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
//        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        EntityRenderers.register(ModEntityTypes.CAVE_DWELLER.get(), CaveDwellerRenderer::new);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        NetworkHandler.register();
    }

    @SubscribeEvent
    public void serverStartup(final ServerStartedEvent event) {
        RELOAD_ALL = true;
    }

    @SubscribeEvent
    public void serverTick(final TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Prevent ticking twice per server tick
            return;
        }

        if (RELOAD_ALL) {
            TIMERS.clear();
            RELOAD_ALL = false;
        }

        String key = event.world.dimension().location().toString();

        if (TIMERS.get(key) == null) {
            boolean isRelevant = ServerConfig.DIMENSION_WHITELIST.get().contains(key);

            if (isRelevant) {
                TIMERS.put(key, new Timer());
            }
        }

        if (TIMERS.get(key) != null && event.world instanceof ServerLevel serverLevel) {
            handleLogic(serverLevel);
        }
    }

    private void handleLogic(final ServerLevel level) {
        if (level == null) {
            return;
        }

        List<ServerPlayer> players = level.getPlayers(this::isRelevantPlayer);

        if (players.isEmpty()) {
            return;
        }

        String key = level.dimension().location().toString();
        Timer timer = TIMERS.get(key);

        if (timer.currentVictim == null || players.stream().filter(element -> element.getStringUUID().equals(timer.currentVictim.getStringUUID())).toList().isEmpty()) {
            timer.currentVictim = players.get(RANDOM.nextInt(players.size()));
        }

        Iterable<Entity> entities = level.getAllEntities();
        AtomicInteger caveDwellerCount = new AtomicInteger();

        entities.forEach(entity -> {
            if (entity instanceof CaveDwellerEntity) {
                caveDwellerCount.getAndAdd(1);
            }
        });

        timer.currentSpawn++;
        timer.currentNoise++;

        if (timer.isNoiseTimerReached() && (caveDwellerCount.get() > 0 || timer.currentSpawn >= Utils.secondsToTicks(ServerConfig.CAN_SPAWN_MAX.get()) / 2)) {
            playCaveSoundToSpelunkers(players, timer);
        }

        if (timer.isSpawnTimerReached() && caveDwellerCount.get() < ServerConfig.MAXIMUM_AMOUNT.get()) {
            if (RANDOM.nextDouble() <= ServerConfig.SPAWN_CHANCE_PER_TICK.get()) {
                if (timer.currentVictim != null) {
                    Optional<CaveDwellerEntity> optionalEntity = Utils.trySpawnMob(timer.currentVictim, ModEntityTypes.CAVE_DWELLER.get(), MobSpawnType.TRIGGERED, level, timer.currentVictim.blockPosition(), 40, /* x & z offset */ 35, /* y offset */ 6);

                    if (optionalEntity.isPresent()) {
                        playCaveSoundToSpelunkers(players, timer);

                        CaveDwellerEntity caveDweller = optionalEntity.get();
                        caveDweller.setInvisible(true);
                        caveDweller.hasSpawned = true;

                        timer.resetSpawnTimer();
                    } else {
                        // Spawn failed - potentially try a different player
                        timer.currentVictim = null;
                    }
                }
            }
        }
    }

    private void playCaveSoundToSpelunkers(final List<ServerPlayer> players, final Timer timer) {
        Entity currentVictim = timer.currentVictim;

        if (currentVictim == null) {
            return;
        }

        players.forEach(player -> {
            ResourceLocation soundLocation = switch (RANDOM.nextInt(4)) {
                case 1 -> ModSounds.CAVENOISE_2.get().getLocation();
                case 2 -> ModSounds.CAVENOISE_3.get().getLocation();
                case 3 -> ModSounds.CAVENOISE_4.get().getLocation();
                default -> ModSounds.CAVENOISE_1.get().getLocation();
            };

            if (!ServerConfig.ONLY_PLAY_NOISE_TO_TARGET.get() || player.is(timer.currentVictim)) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CaveSound(soundLocation, currentVictim.blockPosition(), 2.0F, 1.0F));
            }
        });

        timer.resetNoiseTimer();
    }

    private boolean isRelevantPlayer(final ServerPlayer player) {
        if (!Utils.isValidPlayer(player)) {
            return false;
        }

        // Height level check
        if (player.position().y > ServerConfig.SPAWN_HEIGHT.get()) {
            return false;
        }

        ServerLevel serverLevel = player.getLevel();

        // Sky light level check
        // Referenced from DaylightDetectorBlock
        int actualSkyLightLevel = serverLevel.getBrightness(LightLayer.SKY, player.blockPosition()) - serverLevel.getSkyDarken();
        float sunAngle = serverLevel.getSunAngle(1.0F);

        if (actualSkyLightLevel > 0) {
            float f1 = sunAngle < (float) Math.PI ? 0.0F : ((float) Math.PI * 2F);
            sunAngle += (f1 - sunAngle) * 0.2F;
            actualSkyLightLevel = Math.round((float) actualSkyLightLevel * Mth.cos(sunAngle));
        }

        actualSkyLightLevel = Mth.clamp(actualSkyLightLevel, 0, 15);

        if (actualSkyLightLevel > ServerConfig.SKY_LIGHT_LEVEL.get()) {
            return false;
        }

        // Block light level check
        LayerLightEventListener blockLighting = player.getLevel().getLightEngine().getLayerListener(LightLayer.BLOCK);

        if (blockLighting.getLightValue(player.blockPosition()) > ServerConfig.BLOCK_LIGHT_LEVEL.get()) {
            return false;
        }

        boolean isOnSurface = Utils.isOnSurface(player);

        if (isOnSurface) {
            if (!ServerConfig.ALLOW_SURFACE_SPAWN.get()) {
                return false;
            }

            return ServerConfig.isInValidBiome(player);
        }

        return true;
    }

    public static boolean speedUpTimers(final String key, int spawnDelta, int noiseDelta) {
        Timer timer = TIMERS.get(key);
        CaveDweller.LOG.debug("Speeding up timers for the dimension [{}], timer: [{}]", key, timer);

        if (timer != null) {
            timer.currentSpawn += spawnDelta;
            timer.currentNoise += noiseDelta;
            return true;
        }

        return false;
    }

    public static Pair<Integer, Integer> getTimer(final String key, final String type) {
        Timer timer = TIMERS.get(key);

        int current = -1;
        int target = -1;

        if (timer != null) {
            switch (type) {
                case "spawn" -> {
                    current = timer.currentSpawn;
                    target = timer.targetSpawn;
                }
                case "noise" -> {
                    current = timer.currentNoise;
                    target = timer.targetNoise;
                }
            }
        }

        return Pair.of(current, target);
    }

    public static @Nullable Entity getCurrentVictim(final String key) {
        Timer timer = TIMERS.get(key);

        if (timer != null) {
            return timer.currentVictim;
        }

        return null;
    }
}
