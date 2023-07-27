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
import org.slf4j.Logger;
import software.bernie.geckolib3.GeckoLib;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(CaveDweller.MODID)
public class CaveDweller {
    public static final String MODID = "cave_dweller";
    public static final Logger LOG = LogUtils.getLogger();

    public static boolean doReload;

    private final List<Player> spelunkers = new ArrayList<>();
    private final Random random = new Random();

    private int calmTimer;
    private int noiseTimer;

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
        doReload = true;
    }

    @SubscribeEvent
    public void serverTick(final TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Prevent ticking twice per server tick
            return;
        }

        if (doReload) {
            spelunkers.clear();
            resetNoiseTimer();
            resetCalmTimer();
            doReload = false;
            LOG.info("Values have been reloaded");
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
                resetCalmTimer();
            }
        });

        --noiseTimer;
        if (noiseTimer <= 0 && (dwellerExists.get() || calmTimer <= Utils.secondsToTicks(ServerConfig.CAN_SPAWN_MAX.get()) / 2)) {
            overworld.getPlayers(this::playCaveSoundToSpelunkers);
            resetNoiseTimer();
        }

        boolean canSpawn = calmTimer <= 0;

        --calmTimer; // FIXME :: Maybe don't let this go too low (if server is running empty e.g.)
        if (canSpawn && !dwellerExists.get()) {
            if (random.nextDouble() <= ServerConfig.SPAWN_CHANCE_PER_TICK.get()) {
                spelunkers.clear();

                overworld.getPlayers(this::listSpelunkers);

                if (!spelunkers.isEmpty()) {
                    Player victim = spelunkers.get(random.nextInt(spelunkers.size()));
                    overworld.getPlayers(this::playCaveSoundToSpelunkers);

                    CaveDwellerEntity caveDweller = new CaveDwellerEntity(ModEntityTypes.CAVE_DWELLER.get(), overworld);
                    caveDweller.setInvisible(true);
                    caveDweller.setPos(caveDweller.generatePos(victim));
                    overworld.addFreshEntity(caveDweller);

                    resetCalmTimer();
                    resetNoiseTimer();
                }
            }
        }
    }

    private boolean listSpelunkers(final ServerPlayer player) {
        if (isPlayerSpelunker(player)) {
            spelunkers.add(player);
        }

        return true;
    }

    public boolean playCaveSoundToSpelunkers(final ServerPlayer player) {
        if (!isPlayerSpelunker(player)) {
            return false;
        }

        // TODO :: Play the same sound to all players?
        ResourceLocation soundLocation = switch (random.nextInt(4)) {
            case 1 -> ModSounds.CAVENOISE_2.get().getLocation();
            case 2 -> ModSounds.CAVENOISE_3.get().getLocation();
            case 3 -> ModSounds.CAVENOISE_4.get().getLocation();
            default -> ModSounds.CAVENOISE_1.get().getLocation();
        };

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CaveSound(soundLocation, player.blockPosition(), 2.0F, 1.0F));

        return true;
    }

    public boolean isPlayerSpelunker(final ServerPlayer player) {
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
        /*
        ResourceLocation biomeResource = ForgeRegistries.BIOMES.getKey(biome.get());

        if (biomeResource == null) {
            LOG.warn("Biome [" + biome.get() + "] could not be determined while checking if the cave dweller can spawn");
            return false;
        }

        boolean isBiomeInList = ServerConfig.SURFACE_BIOME.get().contains(biomeResource.toString());
        */

        boolean isWhitelist = ServerConfig.SURFACE_BIOMES_IS_WHITELIST.get();
        boolean isBiomeInList = biome.is(ModBiomeTagsProvider.CAVE_DWELLER_SURFACE_BIOMES);

        if (isOnSurface && (/* Whitelist */ !isBiomeInList && isWhitelist || /* Blacklist */ isBiomeInList && !isWhitelist)) {
            return false;
        }

        return true;
    }

    private void resetCalmTimer() {

        if (random.nextDouble() <= ServerConfig.CAN_SPAWN_COOLDOWN_CHANCE.get()) {
            calmTimer = Utils.secondsToTicks(ServerConfig.CAN_SPAWN_COOLDOWN.get());
        } else {
            int min = ServerConfig.CAN_SPAWN_MIN.get();
            int max = ServerConfig.CAN_SPAWN_MAX.get();

            if (max < min) {
                int temp = min;
                min = max;
                max = temp;

                LOG.error("Configuration for `RESET_CALM` was wrong - max [{}] was smaller than min [{}] - values have been switched to prevent a crash", max, min);
            }

            calmTimer = random.nextInt(Utils.secondsToTicks(min), Utils.secondsToTicks(max + 1));
        }
    }

    private void resetNoiseTimer() {
        int min = ServerConfig.RESET_NOISE_MIN.get();
        int max = ServerConfig.RESET_NOISE_MAX.get();

        if (max < min) {
            int temp = min;
            min = max;
            max = temp;

            LOG.error("Configuration for `RESET_NOISE` was wrong - max [{}] was smaller than min [{}] - values have been switched to prevent a crash", max, min);
        }

        noiseTimer = random.nextInt(Utils.secondsToTicks(min), Utils.secondsToTicks(max + 1));
    }
}
