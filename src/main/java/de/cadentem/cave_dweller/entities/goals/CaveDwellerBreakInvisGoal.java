package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import de.cadentem.cave_dweller.util.Utils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class CaveDwellerBreakInvisGoal extends Goal {
    private final CaveDwellerEntity caveDweller;

    public CaveDwellerBreakInvisGoal(final CaveDwellerEntity caveDweller) {
        this.caveDweller = caveDweller;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = Utils.getValidTarget(caveDweller);
        return caveDweller.isInvisible() && (!caveDweller.inLineOfSight(target) || !caveDweller.isLookingAtMe(target));
    }

    @Override
    public void start() {
        super.start();
        caveDweller.setInvisible(false);
    }
}