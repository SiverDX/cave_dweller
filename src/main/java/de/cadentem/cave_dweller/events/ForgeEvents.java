package de.cadentem.cave_dweller.events;

import de.cadentem.cave_dweller.CaveDweller;
import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
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

    /** Not all dimensions are immediately loaded */
    @SubscribeEvent
    public static void handleDimensionChange(final EntityTravelToDimensionEvent event) {
        CaveDweller.RELOAD_MISSING = true;
    }

    /** Prevent knockback while climbing */
    @SubscribeEvent
    public static void handleKnockback(final LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity instanceof CaveDwellerEntity caveDweller) {
            if (caveDweller.isClimbing()) {
                event.setCanceled(true);
            }
        }
    }
}
