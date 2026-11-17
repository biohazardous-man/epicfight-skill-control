package dev.skillcontrol.mixin;

import dev.skillcontrol.SkillPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.world.capabilities.item.WeaponCapability;

@Mixin(value = WeaponCapability.class, remap = false)
public abstract class WeaponCapabilityMixin {
    @Inject(method = {"getInnateSkill", "getPassiveSkill"}, at = @At("RETURN"), cancellable = true)
    private void skillcontrol$hideWeaponSkill(CallbackInfoReturnable<Skill> callback) {
        if (SkillPolicy.isDisabled(callback.getReturnValue())) callback.setReturnValue(null);
    }
}
