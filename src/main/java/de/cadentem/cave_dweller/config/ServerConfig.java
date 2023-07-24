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
    public static ForgeConfigSpec.ConfigValue<Boolean> ALLOW_SURFACE_SPAWN;
    public static ForgeConfigSpec.ConfigValue<Integer> SKY_LIGHT_LEVEL;
    public static ForgeConfigSpec.ConfigValue<Integer> BLOCK_LIGHT_LEVEL;

    public static ForgeConfigSpec.ConfigValue<Integer> SPOTTING_RANGE;
    public static ForgeConfigSpec.ConfigValue<Integer> TIME_STARING;
    public static ForgeConfigSpec.ConfigValue<Integer> TIME_UNTIL_LEAVE;
    public static ForgeConfigSpec.ConfigValue<Integer> TIME_UNTIL_LEAVE_CHASE;
    public static ForgeConfigSpec.ConfigValue<Double> DEPTH_STRIDER_BONUS;
    public static ForgeConfigSpec.ConfigValue<Boolean> CAN_CLIMB;
    public static ForgeConfigSpec.ConfigValue<Boolean> CAN_BREAK_DOOR;
    public static ForgeConfigSpec.ConfigValue<Integer> BREAK_DOOR_TIME;

    public static ForgeConfigSpec.ConfigValue<Double> MAX_HEALTH;
    public static ForgeConfigSpec.ConfigValue<Double> ATTACK_DAMAGE;
    public static ForgeConfigSpec.ConfigValue<Double> ATTACK_SPEED;
    public static ForgeConfigSpec.ConfigValue<Double> MOVEMENT_SPEED;
    public static ForgeConfigSpec.ConfigValue<Double> FOLLOW_RANGE;

    static {
        BUILDER.push("Spawn Timers");
        BUILDER.comment("Influence the time it takes for a cave dweller to spawn");
        RESET_CALM_MIN = BUILDER.comment("Minimum time between spawns in seconds").defineInRange("reset_calm_min", 300, 0, 60 * 60 * 24);
        RESET_CALM_MAX = BUILDER.comment("Maximum time between spawns in seconds").defineInRange("reset_calm_max", 600, 0, 60 * 60 * 24);
        RESET_CALM_COOLDOWN_CHANCE = BUILDER.comment("Chance for a spawn cooldown to occur").defineInRange("reset_calm_cooldown_chance", 0.4, 0, 1);
        RESET_CALM_COOLDOWN = BUILDER.comment("Spawn cooldown length in seconds").defineInRange("reset_calm_cooldown", 1200, 0, 60 * 60 * 24);
        BUILDER.pop();

        BUILDER.push("Sound Timers");
        RESET_NOISE_MIN = BUILDER.comment("Minimum time between noise occurrences in seconds").defineInRange("reset_noise_min", 240, 0, 60 * 60 * 24);
        RESET_NOISE_MAX = BUILDER.comment("Maximum time between noise occurrences in seconds").defineInRange("reset_noise_max", 360, 0, 60 * 60 * 24);
        BUILDER.pop();

        BUILDER.push("Spawn Conditions");
        SPAWN_CHANCE_PER_TICK = BUILDER.comment("The spawn chance per tick (once the calm timer is finished)").defineInRange("spawn_chance_per_tick", 0.005, 0, 1);
        SPAWN_HEIGHT = BUILDER.comment("Depth at which the Cave Dweller can start to spawn").define("spawn_height", 40);
        ALLOW_SURFACE_SPAWN = BUILDER.comment("Whether the Cave Dweller can spawn on the surface or not").define("allow_surface_spawn", false);
        SKY_LIGHT_LEVEL = BUILDER.comment("The maximum sky light level the Cave Dweller can spawn at").defineInRange("sky_light_level", 8, 0, 15);
        BLOCK_LIGHT_LEVEL = BUILDER.comment("The maximum block light level the Cave Dweller can spawn at").defineInRange("block_light_level", 15, 0, 15);
        BUILDER.pop();

        BUILDER.push("Behaviour");
        SPOTTING_RANGE = BUILDER.comment("The distance in blocks at which the Cave Dweller can detect whether a player is looking at it or not").defineInRange("spotting_range", 40, 0, 128);
        TIME_STARING = BUILDER.comment("Time (in seconds) the Cave Dweller will stare at the player (once spotted) before taking a new action or leaving").defineInRange("time_staring", 4, 0, 60);
        TIME_UNTIL_LEAVE = BUILDER.comment("Time (in seconds) it takes for the Cave Dweller to leave").defineInRange("time_until_leave", 300, 1, 6000);
        TIME_UNTIL_LEAVE_CHASE = BUILDER.comment("Time (in seconds) it takes for the Cave Dweller to leave once a chase begins").defineInRange("time_until_leave_chase", 30, 1, 600);
        DEPTH_STRIDER_BONUS = BUILDER.comment("Depth Strider speed bonus").defineInRange("depth_strider_bonus", 1.5, 0, 3);
        CAN_CLIMB = BUILDER.comment("Whether the cave dweller can climb or not").define("can_climb", true);
        CAN_BREAK_DOOR = BUILDER.comment("Whether the cave dweller can break down doors or not").define("can_break_door", true);
        BREAK_DOOR_TIME = BUILDER.comment("Time (in seconds) it takes the Cave Dweller to break down a door").defineInRange("break_door_time", 3, 1, 60);
        BUILDER.pop();

        BUILDER.push("Attributes");
        MAX_HEALTH = BUILDER.comment("Maximum health").defineInRange("maximum_health", 60.0, 1, 100_000);
        ATTACK_DAMAGE = BUILDER.comment("Attack damage").defineInRange("attack_damage", 6.0, 0, 1_000);
        ATTACK_SPEED = BUILDER.comment("Attack speed").defineInRange("attack_speed", 0.35, 0, 10);
        MOVEMENT_SPEED = BUILDER.comment("Movement speed").defineInRange("movement_speed", 0.5, 0, 5);
//        FOLLOW_RANGE = BUILDER.comment("").defineInRange("follow_range", 100.0, 0, 1_000);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
