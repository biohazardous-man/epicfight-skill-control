package dev.skillcontrol.mixin;

import dev.skillcontrol.SkillPolicy;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.network.EpicFightServerBoundPayloadHandler;
import yesman.epicfight.network.client.CPChangeSkill;
import yesman.epicfight.skill.Skill;

@Mixin(value = EpicFightServerBoundPayloadHandler.class, remap = false)
public interface ServerPayloadMixin {
    @Inject(method = "handleChangeSkill", at = @At("HEAD"), cancellable = true)
    private static void skillcontrol$denyLearning(CPChangeSkill data, IPayloadContext context, CallbackInfo callback) {
        if (SkillPolicy.isDisabled(Skill.skillOrNull(data.skill()))) callback.cancel();
    }
}
