package de.cadentem.cave_dweller.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

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
//           return "_frogballoon";
//        }

        return "";
    }

    public static boolean isValidPlayer(final LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        return !(player.isCreative() || player.isSpectator());
    }
}
