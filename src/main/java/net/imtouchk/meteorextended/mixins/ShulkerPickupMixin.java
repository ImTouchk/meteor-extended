package net.imtouchk.meteorextended.mixins;

import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.imtouchk.meteorextended.modules.BaseRaider;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ShulkerPickupMixin {
    @Shadow
    public abstract ItemStack getStack();

    @Inject(at = @At("HEAD"), method = "onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V")
    private void onPlayerCollision(PlayerEntity player, CallbackInfo ci) {
//        if(BaseRaider.isShulkerBox(getStack().getItem())) {
//            var modules = Systems.get(Modules.class);
//            var stashLooter = (BaseRaider)modules.get("StashLooter");
//            if(stashLooter != null)
//                stashLooter.onShulkerPickedUp();
//        }
    }
}
