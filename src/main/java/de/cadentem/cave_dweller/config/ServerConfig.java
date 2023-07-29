package de.cadentem.cave_dweller.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class ServerConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // Timers
    public static ForgeConfigSpec.IntValue CAN_SPAWN_MIN;
    public static ForgeConfigSpec.IntValue CAN_SPAWN_MAX;
    public static ForgeConfigSpec.IntValue CAN_SPAWN_COOLDOWN;
    public static ForgeConfigSpec.DoubleValue CAN_SPAWN_COOLDOWN_CHANCE;
    public static ForgeConfigSpec.IntValue RESET_NOISE_MIN;
    public static ForgeConfigSpec.IntValue RESET_NOISE_MAX;
    public static ForgeConfigSpec.IntValue TIME_UNTIL_LEAVE;
    public static ForgeConfigSpec.IntValue TIME_UNTIL_LEAVE_CHASE;

    // Spawn Conditions
    public static ForgeConfigSpec.DoubleValue SPAWN_CHANCE_PER_TICK;
    public static ForgeConfigSpec.ConfigValue<Integer> SPAWN_HEIGHT;
    public static ForgeConfigSpec.BooleanValue ALLOW_SURFACE_SPAWN;
    public static ForgeConfigSpec.IntValue SKY_LIGHT_LEVEL;
    public static ForgeConfigSpec.IntValue BLOCK_LIGHT_LEVEL;
    public static ForgeConfigSpec.ConfigValue<List<String>> DIMENSION_WHITELIST;
    public static ForgeConfigSpec.BooleanValue SURFACE_BIOMES_IS_WHITELIST;
    public static ForgeConfigSpec.IntValue MAXIMUM_AMOUNT;

    // Behaviour
    public static ForgeConfigSpec.IntValue SPOTTING_RANGE;
    public static ForgeConfigSpec.BooleanValue CAN_CLIMB;
    public static ForgeConfigSpec.BooleanValue CAN_BREAK_DOOR;
    public static ForgeConfigSpec.IntValue BREAK_DOOR_TIME;
    public static ForgeConfigSpec.BooleanValue ALLOW_RIDING;
    public static ForgeConfigSpec.BooleanValue TARGET_INVISIBLE;

    // Attributes
    public static ForgeConfigSpec.DoubleValue MAX_HEALTH;
    public static ForgeConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static ForgeConfigSpec.DoubleValue ATTACK_SPEED;
    public static ForgeConfigSpec.DoubleValue MOVEMENT_SPEED;
    public static ForgeConfigSpec.DoubleValue DEPTH_STRIDER_BONUS;

    static {
        BUILDER.push("Timers");
        CAN_SPAWN_MIN = BUILDER.comment("Minimum time between spawns in seconds").defineInRange("can_spawn_min", 300, 0, 60 * 60 * 24);
        CAN_SPAWN_MAX = BUILDER.comment("Maximum time between spawns in seconds").defineInRange("can_spawn_max", 600, 0, 60 * 60 * 24);
        CAN_SPAWN_COOLDOWN_CHANCE = BUILDER.comment("Chance for a spawn cooldown to occur").defineInRange("can_spawn_cooldown_chance", 0.4, 0, 1);
        CAN_SPAWN_COOLDOWN = BUILDER.comment("Spawn cooldown length in seconds").defineInRange("can_spawn_cooldown", 1200, 0, 60 * 60 * 24);
        RESET_NOISE_MIN = BUILDER.comment("Minimum time between noise occurrences in seconds").defineInRange("reset_noise_min", 240, 0, 60 * 60 * 24);
        RESET_NOISE_MAX = BUILDER.comment("Maximum time between noise occurrences in seconds").defineInRange("reset_noise_max", 360, 0, 60 * 60 * 24);
        TIME_UNTIL_LEAVE = BUILDER.comment("Time (in seconds) it takes for the Cave Dweller to leave").defineInRange("time_until_leave", 300, 1, 6000);
        TIME_UNTIL_LEAVE_CHASE = BUILDER.comment("Time (in seconds) it takes for the Cave Dweller to leave once a chase begins").defineInRange("time_until_leave_chase", 30, 1, 600);
        BUILDER.pop();

        BUILDER.push("Spawn Conditions");
        SPAWN_CHANCE_PER_TICK = BUILDER.comment("The spawn chance per tick (once the spawn timer is finished)").defineInRange("spawn_chance_per_tick", 0.005, 0, 1);
        SPAWN_HEIGHT = BUILDER.comment("Depth at which the Cave Dweller can start to spawn").define("spawn_height", 40);
        ALLOW_SURFACE_SPAWN = BUILDER.comment("Whether the Cave Dweller can spawn on the surface or not").define("allow_surface_spawn", false);
        SKY_LIGHT_LEVEL = BUILDER.comment("The maximum sky light level the Cave Dweller can spawn at").defineInRange("sky_light_level", 8, 0, 15);
        BLOCK_LIGHT_LEVEL = BUILDER.comment("The maximum block light level the Cave Dweller can spawn at").defineInRange("block_light_level", 15, 0, 15);
        SURFACE_BIOMES_IS_WHITELIST = BUILDER.comment("Use the surface biome list either as white- or blacklist").define("surface_biomes_is_whitelist", true);
        MAXIMUM_AMOUNT = BUILDER.comment("The maximum amount of cave dwellers which can exist at the same time").defineInRange("maximum_amoount", 1, 0, 100);
        BUILDER.pop();

        BUILDER.push("Behaviour");
        SPOTTING_RANGE = BUILDER.comment("The distance in blocks at which the Cave Dweller can detect whether a player is looking at it or not").defineInRange("spotting_range", 60, 0, 128);
        CAN_CLIMB = BUILDER.comment("Whether the cave dweller can climb or not").define("can_climb", true);
        CAN_BREAK_DOOR = BUILDER.comment("Whether the cave dweller can break down doors or not").define("can_break_door", true);
        BREAK_DOOR_TIME = BUILDER.comment("Time (in seconds) it takes the Cave Dweller to break down a door").defineInRange("break_door_time", 3, 1, 60);
        ALLOW_RIDING = BUILDER.comment("Allow the Cave Dweller to follow vanilla riding logic (e.g. boats)").define("allow_riding", false);
        TARGET_INVISIBLE = BUILDER.comment("Whether invisible players can be targets or not").define("target_invisible", true);
        BUILDER.pop();

        BUILDER.push("Attributes");
        MAX_HEALTH = BUILDER.comment("Maximum health").defineInRange("maximum_health", 60.0, 1, 100_000);
        ATTACK_DAMAGE = BUILDER.comment("Attack damage").defineInRange("attack_damage", 6.0, 0, 1_000);
        ATTACK_SPEED = BUILDER.comment("Attack speed").defineInRange("attack_speed", 0.35, 0, 10);
        MOVEMENT_SPEED = BUILDER.comment("Movement speed").defineInRange("movement_speed", 0.5, 0, 5);
        DEPTH_STRIDER_BONUS = BUILDER.comment("Depth Strider (movement speed in water) bonus").defineInRange("depth_strider_bonus", 1.5, 0, 3);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}