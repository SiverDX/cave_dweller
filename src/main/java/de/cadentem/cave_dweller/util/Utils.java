package de.cadentem.cave_dweller.util;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
}
