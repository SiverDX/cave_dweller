package de.cadentem.cave_dweller.util;

import de.cadentem.cave_dweller.config.ClientConfig;

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
        if (ClientConfig.USE_UPDATED_TEXTURES.get()) {
           return "_frogballoon";
        }

        return "";
    }
}
