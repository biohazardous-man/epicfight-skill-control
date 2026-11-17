package dev.skillcontrol.mixin;

import dev.skillcontrol.SkillPolicy;
import java.util.stream.Stream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.world.capabilities.skill.PlayerSkills;

@Mixin(value = PlayerSkills.class, remap = false)
public abstract class PlayerSkillsMixin {
    @Inject(method = "listAcquiredSkills", at = @At("RETURN"), cancellable = true)
    private void skillcontrol$hideLearnedSkills(CallbackInfoReturnable<Stream<Skill>> callback) {
        if (SkillPolicy.hasDisabledSkills()) {
            callback.setReturnValue(callback.getReturnValue().filter(skill -> !SkillPolicy.isDisabled(skill)));
        }
    }
}
