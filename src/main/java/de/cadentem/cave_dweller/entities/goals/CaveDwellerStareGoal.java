package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.List;
import java.util.Random;

public class CaveDwellerStareGoal extends Goal {
    private final CaveDwellerEntity mob;
    private float ticksUntilLeave;
    private boolean shouldLeave;

    public CaveDwellerStareGoal(final CaveDwellerEntity mob, float ticksUntilLeave) {
        this.mob = mob;
        this.ticksUntilLeave = ticksUntilLeave;
    }

    @Override
    public boolean canUse() {
        if (mob.isInvisible()) {
            return false;
        } else if (mob.getTarget() == null) {
            return false;
        } else {
            return mob.currentRoll == Roll.STARE;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.getTarget() == null) {
            return false;
        } else {
            return mob.currentRoll == Roll.STARE;
        }
    }

    @Override
    public void start() {
        shouldLeave = false;
    }

    @Override
    public void stop() {
        super.stop();
        mob.getEntityData().set(CaveDwellerEntity.SPOTTED_ACCESSOR, false);
    }

    @Override
    public void tick() {
        tickStareClock();
        LivingEntity target = mob.getTarget();

        if (shouldLeave) {
            if (new Random().nextDouble() < 0.5) {
                mob.pickRoll(List.of(Roll.CHASE, Roll.FLEE));
            } else if (!mob.isLookingAtMe(target) || !inPlayerLineOfSight()) {
                // Once the player stops looking at it
                mob.playDisappearSound();
                mob.discard();
            }
        }

        if (target != null) {
            mob.getLookControl().setLookAt(target);
        }
    }

    private void tickStareClock() {
        --ticksUntilLeave;

        if (ticksUntilLeave <= 0.0F) {
            shouldLeave = true;
        }
    }

    private boolean inPlayerLineOfSight() {
        LivingEntity pendingTarget = mob.getTarget();
        return pendingTarget != null && pendingTarget.hasLineOfSight(mob);
    }
}