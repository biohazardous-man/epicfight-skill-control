package dev.skillcontrol.mixin;

import dev.skillcontrol.SkillPolicy;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LootTable.class, remap = false)
public abstract class LootTableMixin {
    @Inject(method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN"))
    private void skillcontrol$filterFinalLoot(LootContext context, CallbackInfoReturnable<ObjectArrayList<ItemStack>> callback) {
        if (SkillPolicy.hasDisabledSkills()) callback.getReturnValue().removeIf(SkillPolicy::isDisabled);
    }
}
