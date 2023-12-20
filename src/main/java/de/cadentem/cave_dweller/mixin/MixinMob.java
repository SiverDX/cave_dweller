package de.cadentem.cave_dweller.mixin;

import de.cadentem.cave_dweller.config.ServerConfig;
import de.cadentem.cave_dweller.entities.CaveDwellerEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MixinMob {
    @Inject(method = "maybeDisableShield", at = @At("HEAD"), cancellable = true)
    private void disableShield(final Player player, final ItemStack mobStack, final ItemStack playerStack, final CallbackInfo callback) {
        if (ServerConfig.CAN_DISABLE_SHIELDS.get() && (Object) this instanceof CaveDwellerEntity && !(mobStack.getItem() instanceof AxeItem)) {
            maybeDisableShield(player, Items.DIAMOND_AXE.getDefaultInstance(), playerStack);
            callback.cancel();
        }
    }

    @Shadow protected abstract void maybeDisableShield(Player pPlayer, ItemStack pMobItemStack, ItemStack pPlayerItemStack);
}
