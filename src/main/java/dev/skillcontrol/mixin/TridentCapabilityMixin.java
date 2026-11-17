package dev.skillcontrol.mixin;

import dev.skillcontrol.SkillPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.world.capabilities.item.TridentCapability;

@Mixin(value = TridentCapability.class, remap = false)
public abstract class TridentCapabilityMixin {
    @Inject(method = "getInnateSkill", at = @At("RETURN"), cancellable = true)
    private void skillcontrol$hideTridentSkill(CallbackInfoReturnable<Skill> callback) {
        if (SkillPolicy.isDisabled(callback.getReturnValue())) callback.setReturnValue(null);
    }
}
