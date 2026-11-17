package dev.skillcontrol.mixin;

import dev.skillcontrol.SkillPolicy;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.item.SkillBookItem;

@Mixin(value = SkillBookItem.class, remap = false)
public abstract class SkillBookItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void skillcontrol$denyBookUse(Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> callback) {
        ItemStack stack = player.getItemInHand(hand);
        if (SkillPolicy.isDisabled(stack)) callback.setReturnValue(InteractionResultHolder.fail(stack));
    }
}
