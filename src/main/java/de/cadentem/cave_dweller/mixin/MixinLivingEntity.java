package de.cadentem.cave_dweller.mixin;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {
    /** Give the Cave Dweller the depth strider effect */
    @ModifyVariable(method = "travel", at = @At("STORE"), name = "f6")
    public float fakeDepthStrider(float depthStriderBonus) {
        LivingEntity livingEntity = (LivingEntity) (Object) this;

        if (livingEntity instanceof CaveDwellerEntity) {
            return ServerConfig.DEPTH_STRIDER_BONUS.get().floatValue();
        }

        return depthStriderBonus;
    }

    /** Currently needed to prevent the mob from sliding to its previously set target location */
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    public void pleaseStopMoving(final Vec3 travelVector, final CallbackInfo callback) {
        LivingEntity livingEntity = (LivingEntity) (Object) this;

        if (livingEntity instanceof CaveDwellerEntity caveDweller) {
            if (caveDweller.pleaseStopMoving) {
                callback.cancel();
            }
        }
    }
}
