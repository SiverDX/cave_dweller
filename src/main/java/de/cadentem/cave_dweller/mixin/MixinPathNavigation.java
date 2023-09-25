package de.cadentem.cave_dweller.mixin;

import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(PathNavigation.class)
public abstract class MixinPathNavigation {
    @Unique private boolean cave_dweller$wasCrawling;

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"))
    public void setCrawling_true(final Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange, final CallbackInfoReturnable<Path> callback) {
        if (mob instanceof CaveDwellerEntity caveDweller) {
            cave_dweller$wasCrawling = caveDweller.isCrawling();
            caveDweller.setCrawling(true);
        }
    }

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"))
    public void setCrawling_false(final Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange, final CallbackInfoReturnable<Path> callback) {
        if (mob instanceof CaveDwellerEntity caveDweller) {
            caveDweller.setCrawling(cave_dweller$wasCrawling);
        }
    }

    @Shadow @Final protected Mob mob;
}
