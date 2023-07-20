package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class CaveDwellerStareGoal extends Goal {
    private final CaveDwellerEntity caveDweller;
    private float currentTicksTillLeave;
    private boolean shouldLeave;

    public CaveDwellerStareGoal(final CaveDwellerEntity caveDweller, float pTicksTillLeave) {
        this.caveDweller = caveDweller;
        this.currentTicksTillLeave = pTicksTillLeave;
    }

    @Override
    public boolean canUse() {
        if (this.caveDweller.isInvisible()) {
            return false;
        } else if (this.caveDweller.getTarget() == null) {
            return false;
        } else {
            return this.caveDweller.reRollResult == 1;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (this.caveDweller.getTarget() == null) {
            return false;
        } else {
            return this.caveDweller.reRollResult == 1;
        }
    }

    @Override
    public void start() {
        this.shouldLeave = false;
    }

    @Override
    public void tick() {
        this.tickStareClock();
        LivingEntity target = this.caveDweller.getTarget();

        if (this.shouldLeave && (!this.caveDweller.isLookingAtMe(target) || !this.inPlayerLineOfSight())) {
            this.caveDweller.playDisappearSound();
            this.caveDweller.discard();
        }

        if (target != null) {
            this.caveDweller.getLookControl().setLookAt(target, 180.0F, 1.0F);
        }
    }

    private void tickStareClock() {
        --this.currentTicksTillLeave;

        if (this.currentTicksTillLeave <= 0.0F) {
            this.shouldLeave = true;
        }
    }

    private boolean inPlayerLineOfSight() {
        LivingEntity pendingTarget = this.caveDweller.getTarget();
        return pendingTarget != null && pendingTarget.hasLineOfSight(this.caveDweller);
    }
}