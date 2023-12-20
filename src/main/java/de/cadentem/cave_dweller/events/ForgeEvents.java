package de.cadentem.cave_dweller.events;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import de.cadentem.cave_dweller.CaveDweller;
import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = CaveDweller.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvents {
    public static final ConcurrentHashMap<Integer, Integer> HIT_COUNTER = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void handleHurt(final LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity instanceof CaveDwellerEntity caveDweller) {
            boolean skipDamage = event.getSource() == DamageSource.DROWN || event.getSource() == DamageSource.OUT_OF_WORLD || event.getSource() == DamageSource.IN_WALL;
            boolean increaseCounter = event.getSource() == DamageSource.DROWN || event.getSource() == DamageSource.OUT_OF_WORLD;

            if (skipDamage) {
                if (increaseCounter && !caveDweller.level.isClientSide()) {
                    HIT_COUNTER.merge(caveDweller.getId(), 1, Integer::sum);

                    if (HIT_COUNTER.get(caveDweller.getId()) > 5) {
                        HIT_COUNTER.remove(caveDweller.getId());

                        boolean couldTeleport = caveDweller.teleportToTarget();
                        caveDweller.hurtMarked = true;

                        if (!couldTeleport) {
                            String key = caveDweller.level.dimension().location().toString();

                            if (ServerConfig.isValidDimension(key)) {
                                int spawnDelta = (int) (ServerConfig.CAN_SPAWN_MIN.get() * 0.3);
                                int noiseDelta = (int) (ServerConfig.RESET_NOISE_MIN.get() * 0.3);
                                CaveDweller.speedUpTimers(key, spawnDelta, noiseDelta);
                            }

                            caveDweller.disappear();
                        }
                    }
                }

                event.setCanceled(true);
            } else if (event.getSource() == DamageSource.FALL) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Not all dimensions are immediately loaded
     */
    @SubscribeEvent
    public static void handleDimensionChange(final EntityTravelToDimensionEvent event) {
        CaveDweller.RELOAD_MISSING = true;
    }

    /**
     * Prevent knockback while climbing
     */
    @SubscribeEvent
    public static void handleKnockback(final LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity instanceof CaveDwellerEntity caveDweller) {
            if (caveDweller.isClimbing()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void registerEvents(final RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("cave_dweller").requires(source -> source.hasPermission(2));

        /* TODO
            Does not work - the cache gets reset but it reads some old state of the file?
        builder.then(Commands.literal("reload")
                .executes(context -> {
                    ServerConfig.SPEC.afterReload();
                    CaveDweller.RELOAD_ALL = true;
                    context.getSource().sendSuccess(Component.literal("Server configuration has been reloaded"), true);
                    return 1;
                })
        );
        */

        builder.then(Commands.literal("fast_forward")
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .then(Commands.argument("spawn_delta_ticks", IntegerArgumentType.integer())
                                .then(Commands.argument("noise_delta_ticks", IntegerArgumentType.integer())
                                        .executes(context -> {
                                            String dimension = DimensionArgument.getDimension(context, "dimension").dimension().location().toString();
                                            int spawnDelta = IntegerArgumentType.getInteger(context, "spawn_delta_ticks");
                                            int noiseDelta = IntegerArgumentType.getInteger(context, "noise_delta_ticks");

                                            boolean wasSuccessful = CaveDweller.speedUpTimers(dimension, spawnDelta, noiseDelta);

                                            if (wasSuccessful) {
                                                context.getSource().sendSuccess(Component.literal("Timer has been successfully changed"), true);
                                            } else {
                                                context.getSource().sendFailure(Component.literal("Timer for dimension [" + dimension + "] does not exist"));
                                            }

                                            return 1;
                                        })
                                )
                        )
                )
        );

        builder.then(Commands.literal("get_target")
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(context -> {
                            String dimension = DimensionArgument.getDimension(context, "dimension").dimension().location().toString();
                            Entity currentVictim = CaveDweller.getCurrentVictim(dimension);

                            if (currentVictim != null) {
                                context.getSource().sendSuccess(Component.literal(currentVictim.toString()), true);
                            } else {
                                context.getSource().sendFailure(Component.literal("Timer for dimension [" + dimension + "] does not exist or has no target"));
                            }

                            return 1;
                        })
                )
        );

        builder.then(Commands.literal("get_timer")
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .then(Commands.argument("type", StringArgumentType.string()).suggests((context, suggestionsBuilder) -> SharedSuggestionProvider.suggest(new String[]{"spawn", "noise"}, suggestionsBuilder))
                                .executes(context -> {
                                    String dimension = DimensionArgument.getDimension(context, "dimension").dimension().location().toString();
                                    String type = StringArgumentType.getString(context, "type");

                                    Pair<Integer, Integer> timer = CaveDweller.getTimer(dimension, type);

                                    if (timer.getFirst() != -1 && timer.getSecond() != -1) {
                                        double currentSeconds = timer.getFirst() / 20d;
                                        double targetSeconds = timer.getSecond() / 20d;

                                        context.getSource().sendSuccess(Component.literal(currentSeconds + " / " + targetSeconds + " seconds"), true);
                                    } else {
                                        context.getSource().sendFailure(Component.literal("Timer for dimension [" + dimension + "] does not exist"));
                                    }

                                    return 1;
                                })
                        )
                )
        );

        event.getDispatcher().register(builder);
    }
}
