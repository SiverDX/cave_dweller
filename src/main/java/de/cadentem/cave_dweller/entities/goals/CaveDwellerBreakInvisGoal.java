package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

public class CaveDwellerBreakInvisGoal extends Goal {
    private final CaveDwellerEntity caveDweller;

    private Player pendingTarget;

    public CaveDwellerBreakInvisGoal(final CaveDwellerEntity caveDweller) {
        this.caveDweller = caveDweller;
    }

    @Override
    public boolean canUse() {
        this.pendingTarget = this.caveDweller.level.getNearestPlayer(this.caveDweller, 200.0);
        return caveDweller.isInvisible() && (!this.inPlayerLineOfSight() || !caveDweller.isLookingAtMe(pendingTarget));
    }

    @Override
    public void start() {
        super.start();
        this.caveDweller.setInvisible(false);
    }

    private boolean inPlayerLineOfSight() {
        return this.pendingTarget != null && this.pendingTarget.hasLineOfSight(this.caveDweller);
    }
}