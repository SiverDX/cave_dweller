package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;

public class CustomHurtByTargetGoal extends HurtByTargetGoal {
    public CustomHurtByTargetGoal(final PathfinderMob mob, final Class<?>... toIgnoreDamage) {
        super(mob, toIgnoreDamage);
    }

    @Override
    public boolean canUse() {
        if (mob instanceof CaveDwellerEntity caveDweller) {
            if (caveDweller.currentRoll == Roll.CHASE || caveDweller.currentRoll == Roll.FLEE) {
                // Since this would (in theory) cause a target change
                return mob.getRandom().nextDouble() < 0.2 && super.canUse();
            }
        }

        return super.canUse();
    }

    @Override
    public void start() {
        super.start();

        if (mob.getLastHurtByMob() instanceof Player) {
            // Sets the target to null - so either extend the duration or override the stop()
            unseenMemoryTicks = ServerConfig.TIME_UNTIL_LEAVE.get();
        } else {
            // Default
            unseenMemoryTicks = 300;
        }
    }
}
