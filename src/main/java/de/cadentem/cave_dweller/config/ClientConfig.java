package de.cadentem.cave_dweller.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static ForgeConfigSpec.ConfigValue<Boolean> USE_UPDATED_TEXTURES;

    static {
        USE_UPDATED_TEXTURES = BUILDER.comment("Use updated textures by the user 'Frogballoon'").define("use_updated_textures", true);

        SPEC = BUILDER.build();
    }
}
