package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class CaveDwellerTargetSeesMeGoal extends NearestAttackableTargetGoal<Player> {
    private final CaveDwellerEntity caveDweller;

    private Player pendingTarget;

    public CaveDwellerTargetSeesMeGoal(final CaveDwellerEntity caveDweller) {
        super(caveDweller, Player.class, false);
        this.caveDweller = caveDweller;
    }

    @Override
    public boolean canUse() {
        if (this.caveDweller.isInvisible()) {
            return false;
        } else {
            this.setPendingTarget(this.caveDweller.level.getNearestPlayer(this.caveDweller, 200.0));

            if (this.pendingTarget == null) {
                return false;
            } else if (this.pendingTarget.isCreative()) {
                return false;
            } else {
                return this.inPlayerLineOfSight() && caveDweller.isLookingAtMe(pendingTarget);
            }
        }
    }

    @Override
    public void start() {
        super.target = this.pendingTarget;
        this.caveDweller.setTarget(this.pendingTarget);
        this.caveDweller.spottedByPlayer = true;
        this.caveDweller.getEntityData().set(CaveDwellerEntity.SPOTTED_ACCESSOR, true);
        this.caveDweller.reRoll(); // TODO :: Add reroll afer x seconds?
        super.start();
    }

    @Override
    public void stop() {
        this.pendingTarget = null;
        super.stop();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.pendingTarget.isCreative()) {
            return false;
        } else {
            return this.pendingTarget != null;
        }
    }

    @Override
    public void tick() {
        super.tick();
    }

    private void setPendingTarget(@Nullable final Player pendingTarget) {
        this.pendingTarget = pendingTarget;
    }

    private boolean inPlayerLineOfSight() {
        return this.pendingTarget != null && this.pendingTarget.hasLineOfSight(this.caveDweller);
    }
}
