package dev.skillcontrol.mixin;

import dev.skillcontrol.SkillPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.event.types.player.SkillCastEvent;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

@Mixin(value = SkillContainer.class, remap = false)
public abstract class SkillContainerMixin {
    @Shadow public abstract Skill getSkill();

    @ModifyVariable(method = "setSkill(Lyesman/epicfight/skill/Skill;Z)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Skill skillcontrol$filterInstallation(Skill skill) {
        return SkillPolicy.allowed(skill);
    }

    @ModifyVariable(method = "setSkillRemote", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Skill skillcontrol$filterRemoteInstallation(Skill skill) {
        return SkillPolicy.allowed(skill);
    }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void skillcontrol$denyCasting(PlayerPatch<?> executor, SkillCastEvent event, CallbackInfoReturnable<Boolean> callback) {
        if (SkillPolicy.isDisabled(getSkill())) callback.setReturnValue(false);
    }
}
