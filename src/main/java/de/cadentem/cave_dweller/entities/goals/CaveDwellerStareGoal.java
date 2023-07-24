package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

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
    public boolean requiresUpdateEveryTick() {
        return true;
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
            // TODO :: If the player stares for too long teleport to a random spot or apply blindness? (and reset timer)
            if (new Random().nextDouble() < 0.5 && target != null && mob.distanceTo(target) < 15 /* Triggers before TargetTooClose goal can be triggered */) {
                mob.pickRoll(List.of(Roll.CHASE, Roll.FLEE));
            } else if (isTargetNotLooking()) {
                // Once the player stops looking at it
                mob.playDisappearSound();
                mob.discard();
            }
        }

        if (target != null) {
            // Move towards the player when they are not looking
            if (isTargetNotLooking()) {
                this.mob.getNavigation().moveTo(target, 0.5);
            } else {
                // Just stopping the navigation does not work
                mob.getNavigation().stop();
                mob.setDeltaMovement(new Vec3(0, 0, 0));
            }

            mob.getLookControl().setLookAt(target);
        }
    }

    private void tickStareClock() {
        --ticksUntilLeave;

        if (ticksUntilLeave <= 0.0F) {
            shouldLeave = true;
        }
    }

    private boolean isTargetNotLooking() {
        LivingEntity target = mob.getTarget();

        if (target == null) {
            return false;
        }

        Vec3 viewVector = target.getViewVector(1.0F).normalize();
        Vec3 difference = new Vec3(mob.getX() - target.getX(), mob.getEyeY() - target.getEyeY(), mob.getZ() - target.getZ());
        difference = difference.normalize();
        double dot = viewVector.dot(difference);

        return dot < 0; /* TODO :: Increase to 0.1 so it stops moving before player can notice that? */
    }
}