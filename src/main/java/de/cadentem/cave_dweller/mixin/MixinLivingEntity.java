package de.cadentem.cave_dweller.mixin;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {
    /** Give the Cave Dweller the depth strider effect */
    @ModifyVariable(method = "travel", at = @At("STORE"), name = "f6")
    public float fakeDepthStrider(float depthStriderBonus) {
        LivingEntity livingEntity = (LivingEntity) (Object) this;

        if (livingEntity instanceof CaveDwellerEntity) {
            return ServerConfig.DEPTH_STRIDER_BONUS.get().floatValue();
        }

        return depthStriderBonus;
    }
}
