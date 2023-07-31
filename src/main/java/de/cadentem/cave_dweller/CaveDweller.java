package de.cadentem.cave_dweller;

import com.mojang.logging.LogUtils;
import de.cadentem.cave_dweller.client.CaveDwellerRenderer;
import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.datagen.ModBiomeTagsProvider;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.network.CaveSound;
import de.cadentem.cave_dweller.network.NetworkHandler;
import de.cadentem.cave_dweller.registry.ModEntityTypes;
import de.cadentem.cave_dweller.registry.ModItems;
import de.cadentem.cave_dweller.registry.ModSounds;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
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
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.core.jmx.Server;
import org.slf4j.Logger;
import software.bernie.geckolib3.GeckoLib;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@Mod(CaveDweller.MODID)
public class CaveDweller {
    public static final String MODID = "cave_dweller";
    public static final Logger LOG = LogUtils.getLogger();

    private static final int SPAWN_TIMER = 0;
    private static final int NOISE_TIMER = 1;

    public static boolean RELOAD_ALL = false;
    public static boolean RELOAD_MISSING = false;

    private final Random random = new Random();
    // TODO :: Could add these as capability to the level so they don't always reset on a server restart
    private final HashMap<String, Integer[]> timers = new HashMap<>();

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
            timers.clear();
        }

        String key = event.world.dimension().location().toString();

        // Not doing this together in the `handeLogic` loop in case the boolean gets set from a different thread
        // FIXME :: The reload booleans are kinda useless in this version
        if (RELOAD_ALL || timers.get(key) == null) {
            boolean isRelevant = ServerConfig.DIMENSION_WHITELIST.get().contains(key);

            if (isRelevant) {
                resetTimers(key);
            }

            RELOAD_ALL = false;
            RELOAD_MISSING = false;
        } else if (RELOAD_MISSING || timers.get(key) == null) {
            boolean isRelevant = timers.get(key) == null && ServerConfig.DIMENSION_WHITELIST.get().contains(key);

            if (isRelevant) {
                resetTimers(key);
            }

            RELOAD_MISSING = false;
        }

        if (timers.get(key) != null && event.world instanceof ServerLevel serverLevel) {
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

        Iterable<Entity> entities = level.getAllEntities();
        AtomicInteger caveDwellerCount = new AtomicInteger();

        // TODO :: Have a global (across all levels) count?
        entities.forEach(entity -> {
            if (entity instanceof CaveDwellerEntity) {
                caveDwellerCount.getAndAdd(1);
            }
        });

        String key = level.dimension().location().toString();
        timers.merge(key, new Integer[]{-1, -1}, this::addDelta);

        if (timers.get(key)[NOISE_TIMER] <= 0 && (caveDwellerCount.get() > 0 || timers.get(key)[SPAWN_TIMER] <= Utils.secondsToTicks(ServerConfig.CAN_SPAWN_MAX.get()) / 2)) {
            players.forEach(this::playCaveSoundToSpelunkers);
            resetNoiseTimer(key);
        }

        boolean canSpawn = timers.get(key)[SPAWN_TIMER] <= 0;

        if (canSpawn && caveDwellerCount.get() < ServerConfig.MAXIMUM_AMOUNT.get()) {
            if (random.nextDouble() <= ServerConfig.SPAWN_CHANCE_PER_TICK.get()) {
                if (!players.isEmpty()) {
                    Player victim = players.get(random.nextInt(players.size()));
                    level.getPlayers(this::playCaveSoundToSpelunkers);

                    CaveDwellerEntity caveDweller = new CaveDwellerEntity(ModEntityTypes.CAVE_DWELLER.get(), level);
                    caveDweller.setInvisible(true);
                    caveDweller.setPos(caveDweller.generatePos(victim));
                    caveDweller.finalizeSpawn(level, level.getCurrentDifficultyAt(victim.blockPosition()), MobSpawnType.TRIGGERED, null, null);
                    level.addFreshEntity(caveDweller);

                    resetSpawnTimer(key);
                    resetNoiseTimer(key);
                }
            }
        }
    }

    private boolean playCaveSoundToSpelunkers(final ServerPlayer player) {
        ResourceLocation soundLocation = switch (random.nextInt(4)) {
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
        int baseSkyLightLevel = serverLevel.getBrightness(LightLayer.SKY, player.blockPosition()) - serverLevel.getSkyDarken();
        int actualSkyLightLevel = baseSkyLightLevel;
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

        // canSeeSky returns false when you stand below trees etc.
        // FIXME :: Also unreliable, might need to check for biomes instead
        boolean isOnSurface = baseSkyLightLevel > 0;

        if (!ServerConfig.ALLOW_SURFACE_SPAWN.get() && isOnSurface) {
            return false;
        }

        // Check biome
        Holder<Biome> biome = serverLevel.getBiome(player.blockPosition());

        boolean isWhitelist = ServerConfig.SURFACE_BIOMES_IS_WHITELIST.get();
        boolean isBiomeInList;

        if (ServerConfig.OVERRIDE_BIOME_DATAPACK_CONFIG.get()) {
            ResourceLocation resource = ForgeRegistries.BIOMES.getKey(biome.value());
            isBiomeInList = resource != null && ServerConfig.SURFACE_BIOMES.get().contains(resource.toString());
        } else {
            isBiomeInList = biome.is(ModBiomeTagsProvider.CAVE_DWELLER_SURFACE_BIOMES);
        }

        if (isOnSurface && (/* Whitelist */ !isBiomeInList && isWhitelist || /* Blacklist */ isBiomeInList && !isWhitelist)) {
            return false;
        }

        return true;
    }

    private void resetTimers(final String key) {
        resetSpawnTimer(key);
        resetNoiseTimer(key);

        LOG.info("Timers have been reset for [" + key + "]");
    }

    private void resetSpawnTimer(final String key) {
        int spawnTimer;

        if (random.nextDouble() <= ServerConfig.CAN_SPAWN_COOLDOWN_CHANCE.get()) {
            spawnTimer = Utils.secondsToTicks(ServerConfig.CAN_SPAWN_COOLDOWN.get());
        } else {
            int min = ServerConfig.CAN_SPAWN_MIN.get();
            int max = ServerConfig.CAN_SPAWN_MAX.get();

            if (max < min) {
                int temp = min;
                min = max;
                max = temp;

                LOG.error("Configuration for `RESET_CALM` was wrong - max [{}] was smaller than min [{}] - values have been switched to prevent a crash", max, min);
            }

            spawnTimer = random.nextInt(Utils.secondsToTicks(min), Utils.secondsToTicks(max + 1));
        }

        timers.merge(key, new Integer[]{spawnTimer, 0}, this::setSpawnTimer);
    }

    private void resetNoiseTimer(final String key) {
        int min = ServerConfig.RESET_NOISE_MIN.get();
        int max = ServerConfig.RESET_NOISE_MAX.get();

        if (max < min) {
            int temp = min;
            min = max;
            max = temp;

            LOG.error("Configuration for `RESET_NOISE` was wrong - max [{}] was smaller than min [{}] - values have been switched to prevent a crash", max, min);
        }

        int noiseTimer = random.nextInt(Utils.secondsToTicks(min), Utils.secondsToTicks(max + 1));
        timers.merge(key, new Integer[]{0, noiseTimer}, this::setNoiseTimer);
    }

    private Integer[] addDelta(final Integer[] current, final Integer[] delta) {
        Integer[] result = new Integer[2];

        result[SPAWN_TIMER] = current[SPAWN_TIMER] + delta[SPAWN_TIMER];
        result[NOISE_TIMER] = current[NOISE_TIMER] + delta[NOISE_TIMER];

        return result;
    }

    private Integer[] setNoiseTimer(final Integer[] current, final Integer[] delta) {
        Integer[] result = new Integer[2];

        result[SPAWN_TIMER] = current[SPAWN_TIMER];
        result[NOISE_TIMER] = delta[NOISE_TIMER];

        return result;
    }

    private Integer[] setSpawnTimer(final Integer[] current, final Integer[] delta) {
        Integer[] result = new Integer[2];

        result[SPAWN_TIMER] = delta[SPAWN_TIMER];
        result[NOISE_TIMER] = current[NOISE_TIMER];

        return result;
    }
}
