package de.cadentem.cave_dweller;

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
import net.minecraft.util.SpawnUtil;
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
    public void serverTick(final TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Prevent ticking twice per server tick
            return;
        }

        if (RELOAD_ALL) {
            TIMERS.clear();
            RELOAD_ALL = false;
        }

        Iterable<ServerLevel> levels = event.getServer().getAllLevels();

        // Not doing this together in the `handeLogic` loop in case the boolean gets set from a different thread
        if (TIMERS.isEmpty()) {
            for (ServerLevel level : levels) {
                String key = level.dimension().location().toString();
                boolean isRelevant = ServerConfig.DIMENSION_WHITELIST.get().contains(key);

                if (isRelevant) {
                    TIMERS.put(key, new Timer());
                }
            }

            RELOAD_ALL = false;
            RELOAD_MISSING = false;
        } else if (RELOAD_MISSING) {
            for (ServerLevel level : levels) {
                String key = level.dimension().location().toString();
                boolean isRelevant = TIMERS.get(key) == null && ServerConfig.DIMENSION_WHITELIST.get().contains(key);

                if (isRelevant) {
                    TIMERS.put(key, new Timer());
                }
            }

            RELOAD_MISSING = false;
        }

        for (ServerLevel level : levels) {
            String key = level.dimension().location().toString();

            if (TIMERS.get(key) != null) {
                handleLogic(level);
            }
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
            players.forEach(this::playCaveSoundToSpelunkers);
            timer.resetNoiseTimer();
        }

        if (timer.isSpawnTimerReached() && caveDwellerCount.get() < ServerConfig.MAXIMUM_AMOUNT.get()) {
            if (RANDOM.nextDouble() <= ServerConfig.SPAWN_CHANCE_PER_TICK.get()) {
                if (timer.currentVictim != null) {
                    level.getPlayers(this::playCaveSoundToSpelunkers);
                    timer.resetNoiseTimer();
                    Optional<CaveDwellerEntity> optional = SpawnUtil.trySpawnMob(ModEntityTypes.CAVE_DWELLER.get(), MobSpawnType.TRIGGERED, level, timer.currentVictim.blockPosition(), 40, /* x & z offset */ 35, /* y offset */ 6, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER);

                    if (optional.isPresent()) {
                        CaveDwellerEntity caveDweller = optional.get();
                        caveDweller.setInvisible(true);
                        caveDweller.finalizeSpawn(level, level.getCurrentDifficultyAt(timer.currentVictim.blockPosition()), MobSpawnType.TRIGGERED, null, null);

                        timer.resetSpawnTimer();
                    }
                }
            }
        }
    }

    private boolean playCaveSoundToSpelunkers(final ServerPlayer player) {
        ResourceLocation soundLocation = switch (RANDOM.nextInt(4)) {
            case 1 -> ModSounds.CAVENOISE_2.get().getLocation();
            case 2 -> ModSounds.CAVENOISE_3.get().getLocation();
            case 3 -> ModSounds.CAVENOISE_4.get().getLocation();
            default -> ModSounds.CAVENOISE_1.get().getLocation();
        };

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CaveSound(soundLocation, player.blockPosition(), 2.0F, 1.0F));

        return true;
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

    public static void speedUpTimers(final String key, int spawnDelta, int noiseDelta) {
        Timer timer = TIMERS.get(key);
        timer.currentSpawn += spawnDelta;
        timer.currentNoise += noiseDelta;
    }
}
