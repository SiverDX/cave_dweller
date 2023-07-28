package de.cadentem.cave_dweller.mixin;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GroundPathNavigation.class)
public abstract class MixinGroundPathNavigation extends PathNavigation {
    public MixinGroundPathNavigation(final Mob mob, final Level level) {
        super(mob, level);
    }

    /**
     * Won't squeeze through 1x1 blocks without this<br>
     * Only required due to the climbing mechanic - but checking for climbing here seems to cause other issues
     */
    @Inject(method = "canUpdatePath", at = @At("RETURN"), cancellable = true)
    public void canUpdateWhenClimbing(final CallbackInfoReturnable<Boolean> cir) {
        if (mob instanceof CaveDwellerEntity caveDweller) {
            if (!cir.getReturnValue() && caveDweller.getEntityData().get(CaveDwellerEntity.CRAWLING_ACCESSOR)) {
                cir.setReturnValue(true);
            }
        }
    }
}
