package de.cadentem.cave_dweller.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static ForgeConfigSpec.ConfigValue<Integer> RESET_CALM_MIN;
    public static ForgeConfigSpec.ConfigValue<Integer> RESET_CALM_MAX;
    public static ForgeConfigSpec.ConfigValue<Integer> RESET_CALM_COOLDOWN;
    public static ForgeConfigSpec.ConfigValue<Double> RESET_CALM_COOLDOWN_CHANCE;

    public static ForgeConfigSpec.ConfigValue<Integer> RESET_NOISE_MIN;
    public static ForgeConfigSpec.ConfigValue<Integer> RESET_NOISE_MAX;

    public static ForgeConfigSpec.ConfigValue<Double> SPAWN_CHANCE_PER_TICK;
    public static ForgeConfigSpec.ConfigValue<Integer> SPAWN_HEIGHT;
    public static ForgeConfigSpec.ConfigValue<Boolean> ALLOW_OPEN_SKY;


    static {
        BUILDER.comment("Influence the time it takes for a cave dweller to spawn");
        RESET_CALM_MIN = BUILDER.comment("Minimum time in seconds").defineInRange("reset_calm_min", 60, 0, 60 * 60 * 24 - 1);
        RESET_CALM_MAX = BUILDER.comment("Maximum time in seconds (must be higher than the minimum)").defineInRange("reset_calm_max", 120, 0, 60 * 60 * 24);
        RESET_CALM_COOLDOWN_CHANCE = BUILDER.comment("Chance for a cooldown to occur").defineInRange("reset_calm_cooldown_chance", 0.4, 0, 1);
        RESET_CALM_COOLDOWN = BUILDER.comment("Cooldown length in seconds").defineInRange("reset_calm_cooldown", 180, 0, 60 * 60 * 24);
        BUILDER.comment("");
        BUILDER.comment("Influence how often the sounds will be played");
        RESET_NOISE_MIN = BUILDER.comment("Minimum time in seconds").defineInRange("reset_noise_min", 60, 0, 60 * 60 * 24 - 1);
        RESET_NOISE_MAX = BUILDER.comment("Maximum time in seconds (must be higher than the minimum)").defineInRange("reset_noise_max", 120, 0, 60 * 60 * 24);
        BUILDER.comment("");
        SPAWN_CHANCE_PER_TICK = BUILDER.comment("The spawn chance per tick (once the calm timer is finished)").defineInRange("spawn_chance_per_tick", 0.005, 0, 1);
        SPAWN_HEIGHT = BUILDER.comment("Height at which the Cave Dweller can start to spawn").define("spawn_height", 40);
        ALLOW_OPEN_SKY = BUILDER.comment("Whether the Cave Dweller can spawn the open or not").define("allow_open_sky", false);

        SPEC = BUILDER.build();
    }
}
