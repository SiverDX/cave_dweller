package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.Random;

public class CaveDwellerFleeGoal extends Goal {
    private final CaveDwellerEntity caveDweller;
    private final double speedModifier;

    private float currentTicksTillLeave;
    private float currentTicksTillFlee;
    private boolean shouldLeave;
    private double fleeX;
    private double fleeY;
    private double fleeZ;
    private int ticksUntilNextPathRecalculation;

    public CaveDwellerFleeGoal(final CaveDwellerEntity caveDweller, float pTicksTillLeave, double pSpeedModifier) {
        this.caveDweller = caveDweller;
        this.currentTicksTillLeave = pTicksTillLeave;
        this.currentTicksTillFlee = 10.0F;
        this.speedModifier = pSpeedModifier;
    }

    @Override
    public boolean canUse() {
        if (this.caveDweller.isInvisible()) {
            return false;
        } else if (this.caveDweller.rRollResult != 2) {
            return false;
        } else {
            return this.caveDweller.getTarget() != null;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (this.caveDweller.rRollResult != 2) {
            return false;
        } else {
            return this.caveDweller.getTarget() != null;
        }
    }

    @Override
    public void start() {
        this.getSpotToWalk();
        this.caveDweller.spottedByPlayer = false;
        this.shouldLeave = false;
    }

    @Override
    public void tick() {
        LivingEntity target = this.caveDweller.getTarget();

        if (this.shouldLeave && (!caveDweller.isPlayerLookingTowards(target) || !this.inPlayerLineOfSight())) {
            this.caveDweller.discard();
        }

        this.tickFleeClock();
        this.tickStareClock();

        if (this.currentTicksTillFlee <= 0.0F) {
            this.fleeTick();
            this.caveDweller.isFleeing = true;
            this.caveDweller.getEntityData().set(CaveDwellerEntity.FLEEING_ACCESSOR, true);
        } else if (target != null) {
            this.caveDweller.getLookControl().setLookAt(target, 180.0F, 1.0F);
        }
    }

    private boolean inPlayerLineOfSight() {
        return this.caveDweller.getTarget() != null && this.caveDweller.getTarget().hasLineOfSight(this.caveDweller);
    }

    private void getSpotToWalk() {
        Random rand = new Random();
        double randX = rand.nextDouble() - 0.5;
        double randY = rand.nextInt(64) - 32;
        double randZ = rand.nextDouble() - 0.5;

        if (randX > 0.0) {
            this.fleeX = (this.caveDweller.getX() + 1.0) * 64.0;
        } else {
            this.fleeX = (this.caveDweller.getX() - 1.0) * 64.0;
        }

        this.fleeY = this.caveDweller.getY() + randY;
        if (randZ > 0.0) {
            this.fleeZ = (this.caveDweller.getZ() + 1.0) * 64.0;
        } else {
            this.fleeZ = (this.caveDweller.getZ() - 1.0) * 64.0;
        }

        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(this.fleeX, this.fleeY, this.fleeZ);

        while(blockpos$mutableblockpos.getY() > this.caveDweller.level.getMinBuildHeight() && !this.caveDweller.level.getBlockState(blockpos$mutableblockpos).getMaterial().blocksMotion()) {
            blockpos$mutableblockpos.move(Direction.DOWN);
        }
    }

    public void tickStareClock() {
        --this.currentTicksTillLeave;

        if (this.currentTicksTillLeave < 0.0F) {
            this.shouldLeave = true;
        }
    }

    void tickFleeClock() {
        --this.currentTicksTillFlee;
    }

    public void fleeTick() {
        this.caveDweller.playFleeSound();
        this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);

        if (this.ticksUntilNextPathRecalculation == 0) {
            this.ticksUntilNextPathRecalculation = 2;
            if (!this.caveDweller.getNavigation().moveTo(this.fleeX, this.fleeY, this.fleeZ, this.speedModifier)) {
                this.ticksUntilNextPathRecalculation += 2;
            }

            this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
        }
    }
}
