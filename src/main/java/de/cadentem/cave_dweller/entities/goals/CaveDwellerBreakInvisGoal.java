package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class CaveDwellerBreakInvisGoal extends Goal {
    private final CaveDwellerEntity caveDweller;

    public CaveDwellerBreakInvisGoal(final CaveDwellerEntity caveDweller) {
        this.caveDweller = caveDweller;
    }

    @Override
    public boolean canUse() {
        return caveDweller.isInvisible() && !caveDweller.targetIsFacingMe;
    }

    @Override
    public void start() {
        super.start();
        caveDweller.setInvisible(false);
    }
}