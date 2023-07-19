package de.cadentem.cave_dweller.entities.goals;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

public class CaveDwellerStrollGoal extends WaterAvoidingRandomStrollGoal {
    private final CaveDwellerEntity caveDweller;

    public CaveDwellerStrollGoal(final CaveDwellerEntity caveDweller, double pSpeedModifier) {
        super(caveDweller, pSpeedModifier);
        this.caveDweller = caveDweller;
    }

    @Override
    public boolean canUse() {
        return super.canUse() && this.caveDweller.reRollResult == 3;
    }

    @Override
    public boolean canContinueToUse() {
        return super.canContinueToUse() && this.caveDweller.reRollResult == 3;
    }
}
