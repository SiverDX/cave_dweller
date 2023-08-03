package de.cadentem.cave_dweller.util;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.NotNull;

public class Utils {
    public static int ticksToSeconds(int ticks) {
        return ticks / 20;
    }

    public static int secondsToTicks(int seconds) {
        return seconds * 20;
    }

    public static int minutesToTicks(int minutes) {
        return secondsToTicks(minutes * 60);
    }

    public static String getTextureAppend() {
//        if (ClientConfig.USE_UPDATED_TEXTURES.get()) {
//           return "";
//        }

        return "";
    }

    public static boolean isValidPlayer(final Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        if (!player.isAlive()) {
            return false;
        }

        if (!ServerConfig.TARGET_INVISIBLE.get() && player.isInvisible()) {
            return false;
        }

        return !(player.isCreative() || player.isSpectator());
    }

    public static LivingEntity getValidTarget(@NotNull final CaveDwellerEntity caveDweller) {
        return caveDweller.level().getNearestPlayer(caveDweller.position().x, caveDweller.position().y, caveDweller.position().z, 128, Utils::isValidPlayer);
    }

    public static boolean isOnSurface(final Entity entity) {
        if (entity == null) {
            return false;
        }

        if (entity.level() instanceof  ServerLevel serverLevel) {
            BlockPos blockPosition = entity.blockPosition();

            if (serverLevel.canSeeSky(blockPosition)) {
                return true;
            }

            Holder<Biome> biome = serverLevel.getBiome(blockPosition);

            if (biome.is(Tags.Biomes.IS_CAVE) || biome.is(Tags.Biomes.IS_UNDERGROUND)) {
                return false;
            }

            // canSeeSky returns false when you stand below trees etc.
            int baseSkyLightLevel = serverLevel.getBrightness(LightLayer.SKY, blockPosition) - serverLevel.getSkyDarken();

            if (baseSkyLightLevel > 0) {
                return true;
            }
        }

        return false;
    }
}
